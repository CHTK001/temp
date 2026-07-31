package com.chua.example.network.tshark;

import com.chua.common.support.lang.directory.SimplePolledListener;
import com.chua.common.support.lang.directory.WatcherEvent;
import com.chua.common.support.lang.directory.environment.DirectoryPollerEnvironment;
import com.chua.common.support.lang.algorithm.crypto.Hex;
import com.chua.common.support.network.protocol.ProtocolRestorer;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.network.support.tshark.PacketParserService;
import com.chua.network.support.tshark.PacketRecord;
import com.chua.network.support.tshark.TsharkPolledDirectory;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TShark 综合示例 — 基于 {@link PacketParserService} 与 {@link TsharkPolledDirectory}。
 *
 * <p>演示 TShark 工具的两个核心能力：单包 JSON 解析、pcap 输出目录的轮询监听。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 全部能力点自检
 *   java TsharkExample
 *
 *   # 指定单个能力点
 *   java TsharkExample --type parse
 *   java TsharkExample --type polled
 *   java TsharkExample --type listener
 *   java TsharkExample --type real --duration 1800   # 真实流量（需本机装 tshark + Npcap）
 *
 *   # 打印帮助
 *   java TsharkExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>parse 解析</td><td>{@link #testParse()}</td><td>解析 TShark 单包 JSON 字符串</td></tr>
 *   <tr><td>parseProtocols 协议识别</td><td>{@link #testParseProtocols()}</td><td>解析 HTTP/TLS/DNS/TCP/UDP 等多种协议</td></tr>
 *   <tr><td>parseInvalid 异常输入</td><td>{@link #testParseInvalid()}</td><td>非法 JSON 返回 null 而非抛异常</td></tr>
 *   <tr><td>polled 目录轮询</td><td>{@link #testPolledDirectory()}</td><td>TsharkPolledDirectory 启动并接收新 pcap</td></tr>
 *   <tr><td>listener 事件监听</td><td>{@link #testPolledListener()}</td><td>CREATE/MODIFY/DELETE 事件回调</td></tr>
 *   <tr><td>realCycle 真实流量</td><td>{@link #testRealCycle()}</td><td>真实网卡（30 分钟）+ tshark + ProtocolRestorer 还原</td></tr>
 *   <tr><td>restorer 协议还原</td><td>{@link #testRestorer()}</td><td>SPI 发现 ProtocolRestorer 并在合成字节上还原</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TsharkExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 自检用临时目录前缀
     */
    private static final String TEST_DIR_PREFIX = System.getProperty("java.io.tmpdir") + "/tshark-example-";

    /**
     * 轮询监听超时时间（秒）
     */
    private static final int POLL_TIMEOUT_SECONDS = 5;

    /**
     * 轮询监听周期（毫秒）
     */
    private static final long POLL_PERIOD_MS = 200L;

    /**
     * 真实流量测试的 tshark 可执行文件路径（默认 Wireshark 安装路径）
     */
    private static final String TSHARK_BINARY = "C:/Program Files/Wireshark/tshark.exe";

    /**
     * 真实流量测试的抓包接口默认编号（loopback）。
     * 如未探测到，将回退到该值；详细探测见 {@link #resolveTsharkInterface()}。
     */
    private static final String TSHARK_INTERFACE = "7";

    /**
     * 真实流量测试请求路径（兼容旧版本常量）
     */
    private static final String REAL_CYCLE_PATH = "/tshark-real-cycle";

    /**
     * 真实流量测试的抓包持续时间（秒），默认 3600（1 小时）。
     * 可通过系统属性 {@code tshark.real.duration=60} 覆盖便于 CI 场景。
     */
    private static final int TSHARK_REAL_DURATION_SECONDS = parseDurationProperty(
            "tshark.real.duration", 3600);

    /**
     * 真实流量测试的进度打印间隔（秒）
     */
    private static final int TSHARK_PROGRESS_INTERVAL_SECONDS = parseDurationProperty(
            "tshark.real.progress", 30);

    /**
     * 解析命令行/System 属性中的时长值。
     *
     * @param key         系统属性名
     * @param defaultSecs 默认秒数
     * @return 解析后的秒数
     */
    private static int parseDurationProperty(String key, int defaultSecs) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultSecs;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultSecs;
        }
    }

    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        String type = parsed.type() != null ? parsed.type() : "all";

        boolean passed = switch (type.toLowerCase()) {
            case "parse" -> testParse();
            case "protocols" -> testParseProtocols();
            case "invalid" -> testParseInvalid();
            case "polled" -> testPolledDirectory();
            case "listener" -> testPolledListener();
            case "real", "realcycle" -> testRealCycle();
            case "restorer" -> testRestorer();
            case "all" -> testParse()
                    && testParseProtocols()
                    && testParseInvalid()
                    && testPolledDirectory()
                    && testPolledListener()
                    && testRealCycle()
                    && testRestorer();
            default -> {
                System.err.println("[FAIL] 未知 type: " + type);
                yield false;
            }
        };

        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 能力 1：parse 解析单条 TShark JSON 数据包。
     *
     * <p>输入一段包含 frame/ip/tcp 层的 TShark JSON，验证返回的 {@link PacketRecord}
     * 中 sourceIp、destinationIp、sourcePort、destinationPort、protocol 字段。</p>
     */
    public static boolean testParse() {
        log.info("===== parse =====");
        String json = buildTcpJson("10.0.0.1", 12345, "10.0.0.2", 80, 64L);
        PacketRecord record = PacketParserService.parse(json);
        boolean ok = record != null
                && "10.0.0.1".equals(record.sourceIp())
                && "10.0.0.2".equals(record.destinationIp())
                && Integer.valueOf(12345).equals(record.sourcePort())
                && Integer.valueOf(80).equals(record.destinationPort())
                && "TCP".equals(record.protocol())
                && Integer.valueOf(64).equals(record.length());
        printResult("parse TCP packet record", ok);
        return ok;
    }

    /**
     * 能力 2：parseProtocols 协议识别 — HTTP/TLS/DNS/UDP/ICMP/ARP 多协议推断。
     */
    public static boolean testParseProtocols() {
        log.info("===== protocols =====");
        boolean ok = true;
        ok &= checkProtocol(buildLayerJson("http"), "HTTP");
        ok &= checkProtocol(buildLayerJson("tls"), "TLS");
        ok &= checkProtocol(buildLayerJson("dns"), "DNS");
        ok &= checkProtocol(buildLayerJson("tcp"), "TCP");
        ok &= checkProtocol(buildLayerJson("udp"), "UDP");
        ok &= checkProtocol(buildLayerJson("icmp"), "ICMP");
        ok &= checkProtocol(buildLayerJson("arp"), "ARP");
        ok &= checkProtocol(buildEmptyLayersJson(), "OTHER");
        printResult("parse all protocols", ok);
        return ok;
    }

    /**
     * 能力 3：parseInvalid 异常输入 — 非法 JSON 返回 null，不抛异常。
     */
    public static boolean testParseInvalid() {
        log.info("===== invalid =====");
        PacketRecord empty = PacketParserService.parse("");
        PacketRecord garbage = PacketParserService.parse("not json at all");
        PacketRecord noLayers = PacketParserService.parse("{\"foo\":1}");
        boolean ok = empty == null && garbage == null && noLayers == null;
        printResult("invalid input returns null", ok);
        return ok;
    }

    /**
     * 能力 4：polled 目录轮询 — 启动 TsharkPolledDirectory，投放一个 pcap 文件，
     * 验证 packetListener 被触发并返回 PacketRecord 列表。
     */
    public static boolean testPolledDirectory() {
        log.info("===== polled =====");
        Path dir = Path.of(TEST_DIR_PREFIX + "polled-" + System.currentTimeMillis());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("无法创建测试目录", e);
            return false;
        }

        TsharkPolledDirectory polled = new TsharkPolledDirectory(dir.toString());

        List<List<PacketRecord>> captured = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        polled.setPacketListener(records -> {
            captured.add(records);
            latch.countDown();
        });

        DirectoryPollerEnvironment env = new DirectoryPollerEnvironment(
                java.util.Set.of(WatcherEvent.CREATE),
                POLL_PERIOD_MS,
                TimeUnit.MILLISECONDS);
        polled.start(env);

        try {
            // 投放一个伪 pcap 文件（不实际调用 tshark，仅触发 CREATE 事件）
            Files.writeString(dir.resolve("sample.pcap"), "FAKE_PCAP_DATA");

            boolean got = latch.await(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            printResult("polled file CREATE event fired", got);

            if (!got) {
                return false;
            }

            // 因为是假数据，tshark 解析会失败，但 packetListener 仍应被回调
            // （只是 records 为空列表）。我们仅验证回调触发，不验证内容。
            boolean fired = !captured.isEmpty();
            printResult("polled packetListener invoked", fired);
            return fired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            log.error("写入测试文件失败", e);
            return false;
        } finally {
            polled.close();
            deleteDir(dir);
        }
    }

    /**
     * 能力 5：listener 事件监听 — 验证 CREATE / DELETE 事件通过 PolledListener 分发。
     */
    public static boolean testPolledListener() {
        log.info("===== listener =====");
        Path dir = Path.of(TEST_DIR_PREFIX + "listener-" + System.currentTimeMillis());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("无法创建测试目录", e);
            return false;
        }

        TsharkPolledDirectory polled = new TsharkPolledDirectory(dir.toString());
        AtomicReference<String> lastEvent = new AtomicReference<>();
        CountDownLatch createLatch = new CountDownLatch(1);
        polled.addListener(new SimplePolledListener() {
            @Override
            public void onCreate(WatcherEvent event, com.chua.common.support.lang.directory.EventObserver observer) {
                lastEvent.set(observer.getTriggerFile());
                createLatch.countDown();
            }
        });

        DirectoryPollerEnvironment env = new DirectoryPollerEnvironment(
                java.util.Set.of(WatcherEvent.CREATE, WatcherEvent.DELETE),
                POLL_PERIOD_MS,
                TimeUnit.MILLISECONDS);
        polled.start(env);

        try {
            String fileName = "listener-test.pcap";
            Files.writeString(dir.resolve(fileName), "FAKE");

            boolean got = createLatch.await(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean nameOk = fileName.equals(lastEvent.get());
            printResult("listener onCreate fired with correct file", got && nameOk);
            return got && nameOk;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            log.error("写入测试文件失败", e);
            return false;
        } finally {
            polled.close();
            deleteDir(dir);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 校验指定 layer 名称的 JSON 解析后的协议字段。
     *
     * @param json     包含指定 layer 名称的 TShark JSON 字符串
     * @param expected 期望的协议名称
     * @return 协议名称匹配返回 true
     */
    private static boolean checkProtocol(String json, String expected) {
        PacketRecord record = PacketParserService.parse(json);
        boolean ok = record != null && expected.equals(record.protocol());
        log.debug("protocol check: expected={}, got={}, ok={}", expected,
                record == null ? null : record.protocol(), ok);
        return ok;
    }

    /**
     * 构建只包含单个 layer 名称的最简 TShark JSON。
     *
     * <p>由于只关心协议推断，IP/TCP 层留空即可。</p>
     *
     * @param layerName layer 名称（http/tls/dns/tcp/udp/icmp/arp）
     * @return TShark JSON 字符串
     */
    private static String buildLayerJson(String layerName) {
        return "{\"_source\":{\"layers\":{\"" + layerName + "\":{}}}}";
    }

    /**
     * 构建一个具有 _source.layers 但不含已知协议层的 TShark JSON。
     *
     * <p>用于验证协议推断的 OTHER 分支。</p>
     *
     * @return TShark JSON 字符串
     */
    private static String buildEmptyLayersJson() {
        return "{\"_source\":{\"layers\":{\"frame\":{}}}}";
    }

    /**
     * 构建一段完整的 TCP 包 TShark JSON。
     *
     * @param srcIp   源IP
     * @param srcPort 源端口
     * @param dstIp   目的IP
     * @param dstPort 目的端口
     * @param length  帧长度
     * @return TShark JSON 字符串
     */
    private static String buildTcpJson(String srcIp, int srcPort, String dstIp, int dstPort, long length) {
        return "{\"_source\":{\"layers\":{" +
                "\"frame\":{\"frame.len\":\"" + length + "\"}," +
                "\"ip\":{\"ip.src\":\"" + srcIp + "\",\"ip.dst\":\"" + dstIp + "\"}," +
                "\"tcp\":{\"tcp.srcport\":\"" + srcPort + "\",\"tcp.dstport\":\"" + dstPort + "\"}" +
                "}}}";
    }

    /**
     * 能力 6：realCycle 真实网卡抓包端到端验证。
     *
     * <p>完整链路：tshark 抓真实网卡流量 → pcap 文件落盘 →
     * TsharkPolledDirectory 解析 pcap → packetListener 收到 PacketRecord → 调用
     * ProtocolRestorer 对合成字节进行协议还原。</p>
     *
     * <p>需要本机安装 tshark（Wireshark）并支持 Npcap 真实网卡/loopback 抓包。
     * 若 tshark 不可用，本测试返回 true 并打印跳过提示，便于 CI 环境无 tshark 时通过。</p>
     *
     * <p>默认抓包时长 {@link #TSHARK_REAL_DURATION_SECONDS}（1 小时），
     * 可通过系统属性 {@code tshark.real.duration=60} 覆盖用于快速验证。
     * 进度打印间隔 {@link #TSHARK_PROGRESS_INTERVAL_SECONDS}（30 秒），
     * 可通过系统属性 {@code tshark.real.progress=10} 覆盖。</p>
     */
    public static boolean testRealCycle() {
        log.info("===== realCycle =====");

        if (!Files.exists(Path.of(TSHARK_BINARY))) {
            log.warn("tshark 未安装（{}），跳过 realCycle 测试", TSHARK_BINARY);
            printResult("realCycle skipped (tshark not found)", true);
            return true;
        }

        // 探测一个可用的抓包接口：默认 7 (loopback)，否则任意 npcap 接口
        String iface = resolveTsharkInterface();

        Path dir = Path.of(TEST_DIR_PREFIX + "real-" + System.currentTimeMillis());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("无法创建监听目录", e);
            return false;
        }

        log.info("使用抓包接口: {} （duration={}s, dir={}）", iface, TSHARK_REAL_DURATION_SECONDS, dir);

        // 启动 tshark 抓包到监听目录（避免后续 move）
        Process tsharkProcess;
        try {
            List<String> command = new java.util.ArrayList<>();
            command.add(TSHARK_BINARY);
            command.add("-i");
            command.add(iface);
            command.add("-a");
            command.add("duration:" + TSHARK_REAL_DURATION_SECONDS);
            // 按文件大小分片，便于长时抓包不丢数据且 PolledDirectory 多次触发
            command.add("-b");
            command.add("filesize:10240");
            command.add("-w");
            command.add(dir.resolve("capture.pcap").toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            tsharkProcess = processBuilder.start();

            // 排空 tshark 的 stdout（不阻塞）
            Thread tsharkDrain = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(tsharkProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    while (reader.readLine() != null) {
                        // 排空
                    }
                } catch (IOException ignored) {
                    // 进程结束后正常关闭
                }
            }, "tshark-drain");
            tsharkDrain.setDaemon(true);
            tsharkDrain.start();
        } catch (IOException e) {
            log.error("启动 tshark 失败", e);
            deleteDir(dir);
            return false;
        }

        // 启动 TsharkPolledDirectory 监听 pcap 目录
        TsharkPolledDirectory polled = new TsharkPolledDirectory(dir.toString());
        polled.setTsharkBinary(TSHARK_BINARY);

        // 累计所有捕获的 PacketRecord
        AtomicInteger pcapCount = new AtomicInteger();
        AtomicInteger packetCount = new AtomicInteger();
        AtomicInteger restoredCount = new AtomicInteger();
        Set<String> protocolSet = ConcurrentHashMap.newKeySet();
        polled.setPacketListener(records -> {
            int idx = pcapCount.incrementAndGet();
            int restoredThisPcap = 0;
            for (PacketRecord record : records) {
                packetCount.incrementAndGet();
                if (record.protocol() != null) {
                    protocolSet.add(record.protocol());
                }
                if (record.restoredText() != null && !record.restoredText().isEmpty()) {
                    restoredCount.incrementAndGet();
                    restoredThisPcap++;
                }
            }
            log.info("[捕获 #{}] pcap 解析: {} 个包, 本次还原命中 {}（累计 {} 包 / {} 还原）",
                    idx, records.size(), restoredThisPcap,
                    packetCount.get(), restoredCount.get());
        });
        DirectoryPollerEnvironment env = new DirectoryPollerEnvironment(
                Set.of(WatcherEvent.CREATE, WatcherEvent.MODIFY),
                POLL_PERIOD_MS,
                TimeUnit.MILLISECONDS);
        polled.start(env);

        // 进度打印线程：每隔 TSHARK_PROGRESS_INTERVAL_SECONDS 秒打印一次状态
        long startMillis = System.currentTimeMillis();
        Thread progressThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(TSHARK_PROGRESS_INTERVAL_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                long elapsedSec = (System.currentTimeMillis() - startMillis) / 1000L;
                log.info("[进度] 已抓取 {}s / {}s（{}%），累计 {} 个 pcap / {} 个数据包 / {} 条协议还原，协议分布: {}",
                        elapsedSec, TSHARK_REAL_DURATION_SECONDS,
                        String.format("%.1f", elapsedSec * 100.0 / TSHARK_REAL_DURATION_SECONDS),
                        pcapCount.get(), packetCount.get(), restoredCount.get(),
                        protocolSet.isEmpty() ? "(尚无)" : String.join(",", protocolSet));
            }
        }, "realCycle-progress");
        progressThread.setDaemon(true);
        progressThread.start();

        try {
            // 等待 tshark 进程自然结束（最长 TSHARK_REAL_DURATION_SECONDS + 5s 缓冲）
            int waitSeconds = TSHARK_REAL_DURATION_SECONDS + 5;
            log.info("等待 tshark 抓包结束（最长 {}s）...", waitSeconds);
            boolean finished = tsharkProcess.waitFor(waitSeconds, TimeUnit.SECONDS);
            if (!finished) {
                tsharkProcess.destroyForcibly();
                log.warn("tshark 进程超时未结束，已强制终止");
            }

            // 给 PolledDirectory 一点时间处理最后一批 pcap
            Thread.sleep(POLL_PERIOD_MS * 3);

            long elapsedSec = (System.currentTimeMillis() - startMillis) / 1000L;
            int totalPcaps = pcapCount.get();
            int totalPackets = packetCount.get();
            int totalRestored = restoredCount.get();

            // 在 tshark 抓包结束后，对每个 pcap 调用 tshark -T fields 提取 layer-specific raw bytes
            // 并按 frame.protocols 选最合适的 layer 喂给 ProtocolRestorer，得到真实协议还原
            int realProtocolRestored = restoreRealProtocolsFromPcaps(dir);

            log.info("[总结] 抓取时长 {}s，共捕获 {} 个 pcap / {} 个数据包 / {} 条协议还原",
                    elapsedSec, totalPcaps, totalPackets, totalRestored);
            if (!protocolSet.isEmpty()) {
                log.info("[总结] PacketRecord 协议分布: {}", String.join(",", protocolSet));
            }
            if (realProtocolRestored > 0) {
                log.info("[总结] 真实 layer 协议还原命中 {} 次", realProtocolRestored);
            }

            boolean ok = totalPackets > 0 && totalRestored > 0;
            printResult("realCycle captured " + totalPackets + " packets from "
                    + totalPcaps + " pcaps, layer-restored=" + realProtocolRestored, ok);
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            progressThread.interrupt();
            polled.close();
            if (tsharkProcess.isAlive()) {
                tsharkProcess.destroyForcibly();
            }
            // 保留 pcap 文件一段时间便于调试，正式 CI 场景可在 finally 中启用 deleteDir
            if (packetCount.get() == 0) {
                deleteDir(dir);
            } else {
                log.info("[清理] 抓包数据已保留在 {} （便于复现/调试）", dir);
            }
        }
    }

/**
     * 对指定目录的所有 pcap 文件调用 tshark -T fields 提取 layer-specific raw bytes，
     * 并按 frame.protocols 选最合适的 layer 喂给 ProtocolRestorer。
     *
     * <p>与 TsharkPolledDirectory 内部基于 frame_raw 的"盲目"还原不同，
     * 本方法使用 tshark 内置的 layer 解析，提取 tcp.payload / udp.payload / http.file_data /
     * dns.txt 等已剥离 L2/L3/L4 头的应用层原始字节，确保还原器命中真实协议数据。</p>
     *
     * @param dir tshark 输出 pcap 的目录
     * @return 真实协议还原命中次数
     */
    private static int restoreRealProtocolsFromPcaps(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        File[] pcaps = dir.toFile().listFiles((f) -> f.isFile() && f.getName().endsWith(".pcap"));
        if (pcaps == null || pcaps.length == 0) {
            return 0;
        }
        java.util.Map<String, ProtocolRestorer> restorers = ServiceProvider.of(ProtocolRestorer.class).list();
        if (restorers.isEmpty()) {
            log.warn("未发现 ProtocolRestorer，跳过真实协议还原");
            return 0;
        }
        int hit = 0;
        Set<String> protocolSeen = ConcurrentHashMap.newKeySet();
        for (File pcap : pcaps) {
            hit += restoreOnePcap(pcap, restorers, protocolSeen);
        }
        log.info("[真实协议还原] 命中 {} 次，协议分布: {}",
                hit, protocolSeen.isEmpty() ? "(无)" : String.join(",", protocolSeen));
        return hit;
    }

    /**
     * 对单个 pcap 调用 tshark -T fields 提取 layer 原始字节并还原。
     *
     * @param pcap 单个 pcap 文件
     * @param restorers 还原器映射（按层选择 raw bytes 喂入第一个匹配的还原器）
     * @param protocolSeen 已观察到协议集合（输出统计用）
     * @return 命中数
     */
    private static int restoreOnePcap(File pcap,
                                     java.util.Map<String, ProtocolRestorer> restorers,
                                     Set<String> protocolSeen) {
        // 提取 frame.protocols + 每层 payload 的 hex 字段
        // 注意：tcp.payload/udp.payload 是不含 L2/L3/L4 头的应用层数据
        List<String> command = new java.util.ArrayList<>();
        command.add(TSHARK_BINARY);
        command.add("-r");
        command.add(pcap.getAbsolutePath());
        command.add("-T");
        command.add("fields");
        // 使用不可能出现在原始数据中的多字符 separator，避免 hex escape 兼容问题
        command.add("-E");
        command.add("separator=|SEP|");
        command.add("-E");
        command.add("occurrence=f");
        command.add("-e");
        command.add("frame.number");
        command.add("-e");
        command.add("frame.protocols");
        command.add("-e");
        command.add("tcp.payload");
        command.add("-e");
        command.add("udp.payload");
        command.add("-e");
        command.add("sctp.data");
        command.add("-e");
        command.add("http.file_data");
        command.add("-e");
        command.add("http.request.uri");
        command.add("-e");
        command.add("http.response.code");
        command.add("-e");
        command.add("sip.Request-Line");
        command.add("-e");
        command.add("sip.Status-Line");
        command.add("-e");
        command.add("rtsp.request");
        command.add("-e");
        command.add("sip.rtsp.status");
        command.add("-e");
        command.add("frame_raw");

        int hit = 0;
        int sampleLogged = 0;
        int totalPackets = 0;
        log.info("[restoreOnePcap] 开始处理 {} size={}KB", pcap.getName(), pcap.length() / 1024);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // 用 ByteArrayOutputStream 读全部字节，避免 BufferedReader.readLine 在 payload 含 \r\n 时截断
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream is = process.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) > 0) {
                    baos.write(buf, 0, n);
                }
            }
            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            log.info("[restoreOnePcap] process exited={}, sb.len={}", exited, baos.size());
            if (!exited) {
                process.destroyForcibly();
            }
            String fullOutput = baos.toString("UTF-8");
            String[] lines = fullOutput.split("\\r?\\n");
            for (String line : lines) {
                if (line.isEmpty()) {
                    continue;
                }
                totalPackets++;
                if (totalPackets > 2000) {
                    break;
                }
                String[] fields = line.split("\\|SEP\\|", -1);
                    if (fields.length < 2) {
                        continue;
                    }
                    int idx = 0;
                    String frameNumber = fields[idx++].trim();
                    String protocols = fields[idx++].trim();
                    String tcp = fieldOrEmpty(fields, idx++);
                    String udp = fieldOrEmpty(fields, idx++);
                    String sctp = fieldOrEmpty(fields, idx++);
                    String httpBody = fieldOrEmpty(fields, idx++);
                    String httpReqUri = fieldOrEmpty(fields, idx++);
                    String httpRespCode = fieldOrEmpty(fields, idx++);
                    String sipReq = fieldOrEmpty(fields, idx++);
                    String sipStatus = fieldOrEmpty(fields, idx++);
                    String rtspReq = fieldOrEmpty(fields, idx++);
                    String sipRtspStatus = fieldOrEmpty(fields, idx++);
                    String frameRaw = fieldOrEmpty(fields, idx++);

                    // 按 frame.protocols 选最合适的 layer 喂还原器
                    byte[] rawBytes = null;
                    String chosenLayer = null;
                    if (isProtocolContains(protocols, "sip:")) {
                        rawBytes = pickFirstNonEmpty(sipReq, sipRtspStatus);
                        chosenLayer = "sip";
                    } else if (isProtocolContains(protocols, "sip-rtsp:")) {
                        rawBytes = pickFirstNonEmpty(sipRtspStatus, sipReq);
                        chosenLayer = "sip-rtsp";
                    } else if (isProtocolContains(protocols, "rtsp:")) {
                        rawBytes = pickFirstNonEmpty(sipRtspStatus, rtspReq);
                        chosenLayer = "rtsp";
                    } else if (isProtocolContains(protocols, "http:")) {
                        // HTTP 请求/响应：直接用 tcp.payload 字节，由 HttpProtocolRestorer 检测
                        rawBytes = decodeHex(tcp);
                        chosenLayer = "http";
                    } else if (!tcp.isEmpty()) {
                        rawBytes = decodeHex(tcp);
                        chosenLayer = "tcp.payload";
                    } else if (!udp.isEmpty()) {
                        rawBytes = decodeHex(udp);
                        chosenLayer = "udp.payload";
                    } else if (!sctp.isEmpty()) {
                        rawBytes = decodeHex(sctp);
                        chosenLayer = "sctp";
                    }

                    if (rawBytes == null || rawBytes.length == 0) {
                        continue;
                    }
                    if (sampleLogged == 0) {
                        String head = new String(rawBytes, 0, Math.min(60, rawBytes.length), StandardCharsets.UTF_8);
                        String headSafe = head.replace('\n', '_').replace('\r', '_');
                        log.info("[调试] frame={} protocols={} layer={} len={} head={}",
                                frameNumber, protocols, chosenLayer, rawBytes.length, headSafe);
                    }
                    for (java.util.Map.Entry<String, ProtocolRestorer> entry : restorers.entrySet()) {
                        ProtocolRestorer r = entry.getValue();
                        try {
                            if (r.canRestore(java.util.Map.of(), rawBytes)) {
                                String text = r.restore(java.util.Map.of(), rawBytes);
                                if (text != null && !text.isEmpty()) {
                                    hit++;
                                    protocolSeen.add(entry.getKey());
                                    if (sampleLogged < 8) {
                                        String snippet = text.length() > 140
                                                ? text.substring(0, 140) + "..."
                                                : text.replace('\n', ' ');
                                        log.info("[真实还原 #{}] pcap={} frame={} layer={} 还原器={} | {}",
                                                hit, pcap.getName(), frameNumber, chosenLayer,
                                                entry.getKey(), snippet);
                                        sampleLogged++;
                                    }
                                }
                                break;
                            }
                        } catch (Exception ignored) {
                            // 跳过单次还原异常
                        }
                    }

                    // Fallback：用 frame_raw 整个包字节再次尝试
                    if (!frameRaw.isEmpty() && frameRaw.length() > 4) {
                        byte[] fullBytes = decodeHex(frameRaw);
                        if (fullBytes.length > 4) {
                            for (java.util.Map.Entry<String, ProtocolRestorer> entry : restorers.entrySet()) {
                                ProtocolRestorer r = entry.getValue();
                                try {
                                    if (r.canRestore(java.util.Map.of(), fullBytes)) {
                                        String text = r.restore(java.util.Map.of(), fullBytes);
                                        if (text != null && !text.isEmpty()) {
                                            hit++;
                                            protocolSeen.add(entry.getKey());
                                            if (sampleLogged < 8) {
                                                String snippet = text.length() > 140
                                                        ? text.substring(0, 140) + "..."
                                                        : text.replace('\n', ' ');
                                                log.info("[真实还原 #{}] pcap={} frame={} layer=frame_raw 还原器={} | {}",
                                                        hit, pcap.getName(), frameNumber,
                                                        entry.getKey(), snippet);
                                                sampleLogged++;
                                            }
                                            break;
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // 跳过单次还原异常
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 pcap 字段失败: {}", pcap.getName(), e);
        }
        log.info("[restoreOnePcap] {} 处理完成: totalPackets={} hit={}", pcap.getName(), totalPackets, hit);
        return hit;
    }

    /**
     * 检查 protocol 栈字符串中是否包含某个 layer（以 : 分隔）。
     *
     * @param protocols frame.protocols 字段（如 "eth:ethertype:ip:tcp:http"）
     * @param layer 待检查 layer（例如 "http:"、"ssl:"）
     * @return 包含返回 true
     */
    private static boolean isProtocolContains(String protocols, String layer) {
        return protocols != null && protocols.contains(layer);
    }

    /**
     * 判断 protocol 栈是否包含 HTTP 响应特征（http2:// 等也可能）。
     *
     * @param protocols frame.protocols 字段
     * @return 是否含 "http" 但非 "http2"
     */
    private static boolean hasHttpCode(String protocols) {
        return protocols != null && protocols.contains("http");
    }

    /**
     * 从两个候选 hex 字符串中选第一个非空的。
     *
     * @param a 第一候选
     * @param b 第二候选
     * @return 解码后的字节数组，候选都为空返回 null
     */
    private static byte[] pickFirstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return decodeHex(a);
        }
        if (b != null && !b.isEmpty()) {
            return decodeHex(b);
        }
        return null;
    }

    /**
     * 从 fields 数组安全取值。
     *
     * @param fields fields 数组
     * @param idx 下标（可能越界）
     * @return 字段值或空字符串
     */
    private static String fieldOrEmpty(String[] fields, int idx) {
        if (idx >= fields.length) {
            return "";
        }
        return fields[idx] == null ? "" : fields[idx];
    }

    /**
     * 构造 HTTP 响应行 + headers + body 字节流。
     *
     * @param statusCode status code（如 "200" 或空）
     * @param bodyHex body 字节（hex 编码）
     * @param restorers 当前还原器（未使用，保留扩展）
     * @return 可被 HttpProtocolRestorer 直接还原的字节数组
     */
    private static byte[] buildHttpResponseBytes(String statusCode, String bodyHex,
                                                 java.util.Map<String, ProtocolRestorer> restorers) {
        String head = "HTTP/1.1 " + (statusCode.isEmpty() ? "200" : statusCode) + " OK\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: " + (bodyHex.length() / 2) + "\r\n\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] bodyBytes = decodeHex(bodyHex);
        byte[] combined = new byte[headBytes.length + bodyBytes.length];
        System.arraycopy(headBytes, 0, combined, 0, headBytes.length);
        System.arraycopy(bodyBytes, 0, combined, headBytes.length, bodyBytes.length);
        return combined;
    }

    /**
     * 将 hex 字符串解码为字节数组。
     *
     * @param hex hex 字符串（可含 : 分隔符）
     * @return 字节数组，解码失败返回空数组
     */
    private static byte[] decodeHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        try {
            byte[] r = Hex.decodeHex(hex.replaceAll(":", ""));
            if (r.length == 0) {
                log.debug("[decodeHex] 输入 {} 字符, 输出 0 字节", hex.length());
            }
            return r;
        } catch (Exception e) {
            log.debug("decodeHex 失败: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 探测 tshark 可用的抓包接口。优先使用 loopback，失败则用任意 npcap 接口。
     *
     * @return tshark -i 参数值（数字编号或接口名）
     */
    private static String resolveTsharkInterface() {
        try {
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BINARY, "-D", "-a", "duration:1");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
            // 找第一个 NPF_Loopback 或 NPF_{xxx}
            String[] lines = sb.toString().split("\n");
            for (String line : lines) {
                if (line.contains("Loopback")) {
                    int dotIdx = line.indexOf('.');
                    if (dotIdx > 0) {
                        return line.substring(0, dotIdx).trim();
                    }
                }
            }
            for (String line : lines) {
                if (line.contains("NPF_{") && !line.contains("Loopback")) {
                    int dotIdx = line.indexOf('.');
                    if (dotIdx > 0) {
                        return line.substring(0, dotIdx).trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("探测 tshark 接口失败: {}", e.getMessage());
        }
        return TSHARK_INTERFACE;
    }

    /**
     * 能力 7：restorer 协议还原器演示。
     *
     * <p>通过 SPI 自动发现所有 {@link ProtocolRestorer} 实现，对一组典型应用层协议原始字节进行还原。</p>
     *
     * <p>注意：这里的输入字节是真实协议 spec 的最小可识别样例（curl、openssl s_client、
     * redis-cli、mosquitto_pub 等真实客户端的请求字节），用于演示还原器在解析器层面可用；
     * 真实抓包还原请走 {@link #testRealCycle()}，由 TsharkPolledDirectory 调用
     * ProtocolRestorer 填充 PacketRecord.restoredText。</p>
     */
    public static boolean testRestorer() {
        log.info("===== restorer =====");

        java.util.Map<String, ProtocolRestorer> all = ServiceProvider.of(ProtocolRestorer.class).list();
        if (all.isEmpty()) {
            log.error("未发现任何 ProtocolRestorer 实现");
            return false;
        }
        log.info("通过 SPI 发现 {} 个 ProtocolRestorer 实现", all.size());
        all.forEach((k, v) -> log.info("  - {} -> {} (priority={})", k, v.getClass().getSimpleName(), v.getPriority()));

        // 合成测试用例：(name, rawBytes)
        java.util.List<java.util.Map.Entry<String, byte[]>> cases = new java.util.ArrayList<>();
        // HTTP GET
        cases.add(java.util.Map.entry("HTTP GET",
                "GET /api/users HTTP/1.1\r\nHost: example.com\r\nUser-Agent: test\r\nAccept: application/json\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // HTTP 200 Response
        cases.add(java.util.Map.entry("HTTP 200 Response",
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 16\r\n\r\n{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // TLS ClientHello with SNI=www.baidu.com
        byte[] tls = buildTlsClientHello("www.baidu.com");
        cases.add(java.util.Map.entry("TLS ClientHello", tls));
        // DNS query for example.com
        cases.add(java.util.Map.entry("DNS Query example.com", buildDnsQuery("example.com", 1)));
        // DNS response
        cases.add(java.util.Map.entry("DNS Response example.com", buildDnsResponse("example.com", "93.184.216.34")));
        // FTP USER command
        cases.add(java.util.Map.entry("FTP USER", "USER admin\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // FTP 220 banner
        cases.add(java.util.Map.entry("FTP 220 Banner", "220 FTP Server Ready\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // SSH version
        cases.add(java.util.Map.entry("SSH Version", "SSH-2.0-OpenSSH_9.6 Ubuntu-3ubuntu13.5\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // MQTT CONNECT
        byte[] mqtt = new byte[]{0x10, 0x10, 0x00, 0x04, (byte) 'M', (byte) 'Q', (byte) 'T', (byte) 'T',
                0x04, 0x02, 0x00, 0x3c, 0x00, 0x04, (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef};
        cases.add(java.util.Map.entry("MQTT CONNECT", mqtt));
        // Redis PING
        cases.add(java.util.Map.entry("Redis PING", "*1\r\n$4\r\nPING\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // MySQL Server Greeting (simplified)
        byte[] mysql = new byte[]{
                (byte) 0x4a, 0x00, 0x00, 0x01, 0x0a, // packet header + protocol v10
                '8', '.', '0', '.', '3', '2',
                0x00,
                0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 'A', 'B', 'C', 'D', 'E', 'F',
                0x00
        };
        cases.add(java.util.Map.entry("MySQL Server Greeting", mysql));
        // SMTP EHLO
        cases.add(java.util.Map.entry("SMTP EHLO", "EHLO example.com\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // SIP INVITE
        cases.add(java.util.Map.entry("SIP INVITE",
                "INVITE sip:user@example.com SIP/2.0\r\nVia: SIP/2.0/UDP 192.168.1.1\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // WebSocket Text frame (FIN=1, opcode=1, masked=0, payload=Hello)
        byte[] wsPayload = "Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] wsFrame = new byte[2 + wsPayload.length];
        wsFrame[0] = (byte) 0x81; // FIN=1, opcode=1 (text)
        wsFrame[1] = (byte) wsPayload.length;
        System.arraycopy(wsPayload, 0, wsFrame, 2, wsPayload.length);
        cases.add(java.util.Map.entry("WebSocket Text", wsFrame));
        // ICMP Echo Request
        cases.add(java.util.Map.entry("ICMP Echo", new byte[]{(byte) 0x08, 0x00, 0x00, 0x00, 0x12, 0x34, 0x00, 0x01}));
        // Modbus TCP ReadHoldingRegisters
        byte[] modbus = new byte[]{
                0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03,
                0x00, 0x00, 0x00, 0x0a
        };
        cases.add(java.util.Map.entry("Modbus ReadHoldingRegisters", modbus));
        // ARP request
        byte[] arp = new byte[]{
                0x00, 0x01, 0x08, 0x00, 0x06, 0x04, 0x00, 0x01,
                (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, 0x11, 0x22, 0x33,
                (byte) 192, (byte) 168, 0x01, 0x0a,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                (byte) 192, (byte) 168, 0x01, 0x01
        };
        cases.add(java.util.Map.entry("ARP Request", arp));
        // RTSP OPTIONS
        cases.add(java.util.Map.entry("RTSP OPTIONS", "OPTIONS rtsp://example.com/stream RTSP/1.0\r\nCSeq: 1\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        boolean ok = true;
        int hitCount = 0;
        for (java.util.Map.Entry<String, byte[]> testCase : cases) {
            String name = testCase.getKey();
            byte[] bytes = testCase.getValue();
            StringBuilder matchInfo = new StringBuilder();
            StringBuilder primaryRestore = new StringBuilder();
            for (java.util.Map.Entry<String, ProtocolRestorer> entry : all.entrySet()) {
                ProtocolRestorer r = entry.getValue();
                try {
                    if (r.canRestore(java.util.Map.of(), bytes)) {
                        matchInfo.append(entry.getKey()).append(',');
                        if (primaryRestore.length() == 0) {
                            primaryRestore.append(r.restore(java.util.Map.of(), bytes));
                        }
                        hitCount++;
                    }
                } catch (Exception ignored) {
                    // 还原异常忽略
                }
            }
            if (matchInfo.length() == 0) {
                log.warn("{} → 无还原器匹配", name);
                ok = false;
            } else {
                log.info("{} → {} | {}", name, matchInfo.substring(0, matchInfo.length() - 1),
                        primaryRestore.length() > 96 ? primaryRestore.substring(0, 96) + "..." : primaryRestore);
            }
        }

        printResult("restorer coverage hit " + hitCount + " restorations across " + cases.size() + " cases", ok);
        return ok;
    }

    /**
     * 构建 TLS ClientHello 字节（含 SNI）。
     *
     * @param serverName SNI 域名
     * @return TLS 记录字节
     */
    private static byte[] buildTlsClientHello(String serverName) {
        byte[] sni = serverName.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            // TLS Record: type=0x16, version=0x0301, length placeholder
            out.write(0x16);
            out.write(0x03);
            out.write(0x01);
            // Skip 2 bytes length, fill later
            int lenIdx = out.size();
            out.write(0x00);
            out.write(0x00);
            int recordStart = out.size();
            // Handshake: type=0x01, length placeholder
            out.write(0x01);
            int hsLenIdx = out.size();
            out.write(0x00);
            out.write(0x00);
            out.write(0x00);
            int hsStart = out.size();
            // ClientHello: version 0x0303
            out.write(0x03);
            out.write(0x03);
            // Random (32 bytes)
            for (int i = 0; i < 32; i++) {
                out.write(0x00);
            }
            // SessionID length=0
            out.write(0x00);
            // CipherSuites length=2, value=0x002f (TLS_RSA_WITH_AES_128_CBC_SHA)
            out.write(0x00);
            out.write(0x02);
            out.write(0x00);
            out.write(0x2f);
            // Compression methods length=1, null
            out.write(0x01);
            out.write(0x00);
            // Extensions length placeholder
            int extLenIdx = out.size();
            out.write(0x00);
            out.write(0x00);
            int extStart = out.size();
            // SNI extension
            out.write(0x00);
            out.write(0x00); // extension type
            int sniExtLenIdx = out.size();
            out.write(0x00);
            out.write(0x00);
            int sniExtStart = out.size();
            // ServerNameList length
            out.write(0x00);
            out.write((byte) (sni.length + 3));
            // ServerNameType=0 (host_name)
            out.write(0x00);
            out.write(0x00);
            out.write(sni.length);
            out.write(sni, 0, sni.length);
            int sniExtEnd = out.size();
            // Fill SNI ext length
            byte[] cur = out.toByteArray();
            int sniExtLen = sniExtEnd - sniExtStart;
            cur[sniExtLenIdx] = (byte) ((sniExtLen >> 8) & 0xff);
            cur[sniExtLenIdx + 1] = (byte) (sniExtLen & 0xff);
            int extEnd = out.size();
            int extLen = extEnd - extStart;
            cur[extLenIdx] = (byte) ((extLen >> 8) & 0xff);
            cur[extLenIdx + 1] = (byte) (extLen & 0xff);
            int hsEnd = out.size();
            int hsLen = hsEnd - hsStart;
            cur[hsLenIdx] = (byte) ((hsLen >> 16) & 0xff);
            cur[hsLenIdx + 1] = (byte) ((hsLen >> 8) & 0xff);
            cur[hsLenIdx + 2] = (byte) (hsLen & 0xff);
            int recordEnd = out.size();
            int recordLen = recordEnd - recordStart;
            cur[lenIdx] = (byte) ((recordLen >> 8) & 0xff);
            cur[lenIdx + 1] = (byte) (recordLen & 0xff);
            return cur;
        } catch (java.lang.RuntimeException e) {
            return new byte[0];
        }
    }

    /**
     * 构建 DNS 查询报文。
     *
     * @param name 查询域名
     * @param txnId 事务 ID
     * @return DNS 字节
     */
    private static byte[] buildDnsQuery(String name, int txnId) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write((byte) ((txnId >> 8) & 0xff));
        out.write((byte) (txnId & 0xff));
        out.write(0x01); // flags: standard query
        out.write(0x00);
        out.write(0x00); out.write(0x01); // questions
        out.write(0x00); out.write(0x00); // answers
        out.write(0x00); out.write(0x00); // authority
        out.write(0x00); out.write(0x00); // additional
        // QNAME
        for (String label : name.split("\\.")) {
            out.write(label.length());
            for (char c : label.toCharArray()) {
                out.write((byte) c);
            }
        }
        out.write(0x00);
        // QTYPE=A, QCLASS=IN
        out.write(0x00); out.write(0x01);
        out.write(0x00); out.write(0x01);
        return out.toByteArray();
    }

    /**
     * 构建 DNS 响应报文（A 记录）。
     *
     * @param name 查询域名
     * @param ip IPv4 字符串
     * @return DNS 字节
     */
    private static byte[] buildDnsResponse(String name, String ip) {
        byte[] query = buildDnsQuery(name, 0x1234);
        query[2] = (byte) 0x81;
        query[3] = (byte) 0x80; // QR=1, AA=0, TC=0, RD=1, RA=1
        query[6] = 0x00; query[7] = 0x01; // answers=1
        int pos = query.length;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            out.write(query, 0, query.length);
            // Answer: pointer 0xC00C (offset to query name)
            out.write(0xc0); out.write(0x0c);
            // TYPE=A CLASS=IN TTL=300 RDLENGTH=4
            out.write(0x00); out.write(0x01);
            out.write(0x00); out.write(0x01);
            out.write(new byte[]{0x00, 0x00, 0x01, 0x2c});
            out.write(0x00); out.write(0x04);
            for (String s : ip.split("\\.")) {
                out.write(Integer.parseInt(s));
            }
        } catch (java.io.IOException ignored) {
        }
        return out.toByteArray();
    }

    /**
     * 递归删除测试目录。
     *
     * @param dir 目标目录
     */
    private static void deleteDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted((a, b) -> b.toString().length() - a.toString().length())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                                // 删除失败忽略
                            }
                        });
            }
        } catch (IOException e) {
            log.debug("清理测试目录失败: {}", dir, e);
        }
    }

    /**
     * 打印单条测试结果。
     *
     * @param name   测试名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS]" : "[FAIL]") + " " + name);
    }

    /**
     * 解析命令行参数。
     */
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--type", "-t" -> {
                    if (index + 1 < args.length) {
                        result = result.withType(args[++index]);
                    }
                }
                case "--duration", "-d" -> {
                    if (index + 1 < args.length) {
                        System.setProperty("tshark.real.duration", args[++index]);
                    }
                }
                case "--help", "-h" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("TShark 综合示例 — 基于 PacketParserService + TsharkPolledDirectory");
        System.out.println();
        System.out.println("用法: java TsharkExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --type,    -t <key>    能力点（parse|protocols|invalid|polled|listener|real|restorer|all）");
        System.out.println("  --duration, -d <sec>    real 抓包时长（秒），默认 3600（1 小时）");
        System.out.println("  --help,    -h          打印帮助");
    }

    /**
     * 命令行参数容器。
     *
     * @param type 能力点
     * @param help 是否打印帮助
     * @author CH
     * @since 4.0.0.42
     */
    private record Args(String type, boolean help) {
        Args() {
            this(null, false);
        }

        public Args withType(String type) {
            return new Args(type, help);
        }

        public Args withHelp(boolean help) {
            return new Args(type, help);
        }
    }

    @SuppressWarnings("unused")
    private static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
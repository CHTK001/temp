package com.chua.example.network.nio;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.http.ConfigServer;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NIO(Reactor) 与 AIO(IOCP/Proactor) HTTP 传输层对比基准示例。
 *
 * <p>改写自 AioVsNioBenchmark：两种实现以完全相同的路由(/bench，固定文本响应)
 * 分别压测，输出 RPS / 平均延迟 / 失败数。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java AioVsNioBenchmarkExample                            # 默认 rounds=1000
 *   java AioVsNioBenchmarkExample --rounds 10000             # 调整总请求数
 *   java AioVsNioBenchmarkExample --connections 128          # 并发连接数
 *   java AioVsNioBenchmarkExample --port 28082               # 起始端口
 * </pre>
 *
 * <p>端口占用时自动 +10 重试，最多尝试 5 次。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AioVsNioBenchmarkExample {
    private AioVsNioBenchmarkExample() { }


    /**
     * 基准路由路径
     */
    private static final String BENCH_PATH = "/bench";

    /**
     * 基准响应体
     */
    private static final String BENCH_BODY = "OK";

    /**
     * 默认监听端口
     */
    private static final int DEFAULT_PORT = 28082;

    /**
     * 绑定失败重试次数上限
     */
    private static final int MAX_PORT_ATTEMPTS = 5;

    /**
     * 端口重试步长
     */
    private static final int PORT_RETRY_STEP = 10;

    /**
     * 默认总请求数（--rounds 可调）
     */
    private static final long DEFAULT_ROUNDS = 1_000L;

    /**
     * 默认并发连接数
     */
    private static final int DEFAULT_CONNECTIONS = 64;

    /**
     * 预热请求数上限：触发 JIT 与连接建立，不计入统计
     */
    private static final int WARMUP_REQUESTS = 200;

    /**
     * HTTP 200 状态码
     */
    private static final int STATUS_OK = 200;

    /**
     * Main 入口。
     *
     * @param args --rounds/--connections/--port 可调参数
     */
    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        boolean passed = true;
        passed &= bench("aio", parsed);
        passed &= bench("nio", parsed);
        if (!passed) {
            log.info("[FAIL] AIO/NIO 基准存在失败场景");
            System.exit(1);
        }
        log.info("[PASS] AIO/NIO 基准全部完成");
        System.exit(0);
    }

    /**
     * 对指定 SPI 类型执行压测。
     *
     * @param type SPI 类型标识(aio / nio)
     * @param args 命令行参数
     * @return 通过返回 true
     */
    private static boolean bench(String type, Args args) {
        Server server = null;
        try {
            int port = resolveFreePort(args.port());
            if (port < 0) {
                log.info("[FAIL] [{}] 端口 {} 起连续 {} 次探测均失败",
                        type, args.port(), MAX_PORT_ATTEMPTS);
                return false;
            }
            server = startServer(type, port);
            String baseUrl = "http://127.0.0.1:" + server.getPort();
            long totalRequests = Math.max(args.rounds() - WARMUP_REQUESTS, args.rounds() / 2);
            fire(baseUrl, args.connections(), WARMUP_REQUESTS);
            AtomicLong errors = new AtomicLong();
            long start = System.currentTimeMillis();
            List<Long> latencies = fireCollect(baseUrl, args.connections(), totalRequests, errors);
            long elapsedMillis = Math.max(System.currentTimeMillis() - start, 1);

            double rps = latencies.size() * 1000.0 / elapsedMillis;
            double avgLatency = avgOf(latencies);
            log.info("[BENCH] type={} connections={} requests={} elapsedMs={} rps={} avgLatencyMs={} errors={}",
                    type, args.connections(), totalRequests, elapsedMillis,
                    String.format("%.0f", rps), String.format("%.2f", avgLatency), errors.get());
            if (latencies.isEmpty()) {
                log.info("[FAIL] [{}] 无任何成功请求", type);
                return false;
            }
            if (errors.get() > 0) {
                log.info("[FAIL] [{}] 存在 {} 个失败请求", type, errors.get());
                return false;
            }
            log.info("[PASS] [{}] 基准完成 rps={}", type, String.format("%.0f", rps));
            return true;
        } catch (Exception e) {
            log.info("[FAIL] [{}] 基准异常: {}", type, e.getMessage());
            return false;
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    /**
     * 通过 SPI 创建并启动指定类型的基准服务器。
     *
     * @param type SPI 类型标识
     * @param port 监听端口
     * @return 已启动的 ConfigServer
     */
    private static ConfigServer startServer(String type, int port) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setPort(port);
        Server server = ServiceProvider.of(Server.class).getNewExtension(type, setting);
        if (!(server instanceof ConfigServer configServer)) {
            throw new IllegalStateException("[" + type + "] 实现不支持 HTTP 路由: "
                    + server.getClass().getName());
        }
        configServer.registerMapping(BENCH_PATH, HttpMethod.GET,
                (request, response) -> response.setResult(BENCH_BODY));
        configServer.start();
        log.info("[BOOT] [{}] 基准服务器监听 127.0.0.1:{} (期望 {})", type, server.getPort(), port);
        return configServer;
    }

    /**
     * 并发压测(只统计完成量，不采集延迟明细)。
     *
     * @param baseUrl       服务根地址
     * @param connections   并发连接数
     * @param totalRequests 总请求数
     * @throws Exception 压测过程异常
     */
    private static void fire(String baseUrl, int connections, long totalRequests) throws Exception {
        fireCollect(baseUrl, connections, totalRequests, new AtomicLong());
    }

    /**
     * 并发压测并采集延迟明细。
     *
     * <p>每"虚拟线程"持有独立 HttpClient(复用连接)，按总量均分请求配额。</p>
     *
     * @param baseUrl       服务根地址
     * @param connections   并发连接数
     * @param totalRequests 总请求数
     * @param errors        错误计数器
     * @return 每请求延迟明细(毫秒)
     */
    private static List<Long> fireCollect(String baseUrl, int connections, long totalRequests,
                                          AtomicLong errors) throws Exception {
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        long perConnection = Math.max(totalRequests / connections, 1);
        CompletableFuture<?>[] workers = new CompletableFuture[connections];
        for (int i = 0; i < connections; i++) {
            workers[i] = CompletableFuture.runAsync(() -> runWorker(baseUrl, perConnection, latencies, errors));
        }
        CompletableFuture.allOf(workers).join();
        return latencies;
    }

    /**
     * 单个工作线程循环：独立客户端顺序发送配额内请求。
     *
     * @param baseUrl      服务根地址
     * @param quota        本工作线程请求配额
     * @param latencies    延迟采集列表
     * @param errors       错误计数器
     */
    private static void runWorker(String baseUrl, long quota, List<Long> latencies, AtomicLong errors) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + BENCH_PATH)).GET().build();
            for (long j = 0; j < quota; j++) {
                long start = System.nanoTime();
                try {
                    HttpResponse<String> resp = client.send(request,
                            HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() != STATUS_OK) {
                        errors.incrementAndGet();
                    }
                    latencies.add((System.nanoTime() - start) / 1_000_000L);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
        } catch (Exception e) {
            errors.incrementAndGet();
        }
    }

    /**
     * 计算平均延迟。
     *
     * @param latencies 延迟列表
     * @return 平均值(毫秒)
     */
    private static double avgOf(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return 0.0;
        }
        long total = 0;
        for (Long latency : latencies) {
            total += latency;
        }
        return (double) total / latencies.size();
    }

    /**
     * 从期望端口开始探测可用端口，占用则 +10 重试，最多 5 次。
     *
     * @param requestedPort 期望端口
     * @return 可用端口；全部失败返回 -1
     */
    private static int resolveFreePort(int requestedPort) {
        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int candidate = requestedPort + attempt * PORT_RETRY_STEP;
            if (isPortBindable(candidate)) {
                if (attempt > 0) {
                    log.info("[BOOT] 端口 {} 占用，重试使用 {}", requestedPort, candidate);
                }
                return candidate;
            }
        }
        return -1;
    }

    /**
     * 判断端口当前是否可绑定（try-with-resources 即开即关）。
     *
     * @param port 候选端口
     * @return 可绑定返回 true
     */
    private static boolean isPortBindable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 命令行参数容器。
     *
     * @param rounds      总请求数
     * @param connections 并发连接数
     * @param port        起始端口
     * @since 4.0.0.42
     */
    private record Args(long rounds, int connections, int port) {

        /** 解析 */
        static Args parse(String[] args) {
            long rounds = DEFAULT_ROUNDS;
            int connections = DEFAULT_CONNECTIONS;
            int port = DEFAULT_PORT;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--rounds" -> {
                        if (i + 1 < args.length) {
                            rounds = Long.parseLong(args[++i]);
                        }
                    }
                    case "--connections" -> {
                        if (i + 1 < args.length) {
                            connections = Integer.parseInt(args[++i]);
                        }
                    }
                    case "--port" -> {
                        if (i + 1 < args.length) {
                            port = Integer.parseInt(args[++i]);
                        }
                    }
                    default -> log.warn("未知参数: {}", args[i]);
                }
            }
            rounds = Math.max(rounds, WARMUP_REQUESTS + 1);
            connections = Math.max(connections, 1);
            return new Args(rounds, connections, port);
        }
    }
}

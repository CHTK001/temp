package com.chua.example.network.mqtt;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.mqtt.support.client.MqttClientWrapper;
import com.chua.mqtt.support.server.MqttServer;
import com.chua.example.network.perf.PerfReportExample;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * MqttServer 自检 + 性能基准（SPI 形式）。
 *
 * <p>通过 {@code ExampleRunner --example=mqtt-server} 调用。
 * MQTT 服务器：基于原生 JDK ServerSocket 实现的 MQTT 3.1.1 嵌入式服务器，
 * 通过 {@code @Spi("mqtt")} 注册。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=mqtt-server
 *   java ExampleRunner --example=mqtt-server --mode=perf
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MqttServerExampleSpi implements Example {

    /** Default_concurrency */
    private static final int DEFAULT_CONCURRENCY = 8;
    /** Default_requests_per_conn */
    private static final int DEFAULT_REQUESTS_PER_CONN = 200;
    /** Default_connections */
    private static final int DEFAULT_CONNECTIONS = 8;
    /** Default_payload_size */
    private static final int DEFAULT_PAYLOAD_SIZE = 256;

    /** Sweep_concurrency */
    private static final int[] SWEEP_CONCURRENCY = {1, 4, 16, 64, 128, 256, 512};
    /** Sweep_requests_per_conn */
    private static final int SWEEP_REQUESTS_PER_CONN = 500;
    /** Sweep_connections */
    private static final int SWEEP_CONNECTIONS = 256;
    /** Sweep_payload */
    private static final int SWEEP_PAYLOAD = 256;

    @Override
    /** Name */
    public String name() {
        return "mqtt-server";
    }

    @Override
    /** Module */
    public String module() {
        return "mqtt-server";
    }

    @Override
    /** Description */
    public String description() {
        return "MqttServer 自检 + 性能基准（MQTT 3.1.1）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== mqtt-server --test [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "func".equals(mode)) {
            passed &= testConnectPublish();
        }
        if ("all".equals(mode) || "perf".equals(mode)) {
            int concurrency = Integer.parseInt(args.getOrDefault("concurrency", String.valueOf(DEFAULT_CONCURRENCY)));
            int requestsPerConn = Integer.parseInt(args.getOrDefault("requests", String.valueOf(DEFAULT_REQUESTS_PER_CONN)));
            int connections = Integer.parseInt(args.getOrDefault("connections", String.valueOf(DEFAULT_CONNECTIONS)));
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(DEFAULT_PAYLOAD_SIZE)));
            passed &= runPerf(concurrency, connections, requestsPerConn, payloadSize);
        }
        if ("sweep".equals(mode)) {
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(SWEEP_PAYLOAD)));
            passed &= runSweep(payloadSize);
        }
        return passed;
    }

    // ==================== 功能 ====================

    /** Test连接发布 */
    private boolean testConnectPublish() {
        log.info("  [FUNC-01] MQTT CONNECT + PUBLISH/SUBSCRIBE");
        MqttServer server = null;
        MqttClientWrapper client = null;
        try {
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            server = new MqttServer(setting);
            server.start();
            int port = server.getPort();

            CountDownLatch received = new CountDownLatch(1);
            String[] receivedMsg = new String[1];
            server.onSubscribe("test/topic", (topic, payload) -> {
                receivedMsg[0] = payload;
                received.countDown();
            });

            client = MqttClientWrapper.builder()
                    .broker("tcp://127.0.0.1:" + port)
                    .clientId("test-client-" + System.nanoTime())
                    .build();
            client.start();
            client.subscribe().topic("test/topic").qos(1).start();
            Thread.sleep(100);
            client.publish().topic("test/topic").payload("mqtt-publish-test").qos(1).send();

            assertTrue(received.await(5, TimeUnit.SECONDS), "应在 5s 内收到消息");
            assertEquals("mqtt-publish-test", receivedMsg[0], "消息体应一致");
            pass();
            return true;
        } catch (Exception e) {
            fail("MQTT 自检异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    // ==================== 性能 ====================

    /** 运行Perf */
    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReportExample.printEnvironment("MqttServer", "mqtt", "无 (本地直连)");
        log.info("  │ 代理路径 : MqttClientWrapper (Paho) -> MqttServer (原生 ServerSocket + fixed worker pool)");
        MqttServer server = null;
        try {
            server = newMqttServer(connections);
            int port = server.getPort();
            PerfReportExample.SweepRow row = runPerfInner(concurrency, connections, requestsPerConn, payloadSize, port, server);
            if (row == null) {
                return false;
            }
            PerfReportExample.printResult("mqtt-server PUBLISH bench/topic 压力", row.concurrency, row.connections, row.requestsPerConn,
                    payloadSize, row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, 0L);
            pass();
            return true;
        } catch (Exception e) {
            fail("PERF 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** 运行Sweep */
    private boolean runSweep(int payloadSize) {
        PerfReportExample.printEnvironment("MqttServer [sweep]", "mqtt", "无 (本地直连)");
        log.info("  │ 代理路径 : MqttClientWrapper (Paho) -> MqttServer (原生 ServerSocket + fixed worker pool)");
        MqttServer server = null;
        try {
            int maxConn = SWEEP_CONCURRENCY[SWEEP_CONCURRENCY.length - 1] * 4;
            server = newMqttServer(maxConn);
            int port = server.getPort();

            List<PerfReportExample.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReportExample.SweepRow row = runPerfInner(cc, conn, req, payloadSize, port, server);
                if (row != null) {
                    rows.add(row);
                }
            }
            PerfReportExample.printSweepResult("mqtt-server PUBLISH 扫档 (按并发分配连接 / 500 请求每连接 / 并发扫描)", payloadSize, rows);
            return !rows.isEmpty();
        } catch (Exception e) {
            log.error("SWEEP 异常: {}", e.getMessage(), e);
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** NewMqttServer */
    private MqttServer newMqttServer(int connections) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(0);
        setting.setWorkerThreads(Math.max(64, connections * 8));
        MqttServer s = new MqttServer(setting);
        s.start();
        return s;
    }

    /**
     * 运行PerfInner
     * @param concurrency concurrency
     * @param connections connections
     * @param requestsPerConn requestsPerConn
     * @param payloadSize payloadSize
     * @param port port
     * @param server server
     */
    private PerfReportExample.SweepRow runPerfInner(int concurrency, int connections, int requestsPerConn, int payloadSize,
                                              int port, MqttServer server) {
        ExecutorService pool = null;
        List<MqttClientWrapper> clients = Collections.synchronizedList(new ArrayList<>(connections));
        try {
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String message = new String(payload, StandardCharsets.UTF_8);

            CountDownLatch receivedAll = new CountDownLatch(connections * requestsPerConn);
            server.onSubscribe("bench/topic", (topic, payloadStr) -> receivedAll.countDown());

            pool = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256), new ThreadFactory() { private final AtomicInteger n = new AtomicInteger(1); public Thread newThread(Runnable r) { Thread t = new Thread(r, "mqtt-bench-" + n.getAndIncrement()); t.setDaemon(true); return t; } });
            CountDownLatch ready = new CountDownLatch(connections);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(connections);
            LongAdder errors = new LongAdder();
            List<long[]> latencies = Collections.synchronizedList(new ArrayList<>(connections));

            for (int i = 0; i < connections; i++) {
                final int idx = i;
                pool.submit(() -> {
                    MqttClientWrapper client = null;
                    try {
                        String clientId = "bench-" + idx + "-" + System.nanoTime();
                        client = MqttClientWrapper.builder()
                                .broker("tcp://127.0.0.1:" + port)
                                .clientId(clientId)
                                .connectionTimeout(10)
                                .build();
                        client.start();
                        clients.add(client);
                        ready.countDown();
                        start.await();
                        long[] mine = new long[requestsPerConn];
                        for (int k = 0; k < requestsPerConn; k++) {
                            long s = System.nanoTime();
                            client.publish().topic("bench/topic").payload(message).qos(0).sendAndWait(5000);
                            mine[k] = System.nanoTime() - s;
                        }
                        latencies.add(mine);
                    } catch (Exception e) {
                        errors.increment();
                    } finally {
                        done.countDown();
                    }
                });
            }

            if (!ready.await(30, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 客户端就绪超时", concurrency);
                return null;
            }
            Thread.sleep(50);
            long startWall = System.nanoTime();
            start.countDown();
            if (!done.await(300, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 客户端完成超时", concurrency);
                return null;
            }
            long elapsedNs = System.nanoTime() - startWall;

            if (!receivedAll.await(60, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 服务端接收未完成 (剩余={})", concurrency, receivedAll.getCount());
                return null;
            }

            long[] all = PerfReportExample.mergeLatencies(latencies);
            Arrays.sort(all);
            long total = (long) connections * requestsPerConn;
            long elapsedMs = elapsedNs / 1_000_000L;
            return new PerfReportExample.SweepRow(concurrency, connections, requestsPerConn, total, errors.sum(), elapsedMs, all);
        } catch (Exception e) {
            log.warn("  │ 并发={} 异常: {}", concurrency, e.getMessage());
            return null;
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            for (MqttClientWrapper c : clients) {
                closeQuietly(c);
            }
        }
    }

    // ==================== 辅助 ====================

    /** Assert判断相等 */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /** AssertTrue */
    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    /** Pass */
    private static void pass() {
        log.info("  \u2713 通过");
    }

    /** Fail */
    private static void fail(String msg) {
        log.info("  \u2717 失败: {}", msg);
    }

    /** 关闭Quietly */
    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
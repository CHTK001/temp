package com.chua.example.network.netty;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.http.ConfigServer;
import com.chua.example.network.perf.PerfReportExample;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Netty HTTP Server 自检 + 性能基准（SPI 形式）。
 *
 * <p>通过 {@code ExampleRunner --example=netty-http} 调用。
 * HTTP 服务器：基于 Netty 4.2.15 NIO 模型，
 * 通过 {@code ServerBuilder.type("netty")} 加载。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=netty-http
 *   java ExampleRunner --example=netty-http --mode=perf
 *   java ExampleRunner --example=netty-http --mode=sweep
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class NettyHttpServerExampleSpi implements Example {

    /** Default_concurrency */
    private static final int DEFAULT_CONCURRENCY = 256;
    /** Default_requests_per_conn */
    private static final int DEFAULT_REQUESTS_PER_CONN = 1000;
    /** Default_connections */
    private static final int DEFAULT_CONNECTIONS = 64;
    /** Default_payload_size */
    private static final int DEFAULT_PAYLOAD_SIZE = 128;

    /** Sweep_concurrency */
    private static final int[] SWEEP_CONCURRENCY = {1, 4, 16, 64, 128, 256, 512, 1000, 2000};
    /** Sweep_requests_per_conn */
    private static final int SWEEP_REQUESTS_PER_CONN = 500;
    /** Sweep_connections */
    private static final int SWEEP_CONNECTIONS = 256;
    /** Sweep_payload */
    private static final int SWEEP_PAYLOAD = 128;

    @Override
    /** Name */
    public String name() {
        return "netty-http";
    }

    @Override
    /** Module */
    public String module() {
        return "netty-http";
    }

    @Override
    /** Description */
    public String description() {
        return "NettyHttpServer 自检 + SPI 切换 + 性能基准（Netty 4.2.15 NIO）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== netty-http --test [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "spi".equals(mode)) {
            passed &= testSpiSwitch();
        }
        if ("all".equals(mode) || "func".equals(mode)) {
            passed &= testGetEcho();
            passed &= testPostEcho();
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

    // ==================== SPI ====================

    /** TestSpiSwitch */
    private boolean testSpiSwitch() {
        log.info("  [SPI-01] ServerBuilder.type(\"netty\") 加载 NettyHttpServer");
        Server server = null;
        try {
            server = ServerBuilder.create().type("netty").host("127.0.0.1").port(0).build();
            assertTrue(server != null, "应通过 SPI 加载");
            assertEquals("netty", server.getClass().getSimpleName().toLowerCase(), "实际类型应包含 netty");
            assertEquals("http", server.getProtocol(), "协议类型应为 http");
            log.info("    SPI 加载实现: {}", server.getClass().getName());
            pass();
            return true;
        } catch (Exception e) {
            fail("SPI 切换异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    // ==================== 功能 ====================

    /** Test获取Echo */
    private boolean testGetEcho() {
        log.info("  [FUNC-01] GET /echo 回显");
        Server server = null;
        try {
            server = ServerBuilder.create().type("netty").host("127.0.0.1").port(0).build();
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> {
                resp.setResult("netty-http-echo");
            });
            server.start();
            int port = server.getPort();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "GET /echo 状态码");
            assertEquals("netty-http-echo", resp.body(), "GET /echo 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("GET /echo 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestPostEcho */
    private boolean testPostEcho() {
        log.info("  [FUNC-02] POST /echo 回显请求体");
        Server server = null;
        try {
            server = ServerBuilder.create().type("netty").host("127.0.0.1").port(0).build();
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> {
                resp.setResult(req.getBodyString());
            });
            server.start();
            int port = server.getPort();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
                            .timeout(Duration.ofSeconds(5))
                            .POST(HttpRequest.BodyPublishers.ofString("hello-netty-post"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "POST /echo 状态码");
            assertEquals("hello-netty-post", resp.body(), "POST /echo 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("POST /echo 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    // ==================== 性能 ====================

    /** 运行Perf */
    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReportExample.printEnvironment("NettyHttpServer", "netty", "无 (直连)");
        log.info("  │ 代理路径 : HttpClient -> NettyHttpServer (Netty 4.2.15 NIO + virtual-thread 业务)");
        Server server = null;
        try {
            server = ServerBuilder.create().type("netty").host("127.0.0.1").port(0).build();
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> {
                resp.setResult(body);
            });
            server.start();
            int port = server.getPort();

            PerfReportExample.SweepRow row = runPerfInner(concurrency, connections, requestsPerConn, port);
            if (row == null) {
                return false;
            }
            PerfReportExample.printResult("netty-http GET /echo 压力", row.concurrency, row.connections, row.requestsPerConn,
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
        PerfReportExample.printEnvironment("NettyHttpServer [sweep]", "netty", "无 (直连)");
        log.info("  │ 代理路径 : HttpClient -> NettyHttpServer (Netty 4.2.15 NIO + virtual-thread 业务)");
        Server server = null;
        try {
            server = ServerBuilder.create().type("netty").host("127.0.0.1").port(0).build();
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> {
                resp.setResult(body);
            });
            server.start();
            int port = server.getPort();

            List<PerfReportExample.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReportExample.SweepRow row = runPerfInner(cc, conn, req, port);
                if (row != null) {
                    rows.add(row);
                }
            }
            PerfReportExample.printSweepResult("netty-http GET /echo 扫档 (按并发比例分配连接 / 500 请求每连接 / 并发扫描)", payloadSize, rows);
            return !rows.isEmpty();
        } catch (Exception e) {
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** 运行PerfInner */
    private PerfReportExample.SweepRow runPerfInner(int concurrency, int connections, int requestsPerConn, int port) {
        ExecutorService pool = null;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .executor(Executors.newVirtualThreadPerTaskExecutor())
                    .build();

            pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch ready = new CountDownLatch(connections);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(connections);
            LongAdder errors = new LongAdder();
            List<long[]> latencies = Collections.synchronizedList(new ArrayList<>(connections));

            for (int i = 0; i < connections; i++) {
                pool.submit(() -> {
                    try {
                        Thread.sleep(10);
                        ready.countDown();
                        start.await();
                        long[] mine = new long[requestsPerConn];
                        for (int k = 0; k < requestsPerConn; k++) {
                            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
                                    .timeout(Duration.ofSeconds(30))
                                    .GET().build();
                            long s = System.nanoTime();
                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            if (resp.statusCode() != 200) {
                                errors.increment();
                                return;
                            }
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

            if (!ready.await(45, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 就绪超时", concurrency);
                return null;
            }
            Thread.sleep(50);
            long startWall = System.nanoTime();
            start.countDown();
            if (!done.await(180, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 完成超时", concurrency);
                return null;
            }
            long elapsedNs = System.nanoTime() - startWall;

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
        }
    }

    // ==================== 辅助 ====================

    /** Assert判断相等 */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /** Assert判断相等 */
    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
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
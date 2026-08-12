package com.chua.example.network.http;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.http.ConfigServer;
import com.chua.common.support.network.server.impl.JdkHttpServer;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.example.network.perf.PerfReport;
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
 * JdkHttpServer 自检 + 性能基准（SPI 形式）。
 *
 * <p>通过 {@code ExampleRunner --example=http-server} 调用。
 * HTTP 服务器：基于 JDK {@code com.sun.net.httpserver.HttpServer} 包装，
 * 通过 {@code ServerBuilder.type("jdk")} 加载。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=http-server
 *   java ExampleRunner --example=http-server --mode=perf
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HttpServerExampleSpi implements Example {

    private static final int DEFAULT_CONCURRENCY = 64;
    private static final int DEFAULT_REQUESTS_PER_CONN = 500;
    private static final int DEFAULT_CONNECTIONS = 64;
    private static final int DEFAULT_PAYLOAD_SIZE = 128;

    private static final int[] SWEEP_CONCURRENCY = {1, 4, 16, 64, 128, 256, 512, 1000, 2000};
    private static final int SWEEP_REQUESTS_PER_CONN = 500;
    private static final int SWEEP_CONNECTIONS = 256;
    private static final int SWEEP_PAYLOAD = 128;

    @Override
    public String name() {
        return "http-server";
    }

    @Override
    public String module() {
        return "http-server";
    }

    @Override
    public String description() {
        return "JdkHttpServer 自检 + SPI 切换 + 性能基准";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== http-server --test [mode={}] =====", mode);
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

    private boolean testSpiSwitch() {
        log.info("  [SPI-01] ServerBuilder.type(\"jdk\") 加载 JdkHttpServer");
        Server server = null;
        try {
            server = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            assertTrue(server != null, "应通过 SPI 加载");
            assertTrue(server instanceof JdkHttpServer, "实际类型应为 JdkHttpServer，实际: " + server.getClass().getName());
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

    private boolean testGetEcho() {
        log.info("  [FUNC-01] GET /echo 回显");
        Server server = null;
        try {
            server = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> { resp.setResult("http-server-echo"); });
            server.start();
            int port = server.getPort();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "GET /echo 状态码");
            assertEquals("http-server-echo", resp.body(), "GET /echo 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("GET /echo 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    private boolean testPostEcho() {
        log.info("  [FUNC-02] POST /echo 回显请求体");
        Server server = null;
        try {
            server = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> { resp.setResult(req.getBodyString()); });
            server.start();
            int port = server.getPort();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
                            .timeout(Duration.ofSeconds(5))
                            .POST(HttpRequest.BodyPublishers.ofString("hello-post"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "POST /echo 状态码");
            assertEquals("hello-post", resp.body(), "POST /echo 响应体");
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

    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReport.printEnvironment("JdkHttpServer", "jdk", "无 (直连)");
        log.info("  │ 代理路径 : HttpClient -> JdkHttpServer (JDK HttpServer + virtual-thread executor)");
        Server server = null;
        try {
            server = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> { resp.setResult(body); });
            server.start();
            int port = server.getPort();

            PerfReport.SweepRow row = runPerfInner(concurrency, connections, requestsPerConn, port);
            if (row == null) {
                return false;
            }
            PerfReport.printResult("http-server GET /echo 压力", row.concurrency, row.connections, row.requestsPerConn,
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

    private boolean runSweep(int payloadSize) {
        PerfReport.printEnvironment("JdkHttpServer [sweep]", "jdk", "无 (直连)");
        log.info("  │ 代理路径 : HttpClient -> JdkHttpServer (JDK HttpServer + virtual-thread executor)");
        Server server = null;
        try {
            server = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> { resp.setResult(body); });
            server.start();
            int port = server.getPort();

            List<PerfReport.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReport.SweepRow row = runPerfInner(cc, conn, req, port);
                if (row != null) {
                    rows.add(row);
                }
            }
            PerfReport.printSweepResult("http-server GET /echo 扫档 (按并发比例分配连接 / 500 请求每连接 / 并发扫描)", payloadSize, rows);
            return !rows.isEmpty();
        } catch (Exception e) {
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    private PerfReport.SweepRow runPerfInner(int concurrency, int connections, int requestsPerConn, int port) {
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
                                    .timeout(Duration.ofSeconds(10))
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

            if (!ready.await(30, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 就绪超时", concurrency);
                return null;
            }
            Thread.sleep(50);
            long startWall = System.nanoTime();
            start.countDown();
            if (!done.await(120, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 完成超时", concurrency);
                return null;
            }
            long elapsedNs = System.nanoTime() - startWall;

            long[] all = PerfReport.mergeLatencies(latencies);
            Arrays.sort(all);
            long total = (long) connections * requestsPerConn;
            long elapsedMs = elapsedNs / 1_000_000L;
            return new PerfReport.SweepRow(concurrency, connections, requestsPerConn, total, errors.sum(), elapsedMs, all);
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

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void pass() {
        log.info("  \u2713 通过");
    }

    private static void fail(String msg) {
        log.info("  \u2717 失败: {}", msg);
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}

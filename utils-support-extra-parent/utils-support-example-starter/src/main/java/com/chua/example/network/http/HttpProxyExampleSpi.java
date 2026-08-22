package com.chua.example.network.http;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerAttribute;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.ServerFilterConfig;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.example.network.perf.PerfReportExample;
import com.chua.example.spi.Example;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
 * HTTP 反向代理自检 + 性能基准（基于 SimpleForwardFilter 同步转发，JDK HttpClient）。
 *
 * <p>通过 {@code ExampleRunner --example=http-proxy} 调用。
 * HTTP 代理：客户端请求 → JdkHttpServer → SimpleForwardFilter (JDK HttpClient sync) → 后端 HttpServer。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=http-proxy
 *   java ExampleRunner --example=http-proxy --mode=perf
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HttpProxyExampleSpi implements Example {

    /** Default_concurrency */
    private static final int DEFAULT_CONCURRENCY = 64;
    /** Default_requests_per_conn */
    private static final int DEFAULT_REQUESTS_PER_CONN = 300;
    /** Default_connections */
    private static final int DEFAULT_CONNECTIONS = 32;
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
        return "http-proxy";
    }

    @Override
    /** Module */
    public String module() {
        return "http-proxy";
    }

    @Override
    /** Description */
    public String description() {
        return "HTTP 反向代理自检 + 性能基准（SimpleForwardFilter，JDK HttpClient 同步）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== http-proxy --test [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "func".equals(mode)) {
            passed &= testProxyHttpPath();
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

    /** TestProxyHttpPath */
    private boolean testProxyHttpPath() {
        log.info("  [FUNC-01] HTTP 反向代理 GET /echo");
        HttpServer backend = null;
        Server proxy = null;
        try {
            backend = startBackendHttp(0);
            int backendPort = backend.getAddress().getPort();

            proxy = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            // 注入后端地址（在请求进入时由 discovery filter 注入）
            proxy.addFilter(new FixedDiscoveryFilter(backendPort));
            proxy.addFilter(new SimpleForwardFilter(backendPort, 10));

            proxy.start();
            int proxyPort = proxy.getPort();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + proxyPort + "/echo"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "状态码应为 200");
            assertEquals("http-backend-echo", resp.body(), "响应体应来自后端");
            pass();
            return true;
        } catch (Exception e) {
            fail("HTTP 反向代理自检异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            if (backend != null) {
                backend.stop(0);
            }
        }
    }

    // ==================== 性能 ====================

    /** 运行Perf */
    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReportExample.printEnvironment("HTTP ReverseProxy", "jdk + SimpleForwardFilter (JDK HttpClient sync)",
                "fixed (后端固定地址)");
        log.info("  │ 代理路径 : HttpClient -> JdkHttpServer -> SimpleForwardFilter (JDK HttpClient sync) -> Backend HttpServer");
        HttpServer backend = null;
        Server proxy = null;
        try {
            backend = startBackendHttp(0, payloadSize);
            int backendPort = backend.getAddress().getPort();
            proxy = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            proxy.addFilter(new FixedDiscoveryFilter(backendPort));
            proxy.addFilter(new SimpleForwardFilter(backendPort, 10));
            proxy.start();
            int proxyPort = proxy.getPort();

            PerfReportExample.SweepRow row = runPerfInner(concurrency, connections, requestsPerConn, proxyPort);
            if (row == null) {
                return false;
            }
            PerfReportExample.printResult("http-proxy GET /echo 压力", row.concurrency, row.connections, row.requestsPerConn,
                    payloadSize, row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, 0L);
            pass();
            return true;
        } catch (Exception e) {
            fail("PERF 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            if (backend != null) {
                backend.stop(0);
            }
        }
    }

    /** 运行Sweep */
    private boolean runSweep(int payloadSize) {
        PerfReportExample.printEnvironment("HTTP ReverseProxy [sweep]", "jdk + SimpleForwardFilter (JDK HttpClient sync)",
                "fixed (后端固定地址)");
        log.info("  │ 代理路径 : HttpClient -> JdkHttpServer -> SimpleForwardFilter (JDK HttpClient sync) -> Backend HttpServer");
        HttpServer backend = null;
        Server proxy = null;
        try {
            backend = startBackendHttp(0, payloadSize);
            int backendPort = backend.getAddress().getPort();
            proxy = ServerBuilder.create().type("jdk").host("127.0.0.1").port(0).build();
            proxy.addFilter(new FixedDiscoveryFilter(backendPort));
            proxy.addFilter(new SimpleForwardFilter(backendPort, 10));
            proxy.start();
            int proxyPort = proxy.getPort();

            List<PerfReportExample.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReportExample.SweepRow row = runPerfInner(cc, conn, req, proxyPort);
                if (row != null) {
                    rows.add(row);
                }
            }
            PerfReportExample.printSweepResult("http-proxy GET /echo 扫档 (按并发比例分配连接 / 500 请求每连接 / 并发扫描)", payloadSize, rows);
            return !rows.isEmpty();
        } catch (Exception e) {
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            if (backend != null) {
                backend.stop(0);
            }
        }
    }

    /** 运行PerfInner */
    private PerfReportExample.SweepRow runPerfInner(int concurrency, int connections, int requestsPerConn, int proxyPort) {
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
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create("http://127.0.0.1:" + proxyPort + "/echo"))
                                    .timeout(Duration.ofSeconds(10))
                                    .GET()
                                    .build();
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

    // ==================== 后端 ====================

    /** 开始BackendHttp */
    private static HttpServer startBackendHttp(int port) throws IOException {
        return startBackendHttp(port, 0);
    }

    /** 开始BackendHttp */
    private static HttpServer startBackendHttp(int port, int payloadSize) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = "http-backend-echo".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        if (payloadSize > 0) {
            server.createContext("/payload", exchange -> {
                byte[] body = new byte[payloadSize];
                Arrays.fill(body, (byte) 'A');
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    /**
     * 固定后端发现注入器：将后端地址写入请求的 BACKEND_DISCOVERY 属性，
     * 供反向代理过滤器读取。
     */
    private static final class FixedDiscoveryFilter implements ServerFilter {
        /** Backend端口 */
        private final int backendPort;

        FixedDiscoveryFilter(int backendPort) {
            this.backendPort = backendPort;
        }

        @Override
        /** 获取Order */
        public int getOrder() {
            return Integer.MIN_VALUE + 1;
        }

        @Override
        /** Do过滤 */
        public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
            Discovery discovery = Discovery.builder()
                    .host("127.0.0.1")
                    .port(backendPort)
                    .protocol("http")
                    .build();
            ServerAttribute.setBackendDiscovery(request, discovery);
            chain.doFilter(request, response);
        }

        @Override
        /** 初始化 */
        public void init(ServerFilterConfig config) {
        }
    }

    /**
     * 简单同步 HTTP 反向代理过滤器：基于 JDK HttpClient 同步调用后端，
     * 把响应体回写到客户端。用于自检 + 性能压测。
     */
    private static final class SimpleForwardFilter implements ServerFilter {
        /** Backend端口 */
        private final int backendPort;
        /** 超时秒 */
        private final int timeoutSeconds;
        /** httpClient */
        private volatile HttpClient httpClient;

        SimpleForwardFilter(int backendPort, int timeoutSeconds) {
            this.backendPort = backendPort;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        /** 获取Order */
        public int getOrder() {
            return Integer.MAX_VALUE - 100;
        }

        @Override
        /** Do过滤 */
        public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
            HttpClient client = getClient();
            URI uri = URI.create("http://127.0.0.1:" + backendPort + request.getPath());
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds));
            byte[] body = request.getBody();
            String method = request.getMethod() == null ? "GET" : request.getMethod().name();
            switch (method) {
                case "GET" -> builder.GET();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
                case "DELETE" -> builder.DELETE();
                default -> builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
            }
            try {
                HttpResponse<byte[]> backend = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
                response.setStatus(backend.statusCode());
                byte[] respBody = backend.body();
                if (respBody != null && respBody.length > 0) {
                    response.setBody(respBody);
                }
                response.end();
            } catch (Exception e) {
                response.setStatus(502);
                response.setBody(("Bad Gateway: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                response.end();
            }
        }

        /** 获取Client */
        private HttpClient getClient() {
            HttpClient c = httpClient;
            if (c == null) {
                synchronized (this) {
                    if (httpClient == null) {
                        httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();
                    }
                    c = httpClient;
                }
            }
            return c;
        }

        @Override
        /** 初始化 */
        public void init(ServerFilterConfig config) {
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

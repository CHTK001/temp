package com.chua.example.network.http;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.http.ConfigServer;
import com.chua.common.support.network.server.nio.NioHttpServer;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.network.perf.PerfReport;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * HttpServer 全功能自检 + 性能基准（SPI 形式）。
 *
 * <p>通过 {@code --type} 参数切换底层实现（jdk / nio），对等测试同一套用例。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=http-server                       # 默认 jdk
 *   java ExampleRunner --example=http-server --type=nio            # NIO 实现
 *   java ExampleRunner --example=http-server --type=jdk --mode=perf
 *   java ExampleRunner --example=http-server --type=nio --mode=sweep
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HttpServerExampleSpi implements Example {

    /** Default_concurrency */
    private static final int DEFAULT_CONCURRENCY = 64;
    /** Default_requests_per_conn */
    private static final int DEFAULT_REQUESTS_PER_CONN = 500;
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

    /** 压测报告要求的并发等级 */
    private static final int[] BENCH_CONCURRENCY = {100, 500, 1000, 2000, 5000};
    /** Bench_requests_per_conn */
    private static final int BENCH_REQUESTS_PER_CONN = 500;
    /** Bench_connections */
    private static final int BENCH_CONNECTIONS = 256;
    /** Bench_payload */
    private static final int BENCH_PAYLOAD = 128;

    /** 压测报告输出路径 */
    private static final String BENCH_REPORT_PATH = "target/http-server-benchmark.md";

    /** 真·并发压测：并发等级 = 同时连接数（每连接 1 次请求，flash 模式） */
    private static final int[] CONC_CONCURRENCY = {100, 500, 1000, 2000, 5000};
    /** Conc_requests_per_conn */
    private static final int CONC_REQUESTS_PER_CONN = 1;
    /** Conc_payload */
    private static final int CONC_PAYLOAD = 128;

    /** 真·并发压测报告输出路径 */
    private static final String CONC_REPORT_PATH = "target/http-server-concurrent.md";

    /** 全子类压测：遍历所有 HttpServer SPI 实现（按 classpath 实际可用为准） */
    private static final String[] ALL_SERVER_TYPES = {
            "jdk", "nio", "vertx-http", "http", "armeria-http", "jrebel", "kcp-http", "quarkus-http"
    };
    /** 全子类压测报告目录 */
    private static final String ALL_REPORT_DIR = "target/http-server-all";

    /** 当前测试使用的服务器类型（jdk / nio / vertx-http ...） */
    private String serverType = "jdk";

    @Override
    /** Name */
    public String name() {
        return "http-server";
    }

    @Override
    /** Module */
    public String module() {
        return "http-server";
    }

    @Override
    /** Description */
    public String description() {
        return "HttpServer 全功能自检 + SPI 切换 + 性能基准（--type=jdk|nio）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        serverType = args.getOrDefault("type", "jdk");
        String mode = args.getOrDefault("mode", "all");
        log.info("===== http-server [type={}, mode={}] =====", serverType, mode);

        boolean passed = true;
        if ("all".equals(mode) || "spi".equals(mode)) {
            passed &= testSpiSwitch();
        }
        if ("all".equals(mode) || "func".equals(mode)) {
            passed &= testGetEcho();
            passed &= testGetQueryParams();
            passed &= testPostPlainText();
            passed &= testPostJson();
            passed &= testPostFormUrlEncoded();
            passed &= testPutMethod();
            passed &= testDeleteMethod();
            passed &= testPatchMethod();
            passed &= testHeadMethod();
            passed &= testOptionsMethod();
            passed &= testCustomHeaders();
            passed &= testHeaderCaseInsensitivity();
            passed &= testStatusCodes();
            passed &= testKeepAlive();
            passed &= testLargeBody();
            passed &= testRemoteAddress();
            passed &= testByteBody();
            passed &= testSseStreaming();
            passed &= testNotFound404();
            passed &= testSslSelfSigned();
            passed &= testConcurrencyLimit();
            passed &= testWebSocketUpgrade();
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
        if ("bench".equals(mode)) {
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(BENCH_PAYLOAD)));
            String reportPath = args.getOrDefault("report", BENCH_REPORT_PATH);
            passed &= runBench(payloadSize, reportPath);
        }
        if ("conc".equals(mode) || "concurrent".equals(mode)) {
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(CONC_PAYLOAD)));
            String reportPath = args.getOrDefault("report", CONC_REPORT_PATH);
            passed &= runConcurrent(payloadSize, reportPath);
        }
        if ("all".equals(mode) || "all-servers".equals(mode)) {
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(BENCH_PAYLOAD)));
            String reportDir = args.getOrDefault("reportDir", ALL_REPORT_DIR);
            passed &= runAllServers(payloadSize, reportDir);
        }
        log.info("===== http-server [type={}] 结果: {} =====", serverType, passed ? "全部通过 ✓" : "存在失败 ✗");
        return passed;
    }

    /**
     * 压测全部 HttpServer 子类（按 classpath 实际可用为准），每个子类输出 html + md 报告。
     */
    private boolean runAllServers(int payloadSize, String reportDir) {
        boolean allPassed = true;
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(reportDir));
        } catch (java.io.IOException e) {
            log.warn("[all-servers] 报告目录创建失败: {}", e.getMessage());
        }
        for (String type : ALL_SERVER_TYPES) {
            String prevType = serverType;
            serverType = type;
            try {
                String mdPath = reportDir + "/" + type + "-benchmark.md";
                String htmlPath = reportDir + "/" + type + "-benchmark.html";
                boolean ok = runBench(payloadSize, mdPath, htmlPath);
                log.info("[all-servers] {} : {}", type, ok ? "✓" : "✗ (依赖缺失或加载失败)");
                allPassed &= ok;
            } catch (Exception e) {
                log.warn("[all-servers] {} 压测异常: {}", type, e.getMessage());
                allPassed = false;
            } finally {
                serverType = prevType;
            }
        }
        log.info("[all-servers] 报告输出目录: {}", reportDir);
        return allPassed;
    }

    // ==================== SPI ====================

    /** TestSpiSwitch */
    private boolean testSpiSwitch() {
        log.info("  [SPI-01] ServerBuilder.type(\"{}\") 加载实现", serverType);
        Server server = null;
        try {
            server = createServer();
            assertTrue(server != null, "应通过 SPI 加载");
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

    // ==================== 功能测试 ====================

    /** Test获取Echo */
    private boolean testGetEcho() {
        log.info("  [FUNC-01] GET /echo 回显");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/echo",
                    (req, resp) -> resp.setResult(serverType + "-echo")));
            HttpResponse<String> resp = get(server, "/echo");
            assertEquals(200, resp.statusCode(), "GET /echo 状态码");
            assertEquals(serverType + "-echo", resp.body(), "GET /echo 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("GET /echo 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** Test获取查询Params */
    private boolean testGetQueryParams() {
        log.info("  [FUNC-02] GET /query?name=hello&age=18 查询参数");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/query", (req, resp) -> {
                String name = req.getParam("name");
                String age = req.getParam("age");
                resp.setResult("name=" + name + ",age=" + age);
            }));
            HttpResponse<String> resp = get(server, "/query?name=hello&age=18");
            assertEquals(200, resp.statusCode(), "GET /query 状态码");
            assertEquals("name=hello,age=18", resp.body(), "GET /query 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("GET /query 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestPostPlainText */
    private boolean testPostPlainText() {
        log.info("  [FUNC-03] POST /echo 纯文本回显");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/echo",
                    (req, resp) -> resp.setResult(req.getBodyString())));
            HttpResponse<String> resp = post(server, "/echo", "text/plain", "hello-post");
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

    /** TestPostJson */
    private boolean testPostJson() {
        log.info("  [FUNC-04] POST /json JSON 请求体解析");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/json", (req, resp) -> {
                String ct = req.getContentType();
                String body = req.getBodyString();
                resp.setContentType("application/json");
                resp.setResult("{\"received\":" + body + ",\"contentType\":\"" + ct + "\"}");
            }));
            String jsonBody = "{\"name\":\"test\",\"value\":42}";
            HttpResponse<String> resp = post(server, "/json", "application/json", jsonBody);
            assertEquals(200, resp.statusCode(), "POST /json 状态码");
            assertTrue(resp.body().contains("\"received\":" + jsonBody), "POST /json 应包含原始 JSON");
            assertTrue(resp.body().contains("application/json"), "POST /json 应包含 Content-Type");
            pass();
            return true;
        } catch (Exception e) {
            fail("POST /json 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestPostFormUrlEncoded */
    private boolean testPostFormUrlEncoded() {
        log.info("  [FUNC-05] POST /form 表单 application/x-www-form-urlencoded");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/form", (req, resp) -> {
                Map<String, String> form = req.getFormData();
                resp.setResult("user=" + form.get("user") + ",pass=" + form.get("pass"));
            }));
            HttpResponse<String> resp = post(server, "/form",
                    "application/x-www-form-urlencoded", "user=admin&pass=123456");
            assertEquals(200, resp.statusCode(), "POST /form 状态码");
            assertEquals("user=admin,pass=123456", resp.body(), "POST /form 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("POST /form 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestPutMethod */
    private boolean testPutMethod() {
        log.info("  [FUNC-06] PUT /update 回显");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/update", (req, resp) ->
                    resp.setResult("method=" + req.getMethod().name() + ",body=" + req.getBodyString())));
            HttpClient client = client();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(uri(server, "/update"))
                            .timeout(Duration.ofSeconds(5))
                            .PUT(HttpRequest.BodyPublishers.ofString("put-data"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "PUT /update 状态码");
            assertEquals("method=PUT,body=put-data", resp.body(), "PUT /update 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("PUT /update 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** Test删除Method */
    private boolean testDeleteMethod() {
        log.info("  [FUNC-07] DELETE /remove 回显");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/remove", (req, resp) ->
                    resp.setResult("method=" + req.getMethod().name() + ",path=" + req.getPath())));
            HttpResponse<String> resp = client().send(
                    HttpRequest.newBuilder(uri(server, "/remove"))
                            .timeout(Duration.ofSeconds(5)).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "DELETE /remove 状态码");
            assertEquals("method=DELETE,path=/remove", resp.body(), "DELETE /remove 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("DELETE /remove 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestPatchMethod */
    private boolean testPatchMethod() {
        log.info("  [FUNC-08] PATCH /patch 部分更新回显");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/patch", (req, resp) ->
                    resp.setResult("method=" + req.getMethod().name() + ",body=" + req.getBodyString())));
            HttpResponse<String> resp = client().send(
                    HttpRequest.newBuilder(uri(server, "/patch"))
                            .timeout(Duration.ofSeconds(5))
                            .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"name\":\"updated\"}"))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "PATCH /patch 状态码");
            assertEquals("method=PATCH,body={\"name\":\"updated\"}", resp.body(), "PATCH /patch 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("PATCH /patch 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestHeadMethod */
    private boolean testHeadMethod() {
        log.info("  [FUNC-09] HEAD /head 只返回头");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/head", (req, resp) -> {
                resp.setHeader("X-Head-Test", "head-value");
                resp.setResult("this-body-should-be-ignored");
            }));
            HttpResponse<byte[]> resp = client().send(
                    HttpRequest.newBuilder(uri(server, "/head"))
                            .timeout(Duration.ofSeconds(5))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, resp.statusCode(), "HEAD /head 状态码");
            assertEquals("head-value",
                    resp.headers().firstValue("X-Head-Test").orElse(""),
                    "HEAD /head 自定义头");
            pass();
            return true;
        } catch (Exception e) {
            fail("HEAD /head 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestOptionsMethod */
    private boolean testOptionsMethod() {
        log.info("  [FUNC-10] OPTIONS /options");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/options", (req, resp) -> {
                resp.setHeader("Allow", "GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS");
                resp.setStatus(204);
            }));
            HttpResponse<String> resp = client().send(
                    HttpRequest.newBuilder(uri(server, "/options"))
                            .timeout(Duration.ofSeconds(5))
                            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, resp.statusCode(), "OPTIONS 状态码");
            String allow = resp.headers().firstValue("Allow").orElse("");
            assertTrue(allow.contains("GET"), "Allow 应包含 GET");
            assertTrue(allow.contains("PATCH"), "Allow 应包含 PATCH");
            assertTrue(allow.contains("OPTIONS"), "Allow 应包含 OPTIONS");
            pass();
            return true;
        } catch (Exception e) {
            fail("OPTIONS 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestCustomHeaders */
    private boolean testCustomHeaders() {
        log.info("  [FUNC-11] 自定义请求头 + 响应头");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/headers", (req, resp) -> {
                String custom = req.getHeader("X-Custom-Header");
                resp.setHeader("X-Response-Id", "srv-12345");
                resp.setHeader("X-Echo", custom != null ? custom : "missing");
                resp.setResult("ok");
            }));
            HttpResponse<String> resp = client().send(
                    HttpRequest.newBuilder(uri(server, "/headers"))
                            .timeout(Duration.ofSeconds(5))
                            .header("X-Custom-Header", "hello-srv")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "GET /headers 状态码");
            assertEquals("hello-srv", resp.headers().firstValue("X-Echo").orElse(""), "响应头 X-Echo");
            assertEquals("srv-12345", resp.headers().firstValue("X-Response-Id").orElse(""), "响应头 X-Response-Id");
            pass();
            return true;
        } catch (Exception e) {
            fail("自定义头异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestHeaderCaseInsensitivity */
    private boolean testHeaderCaseInsensitivity() {
        log.info("  [FUNC-12] 请求头大小写不敏感");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/h-ci", (req, resp) -> {
                String v1 = req.getHeader("X-My-Header");
                String v2 = req.getHeader("x-my-header");
                String v3 = req.getHeader("X-MY-HEADER");
                String ct = req.getHeader("content-type");
                String ctOrig = req.getContentType();
                resp.setResult("v1=" + v1 + ",v2=" + v2 + ",v3=" + v3
                        + ",ct=" + ct + ",ctOrig=" + ctOrig);
            }));
            HttpResponse<String> resp = client().send(
                    HttpRequest.newBuilder(uri(server, "/h-ci"))
                            .timeout(Duration.ofSeconds(5))
                            .header("X-My-Header", "case-test")
                            .header("Content-Type", "text/plain")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "状态码");
            assertTrue(resp.body().contains("v1=case-test"), "X-My-Header 应匹配");
            assertTrue(resp.body().contains("v2=case-test"), "x-my-header 应匹配");
            assertTrue(resp.body().contains("v3=case-test"), "X-MY-HEADER 应匹配");
            assertTrue(resp.body().contains("ct=text/plain"), "getHeader(content-type) 应返回 text/plain");
            assertTrue(resp.body().contains("ctOrig=text/plain"), "getContentType() 应返回 text/plain");
            pass();
            return true;
        } catch (Exception e) {
            fail("Header 大小写异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestStatusCodes */
    private boolean testStatusCodes() {
        log.info("  [FUNC-13] 状态码: 201, 204, 302, 400, 404, 500");
        Server server = null;
        try {
            server = startServer(cfg -> {
                cfg.registerMapping("/s201", (req, resp) -> { resp.setStatus(201); resp.setResult("created"); });
                cfg.registerMapping("/s204", (req, resp) -> resp.setStatus(204));
                cfg.registerMapping("/s302", (req, resp) -> resp.sendRedirect("/target"));
                cfg.registerMapping("/s400", (req, resp) -> resp.sendError(400, "Bad Request"));
                cfg.registerMapping("/s404", (req, resp) -> resp.sendError(404, "Not Found"));
                cfg.registerMapping("/s500", (req, resp) -> resp.sendError(500, "Server Error"));
            });

            assertEquals(201, get(server, "/s201").statusCode(), "201 Created");
            assertEquals(204, get(server, "/s204").statusCode(), "204 No Content");
            HttpClient noRedirect = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> r302 = noRedirect.send(
                    HttpRequest.newBuilder(uri(server, "/s302")).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(302, r302.statusCode(), "302 Found");
            assertEquals("/target", r302.headers().firstValue("Location").orElse(""), "302 Location");
            assertEquals(400, get(server, "/s400").statusCode(), "400 Bad Request");
            assertEquals(404, get(server, "/s404").statusCode(), "404 Not Found");
            assertEquals(500, get(server, "/s500").statusCode(), "500 Internal Server Error");
            pass();
            return true;
        } catch (Exception e) {
            fail("状态码异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestKeepAlive */
    private boolean testKeepAlive() {
        log.info("  [FUNC-14] Keep-Alive 连接复用（5 次请求同一连接）");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/ka",
                    (req, resp) -> resp.setResult("ka-" + System.nanoTime())));

            HttpClient c = client();
            String prevBody = null;
            for (int i = 0; i < 5; i++) {
                HttpResponse<String> resp = c.send(
                        HttpRequest.newBuilder(uri(server, "/ka"))
                                .timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, resp.statusCode(), "Keep-alive 第 " + (i + 1) + " 次状态码");
                assertTrue(resp.body().startsWith("ka-"), "Keep-alive 第 " + (i + 1) + " 次响应");
                assertTrue(!resp.body().equals(prevBody), "Keep-alive 响应应独立");
                prevBody = resp.body();
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("Keep-alive 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestLargeBody */
    private boolean testLargeBody() {
        log.info("  [FUNC-15] 大报文 1MB round-trip");
        Server server = null;
        try {
            int size = 1024 * 1024;
            byte[] payload = new byte[size];
            for (int i = 0; i < size; i++) {
                payload[i] = (byte) ('A' + (i % 26));
            }
            String payloadStr = new String(payload, StandardCharsets.UTF_8);

            server = startServer(cfg -> cfg.registerMapping("/big",
                    (req, resp) -> resp.setResult(req.getBodyString())));
            HttpResponse<String> resp = post(server, "/big", "text/plain", payloadStr);
            assertEquals(200, resp.statusCode(), "大报文 状态码");
            assertEquals(size, resp.body().length(), "大报文 响应体长度");
            assertEquals(payloadStr.substring(0, 100), resp.body().substring(0, 100), "大报文 前 100 字节");
            pass();
            return true;
        } catch (Exception e) {
            fail("大报文异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestRemoteAddress */
    private boolean testRemoteAddress() {
        log.info("  [FUNC-16] RemoteAddress / RemotePort");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/remote", (req, resp) ->
                    resp.setResult("addr=" + req.getRemoteAddress() + ",port=" + req.getRemotePort())));
            HttpResponse<String> resp = get(server, "/remote");
            assertEquals(200, resp.statusCode(), "GET /remote 状态码");
            assertTrue(resp.body().contains("addr=127.0.0.1"), "远程地址应为 127.0.0.1，实际: " + resp.body());
            String portStr = resp.body().substring(resp.body().indexOf("port=") + 5);
            assertTrue(Integer.parseInt(portStr) > 0, "远程端口应 > 0");
            pass();
            return true;
        } catch (Exception e) {
            fail("RemoteAddress 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestByteBody */
    private boolean testByteBody() {
        log.info("  [FUNC-17] setBody(byte[]) + getOutputStream()");
        Server server = null;
        try {
            server = startServer(cfg -> {
                cfg.registerMapping("/bytes", (req, resp) -> {
                    byte[] data = {0x48, 0x65, 0x6C, 0x6C, 0x6F}; // "Hello"
                    resp.setBody(data);
                });
                cfg.registerMapping("/stream", (req, resp) -> {
                    resp.setContentType("application/octet-stream");
                    try {
                        java.io.OutputStream os = resp.getOutputStream();
                        os.write(new byte[]{0x01, 0x02, 0x03, 0x04});
                    } catch (Exception e) {
                        resp.sendError(500, e.getMessage());
                    }
                });
            });

            HttpResponse<byte[]> resp1 = client().send(
                    HttpRequest.newBuilder(uri(server, "/bytes"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, resp1.statusCode(), "GET /bytes 状态码");
            assertEquals(5, resp1.body().length, "/bytes 响应体长度");
            assertEquals('H', (char) resp1.body()[0], "/bytes 第一个字节");
            assertEquals('o', (char) resp1.body()[4], "/bytes 最后一个字节");

            HttpResponse<byte[]> resp2 = client().send(
                    HttpRequest.newBuilder(uri(server, "/stream"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, resp2.statusCode(), "GET /stream 状态码");
            assertEquals(4, resp2.body().length, "/stream 响应体长度");
            assertEquals(1, resp2.body()[0], "/stream 第一个字节");
            assertEquals(4, resp2.body()[3], "/stream 最后一个字节");

            pass();
            return true;
        } catch (Exception e) {
            fail("Byte body 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestSseStreaming */
    private boolean testSseStreaming() {
        log.info("  [FUNC-18] SSE 流式推送（3 个事件）");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/sse", (req, resp) -> {
                resp.sse();
                resp.sseEvent("msg", "event-1");
                resp.sseEvent("msg", "event-2");
                resp.sseEvent("msg", "event-3");
                resp.sseClose();
            }));

            URL url = new URL("http://127.0.0.1:" + server.getPort() + "/sse");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            assertEquals(200, status, "SSE 状态码");

            String ct = conn.getHeaderField("Content-Type");
            assertTrue(ct != null && ct.contains("text/event-stream"), "SSE Content-Type: " + ct);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String sseBody = sb.toString();
            assertTrue(sseBody.contains("data: event-1"), "SSE 应包含 data: event-1");
            assertTrue(sseBody.contains("data: event-2"), "SSE 应包含 data: event-2");
            assertTrue(sseBody.contains("data: event-3"), "SSE 应包含 data: event-3");
            conn.disconnect();
            pass();
            return true;
        } catch (Exception e) {
            fail("SSE 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    // ==================== 性能 ====================

    /** TestNotFound */
    private boolean testNotFound404() {
        log.info("  [FUNC-19] 未注册路径返回 404");
        Server server = null;
        try {
            server = startServer(cfg -> cfg.registerMapping("/exists", (req, resp) -> resp.setResult("ok")));
            HttpResponse<String> resp = get(server, "/not-exist");
            assertEquals(404, resp.statusCode(), "404 状态码");
            pass();
            return true;
        } catch (Exception e) {
            fail("404 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestSslSelfSigned */
    private boolean testSslSelfSigned() {
        log.info("  [FUNC-20] HTTPS 自签名证书（selfSignedAuto）");
        Server server = null;
        try {
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.getSsl().setSelfSignedAuto(true);
            Server s = ServiceProvider.of(Server.class).getNewExtension(serverType, setting);
            server = s;
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> resp.setResult("ssl-ok"));
            server.start();
            int port = server.getPort();

            // 信任所有证书的 HTTPS 客户端
            javax.net.ssl.SSLContext trustAll = javax.net.ssl.SSLContext.getInstance("TLS");
            trustAll.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                /** 校验ClientTrusted */
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                /** 校验ServerTrusted */
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                /** 获取AcceptedIssuers */
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(trustAll)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/echo"))
                            .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "HTTPS 状态码");
            assertEquals("ssl-ok", resp.body(), "HTTPS 响应体");
            pass();
            return true;
        } catch (Exception e) {
            fail("HTTPS 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestConcurrencyLimit */
    private boolean testConcurrencyLimit() {
        log.info("  [FUNC-21] 并发限流（maxConcurrency=1 → 503）");
        Server server = null;
        try {
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setMaxConcurrency(1);
            Server s = ServiceProvider.of(Server.class).getNewExtension(serverType, setting);
            server = s;
            ((ConfigServer) server).registerMapping("/slow", (req, resp) -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                resp.setResult("done");
            });
            server.start();
            int port = server.getPort();

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            List<Integer> codes = Collections.synchronizedList(new ArrayList<>());
            Server srv = server;
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        HttpResponse<String> r = get(srv, "/slow");
                        codes.add(r.statusCode());
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await(15, TimeUnit.SECONDS);
            pool.shutdownNow();
            boolean saw503 = codes.stream().anyMatch(c -> c == 503);
            assertTrue(saw503, "应出现 503 限流响应，实际: " + codes);
            pass();
            return true;
        } catch (Exception e) {
            fail("并发限流异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** TestWebSocketUpgrade */
    private boolean testWebSocketUpgrade() {
        log.info("  [FUNC-22] WebSocket 升级（仅 nio 实现支持）");
        if (!"nio".equals(serverType)) {
            log.info("    跳过：当前类型 {} 不支持 WebSocket 升级", serverType);
            return true;
        }
        Server server = null;
        try {
            NioHttpServer nio = new NioHttpServer(ServerSetting.defaults());
            nio.getSetting().setHost("127.0.0.1");
            nio.getSetting().setPort(0);
            nio.onSubscribe("chat", (req, resp) -> resp.setResult("echo:" + req.getBodyString()));
            server = nio;
            nio.start();
            int port = nio.getPort();

            CompletableFuture<String> echoed = new CompletableFuture<>();
            java.net.http.WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"),
                            new java.net.http.WebSocket.Listener() {
                                public java.util.concurrent.CompletionStage<?> onText(
                                        java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                                    echoed.complete(data.toString());
                                    webSocket.request(1);
                                    return null;
                                }
                            })
                    .join();
            ws.sendText("chat\nhello", true);
            String reply = echoed.get(10, TimeUnit.SECONDS);
            assertEquals("echo:hello", reply, "WebSocket 回显");
            ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "bye");
            pass();
            return true;
        } catch (Exception e) {
            fail("WebSocket 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** 运行Perf */
    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReport.printEnvironment("HttpServer [" + serverType + "]", serverType, "SPI");
        Server server = null;
        try {
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);

            server = startServer(cfg -> cfg.registerMapping("/echo",
                    (req, resp) -> resp.setResult(body)));
            int port = server.getPort();

            PerfReport.printServerConfig(server.getSetting());
            PerfReport.ResourceMonitor monitor = PerfReport.ResourceMonitor.start();
            PerfReport.SweepRow row = runPerfInner(concurrency, connections, requestsPerConn, port);
            monitor.stop();
            if (row == null) {
                return false;
            }
            PerfReport.printResult("http-server [" + serverType + "] GET /echo", row.concurrency,
                    row.connections, row.requestsPerConn, payloadSize,
                    row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, 0L);
            monitor.printSummary("perf 并发=" + row.concurrency + " 最大并发数=" + server.getSetting().getMaxConcurrency());
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
        PerfReport.printEnvironment("HttpServer [" + serverType + "] [sweep]", serverType, "SPI");
        Server server = null;
        try {
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);

            server = startServer(cfg -> cfg.registerMapping("/echo",
                    (req, resp) -> resp.setResult(body)));
            int port = server.getPort();

            PerfReport.printServerConfig(server.getSetting());
            PerfReport.ResourceMonitor monitor = PerfReport.ResourceMonitor.start();
            List<PerfReport.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReport.SweepRow row = runPerfInner(cc, conn, req, port);
                if (row != null) {
                    rows.add(row);
                }
            }
            monitor.stop();
            PerfReport.printSweepResult("http-server [" + serverType + "] GET /echo 扫档", payloadSize, rows);
            monitor.printSummary("sweep 最大并发数=" + server.getSetting().getMaxConcurrency());
            return !rows.isEmpty();
        } catch (Exception e) {
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** 运行Bench */
    private boolean runBench(int payloadSize, String reportPath) {
        return runBench(payloadSize, reportPath, null);
    }

    /** 运行Bench */
    private boolean runBench(int payloadSize, String reportPath, String htmlPath) {
        Server server = null;
        try {
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);

            server = startServer(cfg -> cfg.registerMapping("/echo",
                    (req, resp) -> resp.setResult(body)));
            int port = server.getPort();

            PerfReport.printEnvironment("HttpServer [" + serverType + "] [bench]", serverType, "SPI");
            PerfReport.printServerConfig(server.getSetting());
            PerfReport.ResourceMonitor monitor = PerfReport.ResourceMonitor.start();
            List<PerfReport.SweepRow> rows = new ArrayList<>();
            for (int cc : BENCH_CONCURRENCY) {
                int conn = Math.min(BENCH_CONNECTIONS, Math.max(1, cc / 8));
                log.info("  ┌─ 压测场景: 并发 {} ─┐", cc);
                PerfReport.SweepRow row = runPerfInner(cc, conn, BENCH_REQUESTS_PER_CONN, port);
                if (row != null) {
                    rows.add(row);
                    PerfReport.printResult("http-server [" + serverType + "] GET /echo @并发" + cc,
                            row.concurrency, row.connections, row.requestsPerConn, payloadSize,
                            row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, 0L);
                }
            }
            monitor.stop();
            monitor.printSummary("bench 最大并发数=" + server.getSetting().getMaxConcurrency());
            if (rows.isEmpty()) {
                fail("BENCH 无有效结果");
                return false;
            }
            String env = String.format("**环境**: %s / JDK %s / CPU %d 核 / 内存 max=%dMB / 最大并发数=%d",
                    System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                    System.getProperty("java.version"),
                    Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().maxMemory() / 1024 / 1024,
                    server.getSetting().getMaxConcurrency());
            String title = "HTTP Server 压测报告 [" + serverType + "]";
            String report = PerfReport.writeBenchmarkReport(reportPath, title, env, rows);
            log.info("压测报告预览:\n{}", report);
            if (htmlPath != null) {
                PerfReport.writeHtmlReport(htmlPath, title, env, rows);
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("BENCH 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /**
     * 真·并发压测：连接数 = 并发数，每连接仅 1 次请求（flash 模式）。
     * <p>与 {@link #runBench} 的吞吐测试（固定少量连接 × 大量请求）互补：
     * 本模式直接考验服务器同时接纳 N 个连接的能力（backlog / accept / 限流）。</p>
     */
    private boolean runConcurrent(int payloadSize, String reportPath) {
        Server server = null;
        try {
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);

            server = startServer(cfg -> cfg.registerMapping("/echo",
                    (req, resp) -> resp.setResult(body)));
            int port = server.getPort();

            PerfReport.printEnvironment("HttpServer [" + serverType + "] [conc]", serverType, "SPI");
            PerfReport.printServerConfig(server.getSetting());
            PerfReport.ResourceMonitor monitor = PerfReport.ResourceMonitor.start();
            List<PerfReport.SweepRow> rows = new ArrayList<>();
            for (int cc : CONC_CONCURRENCY) {
                log.info("  ┌─ 真并发场景: 同时 {} 连接 × {} 请求 ─┐", cc, CONC_REQUESTS_PER_CONN);
                // 连接数 = 并发数，每连接 1 次请求
                PerfReport.SweepRow row = runPerfInner(cc, cc, CONC_REQUESTS_PER_CONN, port);
                if (row != null) {
                    rows.add(row);
                    PerfReport.printResult("http-server [" + serverType + "] GET /echo @同时" + cc + "连接",
                            row.concurrency, row.connections, row.requestsPerConn, payloadSize,
                            row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, 0L);
                }
            }
            monitor.stop();
            monitor.printSummary("conc 最大并发数=" + server.getSetting().getMaxConcurrency());
            if (rows.isEmpty()) {
                fail("CONC 无有效结果");
                return false;
            }
            String env = String.format("**环境**: %s / JDK %s / CPU %d 核 / 内存 max=%dMB / 最大并发数=%d",
                    System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                    System.getProperty("java.version"),
                    Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().maxMemory() / 1024 / 1024,
                    server.getSetting().getMaxConcurrency());
            String report = PerfReport.writeBenchmarkReport(
                    reportPath, "HTTP Server 真并发压测报告 [" + serverType + "]", env, rows);
            log.info("压测报告预览:\n{}", report);
            pass();
            return true;
        } catch (Exception e) {
            fail("CONC 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(server);
        }
    }

    /** 运行PerfInner */
    private PerfReport.SweepRow runPerfInner(int concurrency, int connections, int requestsPerConn, int port) {
        ExecutorService pool = null;
        try {
            HttpClient c = HttpClient.newBuilder()
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
                                    .timeout(Duration.ofSeconds(10)).GET().build();
                            long s = System.nanoTime();
                            HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
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

    @FunctionalInterface
    private interface ServerConfigurer {
        void configure(ConfigServer server);
    }

    /** 创建Server */
    private Server createServer() {
        return ServerBuilder.create().type(serverType).host("127.0.0.1").port(0).build();
    }

    /** 开始Server */
    private Server startServer(ServerConfigurer configurer) {
        Server server = createServer();
        configurer.configure((ConfigServer) server);
        server.start();
        return server;
    }

    /** Client */
    private HttpClient client() {
        return HttpClient.newHttpClient();
    }

    /** Uri */
    private URI uri(Server server, String path) {
        return URI.create("http://127.0.0.1:" + server.getPort() + path);
    }

    /** 获取 */
    private HttpResponse<String> get(Server server, String path) throws Exception {
        return client().send(
                HttpRequest.newBuilder(uri(server, path))
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Post */
    private HttpResponse<String> post(Server server, String path, String contentType, String body) throws Exception {
        return client().send(
                HttpRequest.newBuilder(uri(server, path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

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

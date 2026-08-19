package com.chua.example.nativehttp;

import com.chua.common.support.nativehttp.server.ShmHttpServer;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.handler.ServerHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * ShmHttpServer 综合示例 — 通过共享内存 + Rust hyper 提供 HTTP 服务。
 *
 * <p>本示例展示：
 * <ul>
 *   <li>如何创建一个 ShmHttpServer</li>
 *   <li>注册 GET/POST 路由（与普通 AbstractServer 子类一致）</li>
 *   <li>启动后用 curl 或浏览器访问</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认 8080 端口
 *   java ShmHttpServerExample
 *
 *   # 指定端口 + shm 名称
 *   java ShmHttpServerExample --port 9090 --shm /demo_rhb
 *
 *   # 自检模式（启动后并发发 1000 个 GET 请求验证）
 *   java ShmHttpServerExample --port 8080 --test
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ShmHttpServerExample {

    /**
     * 默认端口
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * 默认 shm 名
     */
    private static final String DEFAULT_SHM = "/demo_rhb";

    /**
     * 退出码：成功
     */
    private static final int EXIT_OK = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_FAIL = 1;

    /**
     * 自检请求数
     */
    private static final int SELF_TEST_REQUESTS = 1000;

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        if (parsed.help()) {
            printHelp();
            return;
        }
        int port = parsed.port() > 0 ? parsed.port() : DEFAULT_PORT;
        String shm = parsed.shm() != null ? parsed.shm() : DEFAULT_SHM;
        int rc;
        try {
            if (parsed.test()) {
                rc = runSelfTest(port, shm);
            } else {
                runServer(port, shm);
                rc = EXIT_OK;
            }
        } catch (Throwable t) {
            log.error("执行异常", t);
            rc = EXIT_FAIL;
        }
        System.exit(rc);
    }

    /**
     * 常驻模式
     */
    private static void runServer(int port, String shm) {
        ServerSetting setting = ServerSetting.defaults();
        setting.setPort(port);

        ShmHttpServer server = new ShmHttpServer.Builder()
                .port(port)
                .shmName(shm)
                .setting(setting)
                .build();

        registerRoutes(server);
        server.start();

        log.info("[ShmHttpServerExample] 服务就绪: http://localhost:{}", port);
        log.info("测试命令:");
        log.info("  curl 'http://localhost:{}/hello?name=chua'", port);
        log.info("  curl -X POST -d 'hello-shm' http://localhost:{}/echo", port);

        awaitShutdown();

        server.stop();
    }

    /**
     * 注册示例路由
     */
    private static void registerRoutes(ShmHttpServer server) {
        server.registerMapping("/hello", HttpMethod.GET, (ServerHandler) (req, resp) -> {
            String name = req.getParam("name");
            resp.setContentType("text/plain;charset=UTF-8");
            resp.setBody("hi, " + (name == null ? "world" : name));
            resp.end();
        });

        server.registerMapping("/echo", (ServerHandler) (req, resp) -> {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.setBody("echo: " + req.getBodyString());
            resp.end();
        });

        server.registerMapping("/json", HttpMethod.GET, (ServerHandler) (req, resp) -> {
            resp.setContentType("application/json;charset=UTF-8");
            resp.setBody("{\"message\":\"shm-http\",\"engine\":\"rust-hyper\"}");
            resp.end();
        });
    }

    /**
     * 自检模式
     */
    private static int runSelfTest(int port, String shm) {
        log.info("===== ShmHttpServer 自检 port={} shm={} =====", port, shm);
        ServerSetting setting = ServerSetting.defaults();
        setting.setPort(port);
        ShmHttpServer server = new ShmHttpServer.Builder()
                .port(port)
                .shmName(shm)
                .setting(setting)
                .build();
        registerRoutes(server);
        server.start();
        try {
            Thread.sleep(300); // 等 Rust hyper 起来

            boolean allPassed = true;
            allPassed &= testHello(port);
            allPassed &= testJson(port);
            allPassed &= testBurst(port);

            if (allPassed) {
                log.info("[PASS] 全部自检通过");
                return EXIT_OK;
            }
            log.error("[FAIL] 存在失败用例");
            return EXIT_FAIL;
        } catch (Exception e) {
            log.error("自检异常", e);
            return EXIT_FAIL;
        } finally {
            server.stop();
        }
    }

    private static boolean testHello(int port) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://127.0.0.1:" + port + "/hello?name=chua"))
                        .GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        boolean ok = resp.statusCode() == 200 && "hi, chua".equals(resp.body());
        log.info((ok ? "[PASS] " : "[FAIL] ") + "GET /hello?name=chua");
        return ok;
    }

    private static boolean testJson(int port) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://127.0.0.1:" + port + "/json"))
                        .GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        boolean ok = resp.statusCode() == 200
                && resp.body().contains("\"engine\":\"rust-hyper\"");
        log.info((ok ? "[PASS] " : "[FAIL] ") + "GET /json");
        return ok;
    }

    private static boolean testBurst(int port) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        long t0 = System.nanoTime();
        int okCount = 0;
        for (int i = 0; i < SELF_TEST_REQUESTS; i++) {
            java.net.http.HttpResponse<String> resp = client.send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:" + port + "/hello?name=t" + i))
                            .GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            if (resp.statusCode() == 200 && ("hi, t" + i).equals(resp.body())) {
                okCount++;
            }
        }
        double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;
        boolean ok = okCount == SELF_TEST_REQUESTS;
        log.info((ok ? "[PASS] " : "[FAIL] ") + "burst " + SELF_TEST_REQUESTS + " reqs in "
                + String.format("%.1f", elapsedMs) + " ms ("
                + String.format("%.0f", SELF_TEST_REQUESTS / (elapsedMs / 1000.0)) + " req/s, "
                + "ok=" + okCount + "/" + SELF_TEST_REQUESTS + ")");
        return ok;
    }

    private static void awaitShutdown() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printHelp() {
        System.out.println("ShmHttpServerExample - 基于共享内存 + Rust hyper 的 HTTP 示例");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  java ShmHttpServerExample [--port <n>] [--shm <name>] [--test]");
    }

    /**
     * 命令行参数容器
     *
     * @author CH
     * @since 4.0.0.42
     */
    private record Args(int port, String shm, boolean test, boolean help) {

        Args() {
            this(0, null, false, false);
        }

        static Args parse(String[] args) {
            Args r = new Args();
            int i = 0;
            while (i < args.length) {
                switch (args[i]) {
                    case "--port" -> {
                        if (i + 1 < args.length) {
                            r = r.withPort(Integer.parseInt(args[++i]));
                        }
                    }
                    case "--shm" -> {
                        if (i + 1 < args.length) {
                            r = r.withShm(args[++i]);
                        }
                    }
                    case "--test" -> r = r.withTest(true);
                    case "--help", "-h" -> r = r.withHelp(true);
                    default -> log.warn("未知参数: {}", args[i]);
                }
                i++;
            }
            return r;
        }

        Args withPort(int v) {
            return new Args(v, shm, test, help);
        }

        Args withShm(String v) {
            return new Args(port, v, test, help);
        }

        Args withTest(boolean v) {
            return new Args(port, shm, v, help);
        }

        Args withHelp(boolean v) {
            return new Args(port, shm, test, v);
        }
    }
}

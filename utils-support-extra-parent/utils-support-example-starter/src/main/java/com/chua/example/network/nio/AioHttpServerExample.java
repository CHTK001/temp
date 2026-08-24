package com.chua.example.network.nio;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.aio.AioHttpServer;
import com.chua.common.support.network.server.filter.ConnectionBudgetServerFilter;
import com.chua.common.support.network.server.filter.HealthCheckServerFilter;
import lombok.extern.slf4j.Slf4j;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AioHttpServer 冒烟示例：验证 IOCP(Proactor) 传输层的基础能力矩阵。
 *
 * <p>改写自 AioHttpServerTest，覆盖六个场景：</p>
 * <ol>
 *   <li>GET /hello 基础路由</li>
 *   <li>POST /echo body 回显</li>
 *   <li>未注册路由返回 404</li>
 *   <li>HealthCheckServerFilter 短路返回 /healthz</li>
 *   <li>ConnectionBudgetServerFilter(1) 单 IP 并发预算下串行请求放行</li>
 *   <li>Keep-Alive 同一连接连续三条请求（解析器复位 + 写循环恢复读）</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 *   java AioHttpServerExample                 # 默认端口 28081
 *   java AioHttpServerExample --port 28100    # 指定起始端口
 * </pre>
 *
 * <p>端口占用时自动 +10 重试，最多尝试 5 次。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AioHttpServerExample {

    /**
     * 默认监听端口
     */
    private static final int DEFAULT_PORT = 28081;

    /**
     * 绑定失败重试次数上限
     */
    private static final int MAX_PORT_ATTEMPTS = 5;

    /**
     * 端口重试步长
     */
    private static final int PORT_RETRY_STEP = 10;

    /**
     * HTTP 200 状态码
     */
    private static final int STATUS_OK = 200;

    /**
     * HTTP 404 状态码
     */
    private static final int STATUS_NOT_FOUND = 404;

    /**
     * Main 入口。
     *
     * @param args --port 起始端口（默认 28081）
     */
    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        boolean passed = true;
        AioHttpServer server = null;
        try {
            server = startServer(parsed.port());
            passed &= testGetHello(server);
            passed &= testPostEchoBody(server);
            passed &= testNotFound(server);
            server.addFilter(new HealthCheckServerFilter());
            passed &= testHealthCheckShortCircuit(server);
            server.addFilter(new ConnectionBudgetServerFilter(1));
            passed &= testConnectionBudgetSerialPass(server);
            passed &= testKeepAliveMultipleRequests(server);
        } catch (Exception e) {
            log.info("[FAIL] 示例执行异常: {}", e.getMessage());
            passed = false;
        } finally {
            if (server != null) {
                server.stop();
            }
        }
        if (!passed) {
            log.info("[FAIL] AioHttpServer 存在失败场景");
            System.exit(1);
        }
        log.info("[PASS] AioHttpServer 全部场景通过");
        System.exit(0);
    }

    /**
     * 场景一：GET /hello 返回 200 与固定文本。
     *
     * @param server 已启动的服务器
     * @return 通过返回 true
     */
    private static boolean testGetHello(AioHttpServer server) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> resp = client.send(requestOf(server, "/hello"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != STATUS_OK || !"Hello World".equals(resp.body())) {
                log.info("[FAIL] GET /hello 期望 200/Hello World，实际 {}/{}", resp.statusCode(), resp.body());
                return false;
            }
            log.info("[PASS] GET /hello 基础路由");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] GET /hello 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景二：POST /echo 回显请求体。
     *
     * @param server 已启动的服务器
     * @return 通过返回 true
     */
    private static boolean testPostEchoBody(AioHttpServer server) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(uriOf(server, "/echo"))
                    .POST(HttpRequest.BodyPublishers.ofString("payload-123")).build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != STATUS_OK || !resp.body().contains("payload-123")) {
                log.info("[FAIL] POST /echo 期望回显 payload-123，实际 {}/{}", resp.statusCode(), resp.body());
                return false;
            }
            log.info("[PASS] POST /echo body 回显");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] POST /echo 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景三：未注册路由应返回 404。
     *
     * @param server 已启动的服务器
     * @return 通过返回 true
     */
    private static boolean testNotFound(AioHttpServer server) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> resp = client.send(requestOf(server, "/no-such-route"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != STATUS_NOT_FOUND) {
                log.info("[FAIL] 未注册路由期望 404，实际 {}", resp.statusCode());
                return false;
            }
            log.info("[PASS] 未注册路由返回 404");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] 404 场景异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景四：HealthCheckServerFilter 应短路返回 /healthz=OK。
     *
     * @param server 已启动并挂载健康检查过滤器的服务器
     * @return 通过返回 true
     */
    private static boolean testHealthCheckShortCircuit(AioHttpServer server) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> resp = client.send(requestOf(server, "/healthz"),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != STATUS_OK || !"OK".equals(resp.body())) {
                log.info("[FAIL] /healthz 期望 200/OK，实际 {}/{}", resp.statusCode(), resp.body());
                return false;
            }
            log.info("[PASS] HealthCheck 过滤器短路返回");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] /healthz 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景五：并发预算为 1 时串行请求应全部放行。
     *
     * @param server 已启动并挂载预算过滤器的服务器
     * @return 通过返回 true
     */
    private static boolean testConnectionBudgetSerialPass(AioHttpServer server) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (int i = 0; i < 3; i++) {
                HttpResponse<String> resp = client.send(requestOf(server, "/hello"),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != STATUS_OK) {
                    log.info("[FAIL] 预算过滤器串行第 {} 次请求期望 200，实际 {}", i + 1, resp.statusCode());
                    return false;
                }
            }
            log.info("[PASS] ConnectionBudget(1) 串行放行");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] 预算过滤器场景异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景六：同一 Keep-Alive 连接连续三条请求均正确响应。
     *
     * @param server 已启动的服务器
     * @return 通过返回 true
     */
    private static boolean testKeepAliveMultipleRequests(AioHttpServer server) {
        try (HttpClient keepAliveClient = HttpClient.newBuilder().build()) {
            for (int i = 0; i < 3; i++) {
                HttpResponse<String> resp = keepAliveClient.send(requestOf(server, "/hello"),
                        HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != STATUS_OK || !"Hello World".equals(resp.body())) {
                    log.info("[FAIL] Keep-Alive 第 {} 次请求期望 200/Hello World，实际 {}/{}",
                            i + 1, resp.statusCode(), resp.body());
                    return false;
                }
            }
            log.info("[PASS] Keep-Alive 连接复用三次请求");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] Keep-Alive 场景异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 探测可用端口并启动服务器、注册测试路由。
     *
     * @param requestedPort 期望端口
     * @return 已启动的 AioHttpServer
     */
    private static AioHttpServer startServer(int requestedPort) {
        int port = resolveFreePort(requestedPort);
        if (port < 0) {
            throw new IllegalStateException("端口 " + requestedPort + " 起连续 "
                    + MAX_PORT_ATTEMPTS + " 次探测均失败");
        }
        ServerSetting setting = ServerSetting.defaults();
        setting.setPort(port);
        AioHttpServer server = new AioHttpServer(setting);
        server.registerMapping("/hello", (request, response) -> response.setResult("Hello World"));
        server.registerMapping("/echo", (request, response) -> response.setResult(request.getBodyString()));
        server.start();
        log.info("[BOOT] AioHttpServer 监听 127.0.0.1:{} (期望 {})", server.getPort(), port);
        return server;
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

    /** Uri */
    private static URI uriOf(AioHttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getPort() + path);
    }

    /** Get请求 */
    private static HttpRequest requestOf(AioHttpServer server, String path) {
        return HttpRequest.newBuilder(uriOf(server, path)).GET().build();
    }

    /**
     * 命令行参数容器。
     *
     * @param port 起始端口
     * @since 4.0.0.42
     */
    private record Args(int port) {

        /** 解析 */
        static Args parse(String[] args) {
            int port = DEFAULT_PORT;
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                }
            }
            return new Args(port);
        }
    }
}

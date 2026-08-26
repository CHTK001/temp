package com.chua.example.network.discovery;

import com.chua.common.support.network.discovery.DefaultServiceDiscovery;
import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.discovery.ServiceDiscoveryServerFilter;
import com.chua.common.support.network.server.filter.proxy.ReverseProxyServer;
import com.chua.common.support.network.server.http.ConfigServer;
import com.chua.common.support.network.server.impl.JdkTcpServer;
import com.chua.common.support.network.server.proxy.DiscoveryProxyTargetResolver;
import com.chua.common.support.network.server.proxy.TcpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ScatterDiscoveryExampleSpi} 的同名独立主示例（驱动型）。
 *
 * <p>真实驱动「注册 → scatterId 业务隔离 → 协议过滤 → HTTP/TCP 代理按分组路由」全链路：
 * 向 /api 注册 4 个节点（order-http×2、user-http×1、order-tcp×1），
 * 断言 order 组只在同组同协议节点间路由。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ... ScatterDiscoveryExample
 *   java ... ScatterDiscoveryExample --mode=all|isolation|http|tcp
 *   java ... ScatterDiscoveryExample --port-http-a=28081 --port-http-b=28082 --port-user=28083 --port-tcp=28091
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ScatterDiscoveryExample {

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(ScatterDiscoveryExample.class);

    /** order 组 HTTP 节点 A 默认端口 */
    private static final int DEFAULT_PORT_HTTP_A = 28081;
    /** order 组 HTTP 节点 B 默认端口 */
    private static final int DEFAULT_PORT_HTTP_B = 28082;
    /** user 组 HTTP 节点默认端口（仅注册，不存活，验证不会被路由到） */
    private static final int DEFAULT_PORT_USER = 28083;
    /** order 组 TCP 节点默认端口 */
    private static final int DEFAULT_PORT_TCP = 28091;

    /**
     * 独立入口：解析参数后按模式执行各场景，通过打印 [PASS]，失败打印 [FAIL] 并以退出码 1 结束。
     *
     * @param args 命令行参数：--mode=all|isolation|http|tcp、
     *             --port-http-a/--port-http-b/--port-user/--port-tcp（默认 28xxx 可调）
     * @throws Exception 场景执行异常
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> arg = parseArgs(args);
        int portHttpA = intVal(arg.get("port-http-a"), DEFAULT_PORT_HTTP_A);
        int portHttpB = intVal(arg.get("port-http-b"), DEFAULT_PORT_HTTP_B);
        int portUser = intVal(arg.get("port-user"), DEFAULT_PORT_USER);
        int portTcp = intVal(arg.get("port-tcp"), DEFAULT_PORT_TCP);
        String mode = arg.getOrDefault("mode", "all");
        LOG.info("===== scatter-discovery 主示例 [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "isolation".equals(mode)) {
            passed &= testRegistryRouting(portHttpA, portHttpB, portUser, portTcp);
        }
        if ("all".equals(mode) || "http".equals(mode)) {
            passed &= testHttpProxyRoute(portHttpA, portHttpB, portUser);
        }
        if ("all".equals(mode) || "tcp".equals(mode)) {
            passed &= testTcpProxyRoute(portTcp);
        }
        log.info(passed ? "[PASS] scatter-discovery" : "[FAIL] scatter-discovery");
        if (!passed) {
            System.exit(1);
        }
    }

    /**
     * SCATTER-01/02：注册中心路由 —— scatterId 业务隔离 + 协议过滤 + 未知分组返回 null。
     *
     * @param portHttpA order/http 节点 A 端口
     * @param portHttpB order/http 节点 B 端口
     * @param portUser  user/http 节点端口
     * @param portTcp   order/tcp 节点端口
     * @return 全部断言通过返回 true
     */
    private static boolean testRegistryRouting(int portHttpA, int portHttpB, int portUser, int portTcp) {
        LOG.info("  [SCATTER-01/02] scatterId 业务隔离 + 协议过滤");
        ServiceDiscovery sd = null;
        try {
            sd = buildRegistry(portHttpA, portHttpB, portUser, portTcp);
            for (int i = 0; i < 20; i++) {
                Discovery d = sd.getService("/api", "order", "weight", "http");
                assertTrue(d != null, "order/http 组应能查到节点");
                assertEquals("order", d.getScatterId(), "应只路由 order 分组节点");
                assertEquals("http", d.getProtocol(), "应只路由 http 协议节点");
                assertTrue(d.getPort() == portHttpA || d.getPort() == portHttpB,
                        "order/http 不应路由到其他节点，实际: " + d.getPort());
            }
            Discovery tcpNode = sd.getService("/api", "order", "weight", "tcp");
            assertTrue(tcpNode != null && tcpNode.getPort() == portTcp,
                    "order/tcp 应路由到端口 " + portTcp);
            assertTrue(sd.getService("/api", "not-exist", "weight", "http") == null,
                    "不存在的 scatterId 应返回 null");
            LOG.info("    order/http 20 次路由均落在 {}/{}，tcp → {}，未知分组 → null ✓",
                    portHttpA, portHttpB, portTcp);
            return true;
        } catch (Exception e) {
            return fail("注册中心路由异常: " + e.getMessage());
        } finally {
            closeQuietly(sd);
        }
    }

    /**
     * 构建标准注册场景：/api 下 4 个节点（不同 scatterId/协议），等待注册生效。
     *
     * @param portHttpA order/http 节点 A 端口
     * @param portHttpB order/http 节点 B 端口
     * @param portUser  user/http 节点端口
     * @param portTcp   order/tcp 节点端口
     * @return 已启动的服务发现实例
     * @throws Exception 注册或等待中断异常
     */
    private static ServiceDiscovery buildRegistry(int portHttpA, int portHttpB, int portUser, int portTcp)
            throws Exception {
        ServiceDiscovery sd = new DefaultServiceDiscovery();
        sd.start();
        sd.registerService("/api", Discovery.builder().serverId("order-1").scatterId("order")
                .protocol("http").host("127.0.0.1").port(portHttpA).weight(1).build());
        sd.registerService("/api", Discovery.builder().serverId("order-2").scatterId("order")
                .protocol("http").host("127.0.0.1").port(portHttpB).weight(1).build());
        sd.registerService("/api", Discovery.builder().serverId("user-1").scatterId("user")
                .protocol("http").host("127.0.0.1").port(portUser).weight(1).build());
        sd.registerService("/api", Discovery.builder().serverId("order-3").scatterId("order")
                .protocol("tcp").host("127.0.0.1").port(portTcp).weight(1).build());
        Thread.sleep(100);
        return sd;
    }

    /**
     * SCATTER-03：HTTP 反向代理按 scatterId=order + protocol=http 过滤路由。
     *
     * @param portHttpA order 组后端 A 端口
     * @param portHttpB order 组后端 B 端口
     * @param portUser  user 组死节点端口（不应被命中）
     * @return 全部断言通过返回 true
     */
    private static boolean testHttpProxyRoute(int portHttpA, int portHttpB, int portUser) {
        LOG.info("  [SCATTER-03] HTTP 代理按 scatterId 路由");
        Server backendA = null;
        Server backendB = null;
        ReverseProxyServer proxy = null;
        ServiceDiscovery sd = null;
        try {
            backendA = echoHttpServer(portHttpA, "order-A");
            backendB = echoHttpServer(portHttpB, "order-B");
            sd = new DefaultServiceDiscovery();
            sd.start();
            sd.registerService("/api", Discovery.builder().serverId("order-1").scatterId("order")
                    .protocol("http").host("127.0.0.1").port(portHttpA).weight(1).build());
            sd.registerService("/api", Discovery.builder().serverId("order-2").scatterId("order")
                    .protocol("http").host("127.0.0.1").port(portHttpB).weight(1).build());
            sd.registerService("/api", Discovery.builder().serverId("user-1").scatterId("user")
                    .protocol("http").host("127.0.0.1").port(portUser).weight(1).build());
            Thread.sleep(100);

            proxy = new ReverseProxyServer(0)
                    .discovery(sd)
                    .route("/api/**", "/api")
                    .scatterId("order")
                    .protocol("http")
                    .balance("weight")
                    .start();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            for (int i = 0; i < 10; i++) {
                HttpResponse<String> resp = client.send(HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + proxy.getPort() + "/api/echo"))
                                .timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                assertTrue("order-A".equals(body) || "order-B".equals(body),
                        "HTTP 代理应只路由 order 组，实际: " + body);
            }
            LOG.info("    HTTP 代理 10 次请求全部路由到 order 组（order-A/order-B）✓");
            return true;
        } catch (Exception e) {
            return fail("HTTP 代理路由异常: " + e.getMessage());
        } finally {
            closeQuietly(proxy);
            closeQuietly(backendA);
            closeQuietly(backendB);
            closeQuietly(sd);
        }
    }

    /**
     * 启动绑定固定端口的 jdk-http 回显后端：/api/echo → body。
     *
     * @param port 监听端口
     * @param body 回显内容
     * @return 已启动的后端
     * @throws Exception 启动异常
     */
    private static Server echoHttpServer(int port, String body) throws Exception {
        Server server = ServerBuilder.create().type("jdk-http").host("127.0.0.1").port(port).build();
        ((ConfigServer) server).registerMapping("/api/echo",
                (req, resp) -> resp.setBody(body.getBytes(StandardCharsets.UTF_8)));
        server.start();
        return server;
    }

    /**
     * SCATTER-04：TCP 代理按 scatterId=order + tcp 协议解析目标并双向桥接。
     *
     * @param portTcp order 组 TCP 后端端口
     * @return 全部断言通过返回 true
     */
    private static boolean testTcpProxyRoute(int portTcp) {
        LOG.info("  [SCATTER-04] TCP 代理按 scatterId + 协议路由");
        JdkTcpServer backend = null;
        TcpProxyServer proxy = null;
        ServiceDiscovery sd = null;
        try {
            backend = (JdkTcpServer) ServerBuilder.create()
                    .type("jdk-tcp").host("127.0.0.1").port(portTcp).build();
            backend.start();
            sd = new DefaultServiceDiscovery();
            sd.start();
            sd.registerService("/api", Discovery.builder().serverId("order-3").scatterId("order")
                    .protocol("tcp").host("127.0.0.1").port(backend.getPort()).weight(1).build());
            Thread.sleep(100);

            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            proxy = new TcpProxyServer(setting,
                    new DiscoveryProxyTargetResolver(sd, "/api", "order", "weight"));
            proxy.start();

            assertEquals("scatter-tcp\n", tcpRoundTrip("127.0.0.1", proxy.getPort(), "scatter-tcp\n"),
                    "TCP 代理回显应与发送一致");
            LOG.info("    TCP 代理按 order+tcp 路由到后端 {}，回显一致 ✓", portTcp);
            return true;
        } catch (Exception e) {
            return fail("TCP 代理路由异常: " + e.getMessage());
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
            closeQuietly(sd);
        }
    }

    /**
     * TCP 回显往返：发送消息并读取等长响应。
     *
     * @param host 目标主机
     * @param port 目标端口
     * @param msg  发送消息
     * @return 回显内容
     * @throws Exception 连接/读写异常
     */
    private static String tcpRoundTrip(String host, int port, String msg) throws Exception {
        byte[] payload = msg.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            byte[] buf = new byte[payload.length];
            int read = 0;
            while (read < buf.length) {
                int n = socket.getInputStream().read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    /**
     * 断言相等（统一装箱比较）。
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param msg      失败描述
     */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " 期望=" + expected + " 实际=" + actual);
        }
    }

    /**
     * 断言为真。
     *
     * @param cond 条件
     * @param msg  失败描述
     */
    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    /**
     * 失败收口：打印错误日志并返回 false。
     *
     * @param msg 失败描述
     * @return 恒为 false
     */
    private static boolean fail(String msg) {
        LOG.error("    ✗ 失败: {}", msg);
        return false;
    }

    /**
     * 静默关闭可关闭资源。
     *
     * @param c 可关闭资源，可为 null
     */
    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }
    }

    /**
     * 解析整型参数，缺失或非法时返回默认值。
     *
     * @param value  字符串值
     * @param defVal 默认值
     * @return 整数值
     */
    private static int intVal(String value, int defVal) {
        if (value == null || value.isEmpty()) {
            return defVal;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    /**
     * 解析命令行参数：支持 {@code --key=value} 与 {@code --key value}。
     *
     * @param args 原始参数
     * @return 键值对
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            int eq = arg.indexOf('=');
            if (eq > 0) {
                result.put(arg.substring(2, eq), arg.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                result.put(arg.substring(2), args[++i]);
            }
        }
        return result;
    }
}

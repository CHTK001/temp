package com.chua.example.network.discovery;

import com.chua.common.support.network.discovery.DefaultServiceDiscovery;
import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.discovery.ServiceDiscoveryServerFilter;
import com.chua.common.support.network.server.filter.proxy.ReverseProxyServerFilter;
import com.chua.common.support.network.server.proxy.DiscoveryProxyTargetResolver;
import com.chua.common.support.network.server.proxy.TcpProxyServer;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Scatter 服务发现示例:注册(带 scatterId/协议)→ scatterId 业务隔离 →
 * 协议过滤 → HTTP/TCP 代理按分组路由。
 *
 * <p>运行方式:{@code --example=scatter-discovery [mode=all|isolation|http|tcp]}</p>
 *
 * <pre>
 * 场景:
 *   注册 /api 下 4 个节点:order-http×2、user-http×1、order-tcp×1
 *   HTTP 请求 order 组 → 只在 order-http 节点间负载均衡,不会路由到 user 节点
 *   TCP 连接 order 组 → 只解析到 order-tcp 节点
 * </pre>
 *
 * @author CH
 * @since 2026/08/16
 */
@Slf4j
public class ScatterDiscoveryExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "scatter-discovery";
    }

    @Override
    /** Module */
    public String module() {
        return "scatter-discovery";
    }

    @Override
    /** Description */
    public String description() {
        return "Scatter 服务发现:注册 + scatterId 业务隔离 + 协议过滤 + HTTP/TCP 代理路由";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== scatter-discovery [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "isolation".equals(mode)) {
            passed &= testScatterIdIsolation();
            passed &= testProtocolFilter();
        }
        if ("all".equals(mode) || "http".equals(mode)) {
            passed &= testHttpProxyRoute();
        }
        if ("all".equals(mode) || "tcp".equals(mode)) {
            passed &= testTcpProxyRoute();
        }
        return passed;
    }

    // ==================== 注册场景 ====================

    /**
     * 构建标准注册场景:/api 下 4 个节点(不同 scatterId/协议)。
     */
    private static ServiceDiscovery buildDiscovery() throws Exception {
        ServiceDiscovery sd = new DefaultServiceDiscovery();
        sd.start();
        sd.registerService("/api", Discovery.builder()
                .serverId("order-1").scatterId("order").protocol("http")
                .host("127.0.0.1").port(18081).weight(1).build());
        sd.registerService("/api", Discovery.builder()
                .serverId("order-2").scatterId("order").protocol("http")
                .host("127.0.0.1").port(18082).weight(1).build());
        sd.registerService("/api", Discovery.builder()
                .serverId("user-1").scatterId("user").protocol("http")
                .host("127.0.0.1").port(18083).weight(1).build());
        sd.registerService("/api", Discovery.builder()
                .serverId("order-3").scatterId("order").protocol("tcp")
                .host("127.0.0.1").port(19091).weight(1).build());
        // 等待注册生效
        Thread.sleep(100);
        return sd;
    }

    // ==================== 功能 ====================

    /** TestScatterIdIsolation */
    private boolean testScatterIdIsolation() {
        log.info("  [SCATTER-01] scatterId 业务隔离");
        ServiceDiscovery sd = null;
        try {
            sd = buildDiscovery();
            // order/http 组:20 次路由,应全部落在 order-http 节点(18081/18082)
            for (int i = 0; i < 20; i++) {
                Discovery d = sd.getService("/api", "order", "weight", "http");
                assertTrue(d != null, "order/http 组应能查到节点");
                assertEquals("order", d.getScatterId(), "应只路由 order 分组节点");
                assertEquals("http", d.getProtocol(), "应只路由 http 协议节点");
                assertTrue(d.getPort() == 18081 || d.getPort() == 18082,
                        "order/http 不应路由到其他节点,实际: " + d.getPort());
            }
            log.info("    order/http 组 20 次路由全部落在 18081/18082 ✓");
            pass();
            return true;
        } catch (Exception e) {
            fail("scatterId 隔离异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(sd);
        }
    }

    /** TestProtocol过滤 */
    private boolean testProtocolFilter() {
        log.info("  [SCATTER-02] 协议过滤(order + tcp)");
        ServiceDiscovery sd = null;
        try {
            sd = buildDiscovery();
            Discovery d = sd.getService("/api", "order", "weight", "tcp");
            assertTrue(d != null, "order/tcp 组应能查到节点");
            assertEquals("tcp", d.getProtocol(), "协议应为 tcp");
            assertEquals(19091, d.getPort(), "order/tcp 应路由到 19091");
            // 不存在的分组 → null
            Discovery none = sd.getService("/api", "not-exist", "weight", "http");
            assertTrue(none == null, "不存在的 scatterId 应返回 null");
            log.info("    order/tcp → 19091,不存在分组 → null ✓");
            pass();
            return true;
        } catch (Exception e) {
            fail("协议过滤异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(sd);
        }
    }

    /** TestHttpProxyRoute */
    private boolean testHttpProxyRoute() {
        log.info("  [SCATTER-03] HTTP 代理按 scatterId 路由");
        ServiceDiscovery sd = null;
        Server backendA = null;
        Server backendB = null;
        Server proxy = null;
        try {
            // 真实后端:order 组两个 http 节点(setBody byte[] 与反向代理已验证路径一致;
            // 后端注册 /api/echo 与代理转发路径一致,避免路径前缀未剥离导致 404)
            backendA = ServerBuilder.create().type("jdk-http").host("127.0.0.1").port(0).build();
            com.chua.common.support.network.server.http.ConfigServer ca = (com.chua.common.support.network.server.http.ConfigServer) backendA;
            ca.registerMapping("/api/echo", (req, resp) -> resp.setBody("order-A".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            backendA.start();
            backendB = ServerBuilder.create().type("jdk-http").host("127.0.0.1").port(0).build();
            com.chua.common.support.network.server.http.ConfigServer cb = (com.chua.common.support.network.server.http.ConfigServer) backendB;
            cb.registerMapping("/api/echo", (req, resp) -> resp.setBody("order-B".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            backendB.start();

            sd = new DefaultServiceDiscovery();
            sd.start();
            sd.registerService("/api", Discovery.builder().serverId("order-1").scatterId("order").protocol("http")
                    .host("127.0.0.1").port(backendA.getPort()).weight(1).build());
            sd.registerService("/api", Discovery.builder().serverId("order-2").scatterId("order").protocol("http")
                    .host("127.0.0.1").port(backendB.getPort()).weight(1).build());
            sd.registerService("/api", Discovery.builder().serverId("user-1").scatterId("user").protocol("http")
                    .host("127.0.0.1").port(18083).weight(1).build());
            Thread.sleep(100);

            // 代理:只路由 order 组(ServerBuilder 链式,内部维护 ServerSetting)
            proxy = ServerBuilder.create().type("jdk-http").host("127.0.0.1").port(0).build();
            ServiceDiscoveryServerFilter df = new ServiceDiscoveryServerFilter(sd);
            df.addRoute("/api/**", "/api");
            df.setScatterId("order");
            df.setProtocol("http");
            df.setBalance("weight");
            proxy.addFilter(df);
            proxy.addFilter(new ReverseProxyServerFilter(30));
            proxy.start();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            int orderHit = 0;
            for (int i = 0; i < 10; i++) {
                HttpResponse<String> resp = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxy.getPort() + "/api/echo"))
                                .timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                assertTrue("order-A".equals(body) || "order-B".equals(body),
                        "HTTP 代理应只路由到 order 组,实际: " + body);
                if ("order-A".equals(body) || "order-B".equals(body)) {
                    orderHit++;
                }
            }
            assertEquals(10, orderHit, "10 次请求应全部命中 order 组");
            log.info("    HTTP 代理 10 次请求全部路由到 order 组(order-A/order-B)✓");
            pass();
            return true;
        } catch (Exception e) {
            fail("HTTP 代理路由异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backendA);
            closeQuietly(backendB);
            closeQuietly(sd);
        }
    }

    /** TestTcpProxyRoute */
    private boolean testTcpProxyRoute() {
        log.info("  [SCATTER-04] TCP 代理按 scatterId + 协议路由");
        ServiceDiscovery sd = null;
        com.chua.common.support.network.server.impl.JdkTcpServer backend = null;
        TcpProxyServer proxy = null;
        try {
            // 真实后端:order-tcp 回显节点
            backend = (com.chua.common.support.network.server.impl.JdkTcpServer) ServerBuilder.create()
                    .type("jdk-tcp").host("127.0.0.1").port(0).build();
            backend.start();

            sd = new DefaultServiceDiscovery();
            sd.start();
            sd.registerService("/api", Discovery.builder().serverId("order-3").scatterId("order").protocol("tcp")
                    .host("127.0.0.1").port(backend.getPort()).weight(1).build());
            Thread.sleep(100);

            // TCP 代理:按 order + tcp 解析目标
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            proxy = new TcpProxyServer(setting,
                    new DiscoveryProxyTargetResolver(sd, "/api", "order", "weight"));
            proxy.start();

            String echoed = tcpRoundTrip("127.0.0.1", proxy.getPort(), "scatter-tcp\n");
            assertEquals("scatter-tcp\n", echoed, "TCP 代理回显应与发送一致");
            log.info("    TCP 代理按 order+tcp 路由到后端,回显一致 ✓");
            pass();
            return true;
        } catch (Exception e) {
            fail("TCP 代理路由异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
            closeQuietly(sd);
        }
    }

    // ==================== 工具 ====================

    /** TcpRoundTrip */
    private static String tcpRoundTrip(String host, int port, String msg) throws Exception {
        try (java.net.Socket socket = new java.net.Socket(host, port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            byte[] buf = new byte[msg.getBytes(java.nio.charset.StandardCharsets.UTF_8).length];
            int read = 0;
            while (read < buf.length) {
                int n = socket.getInputStream().read(buf, read, buf.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Assert判断相等 */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " 期望=" + expected + " 实际=" + actual);
        }
    }

    /** Assert判断相等 */
    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " 期望=" + expected + " 实际=" + actual);
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
        log.info("    ✓ 通过");
    }

    /** Fail */
    private static void fail(String msg) {
        log.error("    ✗ 失败: {}", msg);
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

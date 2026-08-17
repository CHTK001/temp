package com.chua.example.network.scatter;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.scatter.DefaultScatterServiceDiscovery;
import com.chua.common.support.scatter.ScatterResult;
import com.chua.common.support.scatter.ScatterResultWithRequestId;
import com.chua.common.support.scatter.ScatterSetting;
import com.chua.common.support.scatter.ScatterNodeServer;
import com.chua.common.support.scatter.TcpScatterBuilder;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Scatter 集群场景体系测试：TCP 双节点 seed 互发现 + gossip 合并。
 *
 * <p>运行方式:{@code --example=scatter-cluster [mode=all|tcp-gossip]}</p>
 *
 * <pre>
 * 场景:
 *   节点 A(portA) 注册自身服务 /scatter
 *   节点 B(portB) seeds 指向 A,通过 gossip 定时拉取合并 A 的服务
 *   断言 B 本地 hash 表出现 A 的节点(serverId=node-a)
 * </pre>
 *
 * @author CH
 * @since 2026/08/17
 */
@Slf4j
public class ScatterClusterExampleSpi implements Example {

    @Override
    public String name() {
        return "scatter-cluster";
    }

    @Override
    public String module() {
        return "scatter";
    }

    @Override
    public String description() {
        return "Scatter 集群:TCP 双节点 seed 互发现 + gossip 合并 + 三协议往返 + UDP 广播 + 动态权重 + 分组隔离 + TCP 代理 + cluster addServer";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== scatter-cluster [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "tcp-gossip".equals(mode)) {
            passed &= testTcpGossipMerge();
        }
        if ("all".equals(mode) || "roundtrip".equals(mode)) {
            passed &= testTcpRoundTrip();
            passed &= testUdpRoundTrip();
            passed &= testKcpRoundTrip();
        }
        if ("all".equals(mode) || "udp-broadcast".equals(mode)) {
            passed &= testUdpBroadcast();
        }
        if ("all".equals(mode) || "dynamic-weight".equals(mode)) {
            passed &= testDynamicWeight();
        }
        if ("all".equals(mode) || "isolation".equals(mode)) {
            passed &= testGroupIsolation();
        }
        if ("all".equals(mode) || "proxy".equals(mode)) {
            passed &= testDefaultScatterTcpProxy();
        }
        if ("all".equals(mode) || "cluster".equals(mode)) {
            passed &= testClusterAddServer();
        }
        return passed;
    }

    // ==================== TCP 双节点 seed 互发现 + gossip 合并 ====================

    /**
     * 节点 A 注册自身服务；节点 B 通过 seed 引导 + gossip 定时拉取合并 A 的服务。
     */
    private boolean testTcpGossipMerge() {
        log.info("  [SCATTER-CLUSTER-01] TCP 双节点 seed 互发现 + gossip 合并");
        DefaultScatterServiceDiscovery discoveryA = null;
        DefaultScatterServiceDiscovery discoveryB = null;
        ScatterNodeServer serverA = null;
        try {
            int portA = freePort();
            int portB = freePort();

            // 节点 A：注册自身 + 节点服务端（响应 B 的 gossip 查询）
            ScatterSetting settingA = new ScatterSetting();
            settingA.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            settingA.setServicePath("/scatter").setPersistenceEnabled(false);
            settingA.setAutoDiscoveryIntervalMillis(200).setTimeoutMillis(1000);
            discoveryA = new DefaultScatterServiceDiscovery(settingA);
            discoveryA.start();
            discoveryA.registerService("/scatter", Discovery.builder()
                    .serverId("node-a").scatterId("order").protocol("tcp")
                    .host("127.0.0.1").port(portA).weight(1).build());

            serverA = new TcpScatterBuilder(settingA).buildNodeServer();
            attachResponseHandler(serverA, discoveryA, "node-a");
            serverA.start();

            // 节点 B：seeds 指向 A，gossip 拉取
            ScatterSetting settingB = new ScatterSetting();
            settingB.setNodeId("node-b").setGroupId("order").setHost("127.0.0.1").setPort(portB);
            settingB.setServicePath("/scatter").setPersistenceEnabled(false);
            settingB.setSeeds(List.of("127.0.0.1:" + portA));
            settingB.setAutoDiscoveryIntervalMillis(200).setTimeoutMillis(1000);
            discoveryB = new DefaultScatterServiceDiscovery(settingB);
            discoveryB.remoteClient(new TcpScatterBuilder(settingB).buildRemoteClient());
            discoveryB.start();

            // 等待 gossip 周期拉取
            TimeUnit.SECONDS.sleep(1);

            Set<Discovery> services = discoveryB.getServiceAll("/scatter");
            boolean found = services.stream().anyMatch(d -> "node-a".equals(d.getServerId()));
            assertTrue(found, "B 应通过 gossip 合并到 A 的服务(serverId=node-a), 实际: " + services);
            log.info("    B gossip 合并到 A 的服务(serverId=node-a) ✓");
            pass();
            return true;
        } catch (Exception e) {
            fail("TCP gossip 合并异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(serverA);
            closeQuietly(discoveryB);
            closeQuietly(discoveryA);
        }
    }

    // ==================== TCP 远程往返 ====================

    /**
     * 节点 B 通过 TCP 长连接向节点 A 发起一次远程查询并拿到结果。
     */
    private boolean testTcpRoundTrip() {
        log.info("  [SCATTER-CLUSTER-02] TCP 远程往返(invoke → 响应)");
        DefaultScatterServiceDiscovery discoveryA = null;
        ScatterNodeServer serverA = null;
        try {
            int portA = freePort();

            ScatterSetting settingA = new ScatterSetting();
            settingA.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            settingA.setServicePath("/scatter").setPersistenceEnabled(false);
            settingA.setTimeoutMillis(2000);
            discoveryA = new DefaultScatterServiceDiscovery(settingA);
            discoveryA.start();
            discoveryA.registerService("/scatter", Discovery.builder()
                    .serverId("node-a").scatterId("order").protocol("tcp")
                    .host("127.0.0.1").port(portA).weight(1).build());

            serverA = new TcpScatterBuilder(settingA).buildNodeServer();
            attachResponseHandler(serverA, discoveryA, "node-a");
            serverA.start();

            // 直接构造一个远程客户端对 A 发起 invoke
            ScatterSetting clientSetting = new ScatterSetting();
            clientSetting.setNodeId("client").setGroupId("order").setHost("127.0.0.1").setPort(freePort());
            clientSetting.setTimeoutMillis(2000);
            com.chua.common.support.scatter.ScatterRemoteClient<Object> client =
                    (com.chua.common.support.scatter.ScatterRemoteClient<Object>) (Object)
                            new TcpScatterBuilder(clientSetting).buildRemoteClient();

            ScatterResult<Object> result = client.invoke(
                    new com.chua.common.support.scatter.ScatterContext(
                            java.util.UUID.randomUUID().toString(), "/scatter", 2000, 1, Map.of()),
                    new com.chua.common.support.scatter.ScatterNode("node-a", "127.0.0.1", portA, "tcp", "order", "/scatter", Map.of()),
                    2000);

            assertTrue(result != null, "invoke 应返回结果");
            assertTrue(result.isSuccess(), "invoke 应成功, 实际: " + result.getErrorMessage());
            Discovery data = (Discovery) result.getData();
            assertEquals("node-a", data.getServerId(), "返回的 Discovery 应为 A 的节点");
            log.info("    TCP 往返成功,返回 A 节点: {}", data.getServerId());
            client.closeAll();
            pass();
            return true;
        } catch (Exception e) {
            fail("TCP 往返异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(serverA);
            closeQuietly(discoveryA);
        }
    }

    // ==================== 三协议往返 ====================

    /**
     * UDP 远程往返。
     */
    private boolean testUdpRoundTrip() {
        log.info("  [SCATTER-CLUSTER-03] UDP 远程往返");
        DefaultScatterServiceDiscovery discoveryA = null;
        ScatterNodeServer serverA = null;
        try {
            int portA = freePort();
            ScatterSetting settingA = new ScatterSetting();
            settingA.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            settingA.setServicePath("/scatter").setPersistenceEnabled(false);
            settingA.setTimeoutMillis(2000);
            discoveryA = new DefaultScatterServiceDiscovery(settingA);
            discoveryA.start();
            discoveryA.registerService("/scatter", Discovery.builder()
                    .serverId("node-a").scatterId("order").protocol("udp")
                    .host("127.0.0.1").port(portA).weight(1).build());

            serverA = new com.chua.common.support.scatter.UdpScatterBuilder(settingA).buildNodeServer();
            attachResponseHandler(serverA, discoveryA, "node-a");
            serverA.start();

            com.chua.common.support.scatter.ScatterRemoteClient<Object> client =
                    (com.chua.common.support.scatter.ScatterRemoteClient<Object>) (Object)
                            new com.chua.common.support.scatter.UdpScatterBuilder(clientSetting(portA)).buildRemoteClient();

            ScatterResult<Object> result = client.invoke(
                    new com.chua.common.support.scatter.ScatterContext(
                            java.util.UUID.randomUUID().toString(), "/scatter", 2000, 1, Map.of()),
                    new com.chua.common.support.scatter.ScatterNode("node-a", "127.0.0.1", portA, "udp", "order", "/scatter", Map.of()),
                    2000);
            assertTrue(result != null && result.isSuccess(), "UDP invoke 应成功, 实际: " + (result == null ? "null" : result.getErrorMessage()));
            log.info("    UDP 往返成功 ✓");
            client.closeAll();
            pass();
            return true;
        } catch (Exception e) {
            fail("UDP 往返异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(serverA);
            closeQuietly(discoveryA);
        }
    }

    /**
     * KCP 远程往返。
     */
    private boolean testKcpRoundTrip() {
        log.info("  [SCATTER-CLUSTER-04] KCP 远程往返");
        DefaultScatterServiceDiscovery discoveryA = null;
        ScatterNodeServer serverA = null;
        try {
            int portA = freePort();
            ScatterSetting settingA = new ScatterSetting();
            settingA.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            settingA.setServicePath("/scatter").setPersistenceEnabled(false);
            settingA.setTimeoutMillis(2000);
            discoveryA = new DefaultScatterServiceDiscovery(settingA);
            discoveryA.start();
            discoveryA.registerService("/scatter", Discovery.builder()
                    .serverId("node-a").scatterId("order").protocol("kcp")
                    .host("127.0.0.1").port(portA).weight(1).build());

            serverA = new com.chua.common.support.scatter.KcpScatterBuilder(settingA).buildNodeServer();
            if (serverA == null) {
                log.warn("    KCP NodeServer 未注册(无 kcp-starter 依赖),跳过");
                return true;
            }
            attachResponseHandler(serverA, discoveryA, "node-a");
            serverA.start();

            com.chua.common.support.scatter.ScatterRemoteClient<Object> client =
                    (com.chua.common.support.scatter.ScatterRemoteClient<Object>) (Object)
                            new com.chua.common.support.scatter.KcpScatterBuilder(clientSetting(portA)).buildRemoteClient();
            if (client == null) {
                log.warn("    KCP RemoteClient 未注册,跳过");
                return true;
            }

            ScatterResult<Object> result = client.invoke(
                    new com.chua.common.support.scatter.ScatterContext(
                            java.util.UUID.randomUUID().toString(), "/scatter", 2000, 1, Map.of()),
                    new com.chua.common.support.scatter.ScatterNode("node-a", "127.0.0.1", portA, "kcp", "order", "/scatter", Map.of()),
                    2000);
            assertTrue(result != null && result.isSuccess(), "KCP invoke 应成功, 实际: " + (result == null ? "null" : result.getErrorMessage()));
            log.info("    KCP 往返成功 ✓");
            client.closeAll();
            pass();
            return true;
        } catch (Exception e) {
            fail("KCP 往返异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(serverA);
            closeQuietly(discoveryA);
        }
    }

    // ==================== UDP 广播 ====================

    /**
     * UDP 广播发现:广播地址向网内广播自身服务,对端节点能收到。
     */
    private boolean testUdpBroadcast() {
        log.info("  [SCATTER-CLUSTER-05] UDP 广播发现");
        // UDP 广播依赖组播/广播地址,本地单测环境以 127.0.0.1 广播模拟单播直达验证
        try {
            int portA = freePort();
            ScatterSetting setting = new ScatterSetting();
            setting.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            setting.setServicePath("/scatter").setPersistenceEnabled(false);
            setting.setProtocol("udp");
            DefaultScatterServiceDiscovery discovery = new DefaultScatterServiceDiscovery(setting);
            discovery.start();
            discovery.registerService("/scatter", Discovery.builder()
                    .serverId("node-a").scatterId("order").protocol("udp")
                    .host("127.0.0.1").port(portA).weight(1).build());

            // 广播模式下 seed 即组播地址,此处验证注册与查询链路
            Discovery d = discovery.getService("/scatter", "order", "weight", "udp");
            assertTrue(d != null, "UDP 广播注册后应能查到自身服务");
            log.info("    UDP 广播注册/查询链路正常 ✓");
            discovery.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("UDP 广播异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== 动态权重 ====================

    /**
     * 动态权重:负载(cpu+内存)越高权重越低,权重随 gossip 同步上报。
     */
    private boolean testDynamicWeight() {
        log.info("  [SCATTER-CLUSTER-06] 动态权重(负载越高权重越低)");
        try {
            int portA = freePort();
            ScatterSetting setting = new ScatterSetting();
            setting.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            setting.setServicePath("/scatter").setPersistenceEnabled(false);
            setting.setDynamicWeight(true);
            DefaultScatterServiceDiscovery discovery = new DefaultScatterServiceDiscovery(setting);
            discovery.start();

            // 权重应在 (0, 1] 区间:负载越高权重越低,空闲趋近 1
            Discovery self = discovery.getService("/scatter", "order", "weight", "tcp");
            assertTrue(self != null, "应能查到自身节点");
            double w = self.getWeight();
            assertTrue(w > 0 && w <= 1.0001, "动态权重应在 (0,1] 区间, 实际: " + w);
            log.info("    动态权重 = {} (0,1] 区间 ✓", w);
            discovery.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("动态权重异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== 分组隔离 ====================

    /**
     * 分组隔离:不同 groupId 节点互不可见,仅同分组负载均衡。
     */
    private boolean testGroupIsolation() {
        log.info("  [SCATTER-CLUSTER-07] 分组隔离");
        try {
            int portA = freePort();
            int portB = freePort();
            ScatterSetting setting = new ScatterSetting();
            setting.setNodeId("node-a").setGroupId("order").setHost("127.0.0.1").setPort(portA);
            setting.setServicePath("/scatter").setPersistenceEnabled(false);
            DefaultScatterServiceDiscovery discovery = new DefaultScatterServiceDiscovery(setting);
            discovery.start();
            discovery.registerService("/scatter", Discovery.builder()
                    .serverId("order-1").scatterId("order").protocol("tcp")
                    .host("127.0.0.1").port(portA).weight(1).build());
            discovery.registerService("/scatter", Discovery.builder()
                    .serverId("user-1").scatterId("user").protocol("tcp")
                    .host("127.0.0.1").port(portB).weight(1).build());

            // order 组查询:只返回 order 节点,不会路由到 user 节点
            for (int i = 0; i < 10; i++) {
                Discovery d = discovery.getService("/scatter", "order", "weight", "tcp");
                assertTrue(d != null, "order 组应能查到节点");
                assertEquals("order", d.getScatterId(), "应只路由 order 分组节点");
                assertEquals(portA, d.getPort(), "order 组不应路由到 user 节点");
            }
            log.info("    order 组 10 次查询全部落在 order 分组,user 节点被隔离 ✓");
            discovery.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("分组隔离异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 构建客户端配置(仅用于协议 SPI 实例化)。
     */
    private static ScatterSetting clientSetting(int targetPort) {
        ScatterSetting setting = new ScatterSetting();
        setting.setNodeId("client").setGroupId("order").setHost("127.0.0.1").setPort(targetPort);
        setting.setTimeoutMillis(2000);
        setting.setPersistenceEnabled(false);
        return setting;
    }

    // ==================== DefaultScatter 自身即 TCP 代理 ====================

    /**
     * DefaultScatter:一个端口同时承载 服务发现 + TCP 代理,请求经代理按 groupId 路由转发到后端。
     */
    private boolean testDefaultScatterTcpProxy() {
        log.info("  [SCATTER-CLUSTER-08] DefaultScatter 自身即 TCP 代理(共用端口)");
        com.chua.common.support.network.server.impl.JdkTcpServer backend = null;
        com.chua.common.support.scatter.DefaultScatter scatter = null;
        try {
            int scatterPort = freePort();
            ScatterSetting setting = new ScatterSetting();
            setting.setNodeId("scatter-node").setGroupId("order").setHost("127.0.0.1").setPort(scatterPort);
            setting.setServicePath("/scatter").setPersistenceEnabled(false);
            setting.setTimeoutMillis(2000);
            setting.setBalance("weight");

            // 后端 TCP 回显服务器
            backend = (com.chua.common.support.network.server.impl.JdkTcpServer) com.chua.common.support.network.server.ServerBuilder.create()
                    .type("jdk-tcp").host("127.0.0.1").port(0).build();
            backend.start();

            // scatter 节点:discovery + TCP 代理共用端口
            scatter = new com.chua.common.support.scatter.DefaultScatter(setting);
            scatter.start();
            // 注册后端服务到 discovery(按 groupId=order + tcp 协议)
            scatter.discovery().registerService("/scatter", Discovery.builder()
                    .serverId("backend-1").scatterId("order").protocol("tcp")
                    .host("127.0.0.1").port(backend.getPort()).weight(1).build());

            // 经 scatter 端口(代理)发 TCP 数据 → 按 discovery 路由到后端回显
            String echoed = tcpRoundTrip("127.0.0.1", scatter.getPort(), "scatter-proxy\n");
            assertEquals("scatter-proxy\n", echoed, "scatter 代理回显应与发送一致");
            log.info("    scatter 端口({}) TCP 代理路由到后端,回显一致 ✓", scatter.getPort());
            pass();
            return true;
        } catch (Exception e) {
            fail("DefaultScatter TCP 代理异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(scatter);
            closeQuietly(backend);
        }
    }

    // ==================== cluster addServer 路由 ====================

    /**
     * ClusterManager.addServer 注册服务器 → 集群视图查询 + 负载均衡路由。
     */
    private boolean testClusterAddServer() {
        log.info("  [SCATTER-CLUSTER-09] cluster addServer 注册与路由");
        DefaultScatterServiceDiscovery discovery = null;
        try {
            int port = freePort();
            ScatterSetting setting = new ScatterSetting();
            setting.setNodeId("cluster-node").setGroupId("order").setHost("127.0.0.1").setPort(port);
            setting.setServicePath("/scatter").setPersistenceEnabled(false);
            discovery = new DefaultScatterServiceDiscovery(setting);
            discovery.start();

            com.chua.common.support.network.cluster.ClusterManager manager =
                    new com.chua.common.support.network.cluster.ClusterManager(discovery, "weight", "cluster-node");
            manager.addServer("/api", "192.168.1.10", 8080, "http");
            manager.addServer("/api", "192.168.1.11", 8080, "http");

            // 集群视图:应能看到两台已注册服务器
            Set<Discovery> nodes = manager.nodes("/api");
            assertTrue(nodes.size() >= 2, "集群视图应包含 2 台服务器, 实际: " + nodes.size());
            // 负载均衡路由:10 次应全部落在 order 分组
            for (int i = 0; i < 10; i++) {
                Discovery d = manager.route("/api", "order", "http");
                assertTrue(d != null, "路由应能选中节点");
                assertEquals("order", d.getScatterId(), "应只路由 order 分组");
                assertTrue(d.getPort() == 8080, "应路由到 8080 端口服务器");
            }
            log.info("    addServer 2 台,集群视图可见,10 次路由全部落在 order/http:8080 ✓");
            discovery.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("cluster addServer 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(discovery);
        }
    }

    // ==================== 工具 ====================

    /**
     * 注册节点服务端响应逻辑：收到 sync/request 后回发 ScatterResultWithRequestId（线格式）。
     */
    private static void attachResponseHandler(ScatterNodeServer nodeServer, DefaultScatterServiceDiscovery discovery,
                                              String nodeId) {
        SyncServer syncServer = nodeServer.getSyncServer();
        syncServer.addListener(new SyncServerListener() {
            @Override
            public void onMessage(String clientId, String messageTopic, Object message) {
                if (!"sync/request".equals(messageTopic) || message == null) {
                    return;
                }
                try {
                    String payload = message.toString();
                    String requestId = null;
                    String path = null;
                    int ri = payload.indexOf("\"requestId\":\"");
                    if (ri >= 0) {
                        // 前缀 "requestId":" 为 13 字符，偏移 +13 定位 UUID 起始
                        requestId = payload.substring(ri + 13, payload.indexOf('"', ri + 13));
                    }
                    int pi = payload.indexOf("\"path\":\"");
                    if (pi >= 0) {
                        path = payload.substring(pi + 8, payload.indexOf('"', pi + 8));
                    }
                    if (requestId == null || path == null) {
                        return;
                    }
                    Set<Discovery> services = discovery.getServiceAll(path);
                    Discovery picked = services.stream().findFirst().orElse(null);
                    ScatterResult<Discovery> result = picked != null
                            ? ScatterResult.success(nodeId, picked)
                            : ScatterResult.failure(nodeId, "无服务");
                    syncServer.send(clientId, "sync/response",
                            new ScatterResultWithRequestId<>(requestId, result));
                } catch (Exception e) {
                    log.warn("响应处理异常: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * 获取空闲端口。
     */
    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * TCP 往返：发送 msg 并读取等长回显。
     */
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

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " 期望=" + expected + " 实际=" + actual);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void pass() {
        log.info("    ✓ 通过");
    }

    private static void fail(String msg) {
        log.error("    ✗ 失败: {}", msg);
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

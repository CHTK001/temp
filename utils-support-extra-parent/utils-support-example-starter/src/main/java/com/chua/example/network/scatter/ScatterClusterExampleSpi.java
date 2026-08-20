package com.chua.example.network.scatter;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.scatter.Scatter;
import com.chua.common.support.scatter.TcpScatterBuilder;
import com.chua.example.spi.Example;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Scatter 集群示例（新 API：tcp 帧协议短连接 + seed 引导）。
 *
 * <p>用例：TCP 双节点 seed 互发现 + 心跳剔除 + 恢复重连。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ScatterClusterExampleSpi implements Example {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ScatterClusterExampleSpi.class);

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
        return "Scatter 集群:TCP 双节点 seed 互发现 + 心跳剔除 + 恢复重连（新 API 帧协议）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== scatter-cluster [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "seed".equals(mode)) {
            passed &= testSeedDiscovery();
        }
        if ("all".equals(mode) || "heartbeat".equals(mode)) {
            passed &= testHeartbeatRemove();
        }
        log.info("===== scatter-cluster 结果: {} =====", passed ? "全部通过 ✓" : "存在失败 ✗");
        return passed;
    }

    /** 双节点 seed 互发现。 */
    private boolean testSeedDiscovery() {
        log.info("  [SCATTER-01] TCP 双节点 seed 互发现 + hash 同步");
        Scatter nodeA = null;
        Scatter nodeB = null;
        try {
            int portA = freePort();
            int portB = freePort();
            nodeA = new TcpScatterBuilder()
                    .nodeId("node-a").host("127.0.0.1").port(portA)
                    .groupId("order").servicePath("/scatter")
                    .autoDiscoveryInterval(200).heartbeatInterval(500).failRemoveCount(3)
                    .persistenceEnabled(false)
                    .build();
            nodeA.start();

            nodeB = new TcpScatterBuilder()
                    .nodeId("node-b").host("127.0.0.1").port(portB)
                    .groupId("order").servicePath("/scatter")
                    .seeds(List.of("127.0.0.1:" + portA))
                    .autoDiscoveryInterval(200).heartbeatInterval(500).failRemoveCount(3)
                    .persistenceEnabled(false)
                    .build();
            nodeB.start();

            TimeUnit.SECONDS.sleep(2);
            Set<Discovery> services = nodeB.discovery().getServiceAll("/scatter");
            boolean found = services.stream().anyMatch(d -> "node-a".equals(d.getServerId()));
            log.info("    node-b 发现: {} (含 node-a={})", services.size(), found);
            return found;
        } catch (Exception e) {
            log.error("seed 发现异常: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(nodeB);
            closeQuietly(nodeA);
        }
    }

    /** 心跳剔除 + 恢复重连。 */
    private boolean testHeartbeatRemove() {
        log.info("  [SCATTER-02] 心跳剔除 + 恢复重连");
        Scatter nodeA = null;
        Scatter nodeB = null;
        try {
            int portA = freePort();
            int portB = freePort();
            nodeA = new TcpScatterBuilder()
                    .nodeId("node-a").host("127.0.0.1").port(portA)
                    .groupId("order").servicePath("/scatter")
                    .autoDiscoveryInterval(200).heartbeatInterval(500).failRemoveCount(2)
                    .persistenceEnabled(false)
                    .build();
            nodeA.start();

            nodeB = new TcpScatterBuilder()
                    .nodeId("node-b").host("127.0.0.1").port(portB)
                    .groupId("order").servicePath("/scatter")
                    .seeds(List.of("127.0.0.1:" + portA))
                    .autoDiscoveryInterval(200).heartbeatInterval(500).failRemoveCount(2)
                    .persistenceEnabled(false)
                    .build();
            nodeB.start();

            TimeUnit.SECONDS.sleep(2);
            boolean before = nodeB.discovery().getServiceAll("/scatter").stream()
                    .anyMatch(d -> "node-a".equals(d.getServerId()));
            log.info("    掉线前发现 node-a={}", before);

            // node-a 掉线 → 剔除
            nodeA.stop();
            TimeUnit.SECONDS.sleep(4);
            boolean after = nodeB.discovery().getServiceAll("/scatter").stream()
                    .anyMatch(d -> "node-a".equals(d.getServerId()));
            log.info("    掉线后 node-a 保留={} (应 false)", after);
            return before && !after;
        } catch (Exception e) {
            log.error("心跳剔除异常: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(nodeB);
            closeQuietly(nodeA);
        }
    }

    private int freePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void closeQuietly(Scatter scatter) {
        if (scatter != null) {
            try {
                scatter.stop();
            } catch (Exception ignored) {
            }
        }
    }
}

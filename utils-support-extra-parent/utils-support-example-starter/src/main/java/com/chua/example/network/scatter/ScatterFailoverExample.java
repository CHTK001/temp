package com.chua.example.network.scatter;

import com.chua.common.support.scatter.Scatter;
import com.chua.common.support.scatter.TcpScatterBuilder;

import java.util.List;

/**
 * 单 JVM 内嵌双节点故障测试（新 API）：掉线剔除 + 恢复重连 + 稳定。
 *
 * <p>node-c（对端，seed 引导）+ node-b（观察者），短心跳 + 低剔除阈值加速收敛。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ScatterFailoverExample {

    public static void main(String[] args) throws Exception {
        System.out.println("===== Scatter 故障测试：掉线剔除 + 恢复重连（新 API） =====");
        int portC = 19082;
        int portB = 19081;

        // ===== 节点 C（对端，seed 引导） =====
        Scatter nodeC = new TcpScatterBuilder()
                .nodeId("node-c").host("127.0.0.1").port(portC)
                .groupId("order").servicePath("/scatter")
                .autoDiscoveryInterval(300).heartbeatInterval(1000).failRemoveCount(2)
                .persistenceEnabled(false)
                .build();
        nodeC.start();
        System.out.println("[TEST] node-c 已启动 @ " + nodeC.getPort());

        // ===== 节点 B（观察者，seeds 指向 C） =====
        Scatter nodeB = new TcpScatterBuilder()
                .nodeId("node-b").host("127.0.0.1").port(portB)
                .groupId("order").servicePath("/scatter")
                .seeds(List.of("127.0.0.1:" + portC))
                .autoDiscoveryInterval(300).heartbeatInterval(1000).failRemoveCount(2)
                .persistenceEnabled(false)
                .build();
        nodeB.start();
        System.out.println("[TEST] node-b 已启动 @ " + nodeB.getPort());

        // ===== 阶段 1：互发现（等 3s） =====
        System.out.println("\n===== 阶段1: 互发现（等 3s） =====");
        Thread.sleep(3000);
        printServices("node-b 发现", nodeB);

        // ===== 阶段 2：node-c 掉线 =====
        System.out.println("\n===== 阶段2: node-c 掉线（心跳 1s×失败2次≈2-3s 剔除） =====");
        nodeC.stop();
        System.out.println("[TEST] node-c 已停止");
        Thread.sleep(6000);
        printServices("node-b 发现(掉线后)", nodeB);

        // ===== 阶段 3：node-c 恢复 =====
        System.out.println("\n===== 阶段3: node-c 恢复（重启，同端口） =====");
        Scatter nodeC2 = new TcpScatterBuilder()
                .nodeId("node-c").host("127.0.0.1").port(portC)
                .groupId("order").servicePath("/scatter")
                .autoDiscoveryInterval(300).heartbeatInterval(1000).failRemoveCount(2)
                .persistenceEnabled(false)
                .build();
        nodeC2.start();
        System.out.println("[TEST] node-c 已重启");
        Thread.sleep(5000);
        printServices("node-b 发现(恢复后)", nodeB);

        // ===== 阶段 4：稳定观察（12s） =====
        System.out.println("\n===== 阶段4: 稳定观察（12s，验证去重不膨胀） =====");
        for (int i = 0; i < 4; i++) {
            Thread.sleep(3000);
            int n = nodeB.discovery().getServiceAll("/scatter").size();
            System.out.println("[TEST] t+" + ((i + 1) * 3) + "s node-b 服务数=" + n);
        }
        System.out.println("\n===== 测试结束 =====");
        nodeC2.stop();
        nodeB.stop();
        System.exit(0);
    }

    private static void printServices(String tag, Scatter scatter) {
        var services = scatter.discovery().getServiceAll("/scatter");
        System.out.println("[TEST] " + tag + ": " + services.size() + " 个 -> "
                + services.stream().map(d -> d.getServerId()).collect(java.util.stream.Collectors.toList()));
    }
}

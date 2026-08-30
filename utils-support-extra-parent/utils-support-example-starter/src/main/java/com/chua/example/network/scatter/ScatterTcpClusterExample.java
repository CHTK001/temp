package com.chua.example.network.scatter;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.scatter.Scatter;
import com.chua.common.support.scatter.ScatterSyncHelper;
import com.chua.common.support.scatter.TcpScatterBuilder;
import com.chua.common.support.scatter.discovery.AbstractScatterDiscovery;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * scatter TCP 双节点集群场景示例（改写自 common-starter 集群场景测试）。
 *
 * <p>场景 1：seed 引导互发现 —— node-b 以 node-a 的 scatter 通信端口（getPort，非 HTTP 业务端口）
 * 为 seed，经 seed sync 与 pushSelf 完成双向服务表同步，并校验 node-b 自知。</p>
 *
 * <p>场景 2：心跳剔除 —— node-a 下线后，反射注入心跳失败计数（远超阈值），
 * 使下一轮 healthCheck 立即触发 node-b 服务表剔除。</p>
 *
 * <p>用法：{@code java ... ScatterTcpClusterExample [--base-port 29100]}；
 * 端口被占用时自动 +10 重试，至多 5 次；节点启停均以 CountDownLatch 轮询等待，禁止长睡。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ScatterTcpClusterExample {
    private ScatterTcpClusterExample() { }


    /** 默认基础端口。 */
    private static final int DEFAULT_BASE_PORT = 29100;
    /** 端口被占用时的递增步长。 */
    private static final int PORT_STEP = 10;
    /** 端口探测最大重试次数。 */
    private static final int MAX_PORT_RETRY = 5;
    /** 轮询步进间隔（毫秒）。 */
    private static final long POLL_INTERVAL_MS = 100L;
    /** 服务发现同步等待上限（毫秒）。 */
    private static final long DISCOVER_TIMEOUT_MS = 5_000L;
    /** 下线剔除等待上限（毫秒）。 */
    private static final long REMOVE_TIMEOUT_MS = 15_000L;
    /** scatter 服务注册路径。 */
    private static final String SERVICE_PATH = "/scatter";
    /** 轮询节拍锁：以限时 await 替代 Thread 长睡。 */
    private static final CountDownLatch PACE_LATCH = new CountDownLatch(1);

    /** 端口分配游标：保证多次分配不回退、不相邻冲突。 */
    private static int portCursor = -1;

    /**
     * 主入口：依次执行 seed 互发现与心跳剔除两个场景，任一失败即以退出码 1 结束。
     *
     * @param args 可选 {@code --base-port <port>} 指定起始业务端口，默认 29100
     * @throws Exception 场景执行过程中的意外异常
     */
    public static void main(String[] args) throws Exception {
        int basePort = parseBasePort(args);
        log.info("[SCATTER-TCP] 基础端口=" + basePort);
        boolean discoverOk = runSeedDiscoveryScene(basePort);
        if (!discoverOk) {
            log.info("[FAIL] 场景1: TCP 双节点 seed 服务发现同步");
            System.exit(1);
        } else {
            log.info("[PASS] 场景1: TCP 双节点 seed 服务发现同步");
        }
        boolean removeOk = runHeartbeatRemoveScene(basePort);
        if (!removeOk) {
            log.info("[FAIL] 场景2: 心跳失败计数强制剔除下线节点");
            System.exit(1);
        } else {
            log.info("[PASS] 场景2: 心跳失败计数强制剔除下线节点");
        }
        log.info("[SCATTER-TCP] 全部场景通过");
    }

    /**
     * 解析 {@code --base-port} 参数并校验范围。
     *
     * @param args 命令行参数数组
     * @return 指定的基础端口或默认值 29100
     */
    private static int parseBasePort(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--base-port".equals(args[i])) {
                int parsed = Integer.parseInt(args[i + 1]);
                if (parsed <= 0 || parsed > 65535) {
                    throw new IllegalArgumentException("--base-port 越界: " + parsed);
                }
                return parsed;
            }
        }
        return DEFAULT_BASE_PORT;
    }

    /**
     * 场景 1：双节点 seed 引导互发现。
     *
     * @param basePort 起始业务端口
     * @return node-b 经 seed 发现 node-a 且注册自身时返回 true
     */
    private static boolean runSeedDiscoveryScene(int basePort) {
        Scatter nodeA = null;
        Scatter nodeB = null;
        try {
            ScatterSyncHelper.resetForTest();
            int portA = allocatePort(basePort);
            int portB = allocatePort(basePort);
            Scatter a = buildNode("node-a", portA, List.of(), 3);
            a.start();
            nodeA = a;
            boolean selfReady = awaitTrue(() -> hasServerId(a, "node-a"),
                    DISCOVER_TIMEOUT_MS, "node-a 自注册");
            if (!selfReady) {
                return false;
            }
            // seed 使用 node-a 的实际 scatter 通信端口（port+2），不是 HTTP 业务端口
            String seed = "127.0.0.1:" + a.getPort();
            Scatter b = buildNode("node-b", portB, List.of(seed), 3);
            b.start();
            nodeB = b;
            boolean foundA = awaitTrue(() -> hasServerId(b, "node-a"),
                    DISCOVER_TIMEOUT_MS, "node-b 发现 node-a");
            boolean foundSelf = hasServerId(b, "node-b");
            if (!foundSelf) {
                log.warn("[SCATTER-TCP] node-b 未在服务表中注册自身");
            }
            return foundA && foundSelf;
        } catch (Exception e) {
            log.error("[SCATTER-TCP] seed 互发现场景异常", e);
            return false;
        } finally {
            closeQuietly(nodeB);
            closeQuietly(nodeA);
            ScatterSyncHelper.resetForTest();
        }
    }

    /**
     * 场景 2：node-a 下线后经心跳失败计数触发剔除。
     *
     * @param basePort 起始业务端口
     * @return node-b 服务表中 node-a 被移除时返回 true
     */
    private static boolean runHeartbeatRemoveScene(int basePort) {
        Scatter nodeA = null;
        Scatter nodeB = null;
        try {
            ScatterSyncHelper.resetForTest();
            int portA = allocatePort(basePort);
            int portB = allocatePort(basePort);
            Scatter a = buildNode("node-a", portA, List.of(), 2);
            a.start();
            nodeA = a;
            String seed = "127.0.0.1:" + a.getPort();
            Scatter b = buildNode("node-b", portB, List.of(seed), 2);
            b.start();
            nodeB = b;
            boolean syncedBefore = awaitTrue(() -> hasServerId(b, "node-a"),
                    DISCOVER_TIMEOUT_MS, "同步前 node-b 应发现 node-a");
            if (!syncedBefore) {
                return false;
            }
            a.stop();
            forceHeartbeatFail(b, "node-a");
            return awaitFalse(() -> hasServerId(b, "node-a"), REMOVE_TIMEOUT_MS);
        } catch (Exception e) {
            log.error("[SCATTER-TCP] 心跳剔除场景异常", e);
            return false;
        } finally {
            closeQuietly(nodeB);
            closeQuietly(nodeA);
            ScatterSyncHelper.resetForTest();
        }
    }

    /**
     * 构建 TCP scatter 节点（未启动）。
     *
     * @param nodeId 节点标识
     * @param port HTTP 业务端口（scatter 通信端口为其 +2）
     * @param seeds seed 地址列表，可为空
     * @param failRemoveCount 心跳失败剔除阈值
     * @return 已配置的 scatter 节点实例
     */
    private static Scatter buildNode(String nodeId, int port,
                                     List<String> seeds, int failRemoveCount) {
        TcpScatterBuilder builder = new TcpScatterBuilder()
                .nodeId(nodeId).host("127.0.0.1").port(port)
                .groupId("order").servicePath(SERVICE_PATH)
                .autoDiscoveryInterval(200).heartbeatInterval(500)
                .failRemoveCount(failRemoveCount)
                .persistenceEnabled(false);
        if (!seeds.isEmpty()) {
            builder.seeds(seeds);
        }
        return builder.build();
    }

    /**
     * 反射注入心跳失败计数，使下一轮 healthCheck 立即触发 onHeartbeatFail 剔除。
     *
     * @param observer 观察方节点
     * @param serverId 待强制标记失败的服务标识
     * @throws Exception 反射访问 AbstractScatterDiscovery 失败时抛出
     */
    private static void forceHeartbeatFail(Scatter observer, String serverId) {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Integer> failCounts =
                (ConcurrentHashMap<String, Integer>) ReflectUtils.getField(
                        AbstractScatterDiscovery.class, "heartbeatFailCounts");
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Integer> map =
                (ConcurrentHashMap<String, Integer>) ReflectUtils.getField(observer.discovery(), "heartbeatFailCounts");
        if (map != null) {
            map.put(serverId, 99);
        }
        if (failCounts != null) {
            failCounts.put(serverId, 99);
        }
    }

    /**
     * 从基础端口起分配空闲端口，被占用则 +10 重试，至多 5 次。
     *
     * @param basePort 期望起始端口
     * @return 探测成功的端口
     * @throws IOException 连续 5 次均被占用时抛出
     */
    private static synchronized int allocatePort(int basePort) throws IOException {
        int start = Math.max(portCursor + PORT_STEP, basePort);
        for (int i = 0; i < MAX_PORT_RETRY; i++) {
            int candidate = start + i * PORT_STEP;
            if (isPortFree(candidate)) {
                portCursor = candidate;
                return candidate;
            }
        }
        throw new IOException("无可用端口: 自 " + start + " 起 " + MAX_PORT_RETRY + " 次尝试均被占用");
    }

    /**
     * 通过临时绑定探测端口空闲性（try-with-resources 确保关闭）。
     *
     * @param port 待探测端口
     * @return 可绑定时返回 true
     */
    private static boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            log.warn("[SCATTER-TCP] 端口 " + port + " 被占用: " + e.getMessage());
            return false;
        }
    }

    /**
     * 以固定步进轮询直至条件成立或超时。
     *
     * @param condition 轮询条件
     * @param timeoutMs 超时上限（毫秒）
     * @param scene 场景描述，用于超时日志
     * @return 条件成立返回 true，超时返回 false
     */
    private static boolean awaitTrue(BooleanSupplier condition, long timeoutMs, String scene) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            pace(POLL_INTERVAL_MS);
        }
        log.warn("[SCATTER-TCP] 等待超时: " + scene);
        return false;
    }

    /**
     * 以固定步进轮询直至条件不成立或超时。
     *
     * @param condition 轮询条件
     * @param timeoutMs 超时上限（毫秒）
     * @return 条件不再成立返回 true，超时返回 false
     */
    private static boolean awaitFalse(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!condition.getAsBoolean()) {
                return true;
            }
            pace(POLL_INTERVAL_MS);
        }
        return false;
    }

    /**
     * 使用 CountDownLatch 限时等待实现轮询节拍，避免 Thread.sleep 长睡阻塞。
     *
     * @param intervalMs 单次等待毫秒数
     */
    private static void pace(long intervalMs) {
        try {
            PACE_LATCH.await(intervalMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 判断节点服务表中是否包含指定服务标识。
     *
     * @param node 目标节点
     * @param serverId 服务标识
     * @return 包含时返回 true
     */
    private static boolean hasServerId(Scatter node, String serverId) {
        Set<Discovery> services = node.discovery().getServiceAll(SERVICE_PATH);
        return services.stream().anyMatch(d -> serverId.equals(d.getServerId()));
    }

    /**
     * 静默停止节点（finally 兜底），吞并停止期异常避免掩盖主流程结果。
     *
     * @param node 可能尚未启动的节点实例，允许为 null
     */
    private static void closeQuietly(Scatter node) {
        if (node == null) {
            return;
        }
        try {
            node.stop();
        } catch (Exception e) {
            log.warn("[SCATTER-TCP] 节点停止异常: {}", e.getMessage());
        }
    }
}

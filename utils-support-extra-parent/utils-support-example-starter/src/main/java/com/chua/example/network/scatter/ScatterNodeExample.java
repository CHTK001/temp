package com.chua.example.network.scatter;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.scatter.DefaultScatter;
import com.chua.common.support.scatter.Scatter;
import com.chua.common.support.scatter.TcpScatterBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * 跨机 scatter 节点启动入口（新 API，独立 main，供服务器/本机分布式部署）。
 *
 * <p>用法：{@code java ... ScatterNodeMain <nodeId> <host> <port> [seed1:port seed2:port ...]}</p>
 *
 * <p>例（服务器A 公网，seed 引导模式）：
 * {@code java ... ScatterNodeMain node-a 0.0.0.0 18080}
 * 例（本机，seeds 指向服务器A）：
 * {@code java ... ScatterNodeMain node-b 0.0.0.0 18081 124.221.230.112:18080}</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ScatterNodeExample {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: ScatterNodeMain <nodeId> <host> <port> [announceHost] [seed1:port ...]");
            System.err.println("  host 为监听地址(0.0.0.0=全部)，announceHost 为对外注册地址(公网 IP，'-' 或空=回落 host)");
            System.exit(2);
        }
        String nodeId = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        // announceHost：监听 0.0.0.0 时对端无法路由，必须显式指定公网注册地址
        String announceHost = args.length > 3 && !"-".equals(args[3]) ? args[3] : host;
        int seedStart = args.length > 3 && !"-".equals(args[3]) ? 4 : 3;
        List<String> seeds = args.length > seedStart
                ? Arrays.asList(Arrays.copyOfRange(args, seedStart, args.length))
                : List.of();

        log.info("[ScatterNodeMain] 启动节点 " + nodeId + " @ " + host + ":" + port
                + " announce=" + announceHost + " seeds=" + seeds);

        TcpScatterBuilder builder = new TcpScatterBuilder()
                .nodeId(nodeId)
                .host(host)
                .announceHost(announceHost)
                .port(port)
                .groupId("order")
                .servicePath("/scatter")
                .autoDiscoveryInterval(1000)
                .heartbeatInterval(2000)
                .failRemoveCount(3)
                .persistenceEnabled(true);
        if (!seeds.isEmpty()) {
            builder.seeds(seeds);
        }

        Scatter scatter = builder.build();
        scatter.start();
        log.info("[ScatterNodeMain] 节点 " + nodeId + " 已启动 @ " + scatter.getPort());

        // 周期打印发现的服务
        Thread reporter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000);
                    var services = scatter.discovery().getServiceAll("/scatter");
                    log.info("[ScatterNodeMain][" + nodeId + "] 当前发现 " + services.size()
                            + " 个服务: " + services.stream()
                            .map(d -> d.getServerId()).collect(java.util.stream.Collectors.toList()));
                } catch (Exception e) {
                    break;
                }
            }
        }, "scatter-reporter");
        reporter.setDaemon(true);
        reporter.start();

        Thread.currentThread().join();
    }
}

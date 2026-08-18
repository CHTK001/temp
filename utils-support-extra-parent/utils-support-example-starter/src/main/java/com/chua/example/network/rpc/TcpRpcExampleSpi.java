package com.chua.example.network.rpc;

import com.chua.common.support.network.discovery.DefaultServiceDiscovery;
import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.discovery.DiscoveryOption;
import com.chua.common.support.network.discovery.ServiceDiscovery;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.SyncServer;
import com.chua.common.support.network.server.SyncServerListener;
import com.chua.common.support.network.sync.SyncClient;
import com.chua.common.support.network.sync.SyncMessageHandler;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 TcpServer + TcpClient + ServiceDiscovery 的 RPC 示例（SPI 形式）。
 *
 * <p>链路：服务端启动 {@code tcp} SyncServer 并把服务地址注册到 ServiceDiscovery
 * （内存 {@link DefaultServiceDiscovery}）；客户端经 ServiceDiscovery 发现服务地址，
 * 再用 {@code tcp} SyncClient 发起 RPC 调用（send 请求 → 服务端 publish 响应 → 客户端订阅收到）。</p>
 *
 * <p>调用：{@code ExampleRunner --example=tcp-rpc} 或 {@code java ... TcpRpcExampleSpi}</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TcpRpcExampleSpi implements Example {

    /** RPC 请求主题 */
    /** Rpc_req_topic */
    private static final String RPC_REQ_TOPIC = "rpc/echo";
    /** RPC 响应主题 */
    /** Rpc_resp_topic */
    private static final String RPC_RESP_TOPIC = "rpc/echo-resp";
    /** 服务名（注册中心 key） */
    /** Service_name */
    private static final String SERVICE_NAME = "rpc/tcp-echo";

    @Override
    public String name() {
        return "tcp-rpc";
    }

    @Override
    public String module() {
        return "network";
    }

    @Override
    public String description() {
        return "基于 TcpServer + TcpClient + ServiceDiscovery 的 RPC 客户端/服务端示例";
    }

    @Override
    public boolean run(Map<String, String> args) {
        log.info("===== tcp-rpc 示例开始 =====");
        SyncServer server = null;
        SyncClient client = null;
        ServiceDiscovery discovery = null;
        try {
            // 1. 服务端：启动 tcp SyncServer
            int port = freePort();
            ServerSetting setting = ServerSetting.builder()
                    .host("127.0.0.1").port(port).protocol("tcp").build();
            server = ServiceProvider.of(SyncServer.class).getNewExtension("tcp", setting);
            if (server == null) {
                log.error("  tcp SyncServer 未注册");
                return false;
            }
            // 服务端收到 RPC 请求 → publish 响应
            final SyncServer srv = server;
            server.addListener(new SyncServerListener() {
                @Override
                public void onMessage(String clientId, String topic, Object message) {
                    if (RPC_REQ_TOPIC.equals(topic)) {
                        srv.publish(RPC_RESP_TOPIC, "echo:" + message);
                    }
                }
            });
            server.start();

            // 2. 服务端把服务地址注册到 ServiceDiscovery（内存实现）
            DiscoveryOption option = new DiscoveryOption();
            discovery = new DefaultServiceDiscovery(option);
            discovery.start();
            discovery.registerService(SERVICE_NAME, new Discovery().setProtocol("tcp")
                    .setHost("127.0.0.1").setPort(port));
            log.info("  服务端已注册到 ServiceDiscovery: {} -> tcp://127.0.0.1:{}", SERVICE_NAME, port);

            // 3. 客户端：经 ServiceDiscovery 发现服务地址
            Discovery target = discovery.getService(SERVICE_NAME, null, "tcp");
            if (target == null) {
                log.error("  ServiceDiscovery 未发现服务: {}", SERVICE_NAME);
                return false;
            }
            String serverUrl = "tcp://" + target.getHost() + ":" + target.getPort();
            log.info("  客户端发现服务地址: {}", serverUrl);

            // 4. 客户端：TcpSyncClient 连接并发起 RPC 调用
            client = ServiceProvider.of(SyncClient.class).getNewExtension("tcp", serverUrl);
            if (client == null) {
                log.error("  tcp SyncClient 未注册");
                return false;
            }
            CountDownLatch respLatch = new CountDownLatch(1);
            AtomicReference<String> resp = new AtomicReference<>();
            client.subscribe(RPC_RESP_TOPIC, new SyncMessageHandler() {
                @Override
                public void handle(String topic, Object message) {
                    resp.set(String.valueOf(message));
                    respLatch.countDown();
                }
            });
            client.connect();

            // 5. 发起 RPC 调用
            client.send(RPC_REQ_TOPIC, "hello-rpc");
            boolean ok = respLatch.await(5, TimeUnit.SECONDS);
            if (ok) {
                log.info("  RPC 调用成功: 请求=hello-rpc 响应={}", resp.get());
            } else {
                log.error("  RPC 调用超时");
            }
            return ok && "echo:hello-rpc".equals(resp.get());
        } catch (Exception e) {
            log.error("  tcp-rpc 示例异常: {}", e.getMessage(), e);
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception ignored) {
                }
            }
            if (discovery != null) {
                try {
                    discovery.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 独立入口：{@code java ... TcpRpcExampleSpi}
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        new TcpRpcExampleSpi().run(Map.of());
    }

    /**
     * 获取空闲端口。
     *
     * @return 空闲端口
     */
    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 0;
        }
    }
}

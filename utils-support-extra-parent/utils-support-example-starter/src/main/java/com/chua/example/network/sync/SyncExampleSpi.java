package com.chua.example.network.sync;

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
 * SyncServer / SyncClient 全子类对接自检示例（SPI 形式）。
 *
 * <p>覆盖 common 的 tcp/udp/http/websocket 与 kcp 共 5 个 sync 协议子类：
 * 每个协议起服务端 + 客户端，验证 publish → onMessage、send → subscribe 双向链路。</p>
 *
 * <p>通过 {@code ExampleRunner --example=sync} 调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SyncExampleSpi implements Example {

    /** 参与自检的 sync 协议子类 */
    private static final List<String> PROTOCOLS = List.of("tcp", "udp", "kcp", "http", "websocket");

    @Override
    public String name() {
        return "sync";
    }

    @Override
    public String module() {
        return "network";
    }

    @Override
    public String description() {
        return "SyncServer/SyncClient 全子类对接自检：tcp/udp/kcp/http/websocket";
    }

    /**
     * 独立入口：{@code java ... SyncExampleSpi --example=sync --mode=throughput --protocol=tcp --messages=1000}
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String kv = arg.substring(2);
                int eq = kv.indexOf('=');
                map.put(eq > 0 ? kv.substring(0, eq) : kv, eq > 0 ? kv.substring(eq + 1) : "");
            }
        }
        new SyncExampleSpi().run(map);
    }

    @Override
    public boolean run(Map<String, String> args) {
        // --mode=throughput 时执行吞吐量测试，否则执行全子类对接自检
        if ("throughput".equalsIgnoreCase(args.getOrDefault("mode", ""))) {
            String protocol = args.getOrDefault("protocol", "tcp");
            int messages = Integer.parseInt(args.getOrDefault("messages", "2000"));
            return throughput(protocol, messages);
        }
        // --mode=rpc 时执行并发请求-响应吞吐测试（真实往返）
        if ("rpc".equalsIgnoreCase(args.getOrDefault("mode", ""))) {
            String protocol = args.getOrDefault("protocol", "tcp");
            int messages = Integer.parseInt(args.getOrDefault("messages", "2000"));
            int threads = Integer.parseInt(args.getOrDefault("threads", "4"));
            return rpcThroughput(protocol, messages, threads);
        }
        log.info("===== sync 全子类自检开始 =====");
        boolean allPassed = true;
        for (String protocol : PROTOCOLS) {
            allPassed &= roundTrip(protocol);
        }
        log.info("===== sync 全子类自检 {} =====", allPassed ? "通过" : "失败");
        return allPassed;
    }

    /**
     * 并发请求-响应吞吐测试：客户端 N 线程并发 send 请求，服务端收到后立即 publish 响应，
     * 客户端订阅响应计数——测真实网络往返（请求+响应一次计 1）。
     * <p>调用：{@code ExampleRunner --example=sync --mode=rpc --protocol=tcp --messages=5000 --threads=4}</p>
     *
     * @param protocol 协议名
     * @param messages 总请求数
     * @param threads  并发线程数
     * @return 是否通过（全部响应收满即通过）
     */
    private boolean rpcThroughput(String protocol, int messages, int threads) {
        int port = freePort();
        SyncServer server = null;
        SyncClient client = null;
        try {
            ServerSetting setting = ServerSetting.builder()
                    .host("127.0.0.1").port(port).protocol(protocol).build();
            if ("ionet".equals(protocol)) {
                server = com.chua.ionet.support.server.IonetSyncServer.builder()
                        .port(port)
                        .scanActionPackage(com.chua.example.ionet.IonetExampleSpi.DemoAction.class)
                        .build();
            } else {
                server = ServiceProvider.of(SyncServer.class).getNewExtension(protocol, setting);
            }
            if (server == null) {
                log.warn("  [{}] SyncServer 未注册，无法测请求-响应", protocol);
                return false;
            }
            // 服务端：收到 perf/req 请求立即 publish perf/resp 响应（SyncServerListener 全 default，需匿名类）
            final SyncServer srv = server;
            server.addListener(new SyncServerListener() {
                @Override
                public void onMessage(String clientId, String topic, Object message) {
                    if ("perf/req".equals(topic)) {
                        srv.publish("perf/resp", message);
                    }
                }
            });
            server.start();
            String serverUrl = protocol + "://127.0.0.1:" + port;
            if ("ionet".equals(protocol)) {
                client = com.chua.ionet.support.client.IonetSyncClient.builder()
                        .host("127.0.0.1")
                        .port(port)
                        .addRegion(new com.chua.example.ionet.IonetExampleSpi.DemoRegion())
                        .build();
            } else {
                client = ServiceProvider.of(SyncClient.class).getNewExtension(protocol, serverUrl);
            }
            if (client == null) {
                log.warn("  [{}] SyncClient 未注册，无法测请求-响应", protocol);
                return false;
            }
            // 客户端计数：收到响应
            final SyncClient cli = client;
            CountDownLatch clientGot = new CountDownLatch(messages);
            client.subscribe("perf/resp", new SyncMessageHandler() {
                @Override
                public void handle(String topic, Object message) {
                    clientGot.countDown();
                }
            });
            client.connect();

            // N 线程并发发送请求
            long start = System.nanoTime();
            int perThread = messages / threads;
            int remainder = messages % threads;
            Thread[] workers = new Thread[threads];
            for (int t = 0; t < threads; t++) {
                int count = perThread + (t < remainder ? 1 : 0);
                final int tid = t;
                workers[t] = new Thread(() -> {
                    for (int i = 0; i < count; i++) {
                        try {
                            cli.send("perf/req", "m" + tid + "-" + i);
                        } catch (Exception e) {
                            log.warn("  [{}] 请求发送异常: {}", protocol, e.getMessage());
                        }
                    }
                }, "rpc-" + tid);
                workers[t].start();
            }
            for (Thread w : workers) {
                try {
                    w.join();
                } catch (InterruptedException ignored) {
                }
            }
            boolean ok = clientGot.await(30, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            double ops = ok ? messages * 1000.0 / Math.max(elapsedMs, 1) : 0;
            log.info("  [{}] 请求-响应吞吐({}线程): {} 请求/{}ms = {} rpc/s, 收到 {} 条",
                    protocol, threads, messages, elapsedMs, Math.round(ops), clientGot.getCount());
            return ok;
        } catch (Exception e) {
            log.error("  [{}] 请求-响应吞吐测试异常: {}", protocol, e.getMessage(), e);
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
        }
    }

    /**
     * 吞吐量测试：长连接场景下循环 publish/send，统计服务端下行与客户端上行 ops/s。
     * <p>调用：{@code ExampleRunner --example=sync --mode=throughput --protocol=tcp --messages=2000}</p>
     *
     * @param protocol 协议名
     * @param messages 消息条数
     * @return 是否通过（无异常即通过）
     */
    private boolean throughput(String protocol, int messages) {
        int port = freePort();
        SyncServer server = null;
        SyncClient client = null;
        try {
            ServerSetting setting = ServerSetting.builder()
                    .host("127.0.0.1").port(port).protocol(protocol).build();
            if ("ionet".equals(protocol)) {
                // ionet 无 SPI 反射构造（缺 scanActionClass/region），特判走 Builder
                server = com.chua.ionet.support.server.IonetSyncServer.builder()
                        .port(port)
                        .scanActionPackage(com.chua.example.ionet.IonetExampleSpi.DemoAction.class)
                        .build();
            } else {
                server = ServiceProvider.of(SyncServer.class).getNewExtension(protocol, setting);
            }
            if (server == null) {
                log.warn("  [{}] SyncServer 未注册，无法测吞吐", protocol);
                return false;
            }
            // 服务端计数：客户端上行消息
            CountDownLatch serverGot = new CountDownLatch(messages);
            server.addListener(new SyncServerListener() {
                @Override
                public void onMessage(String clientId, String topic, Object message) {
                    serverGot.countDown();
                }
            });
            server.start();

            String serverUrl = protocol + "://127.0.0.1:" + port;
            if ("ionet".equals(protocol)) {
                // ionet 客户端同样无 SPI 反射构造（缺 region），特判走 Builder
                client = com.chua.ionet.support.client.IonetSyncClient.builder()
                        .host("127.0.0.1")
                        .port(port)
                        .addRegion(new com.chua.example.ionet.IonetExampleSpi.DemoRegion())
                        .build();
            } else {
                client = ServiceProvider.of(SyncClient.class).getNewExtension(protocol, serverUrl);
            }
            if (client == null) {
                log.warn("  [{}] SyncClient 未注册，无法测吞吐", protocol);
                return false;
            }
            // 客户端计数：服务端下行消息（订阅为精确匹配）
            CountDownLatch clientGot = new CountDownLatch(messages);
            client.subscribe("perf/down", new SyncMessageHandler() {
                @Override
                public void handle(String topic, Object message) {
                    clientGot.countDown();
                }
            });
            client.connect();

            // 服务端 → 客户端：下行吞吐
            long downStart = System.nanoTime();
            for (int i = 0; i < messages; i++) {
                server.publish("perf/down", "m" + i);
            }
            boolean downOk = clientGot.await(10, TimeUnit.SECONDS);
            long downElapsedMs = (System.nanoTime() - downStart) / 1_000_000;
            if (!downOk) {
                log.warn("  [{}] 下行 await 超时: 剩余 {} 条未收到, 服务端连接数 {}",
                        protocol, clientGot.getCount(), server.getConnectedClients());
            }

            // 客户端 → 服务端：上行吞吐
            long upStart = System.nanoTime();
            for (int i = 0; i < messages; i++) {
                client.send("perf/up", "m" + i);
            }
            boolean upOk = serverGot.await(60, TimeUnit.SECONDS);
            long upElapsedMs = (System.nanoTime() - upStart) / 1_000_000;

            double downOps = downOk ? messages * 1000.0 / Math.max(downElapsedMs, 1) : 0;
            double upOps = upOk ? messages * 1000.0 / Math.max(upElapsedMs, 1) : 0;
            log.info("  [{}] 吞吐量: 下行 {} 条/{}ms = {} ops/s, 上行 {} 条/{}ms = {} ops/s",
                    protocol, messages, downElapsedMs, Math.round(downOps),
                    messages, upElapsedMs, Math.round(upOps));
            return downOk && upOk;
        } catch (Exception e) {
            log.error("  [{}] 吞吐量测试异常: {}", protocol, e.getMessage(), e);
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
        }
    }

    /**
     * 单个协议往返自检：server.publish → client.subscribe 收到；client.send → server.onMessage 收到。
     *
     * @param protocol 协议名
     * @return 是否通过
     */
    private boolean roundTrip(String protocol) {
        int port = freePort();
        SyncServer server = null;
        SyncClient client = null;
        try {
            ServerSetting setting = ServerSetting.builder()
                    .host("127.0.0.1").port(port).protocol(protocol).build();
            server = ServiceProvider.of(SyncServer.class).getNewExtension(protocol, setting);
            if (server == null) {
                log.warn("  [{}] SyncServer 未注册，跳过", protocol);
                return true;
            }
            // 服务端监听：记录客户端上行消息（忽略服务端自身 publish 的 broadcast 回环）
            CountDownLatch serverGot = new CountDownLatch(1);
            AtomicReference<String> serverMsg = new AtomicReference<>();
            server.addListener(new SyncServerListener() {
                @Override
                public void onMessage(String clientId, String topic, Object message) {
                    if ("broadcast".equals(clientId)) {
                        return;
                    }
                    serverMsg.set(topic + ":" + message);
                    serverGot.countDown();
                }
            });
            server.start();

            // 客户端订阅：接收服务端下行广播（Tcp/Udp/Kcp 订阅为精确匹配）
            String serverUrl = protocol + "://127.0.0.1:" + port;
            client = ServiceProvider.of(SyncClient.class).getNewExtension(protocol, serverUrl);
            if (client == null) {
                log.warn("  [{}] SyncClient 未注册，跳过", protocol);
                return true;
            }
            CountDownLatch clientGot = new CountDownLatch(1);
            AtomicReference<String> clientMsg = new AtomicReference<>();
            client.subscribe("sync/ann", new SyncMessageHandler() {
                @Override
                public void handle(String topic, Object message) {
                    clientMsg.set(topic + ":" + message);
                    clientGot.countDown();
                }
            });
            client.connect();

            // 服务端 → 客户端
            server.publish("sync/ann", "ping-" + protocol);
            boolean down = clientGot.await(5, TimeUnit.SECONDS);

            // 客户端 → 服务端
            client.send("sync/echo", "pong-" + protocol);
            boolean up = serverGot.await(5, TimeUnit.SECONDS);

            boolean passed = down && up
                    && ("sync/ann:ping-" + protocol).equals(clientMsg.get())
                    && ("sync/echo:pong-" + protocol).equals(serverMsg.get());
            log.info("  [{}] 往返 {} (server→client={}, client→server={})",
                    protocol, passed ? "通过" : "失败", clientMsg.get(), serverMsg.get());
            return passed;
        } catch (Exception e) {
            log.error("  [{}] 自检异常: {}", protocol, e.getMessage());
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
        }
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

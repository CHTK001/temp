package com.chua.example.network.sip;

import com.chua.common.support.network.sip.SipClient;
import com.chua.common.support.network.sip.SipServer;
import com.chua.common.support.network.sip.SipTunnelPort;
import com.chua.common.support.network.sip.SipTunnelService;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SIP 信令服务器 + 客户端 + 隧道自检示例（SPI 形式）。
 *
 * <p>覆盖核心能力：
 * <ul>
 *   <li>注册 — 客户端连接后通过 {@code sip/register} 上报可达地址</li>
 *   <li>寻址 — {@code sip/find} 查询对端地址</li>
 *   <li>消息 — {@code sip/msg} 经服务器中继转发</li>
 *   <li>响应 — {@code sip/resp} 回传对端请求</li>
 *   <li>推送 — {@code sip/push} 服务器广播/定向推送</li>
 *   <li>隧道 — {@link SipTunnelService} 暴露本地 TCP 服务，{@link SipTunnelPort} 本地端口转发到对端服务，实现内网穿透</li>
 *   <li>断连清理 — 服务提供方断开后，其隧道服务被移除，访问方打开隧道应失败</li>
 * </ul>
 *
 * <p>通过 {@code ExampleRunner --example=sip} 调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SipExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "sip";
    }

    @Override
    /** Module */
    public String module() {
        return "network";
    }

    @Override
    /** Description */
    public String description() {
        return "SIP 信令服务器/客户端自检：注册+寻址+消息+响应+推送+隧道内网穿透+断连清理";
    }

    /**
     * 独立入口：{@code java ... SipExampleSpi --mode=all}。
     *
     * @param args 命令行参数（--mode=all|register|tunnel|cleanup）
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
        new SipExampleSpi().run(map);
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== sip [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "register".equals(mode)) {
            passed &= testRegisterFindMsgRespPush();
        }
        if ("all".equals(mode) || "tunnel".equals(mode)) {
            passed &= testTunnel();
        }
        if ("all".equals(mode) || "cleanup".equals(mode)) {
            passed &= testDisconnectCleanup();
        }
        log.info("===== sip {} =====", passed ? "通过" : "失败");
        return passed;
    }

    // ==================== 注册 + 寻址 + 消息 + 响应 + 推送 ====================

    /**
     * 双客户端接入服务器，验证注册、find 寻址、msg 转发、resp 响应、push 推送。
     */
    private boolean testRegisterFindMsgRespPush() {
        log.info("  [SIP-01] 注册 + 寻址 + 消息 + 响应 + 推送");
        SipServer server = null;
        SipClient clientA = null;
        SipClient clientB = null;
        try {
            int port = freePort();
            server = new SipServer(com.chua.common.support.network.sip.SipConfig.builder()
                    .host("127.0.0.1").tcpPort(port).kcpEnabled(false).build());
            server.start();

            clientA = SipClient.tcp("tcp://127.0.0.1:" + port).connect().register("127.0.0.1", 10001);
            clientB = SipClient.tcp("tcp://127.0.0.1:" + port).connect().register("127.0.0.1", 10002);
            awaitRegistered(server, 2);

            String idA = clientA.getClientId();
            String idB = clientB.getClientId();
            assertTrue(idA != null && idB != null && !idA.equals(idB), "两端应获得不同客户端标识");

            // 寻址
            String address = clientA.find(idB, 3000);
            assertTrue(address != null && address.contains(idB), "A 应能寻址到 B, 实际: " + address);
            log.info("    A 寻址到 B: {}", address);

            // 消息转发
            CountDownLatch msgGot = new CountDownLatch(1);
            AtomicReference<String> msgFrom = new AtomicReference<>();
            AtomicReference<String> msgContent = new AtomicReference<>();
            clientB.onMessage((from, content) -> {
                msgFrom.set(from);
                msgContent.set(content);
                msgGot.countDown();
            });
            clientA.send(idB, "hello-b");
            assertTrue(msgGot.await(5, TimeUnit.SECONDS), "B 应收到 A 的消息");
            assertEquals(idA, msgFrom.get(), "消息来源应为 A");
            assertEquals("hello-b", msgContent.get(), "消息内容应一致");

            // 响应回传
            CountDownLatch respGot = new CountDownLatch(1);
            AtomicReference<String> respContent = new AtomicReference<>();
            clientA.onResponse((from, requestId, content) -> {
                respContent.set(content);
                respGot.countDown();
            });
            clientB.respond(idA, "req-1", "pong-b");
            assertTrue(respGot.await(5, TimeUnit.SECONDS), "A 应收到 B 的响应");
            assertEquals("pong-b", respContent.get(), "响应内容应一致");

            // 定向推送
            CountDownLatch pushGot = new CountDownLatch(1);
            AtomicReference<String> pushTopic = new AtomicReference<>();
            clientB.onPush((topic, content) -> {
                pushTopic.set(topic);
                pushGot.countDown();
            });
            server.push(idB, "notice", "server-hello");
            assertTrue(pushGot.await(5, TimeUnit.SECONDS), "B 应收到服务器推送");
            assertEquals("notice", pushTopic.get(), "推送主题应一致");
            log.info("    push 定向推送 ✓");

            // 广播推送
            CountDownLatch pushAll = new CountDownLatch(2);
            clientA.onPush((topic, content) -> pushAll.countDown());
            clientB.onPush((topic, content) -> pushAll.countDown());
            server.publish("broadcast", "to-all");
            assertTrue(pushAll.await(5, TimeUnit.SECONDS), "广播应送达两个客户端");
            log.info("    publish 广播 ✓");

            assertTrue(server.getConnectedClients().size() >= 2, "服务器应记录至少 2 个客户端");
            pass();
            return true;
        } catch (Exception e) {
            fail("SIP-01 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(clientB::close);
            closeQuietly(clientA::close);
            closeQuietly(server::stop);
        }
    }

    // ==================== 隧道内网穿透 ====================

    /**
     * 内网穿透全链路：本地 Echo 服务 → SipTunnelService 暴露 → SipTunnelPort 本地端口转发 → 访问方回显一致。
     */
    private boolean testTunnel() {
        log.info("  [SIP-02] 隧道内网穿透(本地 Echo 服务经隧道双向转发)");
        SipServer server = null;
        SipClient provider = null;
        SipClient accessor = null;
        SipTunnelService tunnelService = null;
        SipTunnelPort tunnelPort = null;
        EchoServer echo = null;
        try {
            int port = freePort();
            int accessorPort = freePort();
            server = new SipServer(com.chua.common.support.network.sip.SipConfig.builder()
                    .host("127.0.0.1").tcpPort(port).kcpEnabled(false).build());
            server.start();

            // 服务提供方：本机 Echo 服务 + 隧道暴露
            echo = EchoServer.start(0);
            provider = SipClient.tcp("tcp://127.0.0.1:" + port);
            tunnelService = new SipTunnelService(provider, "echo", "127.0.0.1", echo.getPort());
            tunnelService.start();

            // 访问方：本地端口转发到对端 echo 服务
            accessor = SipClient.tcp("tcp://127.0.0.1:" + port);
            tunnelPort = new SipTunnelPort(accessor, "echo", "127.0.0.1", accessorPort);
            tunnelPort.start();
            awaitRegistered(server, 2);

            // 通过本地转发端口访问，等价访问对端 Echo 服务
            String echoed = roundTrip("127.0.0.1", accessorPort, "sip-tunnel-hello\n");
            assertEquals("sip-tunnel-hello\n", echoed, "隧道回显应与发送一致");
            log.info("    访问方 {}:{} 经隧道访问对端 Echo 服务, 回显一致 ✓", "127.0.0.1", accessorPort);
            pass();
            return true;
        } catch (Exception e) {
            fail("SIP-02 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(tunnelPort::stop);
            closeQuietly(tunnelService::stop);
            closeQuietly(echo::close);
            closeQuietly(accessor::close);
            closeQuietly(provider::close);
            closeQuietly(server::stop);
        }
    }

    // ==================== 断连清理 ====================

    /**
     * 服务提供方断开后，其隧道服务被移除；访问方再打开该服务应失败而非假成功。
     */
    private boolean testDisconnectCleanup() {
        log.info("  [SIP-03] 断连清理(提供方离线 → 隧道服务移除)");
        SipServer server = null;
        SipClient provider = null;
        SipClient accessor = null;
        SipTunnelService tunnelService = null;
        try {
            int port = freePort();
            server = new SipServer(com.chua.common.support.network.sip.SipConfig.builder()
                    .host("127.0.0.1").tcpPort(port).kcpEnabled(false).build());
            server.start();

            EchoServer echo = EchoServer.start(0);
            provider = SipClient.tcp("tcp://127.0.0.1:" + port);
            tunnelService = new SipTunnelService(provider, "echo", "127.0.0.1", echo.getPort());
            tunnelService.start();
            awaitRegistered(server, 1);

            // 提供方断开，服务器应清理其隧道服务与通道
            tunnelService.stop();
            Thread.sleep(300);

            accessor = SipClient.tcp("tcp://127.0.0.1:" + port).connect();
            awaitRegistered(server, 1);

            boolean failed = false;
            try {
                accessor.openTunnel("echo", 3000);
            } catch (RuntimeException e) {
                failed = true;
            }
            assertTrue(failed, "提供方离线后打开隧道应失败(服务已移除)");
            log.info("    提供方离线后 openTunnel 抛异常 ✓");
            echo.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("SIP-03 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(tunnelService::stop);
            closeQuietly(accessor::close);
            closeQuietly(provider::close);
            closeQuietly(server::stop);
        }
    }

    // ==================== 工具 ====================

    /**
     * 等待服务器注册表达到期望客户端数。
     *
     * @param server 服务器
     * @param expect 期望客户端数
     */
    private static void awaitRegistered(SipServer server, int expect) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (server.getConnectedClients().size() >= expect) {
                return;
            }
            Thread.sleep(100);
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

    /**
     * TCP 往返：发送 msg 并读取等长回显。
     *
     * @param host 主机
     * @param port 端口
     * @param msg  消息
     * @return 回显内容
     */
    private static String roundTrip(String host, int port, String msg) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(8000);
            socket.getOutputStream().write(msg.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            byte[] buf = new byte[msg.getBytes(StandardCharsets.UTF_8).length];
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

    /** Assert判断相等 */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
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
    private static void closeQuietly(Runnable closer) {
        if (closer != null) {
            try {
                closer.run();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 简易 TCP Echo 服务（虚拟线程并发处理）。
     *
     * @since 4.0.0.42
     */
    private static final class EchoServer implements AutoCloseable {

        /**
         * 服务器 Socket
         */
        private final ServerSocket serverSocket;

        /**
         * 处理器池
         */
        private final ExecutorService handlerPool;

        /**
         * Accept 线程
         */
        private final Thread acceptThread;

        /**
         * 运行标记
         */
        private volatile boolean running = true;

        /**
         * 创建 Echo 服务。
         *
         * @param port 端口（0 表示自动分配）
         * @throws IOException 创建失败
         */
        private EchoServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            this.handlerPool = Executors.newVirtualThreadPerTaskExecutor();
            this.acceptThread = new Thread(this::acceptLoop, "sip-example-echo");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        /**
         * 创建 Echo 服务。
         *
         * @param port 端口
         * @return Echo 服务
         * @throws IOException 创建失败
         */
        static EchoServer start(int port) throws IOException {
            return new EchoServer(port);
        }

        /**
         * 获取监听端口。
         *
         * @return 端口
         */
        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * 接受连接循环。
         */
        private void acceptLoop() {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    handlerPool.submit(() -> handle(client));
                } catch (IOException e) {
                    if (running) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        /**
         * 处理单个连接：读一段写回一段。
         *
         * @param client 客户端连接
         */
        private void handle(Socket client) {
            try (InputStream in = client.getInputStream();
                 OutputStream out = client.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        /** 关闭 */
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            handlerPool.shutdownNow();
        }
    }
}

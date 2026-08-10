package com.chua.example.network.proxy;

import com.chua.common.support.network.server.proxy.TcpProxyServer;
import com.chua.common.support.network.server.ServerSetting;
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

/**
 * {@link TcpProxyServer} 综合自检（SPI 形式）。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=tcp-proxy} 调用。
 * 演示基于原生 JDK {@link ServerSocket} 的 TCP 反向代理：</p>
 * <ul>
 *     <li>固定后端（最简形式）</li>
 *     <li>自定义 {@link com.chua.common.support.network.server.proxy.ProxyTargetResolver} 动态路由</li>
 *     <li>目标解析器返回 null 时拒绝连接</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 列出全部示例
 *   java ExampleRunner --list
 *
 *   # 运行 TCP 代理自检
 *   java ExampleRunner --example=tcp-proxy
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TcpProxyExampleSpi implements Example {

    @Override
    public String name() {
        return "tcp-proxy";
    }

    @Override
    public String module() {
        return "tcp-proxy";
    }

    @Override
    public String description() {
        return "TcpProxyServer 自检（基于原生 JDK ServerSocket）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        log.info("===== tcp-proxy --test =====");
        boolean passed = true;
        passed &= testFixedBackend();
        passed &= testTargetResolver();
        passed &= testRejectWhenResolverNull();
        return passed;
    }

    /**
     * 固定后端：客户端经代理写入，回显服务原样返回。
     */
    private boolean testFixedBackend() {
        log.info("  [TC-01] TcpProxyServer 固定后端转发");
        EchoServer backend = null;
        TcpProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            proxy = new TcpProxyServer(setting,
                    new InetSocketAddress("127.0.0.1", backend.getPort()));
            proxy.start();
            int proxyPort = proxy.getPort();
            assertTrue(proxyPort > 0, "代理端口应被自动分配");

            String payload = "tcp-proxy-fixed-backend\n";
            String echoed = roundTrip("127.0.0.1", proxyPort, payload);
            assertEquals(payload, echoed, "回显内容应与发送内容一致");
            pass();
            return true;
        } catch (Exception e) {
            fail("固定后端自检异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    /**
     * 自定义目标解析器：所有客户端统一路由到 echo 后端。
     */
    private boolean testTargetResolver() {
        log.info("  [TC-02] TcpProxyServer 自定义目标解析器");
        EchoServer backend = null;
        TcpProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            InetSocketAddress target = new InetSocketAddress("127.0.0.1", backend.getPort());
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            proxy = new TcpProxyServer(setting, remote -> target);
            proxy.start();
            int proxyPort = proxy.getPort();

            String a = roundTrip("127.0.0.1", proxyPort, "A");
            String b = roundTrip("127.0.0.1", proxyPort, "BB");
            assertEquals("A", a, "第一个连接应回显 A");
            assertEquals("BB", b, "第二个连接应回显 BB");
            pass();
            return true;
        } catch (Exception e) {
            fail("目标解析器自检异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    /**
     * 目标解析器返回 null：代理应立即关闭连接，不回显任何字节。
     */
    private boolean testRejectWhenResolverNull() {
        log.info("  [TC-03] TcpProxyServer 目标解析器返回 null 拒绝连接");
        TcpProxyServer proxy = null;
        try {
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            proxy = new TcpProxyServer(setting, remote -> null);
            proxy.start();
            int proxyPort = proxy.getPort();

            try (Socket client = new Socket("127.0.0.1", proxyPort)) {
                client.setSoTimeout(2000);
                client.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();
                byte[] buf = new byte[64];
                int n = client.getInputStream().read(buf);
                assertEquals(-1, n, "目标为 null 时代理应立即关闭连接，read 返回 -1");
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("拒绝连接自检异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
        }
    }

    /**
     * 通过代理端到端写入并读取回显。
     *
     * @param host    代理主机
     * @param port    代理端口
     * @param payload 负载字符串
     * @return 回显字符串
     */
    private static String roundTrip(String host, int port, String payload) throws IOException {
        try (Socket s = new Socket(host, port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
            byte[] buf = new byte[1024];
            int n = in.read(buf);
            assertTrue(n > 0, "应至少读到 1 个字节");
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }

    // ==================== 辅助方法 ====================

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void pass() {
        log.info("  \u2713 通过");
    }

    private static void fail(String msg) {
        log.info("  \u2717 失败: {}", msg);
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 用于测试的回显 TCP 服务端。
     */
    private static final class EchoServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private volatile boolean running = true;

        private EchoServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            this.acceptThread = new Thread(this::acceptLoop, "tcp-proxy-example-echo");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        static EchoServer start(int port) throws IOException {
            return new EchoServer(port);
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    Thread t = new Thread(() -> handle(client), "tcp-proxy-example-handle");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (running) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

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
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}

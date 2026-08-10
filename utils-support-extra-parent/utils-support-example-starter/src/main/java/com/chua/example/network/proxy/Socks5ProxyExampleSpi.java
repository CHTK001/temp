package com.chua.example.network.proxy;

import com.chua.common.support.network.server.proxy.Socks5ProxyServer;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@link Socks5ProxyServer} 综合自检（SPI 形式）。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=socks5-proxy} 调用。
 * 演示基于原生 JDK {@link ServerSocket} 的 SOCKS5 代理：</p>
 * <ul>
 *     <li>IPv4 地址 CONNECT 命令</li>
 *     <li>域名 CONNECT 命令</li>
 *     <li>不支持的方法返回 0xFF</li>
 *     <li>用户名/口令认证成功</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 列出全部示例
 *   java ExampleRunner --list
 *
 *   # 运行 SOCKS5 代理自检
 *   java ExampleRunner --example=socks5-proxy
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class Socks5ProxyExampleSpi implements Example {

    @Override
    public String name() {
        return "socks5-proxy";
    }

    @Override
    public String module() {
        return "socks5-proxy";
    }

    @Override
    public String description() {
        return "Socks5ProxyServer 自检（基于原生 JDK ServerSocket，RFC 1928）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        log.info("===== socks5-proxy --test =====");
        boolean passed = true;
        passed &= testConnectIpv4();
        passed &= testConnectDomain();
        passed &= testUnsupportedMethod();
        passed &= testUserPassAuth();
        return passed;
    }

    /**
     * 测试 IPv4 CONNECT：客户端通过 SOCKS5 代理访问后端 echo 服务。
     */
    private boolean testConnectIpv4() {
        log.info("  [TC-01] SOCKS5 IPv4 CONNECT");
        EchoServer backend = null;
        Socks5ProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            int proxyPort = proxy.getPort();

            try (Socket client = new Socket("127.0.0.1", proxyPort);
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);

                out.write(new byte[]{0x05, 0x01, 0x00});
                out.flush();
                byte[] greet = readExact(in, 2);
                assertEquals((byte) 0x05, greet[0], "SOCKS 版本应为 5");
                assertEquals((byte) 0x00, greet[1], "服务端应选择 NO_AUTH");

                int backendPort = backend.getPort();
                byte[] req = new byte[]{
                        0x05, 0x01, 0x00, 0x01,
                        (byte) 127, (byte) 0, (byte) 0, (byte) 1,
                        (byte) ((backendPort >> 8) & 0xFF), (byte) (backendPort & 0xFF)
                };
                out.write(req);
                out.flush();

                byte[] reply = readExact(in, 10);
                assertEquals((byte) 0x05, reply[0], "SOCKS 版本应为 5");
                assertEquals((byte) 0x00, reply[1], "CONNECT 应成功");

                String payload = "socks5-ipv4\n";
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
                byte[] buf = new byte[64];
                int n = in.read(buf);
                assertTrue(n > 0, "应读到回显字节");
                String echoed = new String(buf, 0, n, StandardCharsets.UTF_8);
                assertEquals(payload, echoed, "回显内容应与发送一致");
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("IPv4 CONNECT 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    /**
     * 测试域名 CONNECT。
     */
    private boolean testConnectDomain() {
        log.info("  [TC-02] SOCKS5 域名 CONNECT");
        EchoServer backend = null;
        Socks5ProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            int proxyPort = proxy.getPort();

            try (Socket client = new Socket("127.0.0.1", proxyPort);
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);

                out.write(new byte[]{0x05, 0x01, 0x00});
                out.flush();
                byte[] greet = readExact(in, 2);
                assertEquals((byte) 0x00, greet[1], "服务端应选择 NO_AUTH");

                String domain = "127.0.0.1";
                int backendPort = backend.getPort();
                byte[] domainBytes = domain.getBytes(StandardCharsets.UTF_8);
                byte[] req = new byte[]{0x05, 0x01, 0x00, 0x03, (byte) domainBytes.length};
                out.write(req);
                out.write(domainBytes);
                out.write(new byte[]{(byte) ((backendPort >> 8) & 0xFF), (byte) (backendPort & 0xFF)});
                out.flush();

                byte[] reply = readExact(in, 10);
                assertEquals((byte) 0x00, reply[1], "域名 CONNECT 应成功");

                out.write("ping".getBytes(StandardCharsets.UTF_8));
                out.flush();
                byte[] buf = new byte[4];
                int n = in.read(buf);
                assertEquals(4, n, "应读到 4 字节回显");
                assertEquals("ping", new String(buf, StandardCharsets.UTF_8), "回显内容应一致");
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("域名 CONNECT 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    /**
     * 测试不识别的认证方法：服务端返回 0xFF。
     */
    private boolean testUnsupportedMethod() {
        log.info("  [TC-03] SOCKS5 不支持的认证方法");
        Socks5ProxyServer proxy = null;
        try {
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            int proxyPort = proxy.getPort();

            try (Socket client = new Socket("127.0.0.1", proxyPort);
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);
                out.write(new byte[]{0x05, 0x01, (byte) 0x80});
                out.flush();
                byte[] reply = readExact(in, 2);
                assertEquals((byte) 0xFF, reply[1], "不支持的方法应返回 0xFF");
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("不支持方法自检异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
        }
    }

    /**
     * 测试用户名/口令认证成功。
     */
    private boolean testUserPassAuth() {
        log.info("  [TC-04] SOCKS5 用户名/口令认证");
        Socks5ProxyServer proxy = null;
        try {
            proxy = new Socks5ProxyServer(ServerSetting.defaults(), "alice", "secret");
            proxy.start();
            int proxyPort = proxy.getPort();

            try (Socket client = new Socket("127.0.0.1", proxyPort);
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);
                out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
                out.flush();
                byte[] methodReply = readExact(in, 2);
                assertEquals((byte) 0x02, methodReply[1], "服务端应选择 USER_PASS");

                byte[] user = "alice".getBytes(StandardCharsets.UTF_8);
                byte[] pass = "secret".getBytes(StandardCharsets.UTF_8);
                out.write(new byte[]{0x01, (byte) user.length});
                out.write(user);
                out.write(new byte[]{(byte) pass.length});
                out.write(pass);
                out.flush();

                byte[] authReply = readExact(in, 2);
                assertEquals((byte) 0x00, authReply[1], "认证应成功");
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("用户名/口令认证异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
        }
    }

    // ==================== 辅助方法 ====================

    private static byte[] readExact(InputStream in, int count) throws IOException {
        byte[] buf = new byte[count];
        int off = 0;
        while (off < count) {
            int n = in.read(buf, off, count - off);
            if (n == -1) {
                throw new IOException("连接提前关闭");
            }
            off += n;
        }
        return buf;
    }

    private static void assertEquals(byte expected, byte actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + String.format("%02X", expected)
                    + "，实际 " + String.format("%02X", actual));
        }
    }

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
            this.acceptThread = new Thread(this::acceptLoop, "socks5-example-echo");
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
                    Thread t = new Thread(() -> handle(client), "socks5-example-handle");
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

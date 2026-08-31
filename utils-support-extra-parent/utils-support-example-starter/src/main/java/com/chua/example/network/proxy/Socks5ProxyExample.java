package com.chua.example.network.proxy;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.proxy.Socks5ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import com.chua.example.util.ExampleUtils;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link Socks5ProxyExampleSpi} 的同名独立主示例（驱动型）。
 *
 * <p>起本地 SOCKS5 服务与回显后端，用原生 {@link Socket} 直连发送握手字节做最小协议校验：
 * NO_AUTH 下 IPv4/域名 CONNECT 转发回显、不支持的认证方法拒绝（0xFF）、
 * 用户名/口令认证（RFC 1929 子协商）。复杂协商与压测不在本示例执行：
 * [SKIP] perf/sweep 场景请经 {@code ExampleRunner --example=socks5-proxy --mode=perf|sweep}
 * 触发 {@link Socks5ProxyExampleSpi}。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ... Socks5ProxyExample
 *   java ... Socks5ProxyExample --backend-port=28087
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class Socks5ProxyExample {

    /** 私有构造，防止实例化 */
    private Socks5ProxyExample() { }

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(Socks5ProxyExample.class);

    /** 本地回显后端默认端口（28xxx，可经 --backend-port 调整；代理监听端口自动分配） */
    private static final int DEFAULT_BACKEND_PORT = 28087;

    /**
     * 独立入口：逐场景校验 SOCKS5 握手与转发，全部通过打印 [PASS]，否则打印 [FAIL] 并以退出码 1 结束。
     *
     * @param args 命令行参数：--backend-port=xxxx（默认 28087）
     * @throws Exception 后端启动失败等异常
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> arg = ExampleUtils.parseArgs(args);
        EchoServer backend = EchoServer.start(intVal(arg.get("backend-port"), DEFAULT_BACKEND_PORT));
        LOG.info("===== socks5-proxy 主示例 [echo-backend=127.0.0.1:{}] =====", backend.getPort());
        boolean passed;
        try {
            passed = testSpiSwitch();
            passed &= testConnectIpv4(backend);
            passed &= testConnectDomain(backend);
            passed &= testUnsupportedMethod();
            passed &= testUserPassAuth();
        } finally {
            backend.close();
        }
        log.info(passed ? "[PASS] socks5-proxy" : "[FAIL] socks5-proxy");
        if (!passed) {
            System.exit(1);
        }
    }

    /**
     * SOCKS-01：ServerBuilder.type("socks5-proxy") 应经 SPI 加载到 Socks5ProxyServer。
     *
     * @return 断言通过返回 true
     */
    private static boolean testSpiSwitch() {
        LOG.info("  [SOCKS-01] SPI 切换 socks5-proxy");
        try (Server proxy = ServerBuilder.create().type("socks5-proxy").host("127.0.0.1").port(0).build()) {
            assertTrue(proxy instanceof Socks5ProxyServer,
                    "实际类型应为 Socks5ProxyServer，实际: " + proxy.getClass().getName());
            LOG.info("    SPI 加载实现: {} ✓", proxy.getClass().getName());
            return true;
        } catch (Exception e) {
            return ExampleUtils.fail("SPI 切换异常: " + e.getMessage());
        }
    }

    /**
     * SOCKS-02：NO_AUTH + IPv4 CONNECT，经代理向后端回显。
     *
     * @param backend 回显后端
     * @return 断言通过返回 true
     */
    private static boolean testConnectIpv4(EchoServer backend) {
        LOG.info("  [SOCKS-02] IPv4 CONNECT");
        Socks5ProxyServer proxy;
        try {
            proxy = startProxy(false);
        } catch (Exception e) {
            return ExampleUtils.fail("IPv4 场景代理启动异常: " + e.getMessage());
        }
        try (Socket client = new Socket("127.0.0.1", proxy.getPort())) {
            client.setSoTimeout(5000);
            greet(client, new byte[]{0x00}, 0x00);
            connectIpv4(client, backend.getPort());
            exchange(client, "socks5-ipv4\n");
            LOG.info("    CONNECT 成功且经代理回显一致 ✓");
            return true;
        } catch (Exception e) {
            return ExampleUtils.fail("IPv4 CONNECT 异常: " + e.getMessage());
        } finally {
            closeQuietly(proxy);
        }
    }

    /**
     * SOCKS-03：域名寻址 CONNECT（ATYP=0x03），经代理向后端回显。
     *
     * @param backend 回显后端
     * @return 断言通过返回 true
     */
    private static boolean testConnectDomain(EchoServer backend) {
        LOG.info("  [SOCKS-03] 域名 CONNECT");
        Socks5ProxyServer proxy;
        try {
            proxy = startProxy(false);
        } catch (Exception e) {
            return ExampleUtils.fail("域名场景代理启动异常: " + e.getMessage());
        }
        try (Socket client = new Socket("127.0.0.1", proxy.getPort())) {
            client.setSoTimeout(5000);
            greet(client, new byte[]{0x00}, 0x00);
            OutputStream out = client.getOutputStream();
            byte[] domain = "127.0.0.1".getBytes(StandardCharsets.UTF_8);
            out.write(new byte[]{0x05, 0x01, 0x00, 0x03, (byte) domain.length});
            out.write(domain);
            out.write(new byte[]{(byte) ((backend.getPort() >> 8) & 0xFF), (byte) (backend.getPort() & 0xFF)});
            out.flush();
            byte[] reply = readExact(client.getInputStream(), 10);
            assertEquals(0x00, reply[1] & 0xFF, "域名 CONNECT 应成功");
            exchange(client, "ping");
            LOG.info("    域名 CONNECT 成功且经代理回显一致 ✓");
            return true;
        } catch (Exception e) {
            return ExampleUtils.fail("域名 CONNECT 异常: " + e.getMessage());
        } finally {
            closeQuietly(proxy);
        }
    }

    /**
     * SOCKS-04：客户端仅提供不支持的认证方法时，服务端应返回 0xFF。
     *
     * @return 断言通过返回 true
     */
    private static boolean testUnsupportedMethod() {
        LOG.info("  [SOCKS-04] 不支持的认证方法 → 0xFF");
        Socks5ProxyServer proxy;
        try {
            proxy = startProxy(false);
        } catch (Exception e) {
            return ExampleUtils.fail("不支持方法场景代理启动异常: " + e.getMessage());
        }
        try (Socket client = new Socket("127.0.0.1", proxy.getPort())) {
            client.setSoTimeout(5000);
            greet(client, new byte[]{(byte) 0x80}, 0xFF);
            return true;
        } catch (Exception e) {
            return ExampleUtils.fail("不支持方法异常: " + e.getMessage());
        } finally {
            closeQuietly(proxy);
        }
    }

    /**
     * SOCKS-05：服务端选择 USER_PASS 后完成 RFC 1929 用户名/口令子协商。
     *
     * @return 断言通过返回 true
     */
    private static boolean testUserPassAuth() {
        LOG.info("  [SOCKS-05] 用户名/口令认证 alice/secret");
        Socks5ProxyServer proxy;
        try {
            proxy = startProxy(true);
        } catch (Exception e) {
            return ExampleUtils.fail("认证场景代理启动异常: " + e.getMessage());
        }
        try (Socket client = new Socket("127.0.0.1", proxy.getPort())) {
            client.setSoTimeout(5000);
            greet(client, new byte[]{0x00, 0x02}, 0x02);
            OutputStream out = client.getOutputStream();
            byte[] u = "alice".getBytes(StandardCharsets.UTF_8);
            byte[] p = "secret".getBytes(StandardCharsets.UTF_8);
            out.write(new byte[]{0x01, (byte) u.length});
            out.write(u);
            out.write(new byte[]{(byte) p.length});
            out.write(p);
            out.flush();
            byte[] authReply = readExact(client.getInputStream(), 2);
            assertEquals(0x01, authReply[0] & 0xFF, "子协商版本应为 0x01");
            assertEquals(0x00, authReply[1] & 0xFF, "认证应成功");
            LOG.info("    服务端选择 USER_PASS 且认证成功 ✓");
            return true;
        } catch (Exception e) {
            return ExampleUtils.fail("用户名/口令认证异常: " + e.getMessage());
        } finally {
            closeQuietly(proxy);
        }
    }

    /**
     * 启动监听自动分配端口的本地 SOCKS5 服务。
     *
     * @param auth true 时启用 alice/secret 用户名口令认证
     * @return 已启动的代理实例
     * @throws Exception 启动异常
     */
    private static Socks5ProxyServer startProxy(boolean auth) throws Exception {
        Socks5ProxyServer proxy = auth
                ? new Socks5ProxyServer(ServerSetting.defaults(), "alice", "secret")
                : new Socks5ProxyServer(ServerSetting.defaults());
        proxy.start();
        return proxy;
    }

    /**
     * 发送方法协商字节并断言服务端选定方法。
     *
     * @param client       与代理的连接
     * @param methods      客户端支持的方法列表
     * @param expectMethod 期望服务端选定的方法（0x00 NO_AUTH / 0x02 USER_PASS / 0xFF 不支持）
     * @throws IOException 读写异常或断言失败
     */
    private static void greet(Socket client, byte[] methods, int expectMethod) throws IOException {
        OutputStream out = client.getOutputStream();
        out.write(new byte[]{0x05, (byte) methods.length});
        out.write(methods);
        out.flush();
        byte[] reply = readExact(client.getInputStream(), 2);
        assertEquals(0x05, reply[0] & 0xFF, "SOCKS 版本应为 5");
        assertEquals(expectMethod, reply[1] & 0xFF, "选定认证方法");
    }

    /**
     * 发送 IPv4 CONNECT 请求字节并断言连接成功。
     *
     * @param client 与代理的连接
     * @param port   目标后端端口
     * @throws IOException 读写异常或断言失败
     */
    private static void connectIpv4(Socket client, int port) throws IOException {
        OutputStream out = client.getOutputStream();
        out.write(new byte[]{0x05, 0x01, 0x00, 0x01, 0x7F, 0x00, 0x00, 0x01,
                (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF)});
        out.flush();
        byte[] reply = readExact(client.getInputStream(), 10);
        assertEquals(0x00, reply[1] & 0xFF, "CONNECT 应成功");
    }

    /**
     * 经代理隧道向回显后端写入载荷并断言原样返回。
     *
     * @param client  与代理的连接
     * @param payload 发送载荷
     * @throws IOException 读写异常或断言失败
     */
    private static void exchange(Socket client, String payload) throws IOException {
        byte[] sent = payload.getBytes(StandardCharsets.UTF_8);
        OutputStream out = client.getOutputStream();
        out.write(sent);
        out.flush();
        assertEquals(payload,
                new String(readExact(client.getInputStream(), sent.length), StandardCharsets.UTF_8),
                "代理转发回显");
    }

    /**
     * 精确读取指定字节数，流提前关闭视为协议错误。
     *
     * @param in    输入流
     * @param count 期望字节数
     * @return 读到的字节
     * @throws IOException 流提前关闭或读取异常
     */
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

    /**
     * 断言相等。
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param msg      失败描述
     */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /**
     * 断言为真。
     *
     * @param cond 条件
     * @param msg  失败描述
     */
    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    /**
     * 静默关闭可关闭资源。
     *
     * @param c 可关闭资源，可为 null
     */
    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }
    }

    /**
     * 解析整型参数，缺失或非法时返回默认值。
     *
     * @param value  字符串值
     * @param defVal 默认值
     * @return 整数值
     */
    private static int intVal(String value, int defVal) {
        if (value == null || value.isEmpty()) {
            return defVal;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    /**
     * 最小回显后端：虚拟线程 accept，每连接原样回写收到的字节。
     */
    private static final class EchoServer implements AutoCloseable {

        /** 监听套接字 */
        private final ServerSocket serverSocket;
        /** running */
        private volatile boolean running = true;

        /**
         * 创建并开始监听指定端口。
         *
         * @param port 监听端口
         * @throws IOException 绑定失败
         */
        private EchoServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            Thread.ofVirtual().start(this::acceptLoop);
        }

        /**
         * 在指定端口启动回显后端。
         *
         * @param port 监听端口
         * @return 后端实例
         * @throws IOException 绑定失败
         */
        static EchoServer start(int port) throws IOException {
            return new EchoServer(port);
        }

        /**
         * 获取实际监听端口。
         *
         * @return 端口
         */
        int getPort() {
            return serverSocket.getLocalPort();
        }

        /** AcceptLoop */
        private void acceptLoop() {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    Thread.ofVirtual().start(() -> echo(client));
                } catch (IOException ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
                }
            }
        }

        /**
         * 回写收到的全部字节直至对端关闭。
         *
         * @param client 客户端连接
         */
        private void echo(Socket client) {
            try (client) {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }
    }
}

package com.chua.example.network.proxy;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.proxy.Socks5ProxyServer;
import com.chua.example.network.perf.PerfReport;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Socks5ProxyServer 自检 + 性能基准（SPI 形式）。
 *
 * <p>通过 {@code ExampleRunner --example=socks5-proxy} 调用。
 * SOCKS5 代理：基于 RFC 1928 协议实现 CONNECT。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=socks5-proxy
 *   java ExampleRunner --example=socks5-proxy --mode=perf
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class Socks5ProxyExampleSpi implements Example {

    /** Default_concurrency */
    private static final int DEFAULT_CONCURRENCY = 32;
    /** Default_requests_per_conn */
    private static final int DEFAULT_REQUESTS_PER_CONN = 300;
    /** Default_connections */
    private static final int DEFAULT_CONNECTIONS = 32;
    /** Default_payload_size */
    private static final int DEFAULT_PAYLOAD_SIZE = 64;

    /** Sweep_concurrency */
    private static final int[] SWEEP_CONCURRENCY = {1, 4, 16, 64, 128, 256, 512, 1000, 2000};
    /** Sweep_requests_per_conn */
    private static final int SWEEP_REQUESTS_PER_CONN = 500;
    /** Sweep_connections */
    private static final int SWEEP_CONNECTIONS = 256;
    /** Sweep_payload */
    private static final int SWEEP_PAYLOAD = 64;

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
        return "Socks5ProxyServer 自检 + SPI 切换 + 性能基准";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== socks5-proxy --test [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "spi".equals(mode)) {
            passed &= testSpiSwitch();
        }
        if ("all".equals(mode) || "func".equals(mode)) {
            passed &= testConnectIpv4();
            passed &= testConnectDomain();
            passed &= testUnsupportedMethod();
            passed &= testUserPassAuth();
        }
        if ("all".equals(mode) || "perf".equals(mode)) {
            int concurrency = Integer.parseInt(args.getOrDefault("concurrency", String.valueOf(DEFAULT_CONCURRENCY)));
            int requestsPerConn = Integer.parseInt(args.getOrDefault("requests", String.valueOf(DEFAULT_REQUESTS_PER_CONN)));
            int connections = Integer.parseInt(args.getOrDefault("connections", String.valueOf(DEFAULT_CONNECTIONS)));
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(DEFAULT_PAYLOAD_SIZE)));
            passed &= runPerf(concurrency, connections, requestsPerConn, payloadSize);
        }
        if ("sweep".equals(mode)) {
            int payloadSize = Integer.parseInt(args.getOrDefault("payload", String.valueOf(SWEEP_PAYLOAD)));
            passed &= runSweep(payloadSize);
        }
        return passed;
    }

    // ==================== SPI ====================

    private boolean testSpiSwitch() {
        log.info("  [SPI-01] ServerBuilder.type(\"socks5-proxy\") 切换 SPI");
        Server proxy = null;
        try {
            proxy = ServerBuilder.create().type("socks5-proxy").host("127.0.0.1").port(0).build();
            assertTrue(proxy != null, "应通过 SPI 加载到 Socks5ProxyServer");
            assertTrue(proxy instanceof Socks5ProxyServer, "实际类型应为 Socks5ProxyServer，实际: " + proxy.getClass().getName());
            log.info("    SPI 加载实现: {}", proxy.getClass().getName());
            pass();
            return true;
        } catch (Exception e) {
            fail("SPI 切换异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
        }
    }

    // ==================== 功能 ====================

    private boolean testConnectIpv4() {
        log.info("  [FUNC-01] SOCKS5 IPv4 CONNECT");
        EchoServer backend = null;
        Socks5ProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            try (Socket client = new Socket("127.0.0.1", proxy.getPort());
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);
                out.write(new byte[]{0x05, 0x01, 0x00});
                out.flush();
                byte[] greet = readExact(in, 2);
                assertEquals((byte) 0x05, greet[0], "SOCKS 版本");
                assertEquals((byte) 0x00, greet[1], "NO_AUTH");
                int bp = backend.getPort();
                byte[] req = new byte[]{0x05, 0x01, 0x00, 0x01,
                        (byte) 127, (byte) 0, (byte) 0, (byte) 1,
                        (byte) ((bp >> 8) & 0xFF), (byte) (bp & 0xFF)};
                out.write(req);
                out.flush();
                byte[] reply = readExact(in, 10);
                assertEquals((byte) 0x00, reply[1], "CONNECT 成功");
                out.write("socks5-ipv4\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                byte[] buf = new byte[64];
                int n = in.read(buf);
                assertEquals("socks5-ipv4\n", new String(buf, 0, n, StandardCharsets.UTF_8), "回显");
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

    private boolean testConnectDomain() {
        log.info("  [FUNC-02] SOCKS5 域名 CONNECT");
        EchoServer backend = null;
        Socks5ProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            try (Socket client = new Socket("127.0.0.1", proxy.getPort());
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);
                out.write(new byte[]{0x05, 0x01, 0x00});
                out.flush();
                byte[] greet = readExact(in, 2);
                assertEquals((byte) 0x00, greet[1], "NO_AUTH");
                String domain = "127.0.0.1";
                int bp = backend.getPort();
                byte[] db = domain.getBytes(StandardCharsets.UTF_8);
                out.write(new byte[]{0x05, 0x01, 0x00, 0x03, (byte) db.length});
                out.write(db);
                out.write(new byte[]{(byte) ((bp >> 8) & 0xFF), (byte) (bp & 0xFF)});
                out.flush();
                byte[] reply = readExact(in, 10);
                assertEquals((byte) 0x00, reply[1], "域名 CONNECT 成功");
                out.write("ping".getBytes(StandardCharsets.UTF_8));
                out.flush();
                byte[] buf = new byte[4];
                int n = in.read(buf);
                assertEquals("ping", new String(buf, StandardCharsets.UTF_8), "回显");
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

    private boolean testUnsupportedMethod() {
        log.info("  [FUNC-03] SOCKS5 不支持的认证方法");
        Socks5ProxyServer proxy = null;
        try {
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            try (Socket client = new Socket("127.0.0.1", proxy.getPort());
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
            fail("不支持方法异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
        }
    }

    private boolean testUserPassAuth() {
        log.info("  [FUNC-04] SOCKS5 用户名/口令认证");
        Socks5ProxyServer proxy = null;
        try {
            proxy = new Socks5ProxyServer(ServerSetting.defaults(), "alice", "secret");
            proxy.start();
            try (Socket client = new Socket("127.0.0.1", proxy.getPort());
                 OutputStream out = client.getOutputStream();
                 InputStream in = client.getInputStream()) {
                client.setSoTimeout(5000);
                out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
                out.flush();
                byte[] methodReply = readExact(in, 2);
                assertEquals((byte) 0x02, methodReply[1], "USER_PASS");
                byte[] u = "alice".getBytes(StandardCharsets.UTF_8);
                byte[] p = "secret".getBytes(StandardCharsets.UTF_8);
                out.write(new byte[]{0x01, (byte) u.length});
                out.write(u);
                out.write(new byte[]{(byte) p.length});
                out.write(p);
                out.flush();
                byte[] authReply = readExact(in, 2);
                assertEquals((byte) 0x00, authReply[1], "认证成功");
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

    // ==================== 性能 ====================

    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReport.printEnvironment("Socks5ProxyServer", "socks5-proxy", "static-resolver (固定后端)");
        log.info("  │ 代理路径 : client -> Socks5ProxyServer(virtual-thread) -> EchoServer");
        EchoServer backend = null;
        Socks5ProxyServer proxy = null;
        ExecutorService pool = null;
        try {
            backend = EchoServer.start(0);
            proxy = new Socks5ProxyServer(ServerSetting.defaults());
            proxy.start();
            PerfReport.SweepRow row = runPerfInner(concurrency, connections, requestsPerConn, payloadSize, proxy, backend.getPort());
            if (row == null) {
                return false;
            }
            PerfReport.printResult("socks5-proxy 64B echo 压力", row.concurrency, row.connections, row.requestsPerConn,
                    payloadSize, row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, 0L);
            pass();
            return true;
        } catch (Exception e) {
            fail("PERF 异常: " + e.getMessage());
            return false;
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    private boolean runSweep(int payloadSize) {
        PerfReport.printEnvironment("Socks5ProxyServer [sweep]", "socks5-proxy", "static-resolver (固定后端)");
        log.info("  │ 代理路径 : client -> Socks5ProxyServer(virtual-thread) -> EchoServer");
        EchoServer backend = null;
        Socks5ProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            ServerSetting setting = ServerSetting.defaults();
            setting.setSoReuseAddr(true);
            proxy = new Socks5ProxyServer(setting);
            proxy.start();

            List<PerfReport.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReport.SweepRow row = runPerfInner(cc, conn, req, payloadSize, proxy, backend.getPort());
                if (row != null) {
                    rows.add(row);
                }
            }
            PerfReport.printSweepResult("socks5-proxy 64B echo 扫档 (按并发比例分配连接 / 500 请求每连接 / 并发扫描)", payloadSize, rows);
            return !rows.isEmpty();
        } catch (Exception e) {
            log.error("SWEEP 异常: {}", e.getMessage(), e);
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    private PerfReport.SweepRow runPerfInner(int concurrency, int connections, int requestsPerConn, int payloadSize,
                                              Socks5ProxyServer proxy, int backendPort) {
        ExecutorService pool = null;
        try {
            int proxyPort = proxy.getPort();
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');

            pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch ready = new CountDownLatch(connections);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(connections);
            LongAdder errors = new LongAdder();
            List<long[]> latencies = Collections.synchronizedList(new ArrayList<>(connections));

            for (int i = 0; i < connections; i++) {
                pool.submit(() -> {
                    try (Socket client = new Socket("127.0.0.1", proxyPort)) {
                        client.setSoTimeout(10000);
                        client.setTcpNoDelay(true);
                        OutputStream out = client.getOutputStream();
                        InputStream in = client.getInputStream();
                        out.write(new byte[]{0x05, 0x01, 0x00});
                        out.flush();
                        byte[] greet = readExact(in, 2);
                        if (greet[1] != 0x00) {
                            errors.increment();
                            return;
                        }
                        byte[] req = new byte[]{0x05, 0x01, 0x00, 0x01,
                                (byte) 127, (byte) 0, (byte) 0, (byte) 1,
                                (byte) ((backendPort >> 8) & 0xFF), (byte) (backendPort & 0xFF)};
                        out.write(req);
                        out.flush();
                        byte[] reply = readExact(in, 10);
                        if (reply[1] != 0x00) {
                            errors.increment();
                            return;
                        }
                        ready.countDown();
                        start.await();
                        long[] mine = new long[requestsPerConn];
                        byte[] buf = new byte[payloadSize];
                        for (int k = 0; k < requestsPerConn; k++) {
                            long s = System.nanoTime();
                            out.write(payload);
                            out.flush();
                            int read = 0;
                            while (read < payloadSize) {
                                int n = in.read(buf, read, payloadSize - read);
                                if (n == -1) {
                                    errors.increment();
                                    return;
                                }
                                read += n;
                            }
                            mine[k] = System.nanoTime() - s;
                        }
                        latencies.add(mine);
                    } catch (Exception e) {
                        errors.increment();
                    } finally {
                        done.countDown();
                    }
                });
            }

            if (!ready.await(30, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} CONNECT 阶段超时", concurrency);
                return null;
            }
            Thread.sleep(50);
            long startWall = System.nanoTime();
            start.countDown();
            if (!done.await(120, TimeUnit.SECONDS)) {
                log.warn("  │ 并发={} 完成超时", concurrency);
                return null;
            }
            long elapsedNs = System.nanoTime() - startWall;

            long[] all = PerfReport.mergeLatencies(latencies);
            Arrays.sort(all);
            long total = (long) connections * requestsPerConn;
            long elapsedMs = elapsedNs / 1_000_000L;
            return new PerfReport.SweepRow(concurrency, connections, requestsPerConn, total, errors.sum(), elapsedMs, all);
        } catch (Exception e) {
            log.warn("  │ 并发={} 异常: {}", concurrency, e.getMessage());
            return null;
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    // ==================== 辅助 ====================

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

    private static final class EchoServer implements AutoCloseable {
        /** 服务器Socket */
        private final ServerSocket serverSocket;
        /** 处理器池 */
        private final java.util.concurrent.ExecutorService handlerPool;
        /** Accept线程 */
        private final Thread acceptThread;
        /** running */
        private volatile boolean running = true;

        private EchoServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            this.handlerPool = Executors.newVirtualThreadPerTaskExecutor();
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
                    handlerPool.submit(() -> handle(client));
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
            handlerPool.shutdownNow();
        }
    }
}

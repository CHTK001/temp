package com.chua.example.network.proxy;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.proxy.TcpProxyServer;
import com.chua.example.network.perf.PerfReportExample;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
 * TcpProxyServer 自检 + 性能基准（SPI 形式）。
 *
 * <p>通过 {@code ExampleRunner --example=tcp-proxy} 调用。
 * TCP 代理：客户端连接代理，代理解析目标后端建立连接并双向桥接。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=tcp-proxy
 *   java ExampleRunner --example=tcp-proxy --mode=perf
 *   java ExampleRunner --example=tcp-proxy --mode=perf --concurrency=128 --connections=64 --requests=500
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TcpProxyExampleSpi implements Example {

    /** Default_concurrency */
    private static final int DEFAULT_CONCURRENCY = 64;
    /** Default_requests_per_conn */
    private static final int DEFAULT_REQUESTS_PER_CONN = 500;
    /** Default_connections */
    private static final int DEFAULT_CONNECTIONS = 64;
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
    /** Name */
    public String name() {
        return "tcp-proxy";
    }

    @Override
    /** Module */
    public String module() {
        return "tcp-proxy";
    }

    @Override
    /** Description */
    public String description() {
        return "TcpProxyServer 自检 + SPI 切换 + 性能基准";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String mode = args.getOrDefault("mode", "all");
        log.info("===== tcp-proxy --test [mode={}] =====", mode);
        boolean passed = true;
        if ("all".equals(mode) || "spi".equals(mode)) {
            passed &= testSpiSwitch();
        }
        if ("all".equals(mode) || "func".equals(mode)) {
            passed &= testFixedBackend();
            passed &= testTargetResolver();
            passed &= testRejectWhenResolverNull();
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

    /** TestSpiSwitch */
    private boolean testSpiSwitch() {
        log.info("  [SPI-01] ServerBuilder.type(\"tcp-proxy\") 切换 SPI");
        Server proxy = null;
        try {
            proxy = ServerBuilder.create().type("tcp-proxy").host("127.0.0.1").port(0).build();
            assertTrue(proxy != null, "应通过 SPI 加载到 TcpProxyServer");
            log.info("    SPI 实际加载类型: {}", proxy.getClass().getName());
            log.info("    SPI 协议: {}", proxy.getProtocolType());
            assertTrue(proxy instanceof TcpProxyServer, "实际类型应为 TcpProxyServer，实际: " + proxy.getClass().getName());
            assertEquals("tcp", proxy.getProtocol(), "协议类型应为 tcp");
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

    /** TestFixedBackend */
    private boolean testFixedBackend() {
        log.info("  [FUNC-01] 固定后端转发");
        EchoServer backend = null;
        TcpProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setProtocol("tcp-proxy");
            proxy = new TcpProxyServer(setting, new InetSocketAddress("127.0.0.1", backend.getPort()));
            proxy.start();
            String echoed = roundTrip("127.0.0.1", proxy.getPort(), "tcp-proxy-fixed-backend\n");
            assertEquals("tcp-proxy-fixed-backend\n", echoed, "回显内容应与发送内容一致");
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

    /** TestTargetResolver */
    private boolean testTargetResolver() {
        log.info("  [FUNC-02] 自定义目标解析器");
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
            String a = roundTrip("127.0.0.1", proxy.getPort(), "A");
            String b = roundTrip("127.0.0.1", proxy.getPort(), "BB");
            assertEquals("A", a, "第一个连接应回显 A");
            assertEquals("BB", b, "第二个连接应回显 BB");
            pass();
            return true;
        } catch (Exception e) {
            fail("目标解析器异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    /** TestRejectWhenResolverNull */
    private boolean testRejectWhenResolverNull() {
        log.info("  [FUNC-03] 目标解析器返回 null 拒绝连接");
        TcpProxyServer proxy = null;
        try {
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            proxy = new TcpProxyServer(setting, remote -> null);
            proxy.start();
            try (Socket client = new Socket("127.0.0.1", proxy.getPort())) {
                client.setSoTimeout(2000);
                client.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();
                byte[] buf = new byte[64];
                int n = client.getInputStream().read(buf);
                assertEquals(-1, n, "目标为 null 时代理应立即关闭连接");
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("拒绝连接异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
        }
    }

    // ==================== 性能 ====================

    /** 运行Perf */
    private boolean runPerf(int concurrency, int connections, int requestsPerConn, int payloadSize) {
        PerfReportExample.printEnvironment("TcpProxyServer", "tcp-proxy", "static-resolver (固定后端)");
        log.info("  │ 代理路径 : client -> TcpProxyServer(virtual-thread) -> EchoServer");
        EchoServer backend = null;
        TcpProxyServer proxy = null;
        ExecutorService pool = null;
        try {
            PerfReportExample.SweepRow row = runPerfOnce(concurrency, connections, requestsPerConn, payloadSize, /* printFull = */ true);
            return row != null;
        } catch (Exception e) {
            fail("PERF 异常: " + e.getMessage());
            return false;
        }
    }

    /** 运行Sweep */
    private boolean runSweep(int payloadSize) {
        PerfReportExample.printEnvironment("TcpProxyServer [sweep]", "tcp-proxy", "static-resolver (固定后端)");
        log.info("  │ 代理路径 : client -> TcpProxyServer(virtual-thread) -> EchoServer");
        EchoServer backend = null;
        TcpProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setProtocol("tcp-proxy");
            proxy = new TcpProxyServer(setting, new InetSocketAddress("127.0.0.1", backend.getPort()));
            proxy.start();

            List<PerfReportExample.SweepRow> rows = new ArrayList<>();
            for (int cc : SWEEP_CONCURRENCY) {
                int conn = Math.min(SWEEP_CONNECTIONS, Math.max(1, cc / 8));
                int req = SWEEP_REQUESTS_PER_CONN;
                PerfReportExample.SweepRow row = runSweepOnce(cc, conn, req, payloadSize, proxy);
                if (row != null) {
                    rows.add(row);
                }
            }
            PerfReportExample.printSweepResult("tcp-proxy 64B echo 扫档 (按并发比例分配连接 / 500 请求每连接 / 并发扫描)", payloadSize, rows);
            return !rows.isEmpty();
        } catch (Exception e) {
            fail("SWEEP 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    /**
     * 单档压测：使用全局 server（sweep 模式复用同一个 proxy + backend）。
     */
    private PerfReportExample.SweepRow runSweepOnce(int concurrency, int connections, int requestsPerConn, int payloadSize, TcpProxyServer proxy) {
        ExecutorService pool = null;
        try {
            int proxyPort = proxy.getPort();
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');

            // 客户端使用 virtual-thread，避免平台线程调度在小并发时延迟
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
                log.warn("  │ 并发={} 就绪超时", concurrency);
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

            long[] all = PerfReportExample.mergeLatencies(latencies);
            Arrays.sort(all);
            long total = (long) connections * requestsPerConn;
            long elapsedMs = elapsedNs / 1_000_000L;
            return new PerfReportExample.SweepRow(concurrency, connections, requestsPerConn, total, errors.sum(), elapsedMs, all);
        } catch (Exception e) {
            log.warn("  │ 并发={} 异常: {}", concurrency, e.getMessage());
            return null;
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
        }
    }

    /**
     * 单档压测（独立 server 生命周期），可独立打印完整报告。
     */
    private PerfReportExample.SweepRow runPerfOnce(int concurrency, int connections, int requestsPerConn, int payloadSize, boolean printFull) {
        EchoServer backend = null;
        TcpProxyServer proxy = null;
        ExecutorService pool = null;
        try {
            backend = EchoServer.start(0);
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setProtocol("tcp-proxy");
            proxy = new TcpProxyServer(setting, new InetSocketAddress("127.0.0.1", backend.getPort()));
            proxy.start();
            int proxyPort = proxy.getPort();
            assertTrue(proxyPort > 0, "代理端口应被自动分配");

            PerfReportExample.SweepRow row = runSweepOnce(concurrency, connections, requestsPerConn, payloadSize, proxy);
            if (row == null) {
                return null;
            }
            if (printFull) {
                long startupMs = 0;
                PerfReportExample.printResult("tcp-proxy 64B echo 压力", row.concurrency, row.connections, row.requestsPerConn,
                        payloadSize, row.total, row.errors, row.elapsedMs, row.sortedLatencyNs, startupMs);
                pass();
            }
            return row;
        } catch (Exception e) {
            fail("PERF 异常: " + e.getMessage());
            return null;
        } finally {
            if (pool != null) {
                pool.shutdownNow();
            }
            closeQuietly(proxy);
            closeQuietly(backend);
        }
    }

    // ==================== 辅助 ====================

    /** RoundTrip */
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

    /** Assert判断相等 */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /** Assert判断相等 */
    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
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
        log.info("  \u2713 通过");
    }

    /** Fail */
    private static void fail(String msg) {
        log.info("  \u2717 失败: {}", msg);
    }

    /** 关闭Quietly */
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

        /**
         * 创建 EchoServer 实例
         * @param port port
         */
        private EchoServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            // virtual-thread 处理高并发 echo
            this.handlerPool = Executors.newVirtualThreadPerTaskExecutor();
            this.acceptThread = new Thread(this::acceptLoop, "tcp-proxy-example-echo");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        /** 开始 */
        static EchoServer start(int port) throws IOException {
            return new EchoServer(port);
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        /** AcceptLoop */
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

        /** 处理 */
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

    /**
     * 独立入口：委托 {@link TcpProxyExample#main(String[])} 执行完整流程。
     *
     * @param args 命令行参数（--key=value）
     */
    public static void main(String[] args) {
        TcpProxyExample.main(args);
    }
}

package com.chua.example.network.proxy;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.vertx.support.server.VertxTcpProxyServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * VertxTcpProxyServer 真实吞吐测试（独立 main）。
 *
 * <p>链路：EchoServer 后端 ← VertxTcpProxyServer（pipeTo 零拷贝）← 并发 Socket 客户端。
 * 与 TcpProxyExampleSpi 同模式（64 并发 × 500 请求/连接 × 64B 载荷），对比 JDK TcpProxyServer。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VertxTcpProxyPerfMain {

    /**
     * 最小回显后端。
     */
    static final class EchoServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        private volatile boolean running = true;

        EchoServer(int port) throws IOException {
            serverSocket = new ServerSocket(port);
            Thread.ofVirtual().start(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        pool.submit(() -> handle(socket));
                    } catch (IOException ignored) {
                    }
                }
            });
        }

        static EchoServer start(int port) throws IOException {
            return new EchoServer(port);
        }

        private void handle(Socket socket) {
            try (socket) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
            }
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            pool.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        int concurrency = Integer.parseInt(args.length > 0 ? args[0] : "64");
        int requestsPerConn = 500;
        int connections = concurrency;
        int payloadSize = 64;

        EchoServer backend = null;
        VertxTcpProxyServer proxy = null;
        try {
            backend = EchoServer.start(0);
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setProtocol("vertx-tcp-proxy");
            proxy = new VertxTcpProxyServer(setting, new InetSocketAddress("127.0.0.1", backend.getPort()));
            proxy.start();
            int proxyPort = proxy.getPort();
            log.info("VertxTcpProxyServer started on {}, backend={}", proxyPort, backend.getPort());

            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch ready = new CountDownLatch(connections);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(connections);
            LongAdder errors = new LongAdder();
            LongAdder ok = new LongAdder();

            for (int i = 0; i < connections; i++) {
                pool.submit(() -> {
                    try (Socket client = new Socket("127.0.0.1", proxyPort)) {
                        client.setSoTimeout(10000);
                        client.setTcpNoDelay(true);
                        OutputStream out = client.getOutputStream();
                        InputStream in = client.getInputStream();
                        ready.countDown();
                        start.await();
                        byte[] buf = new byte[payloadSize];
                        for (int k = 0; k < requestsPerConn; k++) {
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
                            ok.increment();
                        }
                    } catch (Exception e) {
                        errors.increment();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            long t0 = System.nanoTime();
            start.countDown();
            boolean finished = done.await(120, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            long total = ok.sum();
            long errs = errors.sum();
            double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
            log.info("VertxTcpProxy 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                    total, elapsedMs, Math.round(rps), total, errs, finished);
            pool.shutdownNow();
        } finally {
            if (proxy != null) {
                try {
                    proxy.stop();
                } catch (Exception ignored) {
                }
            }
            if (backend != null) {
                backend.close();
            }
        }
    }
}

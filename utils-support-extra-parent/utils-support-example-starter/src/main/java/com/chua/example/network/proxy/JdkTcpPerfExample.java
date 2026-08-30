package com.chua.example.network.proxy;

import com.chua.common.support.network.server.impl.JdkTcpServer;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * JdkTcpServer 回声吞吐测试（独立 main）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class JdkTcpPerfExample {
    private JdkTcpPerfExample() { }


    /** Main */
    public static void main(String[] args) throws Exception {
        int concurrency = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        int requestsPerConn = 500;
        int payloadSize = 64;

        JdkTcpServer server = null;
        try {
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setProtocol("jdk-tcp");
            server = new JdkTcpServer(setting);
            server.start();
            int port = server.getPort();
            log.info("JdkTcpServer started on {}", port);

            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            ExecutorService pool = ThreadUtils.newVirtualThreadPerTaskExecutor();
            CountDownLatch ready = new CountDownLatch(concurrency);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(concurrency);
            LongAdder errors = new LongAdder();
            LongAdder ok = new LongAdder();

            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    try (Socket client = new Socket("127.0.0.1", port)) {
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
            log.info("JdkTcp 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                    total, elapsedMs, Math.round(rps), total, errs, finished);
            pool.shutdownNow();
        } finally {
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
                }
            }
        }
    }
}

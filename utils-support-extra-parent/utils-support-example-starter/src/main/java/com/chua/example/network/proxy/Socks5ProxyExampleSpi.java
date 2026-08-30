package com.chua.example.network.proxy;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.proxy.Socks5ProxyServer;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.example.network.perf.PerfReportExample;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;/** Fail */
    private static void ExampleUtils.fail(String msg) {
        log.info("  \u2717 澶辫触: {}", msg);
    }

    /** 鍏抽棴Quietly */
    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class EchoServer implements AutoCloseable {
        /** 鏈嶅姟鍣⊿ocket */
        private final ServerSocket serverSocket;
        /** 澶勭悊鍣ㄦ睜 */
        private final java.util.concurrent.ExecutorService handlerPool;
        /** Accept绾跨▼ */
        private final Thread acceptThread;
        /** running */
        private volatile boolean running = true;

        /**
         * 鍒涘缓 EchoServer 瀹炰緥
         * @param port port
         */
        private EchoServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            this.handlerPool = ThreadUtils.newVirtualThreadPerTaskExecutor();
            this.acceptThread = new Thread(this::acceptLoop, "socks5-example-echo");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        /** 寮€濮?*/
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

        /** 澶勭悊 */
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
        /** 鍏抽棴 */
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

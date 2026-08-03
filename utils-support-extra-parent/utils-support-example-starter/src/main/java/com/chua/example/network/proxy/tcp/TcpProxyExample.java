package com.chua.example.network.proxy.tcp;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.server.filter.proxy.ProxyTargetResolver;
import com.chua.common.support.network.server.filter.proxy.TcpProxyServerFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP 代理示例 — 覆盖所有 TCP 代理实现。
 *
 * <p>支持的代理实现：
 * <ul>
 *   <li>{@code tcp-proxy} — 基于 {@link TcpProxyServerFilter} 的 TCP 反向代理</li>
 * </ul>
 *
 * <p>用法：
 * <ul>
 *   <li>{@code java TcpProxyExample tcp-proxy 7000 7001} — 监听 7000，转发到 7001</li>
 *   <li>{@code java TcpProxyExample tcp-proxy 7000 7001 --benchmark} — 压测模式</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
public class TcpProxyExample {

    private static final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) {
        String proxyType = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) ? args[0] : "tcp-proxy";
        int listenPort = 7000;
        if (args != null && args.length > 1 && args[1] != null) {
            try {
                listenPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        int backendPort = 7001;
        if (args != null && args.length > 2 && args[2] != null) {
            try {
                backendPort = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        boolean benchmark = args != null && args.length > 3 && "--benchmark".equals(args[3]);

        startBackendEchoServer(backendPort);

        switch (proxyType) {
            case "tcp-proxy" -> startTcpProxy(listenPort, backendPort, benchmark);
            default -> {
                log.error("[TcpProxyExample] unknown proxy type: {}", proxyType);
                System.exit(1);
            }
        }
    }

    private static void startTcpProxy(int listenPort, int backendPort, boolean benchmark) {
        Discovery backend = Discovery.builder()
                .host("127.0.0.1")
                .port(backendPort)
                .protocol("tcp")
                .build();
        ProxyTargetResolver resolver = addr -> backend;
        TcpProxyServerFilter proxy = new TcpProxyServerFilter(5000, 30000, resolver);
        proxy.startProxy(listenPort, 128);
        log.info("[TcpProxyExample] TCP proxy listening on {} -> backend {}", listenPort, backendPort);

        if (benchmark) {
            log.info("[TcpProxyExample] benchmark mode — running, press Ctrl+C to stop");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                proxy.stopProxy();
                log.info("[TcpProxyExample] benchmark stopped");
            }));
            try {
                while (running.get()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            proxy.stopProxy();
        }
        log.info("[TcpProxyExample] stopped");
    }

    private static void startBackendEchoServer(int port) {
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port, 128)) {
                log.info("[TcpProxyExample] backend echo server started on port={}", port);
                while (running.get()) {
                    try {
                        Socket client = ss.accept();
                        new Thread(() -> handleEcho(client), "echo-" + client.getPort()).start();
                    } catch (Exception e) {
                        if (running.get()) {
                            log.error("[TcpProxyExample] backend accept error: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[TcpProxyExample] backend server failed: {}", e.getMessage());
            }
        }, "tcp-backend-echo");
        t.setDaemon(true);
        t.start();
    }

    private static void handleEcho(Socket client) {
        try (client) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignored) {
        }
    }
}
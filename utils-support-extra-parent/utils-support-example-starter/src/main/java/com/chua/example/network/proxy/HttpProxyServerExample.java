package com.chua.example.network.proxy;

import com.chua.common.support.network.discovery.Discovery;
import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerAttribute;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.ServerFilterChain;
import com.chua.common.support.network.server.filter.proxy.HttpReverseProxyFilter;
import com.chua.common.support.network.server.filter.proxy.ReverseProxyServerFilter;
import com.chua.common.support.network.server.request.ServerRequest;
import com.chua.common.support.network.server.response.ServerResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 代理服务器示例 — 覆盖所有代理实现。
 *
 * <p>支持的代理实现：
 * <ul>
 *   <li>{@code reverse-proxy} — 基于 JDK HttpClient 的 HTTP/WS 反向代理</li>
 *   <li>{@code netty-proxy} — 基于 Netty 的 HTTP 反向代理</li>
 * </ul>
 *
 * <p>用法：
 * <ul>
 *   <li>{@code java HttpProxyServerExample reverse-proxy 8080 9090} — 启动反向代理，监听 8080，转发到 9090</li>
 *   <li>{@code java HttpProxyServerExample netty-proxy 8080 9090} — 启动 Netty 代理</li>
 *   <li>{@code java HttpProxyServerExample reverse-proxy 8080 9090 --benchmark} — 压测模式</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
public class HttpProxyServerExample {

    public static void main(String[] args) {
        String proxyType = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) ? args[0] : "reverse-proxy";
        int port = 8080;
        if (args != null && args.length > 1 && args[1] != null) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        int backendPort = 9090;
        if (args != null && args.length > 2 && args[2] != null) {
            try {
                backendPort = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        boolean benchmark = args != null && args.length > 3 && "--benchmark".equals(args[3]);

        startBackendServer(backendPort);

        Server proxyServer = null;
        try {
            switch (proxyType) {
                case "reverse-proxy" -> proxyServer = buildReverseProxyServer(port, backendPort);
                case "netty-proxy" -> proxyServer = buildNettyProxyServer(port, backendPort);
                default -> {
                    log.error("[HttpProxyServerExample] unknown proxy type: {}", proxyType);
                    System.exit(1);
                    return;
                }
            }
            proxyServer.start();
            log.info("[HttpProxyServerExample] proxy type={}, port={}, backend={}", proxyType, port, backendPort);

            if (benchmark) {
                log.info("[HttpProxyServerExample] benchmark mode — running, press Ctrl+C to stop");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { proxyServer.stop(); } catch (Exception ignored) { }
                    log.info("[HttpProxyServerExample] benchmark stopped");
                }));
                Thread.currentThread().join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[HttpProxyServerExample] failed: {}", e.getMessage());
            if (proxyServer != null) {
                try { proxyServer.stop(); } catch (Exception ignored) { }
            }
            System.exit(1);
            return;
        }
        if (!benchmark && proxyServer != null) {
            try {
                proxyServer.stop();
            } catch (Exception ignored) {
            }
        }
        log.info("[HttpProxyServerExample] stopped");
    }

    private static Server buildReverseProxyServer(int port, int backendPort) {
        Discovery backend = Discovery.builder()
                .host("127.0.0.1")
                .port(backendPort)
                .protocol("http")
                .build();
        ReverseProxyServerFilter proxyFilter = new ReverseProxyServerFilter();
        Server server = ServerBuilder.create()
                .type("jdk")
                .port(port)
                .build();
        server.addFilter(new BackendDiscoveryFilter(backend));
        server.addFilter(proxyFilter);
        return server;
    }

    private static Server buildNettyProxyServer(int port, int backendPort) {
        Discovery backend = Discovery.builder()
                .host("127.0.0.1")
                .port(backendPort)
                .protocol("http")
                .build();
        HttpReverseProxyFilter proxyFilter = new HttpReverseProxyFilter();
        Server server = ServerBuilder.create()
                .type("jdk")
                .port(port)
                .build();
        server.addFilter(new BackendDiscoveryFilter(backend));
        server.addFilter(proxyFilter);
        return server;
    }

    private static volatile Server backendServer;

    private static void startBackendServer(int port) {
        try {
            backendServer = ServerBuilder.create()
                    .type("jdk")
                    .port(port)
                    .mapping("/", HttpMethod.GET, (req, resp) -> {
                        resp.setContentType("application/json");
                        resp.setBody("{\"status\":\"ok\",\"from\":\"backend\"}");
                    })
                    .build();
            backendServer.start();
            log.info("[HttpProxyServerExample] backend started on port={}", port);
        } catch (Exception e) {
            log.error("[HttpProxyServerExample] backend failed: {}", e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 前置过滤器 — 将后端 Discovery 注入每个请求的 ServerAttribute 中。
     * <p>代理过滤器通过 {@link ServerAttribute#getBackendDiscovery(ServerRequest)} 读取此后端地址。</p>
     */
    private static class BackendDiscoveryFilter implements ServerFilter {

        private final Discovery backend;

        BackendDiscoveryFilter(Discovery backend) {
            this.backend = backend;
        }

        @Override
        public int getOrder() {
            return Integer.MAX_VALUE - 100;
        }

        @Override
        public void doFilter(ServerRequest request, ServerResponse response, ServerFilterChain chain) throws Exception {
            ServerAttribute.setBackendDiscovery(request, backend);
            chain.doFilter(request, response);
        }
    }
}
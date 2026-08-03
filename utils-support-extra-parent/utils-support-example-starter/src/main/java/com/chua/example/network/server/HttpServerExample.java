package com.chua.example.network.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpServer 示例 — 支持启动/停止及 HTTP 压测模式。
 *
 * <p>用法：
 * <ul>
 *   <li>{@code java HttpServerExample jdk 8080} — 启动 JDK 实现并验证运行状态</li>
 *   <li>{@code java HttpServerExample jdk 8080 --benchmark} — 启动 JDK 实现并保持运行（供 wrk 压测）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class HttpServerExample {

    /**
     * 主入口：按类型与端口启动 HTTP 服务器。
     *
     * @param args 命令行参数，args[0]=type（默认 jdk），args[1]=port（默认 8080），args[2]=--benchmark
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) ? args[0] : "jdk";
        int port = 8080;
        if (args != null && args.length > 1 && args[1] != null) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        boolean benchmark = args != null && args.length > 2 && "--benchmark".equals(args[2]);

        Server server = null;
        try {
            server = ServerBuilder.create()
                    .type(type)
                    .port(port)
                    .build();
            final Server finalServer = server;
            server.start();
            boolean running = server.isRunning();
            log.info("[HttpServerExample] started type={}, port={}, running={}", type, port, running);

            if (benchmark) {
                log.info("[HttpServerExample] benchmark mode — server running, press Ctrl+C to stop");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        if (finalServer != null) {
                            finalServer.stop();
                        }
                    } catch (Exception ignored) {
                    }
                    log.info("[HttpServerExample] benchmark stopped");
                }));
                Thread.currentThread().join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[HttpServerExample] failed: {}", e.getMessage());
            if (server != null) {
                try { server.stop(); } catch (Exception ignored) { }
            }
            System.exit(1);
            return;
        }
        if (!benchmark && server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }
        log.info("[HttpServerExample] stopped");
    }
}
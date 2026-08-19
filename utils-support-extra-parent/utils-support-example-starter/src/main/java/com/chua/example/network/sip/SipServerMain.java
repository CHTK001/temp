package com.chua.example.network.sip;

import com.chua.common.support.network.sip.SipConfig;
import com.chua.common.support.network.sip.SipServer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * SIP 信令服务器常驻入口（供 docker / 独立部署使用）。
 *
 * <p>启动后长期监听 TCP({@code --tcp-port}，默认 19460) 与 KCP({@code --kcp-port}，默认 19461)
 * 双传输，作为内网穿透的中心节点：各节点客户端连接注册后，即可通过隧道互相访问服务。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * java ... SipServerMain --host=0.0.0.0 --tcp-port=19460 --kcp-port=19461
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SipServerMain {

    /**
     * 常驻锁存器，阻止主线程退出。
     */
    private static final CountDownLatch STOP_LATCH = new CountDownLatch(1);

    /**
     * 常驻入口。
     *
     * @param args 命令行参数（--host / --tcp-port / --kcp-port / --tcp-enabled / --kcp-enabled）
     */
    public static void main(String[] args) {
        Map<String, String> kv = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String body = arg.substring(2);
                int eq = body.indexOf('=');
                kv.put(eq > 0 ? body.substring(0, eq) : body, eq > 0 ? body.substring(eq + 1) : "true");
            }
        }

        SipConfig config = SipConfig.builder()
                .host(kv.getOrDefault("host", "0.0.0.0"))
                .tcpPort(Integer.parseInt(kv.getOrDefault("tcp-port", String.valueOf(SipConfig.DEFAULT_TCP_PORT))))
                .kcpPort(Integer.parseInt(kv.getOrDefault("kcp-port", String.valueOf(SipConfig.DEFAULT_KCP_PORT))))
                .tcpEnabled(Boolean.parseBoolean(kv.getOrDefault("tcp-enabled", "true")))
                .kcpEnabled(Boolean.parseBoolean(kv.getOrDefault("kcp-enabled", "true")))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("SIP 服务器收到退出信号，开始停止");
            STOP_LATCH.countDown();
        }));

        SipServer server = new SipServer(config).start();
        log.info("SIP 服务器常驻运行中: host={}, tcp={}, kcp={}",
                config.getHost(), server.getConfig().getTcpPort(), server.getConfig().getKcpPort());
        try {
            STOP_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop();
        }
    }
}

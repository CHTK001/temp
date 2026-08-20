package com.chua.example.network.sip;

import com.chua.common.support.network.sip.SipConfig;
import com.chua.common.support.network.sip.SipServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * SIP 信令服务器常驻入口（供 docker / 独立部署使用）。
 *
 * <p>单端口模式：信令与数据平面共用同一监听端口（默认 19460），
 * 作为内网穿透的中心节点：各节点客户端认证注册后，即可通过隧道互相访问服务。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * java ... SipServerMain --host=0.0.0.0 --port=19460 --token=chua-sip-default-token
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SipServerMain {

    /**
     * 日志对象
     */
    private static final Logger log = LoggerFactory.getLogger(SipServerMain.class);

    /**
     * 常驻锁存器，阻止主线程退出。
     */
    private static final CountDownLatch STOP_LATCH = new CountDownLatch(1);

    /**
     * 常驻入口。
     *
     * @param args 命令行参数（--host / --port / --token）
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
                .port(Integer.parseInt(kv.getOrDefault("port", String.valueOf(SipConfig.DEFAULT_PORT))))
                .token(kv.getOrDefault("token", SipConfig.defaults().getToken()))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("SIP 服务器收到退出信号，开始停止");
            STOP_LATCH.countDown();
        }));

        SipServer server = new SipServer(config);
        server.start();
        log.info("SIP 服务器常驻运行中: host={}, port={}",
                config.getHost(), server.getConfig().getPort());
        try {
            STOP_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop();
        }
    }
}
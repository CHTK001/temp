package com.chua.example.network.sip;

import com.chua.common.support.network.sip.SipClient;
import com.chua.common.support.network.sip.SipTunnelService;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * SIP 隧道客户端常驻入口（内网穿透节点）。
 *
 * <p>三种角色 + 三种访问方式，完整覆盖所有使用场景：</p>
 *
 * <h2>角色说明</h2>
 * <ul>
 *   <li>{@code --mode=provider} — 服务提供方，暴露本地服务给远程</li>
 *   <li>{@code --mode=visitor} — 访问方，通过隧道访问远程服务</li>
 * </ul>
 *
 * <h2>Provider 暴露方式</h2>
 * <ul>
 *   <li>固定服务：{@code --service=web --host=127.0.0.1 --port=8080}</li>
 *   <li>通配模式（暴露整机）：{@code --service=*}，visitor 可指定任意目标</li>
 *   <li>通配 + 白名单：{@code --service=* --allow=192.168.,10.}</li>
 * </ul>
 *
 * <h2>Visitor 访问方式</h2>
 * <ul>
 *   <li>端口映射：{@code --mode=visitor --service=web --port=18080}</li>
 *   <li>通配目标：{@code --mode=visitor --service=192.168.1.100:3389 --port=13389}</li>
 *   <li>SOCKS5 代理：{@code --mode=visitor --socks5=1080}，三方软件配 SOCKS5 代理</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 * # 1. 启动服务器
 * java ... SipServerExample --host=0.0.0.0 --port=19460 --token=my-secret
 *
 * # 2. Provider：暴露固定服务
 * java ... SipClientExample --mode=provider --server=tcp://server:19460 \
 *         --token=my-secret --service=web --host=127.0.0.1 --port=8080
 *
 * # 3. Provider：通配模式暴露整机
 * java ... SipClientExample --mode=provider --server=tcp://server:19460 \
 *         --token=my-secret --service=* --allow=192.168.,10.
 *
 * # 4. Visitor：端口映射
 * java ... SipClientExample --mode=visitor --server=tcp://server:19460 \
 *         --token=my-secret --service=web --port=18080
 *
 * # 5. Visitor：SOCKS5 代理（推荐）
 * java ... SipClientExample --mode=visitor --server=tcp://server:19460 \
 *         --token=my-secret --socks5=1080
 *
 * # 6. Visitor：通配目标（远程桌面）
 * java ... SipClientExample --mode=visitor --server=tcp://server:19460 \
 *         --token=my-secret --service=192.168.1.100:3389 --port=13389
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SipClientExample {

    private SipClientExample() { }

    /**
     * 常驻锁存器，阻止主线程退出
     */
    private static final CountDownLatch STOP_LATCH = new CountDownLatch(1);

    /**
     * 默认认证令牌（与 SipConfig 默认值一致）
     */
    private static final String DEFAULT_TOKEN = "";

    /**
     * 常驻入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Map<String, String> kv = ExampleUtils.parseArgs(args);
        String mode = kv.getOrDefault("mode", "provider");
        String server = kv.getOrDefault("server", "tcp://127.0.0.1:19460");
        String token = kv.getOrDefault("token", DEFAULT_TOKEN);
        String service = kv.getOrDefault("service", "mstsc");
        String host = kv.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(kv.getOrDefault("port", "3389"));
        int socks5Port = Integer.parseInt(kv.getOrDefault("socks5", "0"));
        boolean encrypt = Boolean.parseBoolean(kv.getOrDefault("encrypt", "false"));
        boolean mux = Boolean.parseBoolean(kv.getOrDefault("mux", "false"));
        String tokenFile = kv.get("token-file");
        if (tokenFile != null && !tokenFile.isEmpty()) {
            System.setProperty("sip.token.file", tokenFile);
        }
        String maxFrameMs = kv.get("max-frame-ms");
        if (maxFrameMs != null) {
            System.setProperty("sip.minFrameIntervalNs",
                    String.valueOf((long) (Double.parseDouble(maxFrameMs) * 1_000_000)));
        }
        String maxAuth = kv.get("max-auth-per-min");
        if (maxAuth != null) {
            System.setProperty("sip.maxAuthPerIpPerMin", maxAuth);
        }
        String allowStr = kv.get("allow");
        java.util.List<String> allow = allowStr == null || allowStr.isEmpty()
                ? null : java.util.Arrays.asList(allowStr.split(","));

        Runtime.getRuntime().addShutdownHook(new Thread(STOP_LATCH::countDown, "sip-client-shutdown-hook"));

        SipClient client = SipClient.tcp(server).token(token).encrypt(encrypt).mux(mux);
        client.onReconnect(() -> log.info("SIP 重连成功，资源已重新绑定"));

        if ("provider".equalsIgnoreCase(mode)) {
            // ===== Provider 模式 =====
            boolean isWildcard = "*".equals(service);
            new SipTunnelService(client, service, host, port, allow).start();
            if (isWildcard) {
                log.info("SIP 服务提供方已启动: 通配模式 白名单={} 加密={} 复用={}",
                        allow == null ? "不限制" : allow, encrypt, mux);
            } else {
                log.info("SIP 服务提供方已启动: 服务[{}] -> {}:{} 加密={} 复用={}",
                        service, host, port, encrypt, mux);
            }
        } else if (socks5Port > 0) {
            // ===== Visitor SOCKS5 模式 =====
            client.socks5(socks5Port);
            log.info("SIP SOCKS5 代理已启动: 127.0.0.1:{} -> 所有隧道服务 (加密={})", socks5Port, encrypt);
        } else {
            // ===== Visitor 端口映射模式 =====
            client.tunnel(service).listen(host, port);
            log.info("SIP 访问方已启动: {}:{} -> 服务[{}] 加密={}", host, port, service, encrypt);
        }

        log.info("SIP 客户端运行中: clientId={}, server={}, 按 Ctrl+C 退出", client.getClientId(), server);
        try {
            STOP_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            client.close();
        }
    }

}

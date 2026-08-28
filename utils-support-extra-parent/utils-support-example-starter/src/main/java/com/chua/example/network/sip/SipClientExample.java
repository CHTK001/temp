package com.chua.example.network.sip;

import com.chua.common.support.network.sip.SipClient;
import com.chua.common.support.network.sip.SipTunnelService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * SIP 隧道客户端常驻入口（内网穿透节点）。
 *
 * <p>两种角色：</p>
 * <ul>
 *   <li>{@code --mode=provider}：服务提供方，把本地/可达的 TCP 服务暴露为隧道服务，
 *       如暴露内网 Windows 的远程桌面：{@code --service=mstsc --host=192.168.200.120 --port=3389}</li>
 *   <li>{@code --mode=visitor}：访问方，监听本地端口映射到远端隧道服务，
 *       之后 {@code mstsc /v:127.0.0.1:13389} 即可访问对端远程桌面</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * java ... SipClientExample --mode=provider --server=tcp://124.221.230.112:19460 \
 *         --token=chua-sip-default-token --service=mstsc --host=192.168.200.120 --port=3389
 *
 * java ... SipClientExample --mode=visitor --server=tcp://124.221.230.112:19460 \
 *         --token=chua-sip-default-token --service=mstsc --host=0.0.0.0 --port=13389
 * }</pre>
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
     * @param args 命令行参数（--mode / --server / --token / --service / --host / --port）
     */
    public static void main(String[] args) {
        Map<String, String> kv = parseArgs(args);
        String mode = kv.getOrDefault("mode", "provider");
        String server = kv.getOrDefault("server", "tcp://127.0.0.1:19460");
        String token = kv.getOrDefault("token", DEFAULT_TOKEN);
        String service = kv.getOrDefault("service", "mstsc");
        String host = kv.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(kv.getOrDefault("port", "3389"));
        boolean encrypt = Boolean.parseBoolean(kv.getOrDefault("encrypt", "false"));
        boolean mux = Boolean.parseBoolean(kv.getOrDefault("mux", "false"));
        String tokenFile = kv.get("token-file");
        if (tokenFile != null && !tokenFile.isEmpty()) { System.setProperty("sip.token.file", tokenFile); }
        String maxFrameMs = kv.get("max-frame-ms");
        if (maxFrameMs != null) { System.setProperty("sip.minFrameIntervalNs", String.valueOf((long)(Double.parseDouble(maxFrameMs) * 1_000_000))); }
        String maxAuth = kv.get("max-auth-per-min");
        if (maxAuth != null) { System.setProperty("sip.maxAuthPerIpPerMin", maxAuth); }
        String allowStr = kv.get("allow");
        java.util.List<String> allow = allowStr == null || allowStr.isEmpty()
                ? null : java.util.Arrays.asList(allowStr.split(","));

        Runtime.getRuntime().addShutdownHook(new Thread(STOP_LATCH::countDown, "sip-client-shutdown-hook"));

        SipClient client = SipClient.tcp(server).token(token).encrypt(encrypt).mux(mux);
        client.onReconnect(() -> log.info("SIP 重连成功，资源已重新绑定"));
        if ("provider".equalsIgnoreCase(mode)) {
            new SipTunnelService(client, service, host, port, allow).start();
            log.info("SIP 服务提供方已启动: 服务[{}] -> {}:{} 加密={} 复用={}", service, host, port, encrypt, mux);
        } else if ("visitor".equalsIgnoreCase(mode)) {
            client.tunnel(service).listen(host, port);
            log.info("SIP 访问方已启动: {}:{} -> 服务[{}] 加密={}", host, port, service, encrypt);
        } else {
            log.error("未知模式: {}（仅支持 provider / visitor）", mode);
            System.exit(1);
            return;
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

    /**
     * 解析 --key=value 形式的命令行参数。
     *
     * @param args 命令行参数
     * @return 键值映射
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> kv = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            kv.put(eq > 0 ? body.substring(0, eq) : body, eq > 0 ? body.substring(eq + 1) : "true");
        }
        return kv;
    }
}

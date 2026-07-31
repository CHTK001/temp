package com.chua.example.ngrok;

import com.chua.ngrok.support.NgrokClient;
import com.ngrok.Listener;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;

/**
 * Ngrok 客户端示例 — 验证 NgrokClient 链式 API 与隧道建立。
 *
 * <p>本示例不会硬编码 ngrok authtoken，统一通过 {@code NGROK_AUTHTOKEN} 环境变量读取。
 * 可选 {@code NGROK_DOMAIN} 指定固定子域名（需在 ngrok 面板预留）。
 * 可选 {@code NGROK_TARGET_URL} 指定转发目标，默认 {@code http://127.0.0.1:8080}。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 设置 token 后执行（Windows PowerShell）
 *   $env:NGROK_AUTHTOKEN = "2abc...xyz"
 *   mvn exec:java -pl utils-support-extra-parent/utils-support-example-starter `
 *       -Dexec.mainClass=com.chua.example.ngrok.NgrokClientExample `
 *       -Dexec.args="forward"
 *
 *   # 固定域名（可选）
 *   $env:NGROK_DOMAIN = "example.ngrok-free.app"
 *
 *   # 转发到非 8080 端口
 *   mvn exec:java ... -Dexec.args="forward" -Dngrok.target=http://127.0.0.1:9090
 *
 *   # 仅验证 Session 连接（不起隧道，token 错误时也能跑完，输出错误信息）
 *   mvn exec:java ... -Dexec.args="connect"
 * </pre>
 *
 * <h2>支持的能力点</h2>
 * <table border="1">
 *   <tr><th>--type</th><th>说明</th></tr>
 *   <tr><td>connect</td><td>仅建立 Session，打印 Session id/metadata，不创建隧道</td></tr>
 *   <tr><td>forward</td><td>建立 HTTP 隧道，自动转发到 {@code NGROK_TARGET_URL}，阻塞直到关闭</td></tr>
 *   <tr><td>listen</td><td>建立 HTTP 隧道（listen 模式），不转发；暴露 ngrok URL 后由调用方处理连接</td></tr>
 *   <tr><td>tcp</td><td>建立 TCP 隧道（remoteAddress 由 {@code NGROK_TCP_ADDR} 指定）</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class NgrokClientExample {

    /**
     * 环境变量：ngrok authtoken
     */
    private static final String ENV_AUTHTOKEN = "NGROK_AUTHTOKEN";

    /**
     * 环境变量：固定域名
     */
    private static final String ENV_DOMAIN = "NGROK_DOMAIN";

    /**
     * 环境变量：TCP 远程地址（形如 {@code 1.tcp.ngrok.io:20000}）
     */
    private static final String ENV_TCP_ADDR = "NGROK_TCP_ADDR";

    /**
     * 系统属性：转发目标 URL
     */
    private static final String PROP_TARGET = "ngrok.target";

    /**
     * 系统属性：会话元数据
     */
    private static final String PROP_METADATA = "ngrok.metadata";

    /**
     * 默认转发目标
     */
    private static final String DEFAULT_TARGET = "http://127.0.0.1:8080";

    /**
     * 默认会话元数据
     */
    private static final String DEFAULT_METADATA = "ngrok-client-example";

    public static void main(String[] args) {
        String type = args.length > 0 ? args[0].toLowerCase() : "forward";
        String target = System.getProperty(PROP_TARGET, DEFAULT_TARGET);
        String metadata = System.getProperty(PROP_METADATA, DEFAULT_METADATA);

        log.info("========== Ngrok 客户端示例 [type={}] ==========", type);
        log.info("目标: {}", target);
        log.info("元数据: {}", metadata);

        String authtoken = readAuthtoken();
        if (authtoken == null) {
            log.warn("未读取到 ngrok authtoken，跳过真实连接，仅演示 API 装配");
            log.info("设置方法: set NGROK_AUTHTOKEN=<your-token> 后重新运行");
            log.info("----------------------------------------");
            log.info("API 形态预览（不实际连接）:");
            log.info("  NgrokClient.create(\"TOKEN\")");
            log.info("        .metadata(\"{}\")", metadata);
            log.info("        .connect()");
            log.info("        .http()");
            log.info("        .domain(\"example.ngrok-free.app\")");
            log.info("        .forwardHttp(new URL(\"{}\"))", target);
            log.info("========== Ngrok 客户端示例完成 ==========");
            return;
        }

        try {
            run(type, authtoken, metadata, target);
        } catch (Exception e) {
            log.error("Ngrok 示例执行失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void run(String type, String authtoken, String metadata, String target) throws Exception {
        NgrokClient client = NgrokClient.create(authtoken)
                .metadata(metadata);
        try {
            switch (type) {
                case "connect" -> runConnect(client);
                case "listen" -> runListen(client);
                case "tcp" -> runTcp(client);
                case "forward" -> runForward(client, target);
                default -> {
                    log.error("未知 type: {}（支持: connect / listen / forward / tcp）", type);
                    System.exit(1);
                }
            }
        } finally {
            client.close();
            log.info("Ngrok Client 已关闭");
            log.info("========== Ngrok 客户端示例完成 ==========");
        }
    }

    private static void runConnect(NgrokClient client) {
        client.connect();
        log.info(">>> ✅ Session 已建立");
        log.info("    id       = {}", client.getSession().getId());
        log.info("    metadata = {}", client.getSession().getMetadata());
        log.info("    公共 URL = {}", client.getUrls());
    }

    private static void runListen(NgrokClient client) throws Exception {
        client.connect();
        log.info(">>> ✅ Session 已建立，开始创建 HTTP 隧道（listen 模式）");

        String domain = readDomainOrNull();
        NgrokClient.HttpBuilderStage stage = client.http();
        if (domain != null) {
            log.info("    使用固定域名: {}", domain);
            stage.domain(domain);
        } else {
            log.info("    使用随机域名（ngrok 分配）");
        }

        Listener.Endpoint listener = ((NgrokClient.HttpBuilderStage) stage
                .metadata(metadataFor("listener"))
                .compression())
                .builder()
                .listen();

        log.info(">>> ✅ HTTP 隧道已建立: {}", listener.getUrl());
        log.info("    提示: listen 模式不会自动转发流量，连接建立后由调用方处理");
    }

    private static void runForward(NgrokClient client, String target) throws Exception {
        client.connect();
        log.info(">>> ✅ Session 已建立，开始创建 HTTP 转发隧道");
        log.info("    转发目标: {}", target);

        URL targetUrl = new URL(target);
        String domain = readDomainOrNull();
        NgrokClient.HttpBuilderStage stage = client.http();
        if (domain != null) {
            log.info("    使用固定域名: {}", domain);
            stage.domain(domain);
        } else {
            log.info("    使用随机域名（ngrok 分配）");
        }

        client.forwardHttp(stage.metadata(metadataFor("forwarder")).builder(), targetUrl);
        log.info(">>> ✅ HTTP 转发隧道已建立");
        log.info("    公网 URL = {}", client.getUrls());
        log.info("    目标     = {}", target);
        log.info("    阻塞等待 Session 关闭（Ctrl+C 退出）...");
        client.block();
    }

    private static void runTcp(NgrokClient client) throws Exception {
        String remoteAddr = System.getenv(ENV_TCP_ADDR);
        if (remoteAddr == null || remoteAddr.isBlank()) {
            log.error("TCP 模式需要设置环境变量 NGROK_TCP_ADDR（形如 1.tcp.ngrok.io:20000）");
            System.exit(1);
            return;
        }
        client.connect();
        log.info(">>> ✅ Session 已建立，开始创建 TCP 隧道");
        log.info("    远程地址: {}", remoteAddr);

        client.listenTcp(client.tcp()
                .metadata(metadataFor("tcp-listener"))
                .remoteAddress(remoteAddr)
                .builder());

        log.info(">>> ✅ TCP 隧道已建立");
        log.info("    公网 URL = {}", client.getUrls());
        log.info("    阻塞等待 Session 关闭（Ctrl+C 退出）...");
        client.block();
    }

    private static String readAuthtoken() {
        String env = System.getenv(ENV_AUTHTOKEN);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty("ngrok.token");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return null;
    }

    private static String readDomainOrNull() {
        String domain = System.getenv(ENV_DOMAIN);
        if (domain != null && !domain.isBlank()) {
            return domain;
        }
        return null;
    }

    private static String metadataFor(String suffix) {
        return DEFAULT_METADATA + "/" + suffix;
    }
}

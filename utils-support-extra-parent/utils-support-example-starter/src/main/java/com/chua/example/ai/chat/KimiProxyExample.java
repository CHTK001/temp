package com.chua.example.ai.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

/**
 * Kimi 网页版逆向代理 ChatClient 示例 — 基于 Kimi Web 协议（纯 HTTP，无需浏览器）。
 *
 * <p>appKey 支持 JWT access token 或 refresh token，支持多轮对话上下文。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 对话测试
 *   java KimiProxyExample --token="eyJ..."  --module=chat
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class KimiProxyExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认模型
     */
    private static final String DEFAULT_MODEL = "kimi-k2.6";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("KimiProxyExample")
                .register("token", "t", "Kimi token（JWT access token 或 refresh token）")
                .register("module", "m", "测试能力点：chat / all（默认 chat）", "chat");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String token = cli.get("token");
        if (token == null || token.isBlank()) {
            log.error("必须通过 --token 指定 Kimi token");
            cli.help();
            System.exit(EXIT_CODE_FAILURE);
            return;
        }
        String module = cli.get("module", "chat");

        boolean passed = new KimiProxyExample().runTest(token, module);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 运行指定能力点的自检。
     *
     * @param token  Kimi token
     * @param module 能力点：chat / all
     * @return 是否全部通过
     */
    public boolean runTest(String token, String module) {
        ChatClient client = ChatClient.create(ChatClientSetting.builder()
                .provider("kimi-proxy")
                .appKey(token)
                .model(DEFAULT_MODEL)
                .build());
        try {
            return switch (module.toLowerCase()) {
                case "chat" -> testChat(client);
                case "all" -> testChat(client);
                default -> {
                    log.error("未知能力点: {}", module);
                    yield false;
                }
            };
        } catch (Exception e) {
            log.error("自检异常: {}", e.getMessage(), e);
            return false;
        } finally {
            client.close();
        }
    }

    /**
     * 测试多轮对话能力。
     *
     * @param client ChatClient 实例
     * @return 是否通过
     */
    public boolean testChat(ChatClient client) {
        try {
            long start = System.currentTimeMillis();
            String a1 = client.chatSync("我叫张三");
            log.info("Q1: 我叫张三");
            log.info("A1: {}", a1.trim());
            log.info("耗时: {}ms", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            String a2 = client.chatSync("我刚才说了什么？");
            log.info("Q2: 我刚才说了什么？");
            log.info("A2: {}", a2.trim());
            log.info("耗时: {}ms", System.currentTimeMillis() - start);

            start = System.currentTimeMillis();
            String a3 = client.chatSync("我叫什么名字？");
            log.info("Q3: 我叫什么名字？");
            log.info("A3: {}", a3.trim());
            log.info("耗时: {}ms", System.currentTimeMillis() - start);

            boolean contextOk = a2.contains("张三") || a3.contains("张三");
            log.info("=== 多轮上下文: {} ===", contextOk ? "通过" : "失败");
            return contextOk;
        } catch (Exception e) {
            log.error("对话测试异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
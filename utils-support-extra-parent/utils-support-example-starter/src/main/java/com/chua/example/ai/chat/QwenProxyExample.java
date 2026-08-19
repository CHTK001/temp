package com.chua.example.ai.chat;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.generation.ImageGenerationResult;
import com.chua.common.support.ai.generation.VideoGenerationResult;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

/**
 * 通义千问 Cookie 反代 ChatClient 示例 — 基于 Playwright 实现。
 *
 * <p>使用 Cookie 免费调用 Qwen 网页版（chat.qwen.ai），支持多轮对话、图像生成与视频生成。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 画像生成
 *   java QwenProxyExample --cookie="token=xxx"
 *
 *   # 指定能力点：chat / image / video / all
 *   java QwenProxyExample --cookie="token=xxx" --module=all
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class QwenProxyExample {

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
    private static final String DEFAULT_MODEL = "qwen3.8-max";

    /**
     * cookie 默认值（测试用）
     */
    private static final String DEFAULT_COOKIE = "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6ImFmMTIxYzQ1LTJhNTQtNDgzZS1hNGIyLTAzNzk0ZDM1MzgyOSIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzg2NTA2NjgxLCJleHAiOjE3ODkxMDA2ODl9.uqKQkRZpic98yn_7cgYEj1EtQEK6LVGABzrWH11lFHQ";

    /**
     * 图像生成提示词
     */
    private static final String IMAGE_PROMPT = "一只柯基犬在阳光下，温柔地微笑着";

    /**
     * 图像生成比例
     */
    private static final String IMAGE_RATIO = "16:9";

    /**
     * 视频生成提示词
     */
    private static final String VIDEO_PROMPT = "一只柯基在雪地里奔跑";

    /**
     * 视频生成比例
     */
    private static final String VIDEO_RATIO = "16:9";

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("QwenProxyExample")
                .register("cookie", "c", "Qwen 网页版 Cookie（token=xxx）")
                .register("module", "m", "测试能力点：chat / image / video / all（默认 all）", "all");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String cookie = cli.get("cookie", DEFAULT_COOKIE);
        String module = cli.get("module", "all");

        boolean passed = new QwenProxyExample().runTest(cookie, module);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 运行指定能力点的自检。
     *
     * @param cookie Qwen 网页版 Cookie
     * @param module 能力点：chat / image / video / all
     * @return 是否全部通过
     */
    public boolean runTest(String cookie, String module) {
        ChatClient client = ChatClient.create(ChatClientSetting.builder()
                .provider("qwen-proxy")
                .appKey(cookie)
                .model(DEFAULT_MODEL)
                .build());
        try {
            return switch (module.toLowerCase()) {
                case "chat" -> testChat(client);
                case "image" -> testImage(client);
                case "video" -> testVideo(client);
                case "all" -> testChat(client) && testImage(client) && testVideo(client);
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

    /**
     * 测试图像生成能力。
     *
     * @param client ChatClient 实例
     * @return 是否通过
     */
    public boolean testImage(ChatClient client) {
        try {
            long start = System.currentTimeMillis();
            ImageGenerationResult result = client.generateImage()
                    .prompt(IMAGE_PROMPT)
                    .ratio(IMAGE_RATIO)
                    .generate();
            log.info("=== 图像生成 ===");
            log.info("图片数: {}", result.images().size());
            for (ImageGenerationResult.GeneratedImage image : result.images()) {
                log.info("URL: {}", image.oriUrl());
            }
            log.info("耗时: {}ms", System.currentTimeMillis() - start);
            return !result.images().isEmpty();
        } catch (Exception e) {
            log.error("图像生成测试异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试视频生成能力。
     *
     * <p>注意：Qwen 免费网页版（chat.qwen.ai）当前未提供 {@code video_gen} 工具，
     * 服务端会将"生成视频"请求降级为图像生成，因此正常情况下视频列表为空。</p>
     *
     * @param client ChatClient 实例
     * @return 是否通过（Qwen 不支持视频生成时返回 false 并给出说明）
     */
    public boolean testVideo(ChatClient client) {
        try {
            long start = System.currentTimeMillis();
            VideoGenerationResult result = client.generateVideo()
                    .prompt(VIDEO_PROMPT)
                    .ratio(VIDEO_RATIO)
                    .generate();
            log.info("=== 视频生成 ===");
            log.info("视频数: {}", result.videos().size());
            for (VideoGenerationResult.GeneratedVideo video : result.videos()) {
                log.info("URL: {}", video.videoUrl());
            }
            log.info("耗时: {}ms", System.currentTimeMillis() - start);
            if (result.videos().isEmpty()) {
                log.warn("Qwen 免费网页版不支持视频生成（服务端降级为图像生成），视频列表为空");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("视频生成测试异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
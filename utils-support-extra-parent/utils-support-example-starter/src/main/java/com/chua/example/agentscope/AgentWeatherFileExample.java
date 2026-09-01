package com.chua.example.agentscope;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentHookEvent;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.client.HttpClientFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent + MCP Skill 完整集成测试：天气查询 + 文件写入。
 *
 * <p>运行方式：
 * <pre>{@code
 *   # Mock 模式（无需 API Key）
 *   java ... AgentWeatherFileExample
 *
 *   # 真实 LLM（需设置环境变量）
 *   export OPENAI_API_KEY=xxx
 *   export OPENAI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
 *   java ... AgentWeatherFileExample --real openai
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AgentWeatherFileExample {

    /** 工作目录，运行时由 main 初始化，存储 Agent 中间产物 */
    private static Path workDir;
    /** 记录 AgentHookEvent，供 printSummary() 统计展示 */
    private static final List<AgentHookEvent> hookEvents = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        boolean useReal = false;
        String provider = "openai";
        for (int i = 0; i < args.length; i++) {
            if ("--real".equals(args[i])) useReal = true;
            else if (args[i].startsWith("--provider=")) provider = args[i].substring("--provider=".length());
        }

        workDir = Path.of(System.getProperty("java.io.tmpdir"), "agent-test-" + System.currentTimeMillis());
        Files.createDirectories(workDir);
        log.info("[workdir] {}", workDir.toAbsolutePath());

        if (useReal) {
            log.info("=== 使用真实 LLM: {} ===", provider);
            runWithRealLlm(provider);
        } else {
            log.info("=== 使用 Mock LLM ===");
            runWithMock();
        }

        printSummary();
    }

    private static void runWithRealLlm(String provider) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[ERROR] 未设置 OPENAI_API_KEY 环境变量");
            return;
        }

        ChatClient client = ChatClient.create(provider, apiKey, baseUrl)
                .model("glm-4-flash");

        Agent agent = Agent.create("agentscope")
                .chatClient(client)
                .debug(true)
                .maxToolIterations(3)
                .mode(AgentMode.SINGLE)
                .definition(AgentDefinition.builder()
                        .id("weather-agent")
                        .name("天气助手")
                        .instruction("你是一个天气助手。用户请求时：1)查询指定城市天气；2)将结果写入报告文件。"
                                + "使用 skill 'get_weather' 查询，使用 skill 'save_report' 写文件。")
                        .leader(false)
                        .build())
                .skill("get_weather", "查询指定城市的实时天气（温度、湿度、天气描述）", args -> {
                    String city = (String) args.get("city");
                    String result = queryWeather(city != null ? city : "北京");
                    log.info("[skill] get_weather: {}", result);
                    return com.chua.common.support.ai.skill.SkillResult.success(result);
                })
                .skill("save_report", "将文本内容写入指定路径的文件", args -> {
                    String pathStr = (String) args.get("path");
                    String content = (String) args.get("content");
                    if (pathStr == null || content == null) {
                        return com.chua.common.support.ai.skill.SkillResult.error("path/content 不能为空");
                    }
                    Path p = workDir.resolve(pathStr);
                    try {
                        Files.createDirectories(p.getParent());
                        Files.writeString(p, content, StandardCharsets.UTF_8);
                        long size = Files.size(p);
                        log.info("[skill] save_report: 文件已写入 {} ({} bytes)", p, size);
                        return com.chua.common.support.ai.skill.SkillResult.success(
                                "文件已写入: " + p + " (" + size + " bytes)");
                    } catch (Exception e) {
                        return com.chua.common.support.ai.skill.SkillResult.error(e.getMessage());
                    }
                })
                .debugHook(event -> {
                    hookEvents.add(event);
                    log.info("[HOOK] iteration={} toolCalls={} tokens={} elapsed={}ms type={}",
                            event.getIteration(),
                            event.getToolCallCount(),
                            event.getTotalTokens(),
                            event.getElapsedMillis(),
                            event.getType());
                });

        log.info(">>> 任务: 查询北京天气并写入报告文件");
        AgentResponse response = agent.run(
                "请查询北京今天的天气情况，并将结果整理成一份报告写入 weather_report.txt 文件，最后告诉我天气摘要和文件路径。");
        log.info(">>> Agent 输出: {}", response.getOutput());
    }

    private static void runWithMock() {
        ChatClient mockClient = createMockChatClient();
        
        Agent agent = Agent.create("agentscope")
                .chatClient(mockClient)
                .debug(true)
                .maxToolIterations(5)
                .mode(AgentMode.SINGLE)
                .definition(AgentDefinition.builder()
                        .id("weather-agent")
                        .name("天气助手")
                        .instruction("你是一个天气助手。用户请求时：1)查询指定城市天气；2)将结果写入报告文件。"
                                + "使用 skill 'get_weather' 查询，使用 skill 'save_report' 写文件。")
                        .leader(false)
                        .build())
                .skill("get_weather", "查询指定城市的实时天气（温度、湿度、天气描述）", args -> {
                    String city = (String) args.get("city");
                    String result = queryWeather(city != null ? city : "北京");
                    log.info("[skill] get_weather: {}", result);
                    return com.chua.common.support.ai.skill.SkillResult.success(result);
                })
                .skill("save_report", "将文本内容写入指定路径的文件", args -> {
                    String pathStr = (String) args.get("path");
                    String content = (String) args.get("content");
                    if (pathStr == null || content == null) {
                        return com.chua.common.support.ai.skill.SkillResult.error("path/content 不能为空");
                    }
                    Path p = workDir.resolve(pathStr);
                    try {
                        Files.createDirectories(p.getParent());
                        Files.writeString(p, content, StandardCharsets.UTF_8);
                        long size = Files.size(p);
                        log.info("[skill] save_report: 文件已写入 {} ({} bytes)", p, size);
                        return com.chua.common.support.ai.skill.SkillResult.success(
                                "文件已写入: " + p + " (" + size + " bytes)");
                    } catch (Exception e) {
                        return com.chua.common.support.ai.skill.SkillResult.error(e.getMessage());
                    }
                })
                .debugHook(event -> {
                    hookEvents.add(event);
                    log.info("[HOOK] iteration={} toolCalls={} tokens={} elapsed={}ms type={}",
                            event.getIteration(),
                            event.getToolCallCount(),
                            event.getTotalTokens(),
                            event.getElapsedMillis(),
                            event.getType());
                });

        log.info(">>> 任务: 查询北京天气并写入报告文件");
        AgentResponse response = agent.run(
                "请查询北京今天的天气情况，并将结果整理成一份报告写入 weather_report.txt 文件，最后告诉我天气摘要和文件路径。");
        log.info(">>> Agent 输出: {}", response.getOutput());
    }

    private static ChatClient createMockChatClient() {
        return new com.chua.common.support.ai.chat.ChatClient() {
            private int callCount = 0;

            @Override
            public String chatSync(String prompt) {
                callCount++;
                if (callCount == 1) {
                    return "我将执行以下步骤：\n1. 调用 get_weather(city='北京') 查询天气\n2. 调用 save_report(path='weather_report.txt', content='<天气结果>') 写入报告\n请按上述步骤执行。";
                }
                return "任务完成。天气查询和文件写入均已执行，请整合结果输出。";
            }

            @Override
            public void chat(String prompt, java.util.function.Consumer<com.chua.common.support.ai.chat.ChatResponse> consumer) {
                chat(prompt, consumer, () -> {}, e -> {});
            }

            @Override
            public void chat(String prompt, java.util.function.Consumer<com.chua.common.support.ai.chat.ChatResponse> consumer,
                             Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
                consumer.accept(com.chua.common.support.ai.chat.ChatResponse.builder().state(com.chua.common.support.ai.chat.ChatResponse.State.START).build());
                String out = chatSync(prompt);
                consumer.accept(com.chua.common.support.ai.chat.ChatResponse.builder().state(com.chua.common.support.ai.chat.ChatResponse.State.STREAMING).content(out).build());
                consumer.accept(com.chua.common.support.ai.chat.ChatResponse.builder().state(com.chua.common.support.ai.chat.ChatResponse.State.STOP).fullContent(out).build());
                onComplete.run();
            }

            @Override
            public List<com.chua.common.support.ai.chat.ModelDefinition> models() {
                return List.of();
            }

            @Override
            public void close() {}
        };
    }

    private static String queryWeather(String city) {
        String url = "https://wttr.in/" + city.trim() + "?format=j1";
        try {
            String json = HttpClientFactory.of(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126")
                    .get()
                    .getBodyString();
            if (json == null || json.isEmpty()) {
                return city + " -> 查询失败：无响应";
            }
            int tempC = parseJsonInt(json, "temp_C");
            int humidity = parseJsonInt(json, "humidity");
            String feelsLike = parseJsonInt(json, "FeelsLikeC") + "°C";
            String desc = extractJsonValue(json, "value");
            return String.format("%s 当前 %d°C（体感%s） 湿度%d%% %s",
                    city, tempC, feelsLike, humidity, desc);
        } catch (Exception e) {
            return city + " -> 天气查询异常: " + e.getMessage();
        }
    }

    private static int parseJsonInt(String json, String key) {
        try {
            return Json.parse(json).get(key).toIntValue(-1);
        } catch (Exception e) {
            log.warn("[WARN] parseJsonInt key='{}': {}", key, e.getMessage());
            return -1;
        }
    }

    private static String extractJsonValue(String json, String key) {
        try {
            return Json.parse(json).get(key).toStringValue("?");
        } catch (Exception e) {
            log.warn("[WARN] extractJsonValue key='{}': {}", key, e.getMessage());
            return "?";
        }
    }

    private static void printSummary() {
        log.info("\n========== 执行摘要 ==========");
        log.info("总 Hook 事件数: {}", hookEvents.size());
        long maxTokens = hookEvents.stream()
                .mapToLong(e -> e.getTotalTokens() != null ? e.getTotalTokens() : 0)
                .max().orElse(0);
        long maxElapsed = hookEvents.stream()
                .mapToLong(e -> e.getElapsedMillis() != null ? e.getElapsedMillis() : 0)
                .max().orElse(0);
        int distinctIterations = (int) hookEvents.stream()
                .map(com.chua.common.support.ai.agent.AgentHookEvent::getIteration)
                .filter(v -> v != null)
                .distinct()
                .count();
        int maxToolCalls = hookEvents.stream()
                .mapToInt(e -> e.getToolCallCount() != null ? e.getToolCallCount() : 0)
                .max().orElse(0);
        log.info("执行轮次: {}", distinctIterations);
        log.info("最大工具调用次数: {}", maxToolCalls);
        log.info("累计最大 Token 数: {}", maxTokens);
        log.info("总耗时: {} ms", maxElapsed);

        // 打印文件内容
        if (workDir != null) {
            try {
                Path report = workDir.resolve("weather_report.txt");
                if (Files.exists(report)) {
                    log.info("\n--- 文件内容 ({}) ---%n%s", report, Files.readString(report, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.warn("[WARN] read report file: {}", e.getMessage());
            }
        }
        log.info("==============================");
    }
}

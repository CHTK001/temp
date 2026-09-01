package com.chua.example.agentscope;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentHookEvent;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.network.client.HttpClientFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Agent MCP Skill 完整集成示例：天气查询 + 文件写入。
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 *   # 无需 API Key（使用 Mock LLM，演示 Hook 指标）
 *   java -cp ... com.chua.example.agentscope.AgentWeatherFileExample
 *
 *   # 使用真实 LLM（需设置环境变量）
 *   export OPENAI_API_KEY=sk-xxx
 *   java -cp ... com.chua.example.agentscope.AgentWeatherFileExample --real
 *
 *   export DEEPSEEK_API_KEY=sk-xxx
 *   java -cp ... com.chua.example.agentscope.AgentWeatherFileExample --real --provider deepseek
 * }</pre>
 *
 * <h3>功能说明</h3>
 * <ul>
 *   <li><b>天气查询</b> — 通过 wttr.in 公开接口查询城市实时天气（温度、湿度、天气描述）</li>
 *   <li><b>文件写入</b> — 将查询结果写入临时目录下的报告文件</li>
 *   <li><b>Hook 指标</b> — 实时输出 iteration / toolCallCount / totalTokens / elapsedMillis</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class AgentWeatherFileExample {

    /** 临时工作目录（运行后保留用于检查） */
    private static Path workDir;

    /** Hook 事件收集 */
    private final List<AgentHookEvent> hookEvents = new CopyOnWriteArrayList<>();

    // ==================== 主入口 ====================

    public static void main(String[] args) throws Exception {
        boolean useReal = false;
        String provider = "openai";
        for (String arg : args) {
            if ("--real".equals(arg)) useReal = true;
            else if (arg.startsWith("--provider=")) provider = arg.substring("--provider=".length());
        }

        AgentWeatherFileExample ex = new AgentWeatherFileExample();

        if (useReal) {
            System.out.println("=== 使用真实 LLM ===");
            ex.runWithRealLlm(provider);
        } else {
            System.out.println("=== 使用 Mock LLM（无 API Key）===");
            ex.runWithMock();
        }

        // 打印汇总
        ex.printSummary();

        // 打印 Hook 事件表
        System.out.println("\n--- Hook 事件详情 ---");
        for (AgentHookEvent ev : ex.hookEvents) {
            System.out.printf("  iteration=%-3s tools=%-3s tokens=%-6s elapsed=%-8s type=%s%n",
                    ev.getIteration(), ev.getToolCallCount(),
                    ev.getTotalTokens(), ev.getElapsedMillis(), ev.getType());
        }

        // 打印文件内容
        if (workDir != null && Files.exists(workDir)) {
            Path report = workDir.resolve("weather_report.txt");
            if (Files.exists(report)) {
                System.out.printf("\n--- 文件内容 (%s) ---%n%s%n",
                        report, Files.readString(report, StandardCharsets.UTF_8));
            }
        }
    }

    // ==================== 真实 LLM 模式 ====================

    private void runWithRealLlm(String provider) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("DEEPSEEK_API_KEY");
            if (apiKey != null && !apiKey.isBlank() && provider.equals("deepseek")) {
                // 使用 DeepSeek
            } else {
                apiKey = null;
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("未找到 OPENAI_API_KEY 或 DEEPSEEK_API_KEY，自动降级为 Mock 模式");
            runWithMock();
            return;
        }

        workDir = createTempWorkDir("agent-weather-real");

        ChatClient chatClient = createChatClient(provider, apiKey);
        Agent agent = buildAgent(chatClient);

        System.out.println(">>> 任务: 查询北京天气并写入报告文件");
        AgentResponse response = agent.run(
                "请查询北京今天的天气情况，并将结果整理成一份报告写入 weather_report.txt 文件，"
                        + "最后告诉我天气摘要和文件路径。");

        System.out.println(">>> Agent 输出: " + response.getOutput());
    }

    // ==================== Mock 模式 ====================

    private void runWithMock() {
        workDir = createTempWorkDir("agent-weather-mock");

        ChatClient mockClient = createMockChatClient();
        Agent agent = buildAgent(mockClient);

        System.out.println(">>> 任务: 查询北京天气并写入报告文件");
        AgentResponse response = agent.run(
                "请查询北京今天的天气情况，并将结果整理成一份报告写入 weather_report.txt 文件。");

        System.out.println(">>> Agent 输出: " + response.getOutput());
    }

    // ==================== Agent 构建 ====================

    private Agent buildAgent(ChatClient chatClient) {
        Agent agent = Agent.create("agentscope")
                .chatClient(chatClient)
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
                // 注册天气查询技能（直接调用，不依赖 LLM 路由）
                .skill("get_weather", "查询指定城市的实时天气（温度、湿度、天气描述）", args -> {
                    String city = (String) args.get("city");
                    String result = queryWeather(city != null ? city : "北京");
                    return com.chua.common.support.ai.skill.SkillResult.success(result);
                })
                // 注册文件写入技能
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
                        System.out.println("[skill] 文件已写入: " + p + " (" + size + " bytes)");
                        return com.chua.common.support.ai.skill.SkillResult.success(
                                "文件已写入: " + p + " (" + size + " bytes)");
                    } catch (Exception e) {
                        return com.chua.common.support.ai.skill.SkillResult.error(e.getMessage());
                    }
                })
                // Hook 回调：打印实时指标
                .debugHook(event -> {
                    hookEvents.add(event);
                    System.out.printf("[HOOK] iteration=%s toolCalls=%s tokens=%s elapsed=%dms type=%s%n",
                            event.getIteration(),
                            event.getToolCallCount(),
                            event.getTotalTokens(),
                            event.getElapsedMillis(),
                            event.getType());
                });
        return agent;
    }

    // ==================== 天气查询（HTTP 直连 wttr.in） ====================

    private String queryWeather(String city) {
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
            String weatherDesc = extractJsonValue(json, "weatherDesc");
            String feelsLikeC = extractJsonValue(json, "FeelsLikeC");
            String result = String.format("%s 当前 %s°C（体感%s°C） 湿度%d%% %s",
                    city, tempC, feelsLikeC, humidity, weatherDesc);
            System.out.println("[weather] " + result);
            return result;
        } catch (Exception e) {
            String err = "天气查询异常 (" + city + "): " + e.getMessage();
            System.out.println("[weather] " + err);
            return err;
        }
    }

    private static int parseJsonInt(String json, String key) {
        try {
            int ki = json.indexOf("\"" + key + "\"");
            if (ki < 0) return -1;
            int ci = json.indexOf(':', ki);
            if (ci < 0) return -1;
            int si = json.indexOf('"', ci + 1);
            String val = json.substring(ci + 1, si).trim();
            return Integer.parseInt(val.replace("\"", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String extractJsonValue(String json, String key) {
        try {
            int ki = json.indexOf("\"" + key + "\"");
            if (ki < 0) return "?";
            int ci = json.indexOf(':', ki);
            if (ci < 0) return "?";
            int si = json.indexOf('"', ci + 1);
            int ei = json.indexOf('"', si + 1);
            return json.substring(si + 1, ei);
        } catch (Exception e) {
            return "?";
        }
    }

    // ==================== ChatClient 工厂 ====================

    private ChatClient createChatClient(String provider, String apiKey) {
        String baseUrl = "https://api.openai.com/v1";
        String model = "gpt-4o-mini";
        if ("deepseek".equals(provider)) {
            baseUrl = "https://api.deepseek.com/v1";
            model = "deepseek-chat";
        }
        return ChatClient.create(provider, apiKey)
                .baseUrl(baseUrl)
                .model(model);
    }

    private ChatClient createMockChatClient() {
        return new ChatClient() {
            @Override
            public String chatSync(String prompt) {
                return """
                        我将执行以下步骤：
                        1. 调用 get_weather(city="北京") 查询天气
                        2. 调用 save_report(path="weather_report.txt", content="<天气结果>") 写入报告
                        请按上述步骤执行。
                        """;
            }

            @Override
            public void chat(String prompt, Consumer<ChatResponse> consumer) {
                chat(prompt, consumer, () -> {}, e -> {});
            }

            @Override
            public void chat(String prompt, Consumer<ChatResponse> consumer,
                             Runnable onComplete, Consumer<Throwable> onError) {
                consumer.accept(ChatResponse.builder().state(ChatResponse.State.START).build());
                String out = chatSync(prompt);
                consumer.accept(ChatResponse.builder().state(ChatResponse.State.STREAMING).content(out).build());
                consumer.accept(ChatResponse.builder().state(ChatResponse.State.STOP).fullContent(out).build());
                onComplete.run();
            }

            @Override
            public List<com.chua.common.support.ai.chat.ModelDefinition> models() {
                return List.of();
            }

            @Override
            public void close() {
            }
        };
    }

    // ==================== 工具方法 ====================

    private static Path createTempWorkDir(String prefix) throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), prefix + "-" + System.currentTimeMillis());
        Files.createDirectories(dir);
        System.out.println("[workdir] " + dir.toAbsolutePath());
        return dir;
    }

    private void printSummary() {
        System.out.println("\n========== 执行摘要 ==========");
        long totalTokens = hookEvents.stream()
                .mapToLong(e -> e.getTotalTokens() != null ? e.getTotalTokens() : 0)
                .max().orElse(0);
        long maxElapsed = hookEvents.stream()
                .mapToLong(e -> e.getElapsedMillis() != null ? e.getElapsedMillis() : 0)
                .max().orElse(0);
        int distinctIterations = (int) hookEvents.stream()
                .map(AgentHookEvent::getIteration)
                .filter(v -> v != null)
                .distinct()
                .count();
        int maxToolCalls = hookEvents.stream()
                .mapToInt(e -> e.getToolCallCount() != null ? e.getToolCallCount() : 0)
                .max().orElse(0);
        System.out.printf("  总 Hook 事件数: %d%n", hookEvents.size());
        System.out.printf("  执行轮次 (iteration): %d%n", distinctIterations);
        System.out.printf("  最大工具调用次数: %d%n", maxToolCalls);
        System.out.printf("  累计最大 Token 数: %d%n", totalTokens);
        System.out.printf("  总耗时: %d ms%n", maxElapsed);
        System.out.println("==============================");
    }
}

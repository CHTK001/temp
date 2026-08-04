package com.chua.example.ai.chat;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.probe.ProbeDimension;
import com.chua.common.support.ai.probe.ProbeReport;
import com.chua.common.support.ai.probe.ProbeResult;
import com.chua.common.support.utils.CommandLine;
import com.chua.openai.support.OpenAiProbeStation;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 中转站真伪探测器示例。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AiProxyDetectorExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认测试模型
     */
    private static final String DEFAULT_MODEL = "gpt-4";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("AiProxyDetectorExample")
                .register("url", "u", "API 基础地址（必填，如 https://api.example.com/v1）")
                .register("key", "k", "API Key（必填）")
                .register("model", "m", "指定探测模型（默认: " + DEFAULT_MODEL + "）", DEFAULT_MODEL)
                .register("file", "f", "从文件读取配置（支持 HTML/Markdown）")
                .register("html", "生成 HTML 报告")
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String baseUrl = cli.get("url");
        String apiKey = cli.get("key");
        String model = cli.get("model", DEFAULT_MODEL);

        String file = cli.get("file");
        if (file != null) {
            ParsedConfig parsed = parseFile(file, baseUrl, apiKey, model);
            baseUrl = parsed.url;
            apiKey = parsed.key;
            model = parsed.model != null ? parsed.model : model;
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            log.error("[ERROR] 必须指定 --url 参数 或使用 --file <HTML/MD 路径>");
            cli.help();
            System.exit(EXIT_CODE_FAILURE);
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.error("[ERROR] 必须指定 --key 参数 或使用 --file <HTML/MD 路径>");
            cli.help();
            System.exit(EXIT_CODE_FAILURE);
            return;
        }

        log.info("===== AiProxyDetectorExample [url={}, model={}] =====", baseUrl, model);

        boolean allPassed = runTest(baseUrl, apiKey, model);

        log.info("-----");
        if (allPassed) {
            log.info("[PASS] 探测完成");
            System.exit(EXIT_CODE_SUCCESS);
        } else {
            log.info("[FAIL] 探测失败");
            System.exit(EXIT_CODE_FAILURE);
        }
    }

    private static boolean runTest(String baseUrl, String apiKey, String model) {
        ChatClientSetting setting = ChatClientSetting.builder()
                .provider("openai")
                .appKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build();

        OpenAiProbeStation probeStation = new OpenAiProbeStation(setting);
        ProbeReport report = probeStation.probe();

        printReport(report);
        return true;
    }

    private static void printReport(ProbeReport report) {
        log.info("=== AI 中转站真伪探测报告 ===");
        log.info("");
        log.info("总耗时: {} ms", report.durationMillis());
        log.info("综合置信度: {}", String.format("%.2f%%", report.overallConfidence() * 100));
        log.info("最终判词: {}", report.verdict());
        log.info("疑似真实模型: {}", report.suspectedModel() != null ? report.suspectedModel() : "未知");
        log.info("疑似代理框架: {}", report.proxyFramework() != null ? report.proxyFramework() : "无");
        log.info("");

        log.info("--- 维度详情 ---");
        for (ProbeResult result : report.results()) {
            String status = result.passed() ? "[PASS]" : "[FAIL]";
            String dimName = getDimensionDisplayName(result.dimension());
            log.info("{} {}{}  置信度: {}  {}", status,
                    String.format("%-30s", dimName),
                    "",
                    String.format("%.2f%%", result.confidence() * 100),
                    result.detail());
        }

        if (report.overallConfidence() >= 0.7) {
            log.info("\n✅ 判定结果: 真实模型");
        } else if (report.overallConfidence() < 0.3) {
            log.info("\n⚠️  判定结果: 疑似中转站");
        } else {
            log.info("\n❓ 判定结果: 无法确定");
        }
    }

    private static String getDimensionDisplayName(ProbeDimension dimension) {
        return switch (dimension) {
            case MODELS_SCAN -> "模型列表扫描";
            case MODEL_MATRIX_SNIFF -> "模型名矩阵嗅探";
            case ERROR_MESSAGE_ANALYSIS -> "错误消息分析";
            case IDENTITY_PROBE -> "身份追问";
            case JAILBREAK_PROBE -> "越狱探测";
            case KNOWLEDGE_CUTOFF -> "知识截止日期";
            case SAFETY_ALIGNMENT -> "安全对齐指纹";
            case MATH_TRAP -> "数学推理陷阱";
            case PROMPT_TOKEN_INJECTION -> "Prompt Token 注入检测";
            case FUNCTION_CALLING -> "Function Calling 探测";
            case HTTP_HEADERS -> "HTTP 响应头识别";
            case SPEED_BENCHMARK -> "速度基准测试";
        };
    }

    /**
     * 文件解析结果。
     *
     * @param url   URL
     * @param key   KEY
     * @param model 模型
     */
    private record ParsedConfig(String url, String key, String model) {}

    private static ParsedConfig parseFile(String filePath, String url, String key, String model) {
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(path)) {
            log.error("[ERROR] 文件不存在: {}", filePath);
            return new ParsedConfig(url, key, model);
        }
        try {
            String content = java.nio.file.Files.readString(path);
            String parsedKey = firstMatch(content, "(sk-[A-Za-z0-9]{20,})");
            String parsedUrl = firstMatch(content, "(https?://[^\\s\"<>]+)");
            String parsedModel = firstMatch(content, "class=\"model-item\">([^<]+)</span>");
            if (parsedModel == null) {
                parsedModel = firstMatch(content, "`(agnes-[A-Za-z0-9._-]+)`");
            }
            if (parsedModel == null) {
                parsedModel = firstMatch(content, "(agnes-[A-Za-z0-9._-]+)");
            }
            return new ParsedConfig(
                    parsedUrl != null ? parsedUrl : url,
                    parsedKey != null ? parsedKey : key,
                    parsedModel != null ? parsedModel : model
            );
        } catch (Exception e) {
            log.error("[ERROR] 读取文件失败: {}", e.getMessage());
            return new ParsedConfig(url, key, model);
        }
    }

    private static String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
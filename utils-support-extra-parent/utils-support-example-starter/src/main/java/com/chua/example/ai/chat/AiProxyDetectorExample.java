package com.chua.example.ai.chat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.probe.ProbeDimension;
import com.chua.common.support.ai.probe.ProbeReport;
import com.chua.common.support.ai.probe.ProbeResult;
import com.chua.openai.support.OpenAiProbeStation;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 中转站真伪探测器示例。
 *
 * <p>基于 OpenAiProbeStation 实现，支持对 OpenAI 兼容接口进行 12 维度真伪探测：</p>
 *
 * <ol>
 *   <li>模型列表扫描</li>
 *   <li>模型名矩阵嗅探</li>
 *   <li>错误消息分析</li>
 *   <li>身份追问</li>
 *   <li>越狱探测</li>
 *   <li>知识截止日期</li>
 *   <li>安全对齐指纹</li>
 *   <li>数学推理陷阱</li>
 *   <li>Prompt Token 注入检测</li>
 *   <li>Function Calling 探测</li>
 *   <li>HTTP 响应头识别</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 交互模式（依次输入 baseUrl 和 apiKey）
 *   java AiProxyDetectorExample
 *
 *   # 命令行模式
 *   java AiProxyDetectorExample --url https://api.example.com --key sk-xxx
 *
 *   # 指定模型进行探测
 *   java AiProxyDetectorExample --url https://api.example.com --key sk-xxx --model gpt-4o
 *
 *   # 生成 HTML 报告
 *   java AiProxyDetectorExample --url https://api.example.com --key sk-xxx --html
 *
 *   # 打印帮助
 *   java AiProxyDetectorExample --help
 * </pre>
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

    /**
     * 主入口：解析参数并执行探测。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        Args resolved = parsed;
        if (parsed.file() != null) {
            resolved = parseFile(parsed.file(), parsed);
        }

        String baseUrl = resolved.url();
        String apiKey = resolved.apiKey();
        String model = resolved.model() != null ? resolved.model() : DEFAULT_MODEL;

        if (baseUrl == null || baseUrl.isBlank()) {
            System.err.println("[ERROR] 必须指定 --url 参数 或使用 --file <HTML/MD 路径>");
            printHelp();
            System.exit(EXIT_CODE_FAILURE);
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ERROR] 必须指定 --key 参数 或使用 --file <HTML/MD 路径>");
            printHelp();
            System.exit(EXIT_CODE_FAILURE);
            return;
        }

        log.info("===== AiProxyDetectorExample [url={}, model={}] =====", baseUrl, model);

        boolean allPassed = runTest(baseUrl, apiKey, model);

        System.out.println("-----");
        if (allPassed) {
            System.out.println("[PASS] 探测完成");
            System.exit(EXIT_CODE_SUCCESS);
        } else {
            System.out.println("[FAIL] 探测失败");
            System.exit(EXIT_CODE_FAILURE);
        }
    }

    /**
     * 执行探测测试。
     *
     * @param baseUrl API 基础地址
     * @param apiKey  API 密钥
     * @param model   指定探测模型
     * @return 是否全部通过
     */
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

    /**
     * 打印探测报告。
     *
     * @param report 探测报告
     */
    private static void printReport(ProbeReport report) {
        System.out.println("=== AI 中转站真伪探测报告 ===");
        System.out.println();
        System.out.println("总耗时: " + report.durationMillis() + " ms");
        System.out.println("综合置信度: " + String.format("%.2f%%", report.overallConfidence() * 100));
        System.out.println("最终判词: " + report.verdict());
        System.out.println("疑似真实模型: " + (report.suspectedModel() != null ? report.suspectedModel() : "未知"));
        System.out.println("疑似代理框架: " + (report.proxyFramework() != null ? report.proxyFramework() : "无"));
        System.out.println();

        System.out.println("--- 维度详情 ---");
        for (ProbeResult result : report.results()) {
            String status = result.passed() ? "[PASS]" : "[FAIL]";
            String dimName = getDimensionDisplayName(result.dimension());
            System.out.printf("%s %-30s 置信度: %.2f%%  %s%n",
                    status, dimName, result.confidence() * 100, result.detail());
        }

        if (report.overallConfidence() >= 0.7) {
            System.out.println("\n✅ 判定结果: 真实模型");
        } else if (report.overallConfidence() < 0.3) {
            System.out.println("\n⚠️  判定结果: 疑似中转站");
        } else {
            System.out.println("\n❓ 判定结果: 无法确定");
        }
    }

    /**
     * 获取维度的显示名称。
     *
     * @param dimension 探测维度
     * @return 显示名称
     */
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

    // ==================== 文件读取 ====================

    /**
     * 从文件读取 API 配置。
     *
     * @param filePath 文件路径
     * @param fallback 回退参数
     * @return 填充后的参数
     */
    private static Args parseFile(String filePath, Args fallback) {
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(path)) {
            System.err.println("[ERROR] 文件不存在: " + filePath);
            return fallback;
        }

        try {
            String content = java.nio.file.Files.readString(path);
            String key = firstMatch(content, "(sk-[A-Za-z0-9]{20,})");
            String url = firstMatch(content, "(https?://[^\\s\"<>]+)");
            String model = firstMatch(content, "class=\"model-item\">([^<]+)</span>");
            if (model == null) {
                model = firstMatch(content, "`(agnes-[A-Za-z0-9._-]+)`");
            }
            if (model == null) {
                model = firstMatch(content, "(agnes-[A-Za-z0-9._-]+)");
            }

            return fallback
                    .withApiKey(key != null ? key : fallback.apiKey())
                    .withUrl(url != null ? url : fallback.url())
                    .withModel(model != null ? model : fallback.model());
        } catch (Exception e) {
            System.err.println("[ERROR] 读取文件失败: " + e.getMessage());
            return fallback;
        }
    }

    /**
     * 提取第一个匹配项。
     */
    private static String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ==================== 参数解析 ====================

    /**
     * 解析命令行参数。
     *
     * @param args 命令行参数数组
     * @return 参数对象
     */
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--url", "-u" -> {
                    if (index + 1 < args.length) {
                        result = result.withUrl(args[++index]);
                    }
                }
                case "--key", "-k" -> {
                    if (index + 1 < args.length) {
                        result = result.withApiKey(args[++index]);
                    }
                }
                case "--model", "-m" -> {
                    if (index + 1 < args.length) {
                        result = result.withModel(args[++index]);
                    }
                }
                case "--file", "-f" -> {
                    if (index + 1 < args.length) {
                        result = result.withFile(args[++index]);
                    }
                }
                case "--html" -> result = result.withHtml(true);
                case "--help", "-h" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("AI 中转站真伪探测器示例");
        System.out.println();
        System.out.println("用法: java AiProxyDetectorExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --url,    -u <url>     API 基础地址（必填，如 https://api.example.com/v1）");
        System.out.println("  --key,    -k <key>     API Key（必填）");
        System.out.println("  --model,  -m <model>   指定探测模型（默认: gpt-4）");
        System.out.println("  --file,   -f <path>    从文件读取配置（支持 HTML/Markdown）");
        System.out.println("  --html                 生成 HTML 报告");
        System.out.println("  --help,  -h             显示此帮助");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java AiProxyDetectorExample --url https://api.openai.com/v1 --key sk-xxx");
        System.out.println("  java AiProxyDetectorExample --url https://api.siliconflow.cn/v1 --key sk-xxx --model gpt-4o");
    }

    // ==================== 参数容器 ====================

    /**
     * 命令行参数容器。
     *
     * @param url    API 基础地址
     * @param apiKey API 密钥
     * @param model  探测模型
     * @param html   是否生成 HTML 报告
     * @param help   是否显示帮助
     * @author CH
     * @since 4.0.0.42
     */
    private record Args(
        String url,
        String apiKey,
        String model,
        boolean html,
        boolean help,
        String file
    ) {
        Args() {
            this(null, null, null, false, false, null);
        }

        public Args withUrl(String url) {
            return new Args(url, apiKey, model, html, help, file);
        }

        public Args withApiKey(String apiKey) {
            return new Args(url, apiKey, model, html, help, file);
        }

        public Args withModel(String model) {
            return new Args(url, apiKey, model, html, help, file);
        }

        public Args withHtml(boolean html) {
            return new Args(url, apiKey, model, html, help, file);
        }

        public Args withHelp(boolean help) {
            return new Args(url, apiKey, model, html, help, file);
        }

        public Args withFile(String file) {
            return new Args(url, apiKey, model, html, help, file);
        }
    }
}
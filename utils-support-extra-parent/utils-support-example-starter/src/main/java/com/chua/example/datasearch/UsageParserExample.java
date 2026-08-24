package com.chua.example.datasearch;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.spi.ServiceProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具用量解析器（UsageParser）演示。
 *
 * <p>遍历指定或全部 UsageParser SPI 实现，解析本地用量记录并输出汇总表，
 * 用于验证各解析器的记录数与 Token 统计是否正常。</p>
 *
 * <p>SPI 多实现场景：通过 {@code --spi=<implName>} 指定要测试的实现，
 * 不传或传 {@code all} 时遍历全部实现。例如：</p>
 *
 * <pre>
 * java UsageParserExample --spi=claude-code
 * java UsageParserExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class UsageParserExample {

    /** 遍历全部实现的参数值 */
    private static final String SPI_ALL = "all";

    /** 参数前缀 */
    private static final String PARAM_PREFIX = "--";

    /** 创建 UsageParserExample 实例 */
    private UsageParserExample() {
    }

    /**
     * 独立入口：解析用量记录并输出汇总表。
     *
     * <p>参数格式 {@code --key=value} 或 {@code --key value}：</p>
     * <ul>
     *     <li>{@code --spi=} UsageParser SPI 名称（默认 all，遍历全部实现）</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);
        String spiName = params.getOrDefault("spi", SPI_ALL);
        Map<String, UsageParser> parsers = resolveParsers(spiName);
        if (parsers.isEmpty()) {
            System.out.println("[FAIL] 未找到任何 UsageParser 实现: spi=" + spiName);
            System.exit(1);
            return;
        }
        int failures = 0;
        System.out.printf("%-15s %-9s %-14s %-14s%n", "SPI", "RECORDS", "INPUT_TOKENS", "OUTPUT_TOKENS");
        for (Map.Entry<String, UsageParser> entry : parsers.entrySet()) {
            if (!runParser(entry.getKey(), entry.getValue())) {
                failures++;
            }
        }
        if (failures > 0) {
            System.out.println("[FAIL] 失败实现数: " + failures);
            System.exit(1);
            return;
        }
        System.out.println("[PASS] 全部通过, 共 " + parsers.size() + " 个实现");
        System.exit(0);
    }

    /**
     * 解析命令行参数，支持 {@code --key=value} 与 {@code --key value} 两种形式。
     *
     * @param args 命令行参数
     * @return 参数键值对
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith(PARAM_PREFIX)) {
                continue;
            }
            int idx = arg.indexOf('=');
            if (idx > PARAM_PREFIX.length()) {
                params.put(arg.substring(PARAM_PREFIX.length(), idx), arg.substring(idx + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith(PARAM_PREFIX)) {
                params.put(arg.substring(PARAM_PREFIX.length()), args[++i]);
            }
        }
        return params;
    }

    /**
     * 解析 SPI 名称对应的解析器集合。
     *
     * @param spiName SPI 名称，all 表示全部
     * @return 名称到解析器的映射
     */
    private static Map<String, UsageParser> resolveParsers(String spiName) {
        ServiceProvider<UsageParser> provider = ServiceProvider.of(UsageParser.class);
        if (SPI_ALL.equalsIgnoreCase(spiName)) {
            return provider.list();
        }
        UsageParser parser = provider.getExtension(spiName);
        Map<String, UsageParser> result = new LinkedHashMap<>(2);
        if (parser != null) {
            result.put(spiName, parser);
        }
        return result;
    }

    /**
     * 执行单个解析器并输出汇总行。
     *
     * @param name 解析器 SPI 名称
     * @param parser 解析器实例
     * @return true 表示执行成功
     */
    private static boolean runParser(String name, UsageParser parser) {
        try {
            List<AiUsage> records = parser.parseAll();
            check(records != null, "records 为 null");
            long inputSum = 0L;
            long outputSum = 0L;
            for (AiUsage record : records) {
                if (record.getInputTokens() != null) {
                    inputSum += record.getInputTokens();
                }
                if (record.getOutputTokens() != null) {
                    outputSum += record.getOutputTokens();
                }
            }
            System.out.printf("%-15s %-9d %-14d %-14d%n", name, records.size(), inputSum, outputSum);
            return true;
        } catch (Exception e) {
            System.out.println("[FAIL] " + name + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 断言条件成立，否则抛出 AssertionError。
     *
     * @param condition 条件
     * @param message 失败消息
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

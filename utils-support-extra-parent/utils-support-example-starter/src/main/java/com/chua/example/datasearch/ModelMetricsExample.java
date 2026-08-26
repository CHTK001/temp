package com.chua.example.datasearch;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.datasearch.pricing.spi.AbstractModelMetricsProvider;
import com.chua.common.support.datasearch.pricing.spi.ModelMetricsProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 模型指标提供者（ModelMetricsProvider）演示。
 *
 * <p>遍历指定或全部 ModelMetricsProvider SPI 实现，实测在线抓取结果并与
 * classpath 内置基线对比，输出价格、智能指数、速度、延迟等真实数据样本。</p>
 *
 * <p>SPI 多实现场景：通过 {@code --spi=<implName>} 指定要测试的实现，
 * 不传或传 {@code all} 时遍历全部实现。例如：</p>
 *
 * <pre>
 * java ModelMetricsExample --spi=amazon
 * java ModelMetricsExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ModelMetricsExample {

    /** 遍历全部实现的参数值 */
    private static final String SPI_ALL = "all";

    /** 参数前缀 */
    private static final String PARAM_PREFIX = "--";

    /** 创建 ModelMetricsExample 实例 */
    private ModelMetricsExample() {
    }

    /**
     * 独立入口：逐个实测定价提供者并输出汇总表。
     *
     * <p>参数格式 {@code --key=value} 或 {@code --key value}：</p>
     * <ul>
     *     <li>{@code --spi=} ModelMetricsProvider SPI 名称（默认 all，遍历全部实现）</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        String spiName = parseArgs(args).getOrDefault("spi", SPI_ALL);
        int failures = 0;
        int total = 0;
        System.out.printf("%-18s %7s %8s   %s%n",
                "PROVIDER", "ONLINE", "BUNDLED", "SAMPLE(first .. last)");
        for (ModelMetricsProvider p : ServiceLoader.load(ModelMetricsProvider.class)) {
            if (!(p instanceof AbstractModelMetricsProvider ap)) {
                continue;
            }
            String name = p.name();
            if (!SPI_ALL.equalsIgnoreCase(spiName) && !spiName.equalsIgnoreCase(name)) {
                continue;
            }
            total++;
            try {
                List<ModelDefinition> online = ap.fetchOnlinePricing();
                check(online != null, "fetchOnlinePricing 返回 null");
                int bundled = bundledCount(ap);
                String sample = describe(online);
                System.out.printf("%-18s %7d %8d   %s%n", name, online.size(), bundled, sample);
            } catch (Throwable t) {
                failures++;
                System.out.printf("%-18s %7s %8d   %s%n", name, "ERR", "-", "[FAIL] " + t.getMessage());
            }
        }
        System.out.println("[PASS] 实测完成, 共 " + total + " 个实现");
        if (failures > 0) {
            System.out.println("[FAIL] 异常实现数: " + failures);
            System.exit(1);
            return;
        }
        System.exit(0);
    }

    /**
     * 解析命令行参数，支持 {@code --key=value} 与 {@code --key value} 两种形式。
     *
     * @param args 命令行参数
     * @return 参数键值对
     */
    private static java.util.Map<String, String> parseArgs(String[] args) {
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
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
     * 统计 classpath 内置基线 JSON 的条目数。
     *
     * @param provider 模型指标提供者
     * @return 内置条目数，读取失败返回 -1
     */
    private static int bundledCount(AbstractModelMetricsProvider provider) {
        try {
            Method method = AbstractModelMetricsProvider.class.getDeclaredMethod("readClasspathPricing");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ModelDefinition> bundled = (List<ModelDefinition>) method.invoke(provider);
            return bundled == null ? -1 : bundled.size();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 格式化首末两条模型样本（含智能/速度/延迟维度）。
     *
     * @param models 模型指标列表
     * @return 样本描述
     */
    private static String describe(List<ModelDefinition> models) {
        if (models.isEmpty()) {
            return "(empty)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(sample(models.get(0)));
        if (models.size() > 1) {
            sb.append(" .. ").append(sample(models.get(models.size() - 1)));
        }
        return sb.toString();
    }

    /**
     * 单条模型样本。
     *
     * @param m 模型定义
     * @return 样本文本
     */
    private static String sample(ModelDefinition m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getId()).append("|in=").append(m.getInputUnitPrice())
                .append("|out=").append(m.getOutputUnitPrice())
                .append('|').append(m.getCurrency());
        if (m.getIntelligenceIndex() != null) {
            sb.append("|intel=").append(m.getIntelligenceIndex());
        }
        if (m.getOutputSpeedTokensPerSecond() != null) {
            sb.append("|spd=").append(m.getOutputSpeedTokensPerSecond());
        }
        if (m.getLatencyFirstTokenSeconds() != null) {
            sb.append("|lat=").append(m.getLatencyFirstTokenSeconds());
        }
        return sb.toString();
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

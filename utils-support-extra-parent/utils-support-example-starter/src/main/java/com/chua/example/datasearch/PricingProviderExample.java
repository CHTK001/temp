package com.chua.example.datasearch;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.common.support.datasearch.pricing.spi.AbstractPricingProvider;
import com.chua.common.support.datasearch.pricing.spi.PricingProvider;
import com.chua.common.support.reflection.ReflectUtils;

import java.util.List;
import java.util.ServiceLoader;

/**
 * AI 模型定价提供者（PricingProvider）演示。
 *
 * <p>遍历指定或全部 PricingProvider SPI 实现，实测在线抓取结果并与
 * classpath 内置基线对比，输出每家的真实数据样本。</p>
 *
 * <p>SPI 多实现场景：通过 {@code --spi=<implName>} 指定要测试的实现，
 * 不传或传 {@code all} 时遍历全部实现。例如：</p>
 *
 * <pre>
 * java PricingProviderExample --spi=amazon
 * java PricingProviderExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class PricingProviderExample {

    /** 遍历全部实现的参数值 */
    private static final String SPI_ALL = "all";

    /** 参数前缀 */
    private static final String PARAM_PREFIX = "--";

    /** 创建 PricingProviderExample 实例 */
    private PricingProviderExample() {
    }

    /**
     * 独立入口：逐个实测定价提供者并输出汇总表。
     *
     * <p>参数格式 {@code --key=value} 或 {@code --key value}：</p>
     * <ul>
     *     <li>{@code --spi=} PricingProvider SPI 名称（默认 all，遍历全部实现）</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        String spiName = parseArgs(args).getOrDefault("spi", SPI_ALL);
        int failures = 0;
        int total = 0;
        System.out.printf("%-13s %7s %8s   %s%n",
                "PROVIDER", "ONLINE", "BUNDLED", "SAMPLE(first .. last)");
        for (PricingProvider p : ServiceLoader.load(PricingProvider.class)) {
            if (!(p instanceof AbstractPricingProvider ap)) {
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
                System.out.printf("%-13s %7d %8d   %s%n", name, online.size(), bundled, sample);
            } catch (Throwable t) {
                failures++;
                System.out.printf("%-13s %7s %8d   %s%n", name, "ERR", "-", "[FAIL] " + t.getMessage());
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
     * @param provider 定价提供者
     * @return 内置条目数，读取失败返回 -1
     */
    private static int bundledCount(AbstractPricingProvider provider) {
        try {
            @SuppressWarnings("unchecked")
            List<ModelDefinition> bundled = (List<ModelDefinition>) ReflectUtils.invoke(
                    provider, "readClasspathPricing");
            return bundled == null ? -1 : bundled.size();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 格式化首末两条模型样本。
     *
     * @param models 模型定价列表
     * @return 样本描述
     */
    private static String describe(List<ModelDefinition> models) {
        if (models.isEmpty()) {
            return "(empty)";
        }
        ModelDefinition first = models.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(first.getId()).append('|').append(first.getInputUnitPrice())
                .append('|').append(first.getOutputUnitPrice()).append('|').append(first.getCurrency());
        if (models.size() > 1) {
            ModelDefinition last = models.get(models.size() - 1);
            sb.append(" .. ").append(last.getId()).append('|').append(last.getInputUnitPrice())
                    .append('|').append(last.getOutputUnitPrice());
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

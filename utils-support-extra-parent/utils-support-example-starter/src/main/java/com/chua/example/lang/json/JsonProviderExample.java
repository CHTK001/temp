package com.chua.example.lang.json;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * JsonProvider 驱动型示例 — 同名 SPI {@link JsonProviderExampleSpi} 的独立 main 入口。
 *
 * <p>解析 --key=value 与 --key value 两种形式的命令行参数进 LinkedHashMap，
 * 经 {@code ServiceProvider.of(Example.class).collect()} 发现全部 Example 实现后按
 * {@code name()} 命中 json-provider，真实执行三实现（gson / fory / fastjson）自检矩阵；
 * 关键输出以 [PASS]/[FAIL] 前缀呈现，任一场景失败即以退出码 1 终止。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 全部实现（默认）
 *   java JsonProviderExample
 *
 *   # 指定实现
 *   java JsonProviderExample --type=gson
 *   java JsonProviderExample --type fory
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class JsonProviderExample {

    private JsonProviderExample() {
    }

    /**
     * 独立入口：解析参数 → SPI 发现 json-provider → 执行全实现与单实现两轮自检。
     *
     * @param args 支持 --type=gson|fory|fastjson|all（默认 all）
     */
    public static void main(String[] args) {
        Map<String, String> params = parseArgs(args);
        Example example = discover("json-provider");
        if (example == null || !"json".equals(example.module())) {
            System.out.println("[FAIL] spi-discovery: getExtension(json-provider) 应命中且 module=json");
            System.exit(1);
        }
        System.out.println("[PASS] spi-discovery: " + example.getClass().getSimpleName()
                + " module=" + example.module());
        boolean passed = example.run(params);
        if (!passed) {
            System.out.println("[FAIL] run-all: JsonProvider 三实现自检矩阵未全部通过");
            System.exit(1);
        }
        System.out.println("[PASS] run-all: JsonProvider 自检矩阵通过 type=" + params.getOrDefault("type", "all"));
        Map<String, String> single = new LinkedHashMap<>();
        single.put("type", "gson");
        if (!example.run(single)) {
            System.out.println("[FAIL] run-single: 指定 --type=gson 单实现自检未通过");
            System.exit(1);
        }
        System.out.println("[PASS] run-single: --type=gson 单实现自检通过");
        System.out.println("[PASS] JsonProviderExample 全部场景执行完成");
    }

    /**
     * 经 ServiceProvider 发现全部 Example 实现并按 SPI name() 命中目标。
     *
     * @param name 目标示例名称
     * @return 命中的实现，未发现返回 null
     */
    private static Example discover(String name) {
        for (Example candidate : ServiceProvider.of(Example.class).collect()) {
            if (name.equals(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 将命令行参数解析为有序映射，支持 --key=value 与 --key value 两种形式。
     *
     * @param args 原始命令行参数
     * @return 有序参数映射
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            int eq = key.indexOf('=');
            if (eq >= 0) {
                params.put(key.substring(0, eq), key.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                params.put(key, args[++i]);
            } else {
                params.put(key, "");
            }
        }
        return params;
    }
}

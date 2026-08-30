package com.chua.example.util;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 示例通用工具集。
 *
 * <p>集中存放各 Example 重复使用的辅助常量与方法，避免每个文件独立实现相同逻辑。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 退出码
 * System.exit(ExampleUtils.FAILURE);
 *
 * // 参数解析
 * Map<String, String> args = ExampleUtils.parseArgs(rawArgs);
 *
 * // 场景计时与结果输出（并发模块用）
 * boolean ok = ExampleUtils.timed("scenario-name", () -> runScenario());
 * ExampleUtils.print("scenario-name", ok);
 *
 * // Spi 自检输出
 * ExampleUtils.pass();
 * ExampleUtils.fail("reason");
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ExampleUtils {

    /** 退出码：成功 */
    public static final int SUCCESS = 0;
    /** 退出码：失败 */
    public static final int FAILURE = 1;

    private ExampleUtils() {
    }

    /**
     * 解析命令行参数，支持 {@code --key=value} 与 {@code --key value} 两种格式。
     *
     * @param args 原始命令行参数数组
     * @return key → value 映射；无值开关默认映射为 {@code "true"}
     */
    public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            int eq = key.indexOf('=');
            if (eq > 0) {
                result.put(key.substring(0, eq), key.substring(eq + 1));
                continue;
            }
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                result.put(key, args[++i]);
            } else {
                result.put(key, "true");
            }
        }
        return result;
    }

    /**
     * 计时执行场景并返回是否通过。
     *
     * @param name      场景名称，用于日志输出
     * @param scenario  待执行的场景逻辑
     * @return 场景是否通过
     */
    public static boolean timed(String name, BooleanSupplier scenario) {
        long start = System.currentTimeMillis();
        boolean ok = scenario.getAsBoolean();
        System.out.println("[TIME] " + name + " " + (System.currentTimeMillis() - start) + "ms");
        return ok;
    }

    /**
     * 打印场景通过/失败结果。
     *
     * @param name 场景名称
     * @param ok   是否通过
     */
    public static void print(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    /**
     * Spi 自检通过日志输出。
     */
    public static void pass() {
        log.info("  \u2713 通过");
    }

    /**
     * Spi 自检失败日志输出。
     *
     * @param msg 失败原因
     */
    public static void fail(String msg) {
        log.info("  \u2717 失败: {}", msg);
    }
}

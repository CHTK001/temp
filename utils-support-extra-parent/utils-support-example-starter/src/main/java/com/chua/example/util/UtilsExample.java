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
 * System.exit(UtilsExample.FAILURE);
 *
 * // 参数解析
 * Map<String, String> args = UtilsExample.parseArgs(rawArgs);
 *
 * // 场景计时与结果输出（并发模块用）
 * boolean ok = UtilsExample.timed("scenario-name", () -> runScenario());
 * UtilsExample.print("scenario-name", ok);
 *
 * // Spi 自检输出
 * UtilsExample.pass();
 * UtilsExample.fail("reason");
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class UtilsExample {

    /** 退出码：成功 */
    public static final int SUCCESS = 0;
    /** 退出码：失败 */
    public static final int FAILURE = 1;

    private UtilsExample() {
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
        log.info("[TIME] {} {}ms", name, System.currentTimeMillis() - start);
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
     * 打印场景结果及详细信息（PASS/FAIL + 明细），替代各 Example 内重复的私有 printResult。
     *
     * @param name   场景名
     * @param passed 是否通过
     * @param detail 详细信息
     */
    public static void print(String name, boolean passed, String detail) {
        log.info("{} {} → {}", passed ? "[PASS]" : "[FAIL]", name, detail);
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
    public static boolean fail(String msg) {
        log.warn("[FAIL] {}", msg);
        return false;
    }

    /**
     * 场景失败日志输出（带场景名 + 异常），便于 lambda 收口。
     *
     * @param name 场景名称
     * @param e    异常
     * @return {@code false}（便于链式 {@code return UtilsExample.fail(name, e);}）
     */
    public static boolean fail(String name, Exception e) {
        log.warn("[FAIL] {}: {}", name, e.getMessage());
        return false;
    }

    /**
     * 场景失败日志输出（带场景名 + 自定义消息），便于 lambda 收口。
     *
     * @param name 场景名称
     * @param msg  失败消息
     * @return {@code false}
     */
    public static boolean fail(String name, String msg) {
        log.warn("[FAIL] {}: {}", name, msg);
        return false;
    }
}

package com.chua.example.utils;

import com.chua.common.support.utils.BeanUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * BeanUtils 性能基准示例，测量属性复制与对象转 Map 的吞吐量。
 *
 * <p>改写自 common-starter 单元测试 BeanUtilsBenchmarkTest，保留 nanoTime 计时逻辑，
 * 轮次经 --iterations 参数可调（默认 10 万），输出 ops/s 表格。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.utils.BeanUtilsBenchmarkExample
 *   java com.chua.example.utils.BeanUtilsBenchmarkExample --iterations=500000
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class BeanUtilsBenchmarkExample {
    private BeanUtilsBenchmarkExample() { }


    /**
     * 默认基准迭代次数
     */
    private static final int DEFAULT_ITERATIONS = 100_000;

    /**
     * 基准源对象
     */
    private static final Source SOURCE = new Source("Alice", 30, 8000.0D, "alice@test.com", "138", "Beijing", true, 1L);

    /**
     * 主入口，先做正确性自检，再依次执行三项基准并输出 ops/s 表格。
     *
     * @param args 支持 --iterations=N 或 --iterations N
     */
    public static void main(String[] args) {
        int iterations = parseIterations(args);
        if (iterations <= 0) {
            log.info("[FAIL] 非法 --iterations 参数: " + iterations);
            System.exit(1);
        }
        log.info("[PERF] ===== BeanUtils 基准（迭代 {} 次）=====", iterations);
        if (!verify()) {
            log.info("[FAIL] copyProperties / objectToMap 正确性自检未通过");
            System.exit(1);
        }
        log.info("[PASS] copyProperties / objectToMap 正确性自检通过");
        System.out.printf("[PERF] %-32s %12s %12s %16s %10s%n",
                "场景", "耗时(ms)", "ops/s", "us/call", "迭代");
        bench("copyProperties(src,target)", iterations, () -> {
            Target target = new Target();
            BeanUtils.copyProperties(SOURCE, target);
        });
        bench("copyProperties(src,Target.class)", iterations, () -> BeanUtils.copyProperties(SOURCE, Target.class));
        bench("objectToMap(src)", iterations, () -> BeanUtils.objectToMap(SOURCE));
        log.info("[PERF] ===== BeanUtils 基准完成 =====");
        log.info("[PASS] BeanUtils 基准全部场景通过");
    }

    /**
     * 执行单组基准：预热后计时循环，输出一行表格结果。
     *
     * @param label      场景名称
     * @param iterations 迭代次数
     * @param task       被测任务
     */
    private static void bench(String label, int iterations, Runnable task) {
        int warmup = Math.min(iterations, 1_000);
        for (int i = 0; i < warmup; i++) {
            task.run();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            task.run();
        }
        long elapsed = System.nanoTime() - start;
        double millis = elapsed / 1_000_000.0D;
        double opsPerSecond = iterations * 1_000_000_000.0D / elapsed;
        double usPerCall = elapsed / (double) iterations / 1000.0D;
        System.out.printf("[PERF] %-32s %12.1f %12s %16.3f %10d%n",
                label, millis, String.format("%,.0f", opsPerSecond), usPerCall, iterations);
        log.info("[PASS] " + label);
    }

    /**
     * 正确性自检：三种复制方式的关键字段均与源对象一致。
     *
     * @return 全部一致返回 true
     */
    private static boolean verify() {
        Target copied = BeanUtils.copyProperties(SOURCE, Target.class);
        Map<String, Object> mapped = BeanUtils.objectToMap(SOURCE);
        return copied != null
                && SOURCE.name.equals(copied.name)
                && SOURCE.age == copied.age
                && SOURCE.salary == copied.salary
                && SOURCE.active == copied.active
                && SOURCE.createTime == copied.createTime
                && SOURCE.name.equals(mapped.get("name"));
    }

    /**
     * 解析 --iterations 参数，支持 --iterations=N 与 --iterations N 两种形式。
     *
     * @param args 命令行参数
     * @return 迭代次数，非法输入返回 -1，缺省返回默认值
     */
    static int parseIterations(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            try {
                if (arg.startsWith("--iterations=")) {
                    return Integer.parseInt(arg.substring("--iterations=".length()));
                }
                if ("--iterations".equals(arg) && i + 1 < args.length) {
                    return Integer.parseInt(args[i + 1]);
                }
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return DEFAULT_ITERATIONS;
    }

    /**
     * 基准源对象。
     */
    static class Source {

        /**
         * 姓名
         */
        String name;

        /**
         * 年龄
         */
        int age;

        /**
         * 薪资
         */
        double salary;

        /**
         * 邮箱
         */
        String email;

        /**
         * 电话
         */
        String phone;

        /**
         * 地址
         */
        String address;

        /**
         * 是否在职
         */
        boolean active;

        /**
         * 创建时间戳
         */
        long createTime;

        /**
         * 创建 Source 实例。
         *
         * @param name 姓名
         * @param age 年龄
         * @param salary 薪资
         * @param email 邮箱
         * @param phone 电话
         * @param address 地址
         * @param active 是否在职
         * @param createTime 创建时间戳
         */
        Source(String name, int age, double salary, String email, String phone,
               String address, boolean active, long createTime) {
            this.name = name;
            this.age = age;
            this.salary = salary;
            this.email = email;
            this.phone = phone;
            this.address = address;
            this.active = active;
            this.createTime = createTime;
        }
    }

    /**
     * 基准目标对象。
     */
    static class Target {

        /**
         * 姓名
         */
        String name;

        /**
         * 年龄
         */
        int age;

        /**
         * 薪资
         */
        double salary;

        /**
         * 邮箱
         */
        String email;

        /**
         * 电话
         */
        String phone;

        /**
         * 地址
         */
        String address;

        /**
         * 是否在职
         */
        boolean active;

        /**
         * 创建时间戳
         */
        long createTime;

        /**
         * 创建 Target 实例。
         */
        Target() {
        }
    }
}

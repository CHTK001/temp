package com.chua.example.utils;

import com.chua.common.support.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * IdUtils 性能基准示例，测量内容寻址 ID 与部分采样 ID 的生成吞吐量。
 *
 * <p>改写自 common-starter 单元测试 IdUtilsBenchmarkTest，保留 nanoTime 计时逻辑，
 * 轮次经 --iterations 参数可调（默认 10 万），输出 ops/s 表格。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.utils.IdUtilsBenchmarkExample
 *   java com.chua.example.utils.IdUtilsBenchmarkExample --iterations=500000
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class IdUtilsBenchmarkExample {

    /**
     * 默认基准迭代次数
     */
    private static final int DEFAULT_ITERATIONS = 100_000;

    /**
     * 基准人员对象（10 字段）
     */
    private static final Person PERSON = new Person("Alice", 30, "alice@test.com",
            8000.0D, "IT", "13800138000", "123 Main St", "Beijing", "CN", true);

    /**
     * 主入口，先做正确性自检，再依次执行两项基准并输出 ops/s 表格。
     *
     * @param args 支持 --iterations=N 或 --iterations N
     */
    public static void main(String[] args) {
        int iterations = parseIterations(args);
        if (iterations <= 0) {
            log.info("[FAIL] 非法 --iterations 参数: {}", iterations);
            System.exit(1);
        }
        log.info("[PERF] ===== IdUtils 基准（迭代 {} 次）=====", iterations);
        if (!verify()) {
            log.info("[FAIL] getId / getPartialId 正确性自检未通过");
            System.exit(1);
        }
        System.out.printf("[PERF] %-24s %12s %12s %16s %10s%n",
                "场景", "耗时(ms)", "ops/s", "us/call", "迭代");
        bench("getId(person)", iterations, () -> IdUtils.getId(PERSON));
        bench("getPartialId(person,0.6)", iterations, () -> IdUtils.getPartialId(PERSON, 0.6D));
        log.info("[PERF] ===== IdUtils 基准完成 [PASS] =====");
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
        System.out.printf("[PERF] %-24s %12.1f %12s %16.3f %10d%n",
                label, millis, String.format("%,.0f", opsPerSecond), usPerCall, iterations);
    }

    /**
     * 正确性自检：ID 非空、多次调用确定、全量比例与 getId 等价。
     *
     * @return 全部通过返回 true
     */
    private static boolean verify() {
        String fullId = IdUtils.getId(PERSON);
        String partialId = IdUtils.getPartialId(PERSON, 0.6D);
        String fullRatioId = IdUtils.getPartialId(PERSON, 1.0D);
        return fullId != null && !fullId.isEmpty()
                && fullId.equals(IdUtils.getId(PERSON))
                && partialId != null && !partialId.isEmpty()
                && partialId.equals(IdUtils.getPartialId(PERSON, 0.6D))
                && fullRatioId != null && fullRatioId.equals(fullId);
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
     * 内部测试人员对象。
     */
    static class Person {

        /**
         * 姓名
         */
        String name;

        /**
         * 年龄
         */
        int age;

        /**
         * 邮箱
         */
        String email;

        /**
         * 薪资
         */
        double salary;

        /**
         * 部门
         */
        String department;

        /**
         * 电话
         */
        String phone;

        /**
         * 地址
         */
        String address;

        /**
         * 城市
         */
        String city;

        /**
         * 国家
         */
        String country;

        /**
         * 是否在职
         */
        boolean active;

        /**
         * 创建 Person 实例。
         *
         * @param name 姓名
         * @param age 年龄
         * @param email 邮箱
         * @param salary 薪资
         * @param department 部门
         * @param phone 电话
         * @param address 地址
         * @param city 城市
         * @param country 国家
         * @param active 是否在职
         */
        Person(String name, int age, String email, double salary, String department,
               String phone, String address, String city, String country, boolean active) {
            this.name = name;
            this.age = age;
            this.email = email;
            this.salary = salary;
            this.department = department;
            this.phone = phone;
            this.address = address;
            this.city = city;
            this.country = country;
            this.active = active;
        }
    }
}

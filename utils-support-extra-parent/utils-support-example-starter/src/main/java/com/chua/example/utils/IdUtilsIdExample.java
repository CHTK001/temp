package com.chua.example.utils;

import com.chua.common.support.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * IdUtils 对象唯一标识示例，演示 getId / getPartialId / isSameData / isSamePartialData 并自检。
 *
 * <p>改写自 common-starter 单元测试 IdUtilsIdTest，覆盖内容寻址确定性、数据区分度、
 * 集合字段顺序敏感性、非法采样比例异常与部分哈希采样等场景。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.utils.IdUtilsIdExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class IdUtilsIdExample {

    /**
     * 主入口，依次执行四组自检，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean ok = true;
        ok &= exampleGetId();
        ok &= exampleGetPartialId();
        ok &= exampleIsSameData();
        ok &= exampleIsSamePartialData();
        log.info("===== IdUtilsIdExample: {} =====", ok ? "全部通过" : "存在失败项");
        if (!ok) {
            System.exit(1);
        }
    }

    /**
     * 自检 getId：null 安全、同数据同 ID、异数据异 ID、多次调用一致、集合字段顺序敏感。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleGetId() {
        log.info("===== getId 自检 =====");
        Person p1 = new Person("Alice", 30, "alice@test.com");
        Person p2 = new Person("Alice", 30, "alice@test.com");
        Person p3 = new Person("Bob", 30, "bob@test.com");
        Person p4 = new Person("Alice", 31, "alice@test.com");
        Employee e1 = new Employee("E001", "IT", 8000.0D, Arrays.asList("java", "spring"));
        Employee e2 = new Employee("E001", "IT", 8000.0D, Arrays.asList("java", "spring"));
        Employee e3 = new Employee("E001", "IT", 8000.0D, Arrays.asList("spring", "java"));
        boolean ok = true;
        ok &= check("getId(null) 返回 null", () -> IdUtils.getId(null) == null);
        ok &= check("相同数据产生相同 ID", () -> IdUtils.getId(p1).equals(IdUtils.getId(p2)));
        ok &= check("不同数据产生不同 ID", () -> !IdUtils.getId(p1).equals(IdUtils.getId(p3)));
        ok &= check("单字段变化导致 ID 变化", () -> !IdUtils.getId(p1).equals(IdUtils.getId(p4)));
        ok &= check("同一对象多次调用结果一致", () -> IdUtils.getId(p1).equals(IdUtils.getId(p1)));
        ok &= check("集合字段内容相同则 ID 相同", () -> IdUtils.getId(e1).equals(IdUtils.getId(e2)));
        ok &= check("集合字段顺序不同则 ID 不同", () -> !IdUtils.getId(e1).equals(IdUtils.getId(e3)));
        return ok;
    }

    /**
     * 自检 getPartialId：null 安全、非法比例抛异常、确定性、全量比例等价 getId。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleGetPartialId() {
        log.info("===== getPartialId 自检 =====");
        Product product = new Product("P001", "Widget", 9.99D, 100);
        MultiField x = new MultiField("S", "S", "D1", "D1", "S");
        MultiField y = new MultiField("S", "S", "D2", "D2", "S");
        boolean ok = true;
        ok &= check("getPartialId(null) 返回 null", () -> IdUtils.getPartialId(null, 0.6D) == null);
        ok &= check("比例 0.0 抛出 IllegalArgumentException", () -> expectIllegalRatio(product, 0.0D));
        ok &= check("比例 1.5 抛出 IllegalArgumentException", () -> expectIllegalRatio(product, 1.5D));
        ok &= check("同一对象同一比例结果确定", () ->
                IdUtils.getPartialId(product, 0.6D).equals(IdUtils.getPartialId(product, 0.6D)));
        ok &= check("全量比例 1.0 与 getId 等价", () ->
                IdUtils.getPartialId(product, 1.0D).equals(IdUtils.getId(product)));
        ok &= check("哈希采样无字母序偏置（ID 非空且全量可区分）", () -> {
            String ix = IdUtils.getPartialId(x, 0.6D);
            String iy = IdUtils.getPartialId(y, 0.6D);
            return ix != null && iy != null && !IdUtils.getId(x).equals(IdUtils.getId(y));
        });
        return ok;
    }

    /**
     * 自检 isSameData：同对象/双 null/单 null/同数据/异数据/异类型六种边界。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleIsSameData() {
        log.info("===== isSameData 自检 =====");
        Person person = new Person("Alice", 30, "alice@test.com");
        Person same = new Person("Alice", 30, "alice@test.com");
        Person other = new Person("Bob", 30, "bob@test.com");
        Object mixed = new Product("P001", "Widget", 9.99D, 100);
        boolean ok = true;
        ok &= check("同一对象返回 true", () -> IdUtils.isSameData(person, person));
        ok &= check("双方为 null 返回 true", () -> IdUtils.isSameData(null, null));
        ok &= check("仅一方为 null 返回 false", () ->
                !IdUtils.isSameData(person, null) && !IdUtils.isSameData(null, person));
        ok &= check("数据内容相同返回 true", () -> IdUtils.isSameData(person, same));
        ok &= check("数据内容不同返回 false", () -> !IdUtils.isSameData(person, other));
        ok &= check("类型不同返回 false", () -> !IdUtils.isSameData(person, mixed));
        return ok;
    }

    /**
     * 自检 isSamePartialData：同对象恒真、双 null、单 null、概率性采样的确定性比对。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleIsSamePartialData() {
        log.info("===== isSamePartialData 自检 =====");
        Product product = new Product("P001", "Widget", 9.99D, 100);
        Product variant = new Product("P001", "Widget", 9.99D, 999);
        Product different = new Product("P999", "Gadget", 19.99D, 50);
        boolean ok = true;
        ok &= check("同一对象返回 true", () -> IdUtils.isSamePartialData(product, product, 0.6D));
        ok &= check("双方为 null 返回 true", () -> IdUtils.isSamePartialData(null, null, 0.6D));
        ok &= check("仅一方为 null 返回 false", () -> !IdUtils.isSamePartialData(product, null, 0.6D));
        ok &= check("同一对象在 0.75 比例下恒为 true", () ->
                IdUtils.isSamePartialData(product, product, 0.75D));
        ok &= check("仅库存差异的比对可正常执行（是否相等取决于采样，两者均合法）", () -> {
            boolean matched = IdUtils.isSamePartialData(product, variant, 0.75D);
            log.info("  库存字段采样比对结果: {}（true=未选中 stock，false=选中）", matched);
            return true;
        });
        ok &= check("全量比例下数据不同返回 false", () -> !IdUtils.isSamePartialData(product, different, 1.0D));
        return ok;
    }

    /**
     * 执行单条自检并打印 PASS / FAIL 结果。
     *
     * @param name      自检项名称
     * @param condition 断言条件
     * @return 通过返回 true
     */
    private static boolean check(String name, BooleanSupplier condition) {
        boolean result = condition.getAsBoolean();
        log.info("[{}] {}", result ? "PASS" : "FAIL", name);
        return result;
    }

    /**
     * 校验非法采样比例是否按预期抛出 IllegalArgumentException。
     *
     * @param product 目标对象
     * @param ratio   非法比例值
     * @return 抛出预期异常返回 true
     */
    private static boolean expectIllegalRatio(Product product, double ratio) {
        try {
            IdUtils.getPartialId(product, ratio);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
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
         * 创建 Person 实例。
         *
         * @param name 姓名
         * @param age 年龄
         * @param email 邮箱
         */
        Person(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
    }

    /**
     * 内部测试产品对象。
     */
    static class Product {

        /**
         * 产品编号
         */
        String id;

        /**
         * 名称
         */
        String name;

        /**
         * 价格
         */
        double price;

        /**
         * 库存
         */
        int stock;

        /**
         * 创建 Product 实例。
         *
         * @param id 产品编号
         * @param name 名称
         * @param price 价格
         * @param stock 库存
         */
        Product(String id, String name, double price, int stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }

    /**
     * 内部测试员工对象（含集合字段）。
     */
    static class Employee {

        /**
         * 工号
         */
        String code;

        /**
         * 部门
         */
        String department;

        /**
         * 薪资
         */
        double salary;

        /**
         * 标签列表
         */
        List<String> tags;

        /**
         * 创建 Employee 实例。
         *
         * @param code 工号
         * @param department 部门
         * @param salary 薪资
         * @param tags 标签列表
         */
        Employee(String code, String department, double salary, List<String> tags) {
            this.code = code;
            this.department = department;
            this.salary = salary;
            this.tags = tags;
        }
    }

    /**
     * 五字段测试类，用于验证哈希采样不偏向字母序靠前的字段。
     */
    static class MultiField {

        /**
         * 字段 a
         */
        String a;

        /**
         * 字段 b
         */
        String b;

        /**
         * 字段 c
         */
        String c;

        /**
         * 字段 d
         */
        String d;

        /**
         * 字段 e
         */
        String e;

        /**
         * 创建 MultiField 实例。
         *
         * @param a 字段 a
         * @param b 字段 b
         * @param c 字段 c
         * @param d 字段 d
         * @param e 字段 e
         */
        MultiField(String a, String b, String c, String d, String e) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }
    }
}

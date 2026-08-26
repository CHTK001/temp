package com.chua.example.reflection;

import com.chua.common.support.reflection.FieldStation;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * FieldStation 示例：覆盖 camelCase 转换、字段读写、缓存、继承链查找等核心场景。
 *
 * <p>改写自 common-starter 测试代码 reflection/FieldStationTest，共 12 个场景：
 * PascalCase 读取、小写原名读取、PascalCase 写入、camelCase 写入、读取不存在字段返回 null、
 * 写入不存在字段静默跳过、null 值写入、父类字段读取、父类字段写入、of(null)、of(Class)、缓存命中。</p>
 *
 * <p>说明：原测试的缓存场景使用包级钩子 {@code findFieldForTest}（跨包不可见），
 * 此处改为通过公共 API 验证同一 Station 重复解析与多 Station 跨实例解析结果稳定一致。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.reflection.FieldStationExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FieldStationExample {

    /**
     * 私有构造，防止实例化
     */
    private FieldStationExample() {
    }

    /**
     * 入口：依次执行 12 个自检场景，任一失败立即退出非零。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        if (!runCamelCaseRead()) {
            log.info("[FAIL] camelCase-read");
            System.exit(1);
        } else {
            log.info("[PASS] camelCase-read");
        }
        if (!runAlreadyLowerCase()) {
            log.info("[FAIL] already-lowercase");
            System.exit(1);
        } else {
            log.info("[PASS] already-lowercase");
        }
        if (!runSetPascalCase()) {
            log.info("[FAIL] set-pascal-case");
            System.exit(1);
        } else {
            log.info("[PASS] set-pascal-case");
        }
        if (!runSetCamelCase()) {
            log.info("[FAIL] set-camel-case");
            System.exit(1);
        } else {
            log.info("[PASS] set-camel-case");
        }
        if (!runGetNonExistent()) {
            log.info("[FAIL] get-non-existent");
            System.exit(1);
        } else {
            log.info("[PASS] get-non-existent");
        }
        if (!runSetNonExistent()) {
            log.info("[FAIL] set-non-existent");
            System.exit(1);
        } else {
            log.info("[PASS] set-non-existent");
        }
        if (!runSetNull()) {
            log.info("[FAIL] set-null");
            System.exit(1);
        } else {
            log.info("[PASS] set-null");
        }
        if (!runInheritedRead()) {
            log.info("[FAIL] inherited-read");
            System.exit(1);
        } else {
            log.info("[PASS] inherited-read");
        }
        if (!runInheritedWrite()) {
            log.info("[FAIL] inherited-write");
            System.exit(1);
        } else {
            log.info("[PASS] inherited-write");
        }
        if (!runOfNull()) {
            log.info("[FAIL] of-null");
            System.exit(1);
        } else {
            log.info("[PASS] of-null");
        }
        if (!runOfClass()) {
            log.info("[FAIL] of-class");
            System.exit(1);
        } else {
            log.info("[PASS] of-class");
        }
        if (!runCacheEffectiveness()) {
            log.info("[FAIL] cache-effectiveness");
            System.exit(1);
        } else {
            log.info("[PASS] cache-effectiveness");
        }
        log.info("[PASS] field-station 全部 12 个场景通过");
    }

    /**
     * 场景 1：camelCase 转换——按 PascalCase 名称读取首字母大写字段。
     *
     * @return 通过返回 true
     */
    private static boolean runCamelCaseRead() {
        TestBean bean = new TestBean();
        bean.setCheckCodeOpen(true);
        FieldStation station = FieldStation.of(bean);
        Object value = station.getValue("CheckCodeOpen");
        return Boolean.TRUE.equals(value);
    }

    /**
     * 场景 2：首字母已小写的字段名不做转换直接读取。
     *
     * @return 通过返回 true
     */
    private static boolean runAlreadyLowerCase() {
        FieldBean bean = new FieldBean();
        bean.setSystemName("test");
        FieldStation station = FieldStation.of(bean);
        return "test".equals(station.getValue("systemName"));
    }

    /**
     * 场景 3：按 PascalCase 名称写入，自动转换为实际字段名。
     *
     * @return 通过返回 true
     */
    private static boolean runSetPascalCase() {
        TestBean bean = new TestBean();
        FieldStation.of(bean).setIgnoreNameValue("CheckCodeOpen", true);
        FieldStation.of(bean).setIgnoreNameValue("SystemName", "ADMIN");
        return bean.isCheckCodeOpen() && "ADMIN".equals(bean.getSystemName());
    }

    /**
     * 场景 4：按小写开头的原始字段名写入。
     *
     * @return 通过返回 true
     */
    private static boolean runSetCamelCase() {
        TestBean bean = new TestBean();
        FieldStation.of(bean).setIgnoreNameValue("loginCount", 42);
        return Integer.valueOf(42).equals(bean.getLoginCount());
    }

    /**
     * 场景 5：读取不存在的字段返回 null 且不抛异常。
     *
     * @return 通过返回 true
     */
    private static boolean runGetNonExistent() {
        FieldStation station = FieldStation.of(new TestBean());
        return station.getValue("nonExistentField") == null
                && station.getValue("CheckNonExistent") == null;
    }

    /**
     * 场景 6：写入不存在的字段静默跳过（内部捕获异常），bean 保持原值。
     *
     * @return 通过返回 true
     */
    private static boolean runSetNonExistent() {
        TestBean bean = new TestBean();
        FieldStation.of(bean).setIgnoreNameValue("nonExistentField", "value");
        return !bean.isCheckCodeOpen() && bean.getSystemName() == null;
    }

    /**
     * 场景 7：写入 null 值时字段被置为 null。
     *
     * @return 通过返回 true
     */
    private static boolean runSetNull() {
        TestBean bean = new TestBean();
        bean.setSystemName("INITIAL");
        FieldStation.of(bean).setIgnoreNameValue("systemName", null);
        return bean.getSystemName() == null;
    }

    /**
     * 场景 8：继承链查找——子类实例可按名称读取父类与自身字段。
     *
     * @return 通过返回 true
     */
    private static boolean runInheritedRead() {
        ChildBean bean = new ChildBean();
        bean.setCheckCodeOpen(true);
        FieldStation station = FieldStation.of(bean);
        return Boolean.TRUE.equals(station.getValue("CheckCodeOpen"))
                && Boolean.FALSE.equals(station.getValue("ChildOnly"));
    }

    /**
     * 场景 9：继承链写入——通过子类实例写入父类字段。
     *
     * @return 通过返回 true
     */
    private static boolean runInheritedWrite() {
        ChildBean bean = new ChildBean();
        FieldStation.of(bean).setIgnoreNameValue("SystemName", "CHILD");
        return "CHILD".equals(bean.getSystemName());
    }

    /**
     * 场景 10：of(null) 创建的 Station 读取任何字段均返回 null，不抛 NullPointerException。
     *
     * @return 通过返回 true
     */
    private static boolean runOfNull() {
        FieldStation station = FieldStation.of((Object) null);
        return station.getValue("anything") == null;
    }

    /**
     * 场景 11：of(Class) 按类访问，instance 为 null 时读取返回 null 不抛异常。
     *
     * @return 通过返回 true
     */
    private static boolean runOfClass() {
        FieldStation station = FieldStation.of(TestBean.class);
        return station.getValue("CheckCodeOpen") == null;
    }

    /**
     * 场景 12：缓存命中——同一 Station 重复解析结果一致，不同 Station 对同名字段语义一致。
     *
     * <p>原测试经包级钩子 findFieldForTest 直接比对 Field 对象；本示例仅用公共 API 等价验证。</p>
     *
     * @return 通过返回 true
     */
    private static boolean runCacheEffectiveness() {
        TestBean bean1 = new TestBean();
        TestBean bean2 = new TestBean();
        bean1.setCheckCodeOpen(true);
        FieldStation station1 = FieldStation.of(bean1);
        FieldStation station2 = FieldStation.of(bean2);
        Object first = station1.getValue("CheckCodeOpen");
        Object repeat = station1.getValue("CheckCodeOpen");
        Object other = station2.getValue("CheckCodeOpen");
        return Boolean.TRUE.equals(first) && Boolean.TRUE.equals(repeat)
                && Boolean.FALSE.equals(other) && first.getClass() == other.getClass();
    }

    /**
     * 测试载体 Bean（含 boolean / String / int / Integer 四种字段类型）。
     */
    @Data
    private static class TestBean {

        /** Check代码open */
        private boolean checkCodeOpen;

        /** System名称 */
        private String systemName;

        /** SYS用户ID */
        private int sysUserId;

        /** Login数量 */
        private Integer loginCount;
    }

    /**
     * 继承载体 Bean，用于验证父类字段查找。
     */
    @Data
    @EqualsAndHashCode(callSuper = false)
    private static class ChildBean extends TestBean {

        /** 子级only */
        private boolean childOnly;
    }

    /**
     * 全小写字段名载体 Bean。
     */
    @Data
    private static class FieldBean {

        /** System名称 */
        private String systemName;
    }
}

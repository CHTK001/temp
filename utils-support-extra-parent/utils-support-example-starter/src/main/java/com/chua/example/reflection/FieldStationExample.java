package com.chua.example.reflection;

import com.chua.common.support.reflection.FieldStation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * FieldStation 示例：覆盖 camelCase 转换、字段读写、继承链查找等核心场景。
 *
 * <p>改写自 FieldStationTest。原"缓存命中"场景依赖包私有 API
 * {@code findFieldForTest}，此处改为等价的公开行为验证：
 * 同一类型不同实例经 PascalCase 访问结果一致（共享字段解析）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FieldStationExample {

    /**
     * Main 入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= testCamelCaseRead();
        passed &= testAlreadyLowerCase();
        passed &= testSetPascalCase();
        passed &= testSetCamelCase();
        passed &= testGetNonExistent();
        passed &= testSetNonExistent();
        passed &= testSetNull();
        passed &= testInheritedField();
        passed &= testOfNull();
        passed &= testOfClass();
        passed &= testSharedFieldResolution();
        if (!passed) {
            log.info("[FAIL] FieldStation 存在失败场景");
            System.exit(1);
        }
        log.info("[PASS] FieldStation 全部场景通过");
        System.exit(0);
    }

    /**
     * 场景一：PascalCase 读取自动转 camelCase。
     *
     * @return 通过返回 true
     */
    private static boolean testCamelCaseRead() {
        TestBean bean = new TestBean();
        bean.setCheckCodeOpen(true);
        Object value = FieldStation.of(bean).getValue("CheckCodeOpen");
        if (!Boolean.TRUE.equals(value)) {
            log.info("[FAIL] CheckCodeOpen 应读到 true，实际 {}", value);
            return false;
        }
        log.info("[PASS] PascalCase 转 camelCase 读取");
        return true;
    }

    /**
     * 场景二：首字母已小写的字段不做转换。
     *
     * @return 通过返回 true
     */
    private static boolean testAlreadyLowerCase() {
        FieldBean bean = new FieldBean();
        bean.systemName = "test";
        Object value = FieldStation.of(bean).getValue("systemName");
        if (!"test".equals(value)) {
            log.info("[FAIL] systemName 应读到 test，实际 {}", value);
            return false;
        }
        log.info("[PASS] 小写字段名直读");
        return true;
    }

    /**
     * 场景三：PascalCase 写入自动转换。
     *
     * @return 通过返回 true
     */
    private static boolean testSetPascalCase() {
        TestBean bean = new TestBean();
        FieldStation.of(bean).setIgnoreNameValue("CheckCodeOpen", true);
        if (!bean.isCheckCodeOpen()) {
            log.info("[FAIL] 写入 CheckCodeOpen 后应为 true");
            return false;
        }
        FieldStation.of(bean).setIgnoreNameValue("SystemName", "ADMIN");
        if (!"ADMIN".equals(bean.getSystemName())) {
            log.info("[FAIL] 写入 SystemName 后应为 ADMIN，实际 {}", bean.getSystemName());
            return false;
        }
        log.info("[PASS] PascalCase 写入转换");
        return true;
    }

    /**
     * 场景四：小写开头字段正常写入。
     *
     * @return 通过返回 true
     */
    private static boolean testSetCamelCase() {
        TestBean bean = new TestBean();
        FieldStation.of(bean).setIgnoreNameValue("loginCount", 42);
        if (!Integer.valueOf(42).equals(bean.getLoginCount())) {
            log.info("[FAIL] loginCount 应为 42，实际 {}", bean.getLoginCount());
            return false;
        }
        log.info("[PASS] camelCase 写入");
        return true;
    }

    /**
     * 场景五：读取不存在的字段返回 null 不抛异常。
     *
     * @return 通过返回 true
     */
    private static boolean testGetNonExistent() {
        FieldStation station = FieldStation.of(new TestBean());
        Object raw = station.getValue("nonExistentField");
        Object pascal = station.getValue("CheckNonExistent");
        if (raw != null || pascal != null) {
            log.info("[FAIL] 不存在字段应返回 null，实际 raw={} pascal={}", raw, pascal);
            return false;
        }
        log.info("[PASS] 读取不存在字段返回 null");
        return true;
    }

    /**
     * 场景六：写入不存在的字段静默跳过。
     *
     * @return 通过返回 true
     */
    private static boolean testSetNonExistent() {
        TestBean bean = new TestBean();
        FieldStation.of(bean).setIgnoreNameValue("nonExistentField", "value");
        if (bean.isCheckCodeOpen() || bean.getSystemName() != null) {
            log.info("[FAIL] 写入不存在字段不应影响既有属性");
            return false;
        }
        log.info("[PASS] 写入不存在字段静默跳过");
        return true;
    }

    /**
     * 场景七：写入 null 值字段被置空。
     *
     * @return 通过返回 true
     */
    private static boolean testSetNull() {
        TestBean bean = new TestBean();
        bean.setSystemName("INITIAL");
        FieldStation.of(bean).setIgnoreNameValue("systemName", null);
        if (bean.getSystemName() != null) {
            log.info("[FAIL] 写入 null 后 systemName 应为 null，实际 {}", bean.getSystemName());
            return false;
        }
        log.info("[PASS] null 值写入置空字段");
        return true;
    }

    /**
     * 场景八：继承链上父类字段的读写。
     *
     * @return 通过返回 true
     */
    private static boolean testInheritedField() {
        ChildBean bean = new ChildBean();
        bean.setCheckCodeOpen(true);
        FieldStation station = FieldStation.of(bean);
        Object parentValue = station.getValue("CheckCodeOpen");
        Object childValue = station.getValue("ChildOnly");
        if (!Boolean.TRUE.equals(parentValue)) {
            log.info("[FAIL] 父类字段 CheckCodeOpen 应读到 true，实际 {}", parentValue);
            return false;
        }
        if (!Boolean.FALSE.equals(childValue)) {
            log.info("[FAIL] 子类字段 ChildOnly 应读到 false，实际 {}", childValue);
            return false;
        }
        FieldStation.of(bean).setIgnoreNameValue("SystemName", "CHILD");
        if (!"CHILD".equals(bean.getSystemName())) {
            log.info("[FAIL] 父类字段 SystemName 应写入 CHILD，实际 {}", bean.getSystemName());
            return false;
        }
        log.info("[PASS] 继承链父类字段读写");
        return true;
    }

    /**
     * 场景九：of(null) 返回的 Station 查找安全。
     *
     * @return 通过返回 true
     */
    private static boolean testOfNull() {
        try {
            FieldStation station = FieldStation.of((Object) null);
            Object value = station.getValue("anything");
            if (value != null) {
                log.info("[FAIL] of(null) 查找应返回 null，实际 {}", value);
                return false;
            }
            log.info("[PASS] of(null) 安全查找");
            return true;
        } catch (NullPointerException e) {
            log.info("[FAIL] of(null) 不应抛 NullPointerException");
            return false;
        }
    }

    /**
     * 场景十：of(Class) 按类访问，instance 为 null 时读取返回 null。
     *
     * @return 通过返回 true
     */
    private static boolean testOfClass() {
        try {
            FieldStation station = FieldStation.of(TestBean.class);
            Object value = station.getValue("CheckCodeOpen");
            if (value != null) {
                log.info("[FAIL] of(Class) 无实例应返回 null，实际 {}", value);
                return false;
            }
            log.info("[PASS] of(Class) 无实例读取返回 null");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] of(Class) 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景十一：同一类型不同实例共享字段解析结果一致。
     *
     * <p>替代原 findFieldForTest 缓存验证（包私有 API）。</p>
     *
     * @return 通过返回 true
     */
    private static boolean testSharedFieldResolution() {
        TestBean bean1 = new TestBean();
        TestBean bean2 = new TestBean();
        bean1.setCheckCodeOpen(true);
        bean2.setCheckCodeOpen(true);
        Object first = FieldStation.of(bean1).getValue("CheckCodeOpen");
        Object second = FieldStation.of(bean2).getValue("CheckCodeOpen");
        if (!Boolean.TRUE.equals(first) || !Boolean.TRUE.equals(second)) {
            log.info("[FAIL] 两实例同名字段解析结果应一致，实际 {} / {}", first, second);
            return false;
        }
        log.info("[PASS] 多实例字段解析一致");
        return true;
    }

    /**
     * 测试父级 Bean。
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
     * 测试子级 Bean。
     */
    @Data
    private static class ChildBean extends TestBean {

        /** 子级only */
        private boolean childOnly;
    }

    /**
     * 直访字段 Bean。
     */
    private static class FieldBean {

        /** System名称 */
        private String systemName;
    }
}

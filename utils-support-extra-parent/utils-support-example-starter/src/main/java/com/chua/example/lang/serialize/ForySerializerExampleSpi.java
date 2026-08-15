package com.chua.example.lang.serialize;

import com.chua.common.support.serialize.Serializer;
import com.chua.example.spi.Example;
import com.chua.fory.support.serialize.ForySerialization;
import com.chua.fory.support.serialize.ForySerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fory 序列化自检示例（SPI 形式）— 演示 ForySerializer / ForySerialization 往返能力。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=fory-serializer} 调用，
 * 内部覆盖：循环引用往返、null / 空输入、Serialization 接口实现。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=fory-serializer
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ForySerializerExampleSpi implements Example {

    @Override
    public String name() {
        return "fory-serializer";
    }

    @Override
    public String module() {
        return "fory";
    }

    @Override
    public String description() {
        return "ForySerializer / ForySerialization 往返序列化自检（循环引用 / null 输入 / 接口实现）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        boolean passed = true;
        passed &= testRoundTrip();
        passed &= testNullInput();
        passed &= testSerializationInterface();
        return passed;
    }

    private boolean testRoundTrip() {
        log.info("\n===== fory-serializer --test =====");
        log.info("  [TC-01] ForySerializer 循环引用往返");
        try {
            User user = new User();
            user.setName("chua");
            user.setAge(18);
            user.getTags().add("java");
            user.getTags().add("fory");
            user.setFriend(user); // 循环引用

            Serializer<User> serializer = new ForySerializer<>(User.class);
            byte[] bytes = serializer.serialize(user);
            assertNotNull(bytes, "序列化结果不应为 null");
            assertTrue(bytes.length > 0, "序列化结果不应为空");

            User result = serializer.deserialize(bytes);
            assertNotNull(result, "反序列化结果不应为 null");
            assertEquals("chua", result.getName(), "name 字段");
            assertEquals(18, result.getAge(), "age 字段");
            assertEquals(2, result.getTags().size(), "tags 大小");
            assertEquals("fory", result.getTags().get(1), "tags 内容");
            // 循环引用保持对象身份（引用一致性检查，避免 Lombok equals 递归）
            assertSame(result, result.getFriend(), "循环引用应保持对象身份");
            pass();
            return true;
        } catch (Exception e) {
            fail("循环引用往返异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testNullInput() {
        log.info("  [TC-02] null / 空输入");
        try {
            Serializer<User> serializer = new ForySerializer<>(User.class);
            assertEquals(0, serializer.serialize(null).length, "serialize(null) 应返回空数组");
            assertEquals(null, serializer.deserialize(null), "deserialize(null) 应返回 null");
            assertEquals(null, serializer.deserialize(new byte[0]), "deserialize(空数组) 应返回 null");
            pass();
            return true;
        } catch (Exception e) {
            fail("null 输入异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testSerializationInterface() {
        log.info("  [TC-03] ForySerialization 接口实现");
        try {
            ForySerialization serialization = new ForySerialization();
            assertEquals("fory", serialization.name(), "序列化名称");

            User user = new User();
            user.setName("interface");
            user.setAge(30);

            byte[] bytes = serialization.serialize(user);
            User result = serialization.deserialize(bytes, User.class);
            assertNotNull(result, "接口反序列化结果不应为 null");
            assertEquals("interface", result.getName(), "接口反序列化 name");
            assertEquals(30, result.getAge(), "接口反序列化 age");
            pass();
            return true;
        } catch (Exception e) {
            fail("Serialization 接口异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 测试用户实体（含循环引用）。
     */
    @lombok.Data
    static class User implements Serializable {
        private String name;
        private int age;
        private List<String> tags = new ArrayList<>();
        private User friend;
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }

    private static void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new AssertionError(msg);
        }
    }

    private static void assertSame(Object expected, Object actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void pass() {
        log.info("  ✓ 通过");
    }

    private static void fail(String msg) {
        log.info("  ✗ 失败: {}", msg);
    }
}

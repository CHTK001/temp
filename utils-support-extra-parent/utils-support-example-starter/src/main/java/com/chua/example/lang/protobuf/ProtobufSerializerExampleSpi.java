package com.chua.example.lang.protobuf;

import io.protostuff.Tag;
import com.chua.common.support.base.serialize.Serialization;
import com.chua.common.support.serialize.Serializer;
import com.chua.example.spi.Example;
import com.chua.protobuf.support.serialize.ProtobufSerialization;
import com.chua.protobuf.support.serialize.ProtobufSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Protobuf 序列化自检示例（SPI 形式）— 演示 ProtobufSerializer / ProtobufSerialization 往返能力。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=protobuf-serializer} 调用，
 * 内部覆盖：基本类型往返、嵌套对象、集合字段、null/空输入、Serialization 接口实现、压缩比对比。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=protobuf-serializer
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ProtobufSerializerExampleSpi implements Example {

    /**
     * 返回示例名称。
     *
     * @return 名称字符串
     */
    @Override
    public String name() {
        return "protobuf-serializer";
    }

    /**
     * 返回所属模块。
     *
     * @return 模块名
     */
    @Override
    public String module() {
        return "protobuf";
    }

    /**
     * 返回示例描述。
     *
     * @return 描述字符串
     */
    @Override
    public String description() {
        return "ProtobufSerializer / ProtobufSerialization 往返序列化自检（基本类型 / 嵌套对象 / 集合 / null 输入 / 压缩比）";
    }

    /**
     * 运行序列化自检。
     *
     * @param args 参数映射（未使用）
     * @return 全部用例是否通过
     */
    @Override
    public boolean run(Map<String, String> args) {
        boolean passed = true;
        passed &= testBasicRoundTrip();
        passed &= testNestedObject();
        passed &= testCollectionField();
        passed &= testNullInput();
        passed &= testSerializationInterface();
        passed &= testCompressionRatio();
        return passed;
    }

    /**
     * 自检用例一：基本类型往返。
     *
     * @return 用例是否通过
     */
    private boolean testBasicRoundTrip() {
        log.info("\n===== protobuf-serializer --test-basic =====");
        log.info("  [TC-01] ProtobufSerializer 基本类型往返");
        try {
            User user = new User();
            user.setName("chua");
            user.setAge(18);
            user.setEmail("chua@example.com");
            user.setActive(true);

            Serializer<User> serializer = new ProtobufSerializer<>(User.class);
            byte[] bytes = serializer.serialize(user);
            assertNotNull(bytes, "序列化结果不应为 null");
            assertTrue(bytes.length > 0, "序列化结果不应为空");

            User result = serializer.deserialize(bytes);
            assertNotNull(result, "反序列化结果不应为 null");
            assertEquals("chua", result.getName(), "name 字段");
            assertEquals(18, result.getAge(), "age 字段");
            assertEquals("chua@example.com", result.getEmail(), "email 字段");
            assertTrue(result.isActive(), "active 字段");
            pass();
            return true;
        } catch (Exception e) {
            fail("基本类型往返异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 自检用例二：嵌套对象往返。
     *
     * @return 用例是否通过
     */
    private boolean testNestedObject() {
        log.info("  [TC-02] 嵌套对象往返");
        try {
            Order order = new Order();
            order.setOrderId("ORD-20260817");
            order.setAmount(99.9);

            User buyer = new User();
            buyer.setName("buyer");
            buyer.setAge(25);
            buyer.setEmail("buyer@example.com");
            order.setBuyer(buyer);

            Serializer<Order> serializer = new ProtobufSerializer<>(Order.class);
            byte[] bytes = serializer.serialize(order);
            Order result = serializer.deserialize(bytes);

            assertNotNull(result, "反序列化结果不应为 null");
            assertEquals("ORD-20260817", result.getOrderId(), "orderId 字段");
            assertEquals(99.9, result.getAmount(), 0.01, "amount 字段");
            assertNotNull(result.getBuyer(), "buyer 不应为 null");
            assertEquals("buyer", result.getBuyer().getName(), "buyer.name 字段");
            assertEquals(25, result.getBuyer().getAge(), "buyer.age 字段");
            pass();
            return true;
        } catch (Exception e) {
            fail("嵌套对象往返异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 自检用例三：集合字段往返。
     *
     * @return 用例是否通过
     */
    private boolean testCollectionField() {
        log.info("  [TC-03] 集合字段往返");
        try {
            User user = new User();
            user.setName("listuser");
            user.setAge(30);
            user.getTags().add("java");
            user.getTags().add("protobuf");

            Serializer<User> serializer = new ProtobufSerializer<>(User.class);
            byte[] bytes = serializer.serialize(user);
            User result = serializer.deserialize(bytes);

            assertNotNull(result, "反序列化结果不应为 null");
            assertEquals("listuser", result.getName(), "name 字段");
            assertEquals(2, result.getTags().size(), "tags 大小");
            assertEquals("java", result.getTags().get(0), "tags[0] 内容");
            assertEquals("protobuf", result.getTags().get(1), "tags[1] 内容");
            pass();
            return true;
        } catch (Exception e) {
            fail("集合字段往返异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 自检用例四：null / 空输入。
     *
     * @return 用例是否通过
     */
    private boolean testNullInput() {
        log.info("  [TC-04] null / 空输入");
        try {
            Serializer<User> serializer = new ProtobufSerializer<>(User.class);
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

    /**
     * 自检用例五：ProtobufSerialization 接口实现。
     *
     * @return 用例是否通过
     */
    private boolean testSerializationInterface() {
        log.info("  [TC-05] ProtobufSerialization 接口实现");
        try {
            ProtobufSerialization serialization = new ProtobufSerialization();
            assertEquals("protobuf", serialization.name(), "序列化名称");

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
     * 自检用例六：压缩比对比（Protobuf vs JSON）。
     *
     * @return 用例是否通过
     */
    private boolean testCompressionRatio() {
        log.info("  [TC-06] 压缩比对比（Protobuf vs JSON）");
        try {
            User user = new User();
            user.setName("compression-test-user");
            user.setAge(42);
            user.setEmail("compress@test.com");
            user.getTags().add("benchmark");
            user.getTags().add("protobuf");

            // Protobuf 序列化
            Serializer<User> protobufSerializer = new ProtobufSerializer<>(User.class);
            byte[] protobufBytes = protobufSerializer.serialize(user);

            // JSON 序列化（使用 common-starter 的 JsonSerializer）
            com.chua.common.support.serialize.Serializer<User> jsonSerializer =
                    new com.chua.common.support.serialize.JsonSerializer<>(User.class);
            byte[] jsonBytes = jsonSerializer.serialize(user);

            log.info("    Protobuf size: {} bytes", protobufBytes.length);
            log.info("    JSON size: {} bytes", jsonBytes.length);
            log.info("    Ratio: Protobuf is {:.1f}% of JSON",
                    (double) protobufBytes.length / jsonBytes.length * 100);

            // Protobuf 通常比 JSON 更紧凑
            assertTrue(protobufBytes.length > 0, "Protobuf 序列化结果不为空");
            assertTrue(jsonBytes.length > 0, "JSON 序列化结果不为空");
            pass();
            return true;
        } catch (Exception e) {
            fail("压缩比对比异常: " + e.getMessage());
            return false;
        }
    }

    // ========== 测试实体类（使用 protostuff @Tag 注解） ==========

    /**
     * 测试用户实体。
     */
    @lombok.Data
    public static class User implements Serializable {
        /**
         * 用户名
         */
        @Tag(1)
        private String name;

        /**
         * 年龄
         */
        @Tag(2)
        private int age;

        /**
         * 邮箱
         */
        @Tag(3)
        private String email;

        /**
         * 是否活跃
         */
        @Tag(4)
        private boolean active;

        /**
         * 标签集合
         */
        @Tag(5)
        private List<String> tags = new ArrayList<>();
    }

    /**
     * 测试订单实体（含嵌套对象）。
     */
    @lombok.Data
    public static class Order implements Serializable {
        /**
         * 订单ID
         */
        @Tag(1)
        private String orderId;

        /**
         * 金额
         */
        @Tag(2)
        private double amount;

        /**
         * 买家
         */
        @Tag(3)
        private User buyer;
    }

    // ========== 断言辅助方法 ==========

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

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String msg) {
        if (Math.abs(expected - actual) > delta) {
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
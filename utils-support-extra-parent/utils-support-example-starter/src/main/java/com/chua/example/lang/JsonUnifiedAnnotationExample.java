package com.chua.example.lang;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.annotation.JsonBeanMapper;
import com.chua.common.support.lang.json.annotation.JsonFormat;
import com.chua.common.support.lang.json.annotation.JsonIgnore;
import com.chua.common.support.lang.json.annotation.JsonName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一门户注解示例：演示 {@link JsonName} / {@link JsonIgnore} / {@link JsonFormat}
 * 在默认 Jackson 实现（CommonJsonAnnotationIntrospector）与 {@link JsonBeanMapper} 桥接器中的行为。
 *
 * <p>改写自 common-starter 单元测试 {@code JsonUnifiedAnnotationTest}，全部断言场景以
 * [PASS]/[FAIL] 控制台输出呈现，任一场景失败即以退出码 1 终止。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class JsonUnifiedAnnotationExample {

    private JsonUnifiedAnnotationExample() {
    }

    /**
     * 示例入口：依次执行 Jackson 注解适配与 JsonBeanMapper 桥接两组场景，
     * 结束后恢复默认 Jackson 实现。
     *
     * @param args 命令行参数（未使用）
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        try {
            log.info("===== 统一注解 场景1: 默认 Jackson 实现识别门户注解 =====");
            LocalDateTime time = LocalDateTime.of(2026, 8, 15, 12, 30, 0);
            String json = Json.toJson(new Order("A001", "secret", time, 99.5));
            if (!json.contains("\"order_id\":\"A001\"")) {
                System.out.println("[FAIL] 注解适配: 应使用 @JsonName 指定的字段名 order_id, 实际 " + json);
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: @JsonName 重命名 -> order_id");
            if (json.contains("internal")) {
                System.out.println("[FAIL] 注解适配: 被 @JsonIgnore 标记的字段不应序列化, 实际 " + json);
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: @JsonIgnore 字段被排除");
            if (!json.contains("\"createTime\":\"2026-08-15 12:30:00\"")) {
                System.out.println("[FAIL] 注解适配: 应使用 @JsonFormat 格式化时间, 实际 " + json);
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: @JsonFormat 时间格式 yyyy-MM-dd HH:mm:ss");
            Order order = Json.fromJson(
                    "{\"order_id\":\"A001\",\"internal\":\"x\",\"createTime\":\"2026-08-15 12:30:00\",\"amount\":99.5}",
                    Order.class);
            if (!"A001".equals(order.getOrderId())) {
                System.out.println("[FAIL] 注解适配: 反序列化 orderId 应为 A001");
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: 反序列化 @JsonName 字段 -> A001");
            if (order.getInternal() != null) {
                System.out.println("[FAIL] 注解适配: 反序列化后 @JsonIgnore 字段应为 null");
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: 反序列化 @JsonIgnore 字段为 null");
            if (!time.equals(order.getCreateTime())) {
                System.out.println("[FAIL] 注解适配: 反序列化 createTime 应为 " + time);
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: 反序列化 @JsonFormat 时间还原");
            if (Math.abs(order.getAmount() - 99.5) > 0.001) {
                System.out.println("[FAIL] 注解适配: 反序列化 amount 应为 99.5");
                System.exit(1);
            }
            System.out.println("[PASS] 注解适配: 反序列化普通字段 amount=99.5");

            log.info("===== 统一注解 场景2: JsonBeanMapper 桥接器 =====");
            LocalDateTime bridgeTime = LocalDateTime.of(2026, 8, 15, 12, 30, 0);
            Order source = new Order("A002", "secret", bridgeTime, 10.0);
            Object mapped = JsonBeanMapper.toMap(source);
            if (!(mapped instanceof Map)) {
                System.out.println("[FAIL] 桥接器: toMap 应返回 Map 类型");
                System.exit(1);
            }
            System.out.println("[PASS] 桥接器: toMap 返回 Map");
            Map<String, Object> map = (Map<String, Object>) mapped;
            if (!"A002".equals(map.get("order_id"))) {
                System.out.println("[FAIL] 桥接器: map.order_id 应为 A002");
                System.exit(1);
            }
            System.out.println("[PASS] 桥接器: @JsonName 字段名 -> order_id=A002");
            if (map.containsKey("internal")) {
                System.out.println("[FAIL] 桥接器: @JsonIgnore 字段不应出现在 Map 中");
                System.exit(1);
            }
            System.out.println("[PASS] 桥接器: @JsonIgnore 字段被排除");
            if (!"2026-08-15 12:30:00".equals(map.get("createTime"))) {
                System.out.println("[FAIL] 桥接器: map.createTime 应按 @JsonFormat 格式化为字符串");
                System.exit(1);
            }
            System.out.println("[PASS] 桥接器: @JsonFormat 时间格式化 -> 2026-08-15 12:30:00");

            log.info("----- 统一注解 场景2: fromMap 反向填充 -----");
            Map<String, Object> fillSource = new LinkedHashMap<>();
            fillSource.put("order_id", "A003");
            fillSource.put("internal", "secret");
            fillSource.put("createTime", "2026-08-15 12:30:00");
            fillSource.put("amount", 20.0);
            Order restored = JsonBeanMapper.fromMap(fillSource, Order.class);
            if (!"A003".equals(restored.getOrderId())) {
                System.out.println("[FAIL] 反向填充: orderId 应为 A003");
                System.exit(1);
            }
            System.out.println("[PASS] 反向填充: @JsonName 字段映射 -> A003");
            if (restored.getInternal() != null) {
                System.out.println("[FAIL] 反向填充: @JsonIgnore 字段不应被注入");
                System.exit(1);
            }
            System.out.println("[PASS] 反向填充: @JsonIgnore 字段保持 null");
            LocalDateTime expected = LocalDateTime.parse("2026-08-15 12:30:00",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (!expected.equals(restored.getCreateTime())) {
                System.out.println("[FAIL] 反向填充: createTime 应解析为 " + expected);
                System.exit(1);
            }
            System.out.println("[PASS] 反向填充: @JsonFormat 时间字符串解析还原");
            if (Math.abs(restored.getAmount() - 20.0) > 0.001) {
                System.out.println("[FAIL] 反向填充: amount 应为 20.0");
                System.exit(1);
            }
            System.out.println("[PASS] 反向填充: 普通字段 amount=20.0");
            System.out.println("[PASS] 统一注解 全部场景执行完成");
        } finally {
            Json.setImplementation(new com.chua.common.support.lang.json.JacksonJsonProvider());
        }
    }

    /**
     * 门户注解测试实体。
     */
    static class Order implements Serializable {
        private static final long serialVersionUID = 1L;
        @JsonName("order_id")
        /** 排序ID */
        private String orderId;

        @JsonIgnore
        /** 内部 */
        private String internal;

        @JsonFormat("yyyy-MM-dd HH:mm:ss")
        /** 创建时间 */
        private LocalDateTime createTime;

        /** 金额 */
        private double amount;

        /** 创建 Order 实例 */
        public Order() {
        }

        /**
         * 创建 Order 实例
         *
         * @param orderId    排序ID
         * @param internal   内部字段
         * @param createTime 创建时间
         * @param amount     金额
         */
        public Order(String orderId, String internal, LocalDateTime createTime, double amount) {
            this.orderId = orderId;
            this.internal = internal;
            this.createTime = createTime;
            this.amount = amount;
        }

        /** 获取OrderId */
        public String getOrderId() {
            return orderId;
        }

        /** 设置OrderId */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /** 获取Internal */
        public String getInternal() {
            return internal;
        }

        /** 设置Internal */
        public void setInternal(String internal) {
            this.internal = internal;
        }

        /** 获取创建Time */
        public LocalDateTime getCreateTime() {
            return createTime;
        }

        /** 设置创建Time */
        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        /** 获取Amount */
        public double getAmount() {
            return amount;
        }

        /** 设置Amount */
        public void setAmount(double amount) {
            this.amount = amount;
        }
    }
}

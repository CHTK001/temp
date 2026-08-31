package com.chua.example.network.rpc;

import java.io.Serializable;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 复杂对象传输用例负载 — 验证 native / json / dubbo / sofa 四框架的对象序列化往返。
 *
 * <p>同时满足各框架的序列化约束：</p>
 * <ul>
 *   <li><b>native</b> — JDK 序列化（{@link ObjectOutputStream}），需实现 {@link Serializable}</li>
 *   <li><b>json</b> — Jackson，需公开 getter / setter + 无参构造器</li>
 *   <li><b>dubbo / sofa</b> — Hessian2 等，兼容 POJO 形态</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class RpcPayloadExample implements Serializable {

    /** 私有构造，防止实例化 */
    private RpcPayloadExample() { }

    /**
     * JDK 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 名称字段
     */
    private String name;

    /**
     * 数值字段
     */
    private int value;

    /**
     * 嵌套子对象字段（验证深层对象图序列化往返）
     */
    private RpcPayloadExample nested;

    /**
     * 无参构造器（json 反序列化必需）。
     */
    public RpcPayloadExample() {
    }

    /**
     * 全参构造器。
     *
     * @param name  名称
     * @param value 数值
     */
    public RpcPayloadExample(String name, int value) {
        this.name = name;
        this.value = value;
    }

    /**
     * 全参构造器（含嵌套子对象）。
     *
     * @param name   名称
     * @param value  数值
     * @param nested 嵌套子对象，可为 {@code null}
     */
    public RpcPayloadExample(String name, int value, RpcPayloadExample nested) {
        this.name = name;
        this.value = value;
        this.nested = nested;
    }

    /**
     * 获取嵌套子对象。
     *
     * @return 嵌套子对象
     */
    public RpcPayloadExample getNested() {
        return nested;
    }

    /**
     * 设置嵌套子对象。
     *
     * @param nested 嵌套子对象
     */
    public void setNested(RpcPayloadExample nested) {
        this.nested = nested;
    }

    /**
     * 获取名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取数值。
     *
     * @return 数值
     */
    public int getValue() {
        return value;
    }

    /**
     * 设置数值。
     *
     * @param value 数值
     */
    public void setValue(int value) {
        this.value = value;
    }

    /**
     * 按字段值比较相等性（用于序列化往返断言）。
     *
     * @param o 比较对象
     * @return 字段全部相等返回 {@code true}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RpcPayloadExample)) {
            return false;
        }
        RpcPayloadExample that = (RpcPayloadExample) o;
        return value == that.value && Objects.equals(name, that.name) && Objects.equals(nested, that.nested);
    }

    /**
     * 按字段生成哈希码。
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, value, nested);
    }

    /**
     * 简短字符串表示（便于日志输出）。
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "RpcPayloadExample{name='" + name + "', value=" + value + "}";
    }

    /**
     * 独立入口：JDK 序列化往返自检。
     *
     * <p>构造含嵌套子对象的负载，经 {@code ObjectOutputStream/ObjectInputStream}
     * 往返后按字段比对，通过 {@code System.exit(0/1)} 表达结果。</p>
     *
     * <p>参数格式 {@code --key=value}：</p>
     * <ul>
     *   <li>{@code --name=} 负载名称（默认 demo）</li>
     *   <li>{@code --value=} 数值（默认 42）</li>
     * </ul>
     *
     * @param args 命令行参数
     * @throws java.io.IOException 序列化失败时抛出
     */
    public static void main(String[] args) throws Exception {
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String arg : args) {
            int idx = arg.indexOf('=');
            if (arg.startsWith("--") && idx > 2) {
                params.put(arg.substring(2, idx), arg.substring(idx + 1));
            }
        }
        String name = params.getOrDefault("name", "demo");
        int value = Integer.parseInt(params.getOrDefault("value", "42"));

        RpcPayloadExample source = new RpcPayloadExample(name, value,
                new RpcPayloadExample(name + "-child", value + 1));

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(source);
        }
        RpcPayloadExample restored;
        try (java.io.ObjectInputStream ois =
                     new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            restored = (RpcPayloadExample) ois.readObject();
        }

        boolean passed = source.equals(restored);
        log.info("[PASS] 序列化往返一致: " + restored);
        if (!passed) {
            log.info("[FAIL] 往返后字段不一致");
            System.exit(1);
        }
        System.exit(0);
    }
}

package com.chua.example.network.rpc;

import java.io.Serializable;
import java.util.Objects;

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
public class RpcPayload implements Serializable {

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
     * 无参构造器（json 反序列化必需）。
     */
    public RpcPayload() {
    }

    /**
     * 全参构造器。
     *
     * @param name  名称
     * @param value 数值
     */
    public RpcPayload(String name, int value) {
        this.name = name;
        this.value = value;
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
        if (!(o instanceof RpcPayload)) {
            return false;
        }
        RpcPayload that = (RpcPayload) o;
        return value == that.value && Objects.equals(name, that.name);
    }

    /**
     * 按字段生成哈希码。
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    /**
     * 简短字符串表示（便于日志输出）。
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "RpcPayload{name='" + name + "', value=" + value + "}";
    }
}

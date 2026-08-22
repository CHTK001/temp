package com.chua.example.network.rpc;

import java.util.List;

/**
 * RPC 回显服务接口 — {@link RpcExample} 的公共测试契约。
 *
 * <p>该接口同时被 native / json / dubbo / sofa 四种 RPC 实现注册与调用，
 * 覆盖典型方法形态：</p>
 * <ul>
 *   <li>{@link #echo(String)} — 字符串参数 + 字符串返回值（序列化往返）</li>
 *   <li>{@link #add(int, int)} — 多基本类型参数 + 基本类型返回值（装箱/拆箱）</li>
 *   <li>{@link #echoPayload(RpcPayloadExample)} — 复杂对象参数 + 复杂对象返回值（对象序列化往返）</li>
 *   <li>{@link #batch(List)} — 集合参数 + 集合返回值（泛型擦除与列表序列化）</li>
 * </ul>
 *
 * <h2>注册约定</h2>
 * <ul>
 *   <li>native：以 {@code getClass().getName()} 作为注册名，客户端按接口名查找</li>
 *   <li>dubbo / sofa：以接口全限定名作为 {@code interfaceId}，客户端按接口类解析</li>
 *   <li>json：方法名直达 handler（单服务模式）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface RpcEchoServiceExample {

    /**
     * 回显：原样返回输入消息，前置 {@code echo:} 前缀。
     *
     * @param message 输入消息，可为 {@code null}（此时返回 {@code "echo:null"}）
     * @return 回显结果，格式为 {@code echo:<message>}
     */
    String echo(String message);

    /**
     * 加法：计算两个整数的和。
     *
     * @param a 第一个加数
     * @param b 第二个加数
     * @return {@code a + b}
     */
    int add(int a, int b);

    /**
     * 复杂对象往返：原样返回输入对象（验证四框架的对象序列化能力）。
     *
     * @param payload 负载对象，可为 {@code null}
     * @return 原样返回的负载对象
     */
    RpcPayloadExample echoPayload(RpcPayloadExample payload);

    /**
     * 批量回显：对列表内每个元素前置 {@code echo:} 前缀后原样返回（验证集合参数/返回值序列化）。
     *
     * @param messages 消息列表，可为 {@code null}
     * @return 回显后的列表，元素个数与输入一致
     */
    List<String> batch(List<String> messages);

/**
     * 异常传播测试：抛出一个消息为 {@code <message>} 的 {@link RuntimeException}。
     *
     * <p>用于验证远程异常能否原样传播回客户端，消息会经过序列化与反序列化原样返回。</p>
     *
     * @param message 异常消息
     * @return 正常返回；抛异常时返回空
     */
    String fail(String message);

    /**
     * null 往返测试：参数为 {@code null} 时原样返回 {@code null}。
     *
     * <p>验证序列化框架对 null 值（无类型信息、无字节内容）的处理，防止 NPE 或
     * null 被误写成空串/默认对象。</p>
     *
     * @param value 任意值，可为 {@code null}
     * @return 原样返回
     */
    String echoNullable(String value);

    /**
     * 大对象往返测试：超大字符串原样返回。
     *
     * <p>验证传输层长度帧与序列化对超过常规缓冲区的数据支持（约 1MB），
     * 防止长度头溢出或缓冲区截断。</p>
     *
     * @param large 大字符串
     * @return 原样返回
     */
    String echoLarge(String large);

    /**
     * 深层嵌套对象往返测试：多层嵌套对象原样返回。
     *
     * <p>验证序列化框架对深层对象图（多级引用）的支持，防止循环引用或
     * 递归深度超过框架限制。</p>
     *
     * @param payload 深层嵌套 payload
     * @return 原样返回
     */
    RpcPayloadExample echoNested(RpcPayloadExample payload);
}

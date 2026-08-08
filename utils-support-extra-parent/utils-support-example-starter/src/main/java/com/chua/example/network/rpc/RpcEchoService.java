package com.chua.example.network.rpc;

/**
 * RPC 回显服务接口 — {@link RpcExample} 的公共测试契约。
 *
 * <p>该接口同时被 native / json / dubbo / sofa 四种 RPC 实现注册与调用，
 * 覆盖两种典型方法形态：</p>
 * <ul>
 *   <li>{@link #echo(String)} — 字符串参数 + 字符串返回值（序列化往返）</li>
 *   <li>{@link #add(int, int)} — 多基本类型参数 + 基本类型返回值（装箱/拆箱）</li>
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
public interface RpcEchoService {

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
    RpcPayload echoPayload(RpcPayload payload);

    /**
     * 异常传播：抛出一个消息为 {@code <message>} 的 {@link RuntimeException}。
     *
     * <p>用于验证远程异常能否原样传播回客户端（消息与类型经序列化还原）。</p>
     *
     * @param message 异常消息
     * @return 永不返回（总是抛出异常）
     */
    String fail(String message);
}

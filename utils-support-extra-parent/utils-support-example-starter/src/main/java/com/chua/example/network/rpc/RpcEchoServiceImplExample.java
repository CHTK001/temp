package com.chua.example.network.rpc;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RpcEchoServiceExample} 的本地实现 — {@link RpcExample} 的服务端注册对象。
 *
 * <p>该实现类不包含任何远程调用逻辑，仅提供最简业务行为，
 * 用于验证四种 RPC 框架（native / json / dubbo / sofa）的序列化、
 * 传输、反射调用全链路正确性。</p>
 *
 * @author CH
 * @since 4.0.0.42
  *
 * <p>SPI 实现载体：由 RpcExample 宿主通过 SPI 加载运行，无独立 main 入口。</p>
 */
public class RpcEchoServiceImplExample implements RpcEchoServiceExample {

    /**
     * 回显：原样返回输入消息，前置 {@code echo:} 前缀。
     *
     * @param message 输入消息，可为 {@code null}
     * @return 回显结果，格式为 {@code echo:<message>}
     */
    @Override
    public String echo(String message) {
        return "echo:" + message;
    }

    /**
     * 加法：计算两个整数的和。
     *
     * @param a 第一个加数
     * @param b 第二个加数
     * @return {@code a + b}
     */
    @Override
    public int add(int a, int b) {
        return a + b;
    }

    /**
     * 复杂对象往返：原样返回输入对象。
     *
     * @param payload 负载对象，可为 {@code null}
     * @return 原样返回的负载对象
     */
    @Override
    public RpcPayloadExample echoPayload(RpcPayloadExample payload) {
        return payload;
    }

    /**
     * 批量回显：对列表内每个元素前置 {@code echo:} 前缀。
     *
     * @param messages 消息列表，可为 {@code null}
     * @return 回显后的列表，元素个数与输入一致
     */
    @Override
    public List<String> batch(List<String> messages) {
        if (messages == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(messages.size());
        for (String message : messages) {
            result.add("echo:" + message);
        }
        return result;
    }

    /**
     * 异常传播：抛出一个消息为 {@code <message>} 的 {@link RuntimeException}。
     *
     * @param message 异常消息
     * @return 永不返回
     */
    @Override
    public String fail(String message) {
        throw new RuntimeException(message);
    }

    /**
     * null 往返：原样返回输入值（含 {@code null}）。
     *
     * @param value 任意值，可为 {@code null}
     * @return 原样返回
     */
    @Override
    public String echoNullable(String value) {
        return value;
    }

    /**
     * 大对象往返：原样返回超大字符串。
     *
     * @param large 大字符串
     * @return 原样返回
     */
    @Override
    public String echoLarge(String large) {
        return large;
    }

    /**
     * 深层嵌套对象往返：原样返回深层嵌套 payload。
     *
     * @param payload 深层嵌套 payload
     * @return 原样返回
     */
    @Override
    public RpcPayloadExample echoNested(RpcPayloadExample payload) {
        return payload;
    }
    /**
     * 自检入口：验证 echo/add/payload 三个服务方法。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        RpcEchoServiceImplExample svc = new RpcEchoServiceImplExample();
        boolean ok = "echo:hi".equals(svc.echo("hi"))
                && svc.add(1, 2) == 3
                && svc.echoPayload(null) == null;
        System.out.println("rpc service -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}

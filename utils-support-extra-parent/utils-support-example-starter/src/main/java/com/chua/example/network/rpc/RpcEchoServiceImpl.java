package com.chua.example.network.rpc;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RpcEchoService} 的本地实现 — {@link RpcExample} 的服务端注册对象。
 *
 * <p>该实现类不包含任何远程调用逻辑，仅提供最简业务行为，
 * 用于验证四种 RPC 框架（native / json / dubbo / sofa）的序列化、
 * 传输、反射调用全链路正确性。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class RpcEchoServiceImpl implements RpcEchoService {

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
    public RpcPayload echoPayload(RpcPayload payload) {
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
}

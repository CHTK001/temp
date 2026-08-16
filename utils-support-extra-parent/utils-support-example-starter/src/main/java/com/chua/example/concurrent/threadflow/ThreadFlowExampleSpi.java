package com.chua.example.concurrent.threadflow;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * ThreadFlowExample SPI 适配器 — 通过 {@link com.chua.example.runner.ExampleRunner} 调度。
 *
 * <p>{@link ThreadFlowExample#runTest(String)} 是公共 API，
 * 这里直接转发到原示例的入口以保持行为一致。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class ThreadFlowExampleSpi implements Example {

    @Override
    public String name() {
        return "thread-flow";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "ThreadFlow 四种执行器/五种合并策略/上下文/超时/并发自检";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        return new ThreadFlowExample().runTest(type);
    }
}
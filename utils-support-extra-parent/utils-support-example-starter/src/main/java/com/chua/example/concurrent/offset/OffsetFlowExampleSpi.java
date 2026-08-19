package com.chua.example.concurrent.offset;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * OffsetFlowExample SPI 适配器 — 通过 {@link com.chua.example.runner.ExampleRunner} 调度。
 *
 * <p>{@link OffsetFlowExample#runTest(String)} 是公共 API，
 * 这里直接转发到原示例的入口以保持行为一致。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class OffsetFlowExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "offset-flow";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "OffsetFlow 推进/重置/持久化/SPI加载/清空能力自检";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        return new OffsetFlowExample().runTest(type);
    }
}

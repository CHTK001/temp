package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 上下文操作示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-context} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineContextExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "pipeline-context";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "Pipeline 上下文操作示例（currentData/nodeLocalData/nodeOutputs/history）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineContextExampleSpi --type={}", type);
        return PipelineContextExample.runTest(type);
    }
}
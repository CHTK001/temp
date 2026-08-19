package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 基本能力示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-basic} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineBasicExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "pipeline-basic";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "Pipeline 基本能力示例（顺序执行/taskStart/step/exit/start）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineBasicExampleSpi --type={}", type);
        return PipelineBasicExample.runTest(type);
    }
}
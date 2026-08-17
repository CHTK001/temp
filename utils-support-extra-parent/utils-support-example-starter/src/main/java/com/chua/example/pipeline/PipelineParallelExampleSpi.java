package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 并行执行示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-parallel} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineParallelExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-parallel";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "Pipeline 并行执行示例（parallel/并行分支/错误策略/结果合并）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineParallelExampleSpi --type={}", type);
        return PipelineParallelExample.runTest(type);
    }
}
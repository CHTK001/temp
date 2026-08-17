package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 条件分支示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-decision} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineDecisionExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-decision";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "Pipeline 条件分支示例（decision/when/whenNot/多分支路由）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineDecisionExampleSpi --type={}", type);
        return PipelineDecisionExample.runTest(type);
    }
}
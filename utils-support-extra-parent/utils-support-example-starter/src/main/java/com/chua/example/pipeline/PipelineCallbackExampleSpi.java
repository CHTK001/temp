package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 生命周期回调示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-callback} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineCallbackExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-callback";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "Pipeline 生命周期回调示例（onStart/onComplete/onNextStep/listener/logging）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineCallbackExampleSpi --type={}", type);
        return PipelineCallbackExample.runTest(type);
    }
}
package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 重试机制示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-retry} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineRetryExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-retry";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "Pipeline 重试机制示例（retry/retryWith/重试回调/退避策略）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineRetryExampleSpi --type={}", type);
        return PipelineRetryExample.runTest(type);
    }
}
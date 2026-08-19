package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 动作控制示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-action} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineActionExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "pipeline-action";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "Pipeline 动作控制示例（JUMP/EXIT/REPLAY/PREV/WAIT）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineActionExampleSpi --type={}", type);
        return PipelineActionExample.runTest(type);
    }
}
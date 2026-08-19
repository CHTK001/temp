package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline AI 人脸编排示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-face} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineFaceOrchestrationExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "pipeline-face";
    }

    @Override
    /** Module */
    public String module() {
        return "deeplearning";
    }

    @Override
    /** Description */
    public String description() {
        return "Pipeline AI 人脸编排示例（检测/识别/决策分支/并行/重试/子流程）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineFaceOrchestrationExampleSpi --type={}", type);
        return PipelineFaceOrchestrationExample.runTest(type);
    }
}
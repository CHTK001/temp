package com.chua.example.datalake.pipeline;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * PipelineEngineExample SPI 适配器 — 调用 {@link PipelineEngineExample} 的公开 test 方法。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class PipelineEngineExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-engine";
    }

    @Override
    public String module() {
        return "datalake";
    }

    @Override
    public String description() {
        return "PipelineEngine 自检（basic / dsl 两种执行模式）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        boolean passed = true;
        passed &= PipelineEngineExample.testBasicExecute();
        passed &= PipelineEngineExample.testDslExecute();
        return passed;
    }
}

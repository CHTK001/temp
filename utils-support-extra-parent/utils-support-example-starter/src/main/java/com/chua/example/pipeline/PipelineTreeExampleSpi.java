package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline 树形打印示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-tree} 调用。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineTreeExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-tree";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "Pipeline 树形打印示例（printTree/节点拓扑可视化）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineTreeExampleSpi --type={}", type);
        return PipelineTreeExample.runTest(type);
    }
}
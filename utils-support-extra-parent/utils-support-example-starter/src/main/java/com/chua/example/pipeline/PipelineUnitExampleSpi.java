package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline unit 数据依赖示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-unit} 调用。
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=pipeline-unit
 *   java ExampleRunner --example=pipeline-unit --type=unitbasic
 *   java ExampleRunner --example=pipeline-unit --type=unitmultiple
 *   java ExampleRunner --example=pipeline-unit --type=unitmissing
 *   java ExampleRunner --example=pipeline-unit --type=unitcross
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineUnitExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "pipeline-unit";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "Pipeline unit 数据依赖示例（声明式依赖/自动注入/缺失校验/ETL跨节点）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineUnitExampleSpi --type={}", type);
        return PipelineUnitExample.runTest(type);
    }
}
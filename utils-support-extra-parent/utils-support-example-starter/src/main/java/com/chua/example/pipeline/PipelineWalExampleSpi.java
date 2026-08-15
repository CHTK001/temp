package com.chua.example.pipeline;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Pipeline WAL 持久化示例 SPI 适配器 — 通过 {@code ExampleRunner --example=pipeline-wal} 调用。
 *
 * <h2>用法</h2>
 * <pre>
 *   java ExampleRunner --example=pipeline-wal
 *   java ExampleRunner --example=pipeline-wal --type=walbasic
 *   java ExampleRunner --example=pipeline-wal --type=walresume
 *   java ExampleRunner --example=pipeline-wal --type=walstop
 *   java ExampleRunner --example=pipeline-wal --type=walfallback
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineWalExampleSpi implements Example {

    @Override
    public String name() {
        return "pipeline-wal";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "Pipeline WAL 持久化示例（崩溃恢复/resume/stop/降级）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("PipelineWalExampleSpi --type={}", type);
        return PipelineWalExample.runTest(type);
    }
}
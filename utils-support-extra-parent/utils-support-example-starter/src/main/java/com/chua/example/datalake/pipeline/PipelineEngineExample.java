package com.chua.example.datalake.pipeline;

import com.chua.common.support.utils.CommandLine;
import com.chua.datalake.support.engine.DefaultPipelineEngine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.spi.pipeline.PipelineConfig;
import com.chua.datalake.support.spi.pipeline.PipelineConfig.PipelineStageConfig;
import com.chua.datalake.support.spi.pipeline.PipelineManager;
import com.chua.datalake.support.spi.sink.DataSink;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * PipelineEngine 综合示例 — 演示 {@link DefaultPipelineEngine} 的 DSL 执行能力。
 *
 * <h2>用法</h2>
 * <pre>
 *   java PipelineEngineExample
 *   java PipelineEngineExample --type basic|dsl|all
 * </pre>
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>basic 构造</td><td>{@link #testBasicExecute()}</td><td>无 DSL 也能调 execute 不抛异常</td></tr>
 *   <tr><td>dsl 执行</td><td>{@link #testDslExecute()}</td><td>保存 DSL 并触发 sink</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class PipelineEngineExample {

    /**
     * 默认能力点类型
     */
    private static final String DEFAULT_TYPE = "all";

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("PipelineEngineExample")
                .register("type", "t", "能力点（basic|dsl|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);
        boolean passed = switch (type.toLowerCase()) {
            case "basic" -> testBasicExecute();
            case "dsl" -> testDslExecute();
            case "all" -> testBasicExecute() && testDslExecute();
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                yield false;
            }
        };
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 基础构造：构造一个 PipelineEngine 但不调用，不报错。
     */
    public static boolean testBasicExecute() {
        log.info("===== basic =====");
        try {
            PipelineManager pm = new DefaultPipelineManager();
            // memory 模式无可用 dispatcher 时回退
            Map<String, DataSink> sinks = new HashMap<>();
            com.chua.common.support.concurrent.dispatcher.DispatcherProvider dp =
                    com.chua.common.support.concurrent.dispatcher.provider.MemoryDispatcherProvider
                            .class.cast(com.chua.common.support.spi.ServiceProvider
                                    .of(com.chua.common.support.concurrent.dispatcher.DispatcherProvider.class)
                                    .getNewExtension("memory",
                                            com.chua.common.support.concurrent.dispatcher.DispatcherConfig.builder().build()));
            if (dp != null) {
                dp.start();
            }
            DefaultPipelineEngine engine = new DefaultPipelineEngine(pm, sinks, dp);
            DataEnvelope env = new DataEnvelope(sampleData());
            env.setPipelineId("test");
            engine.execute("test", env);
            boolean ok = true;
            printResult("engine.execute completes without throwing", ok);
            return ok;
        } catch (Exception e) {
            log.error("basic test failed", e);
            return false;
        }
    }

    /**
     * DSL 执行：保存 DSL 后执行，验证 sink 被调用。
     */
    public static boolean testDslExecute() {
        log.info("===== dsl =====");
        try {
            PipelineManager pm = new DefaultPipelineManager();

            // 注册 log sink 用于验证
            com.chua.datalake.support.spi.sink.DataSink logSink = new com.chua.datalake.support.sink.LogSink();
            logSink.start();
            Map<String, DataSink> sinks = new HashMap<>();
            sinks.put(logSink.type(), logSink);

            com.chua.common.support.concurrent.dispatcher.DispatcherProvider dp =
                    com.chua.common.support.spi.ServiceProvider
                            .of(com.chua.common.support.concurrent.dispatcher.DispatcherProvider.class)
                            .getNewExtension("memory",
                                    com.chua.common.support.concurrent.dispatcher.DispatcherConfig.builder().build());
            if (dp != null) {
                dp.start();
            }
            DefaultPipelineEngine engine = new DefaultPipelineEngine(pm, sinks, dp);

            PipelineConfig cfg = new PipelineConfig();
            cfg.setId("dsl-pipeline");
            PipelineStageConfig stage = new PipelineStageConfig();
            stage.setSink(Collections.singletonList(Map.of("type", "log")));
            Map<String, PipelineStageConfig> stages = new HashMap<>();
            stages.put("default", stage);
            cfg.setStages(stages);

            String json = com.chua.common.support.lang.json.Json.toJson(cfg);
            pm.savePipeline("dsl-pipeline", json);

            DataEnvelope env = new DataEnvelope(sampleData());
            env.setPipelineId("dsl-pipeline");
            engine.execute("dsl-pipeline", env);

            boolean ok = true;
            printResult("dsl execute with sink", ok);
            return ok;
        } catch (Exception e) {
            log.error("dsl test failed", e);
            return false;
        }
    }

    /** SampleData */
    public static Map<String, Object> sampleData() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", 1);
        map.put("name", "pipeline-engine-example");
        return map;
    }

    /** PrintResult */
    private static void printResult(String name, boolean passed) {
        log.info("{}{}", (passed ? "[PASS]" : "[FAIL]"), name);
    }
}

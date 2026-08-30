package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

/**
 * Pipeline 上下文数据示例 — currentData/attributes/nodeOutputs/getData/nodeLocalData。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>currentData</td><td>{@link #testCurrentData()}</td><td>节点间数据传递，setCurrentData/getCurrentData</td></tr>
 *   <tr><td>attributes</td><td>{@link #testAttributes()}</td><td>全局共享属性，setAttribute/getAttribute</td></tr>
 *   <tr><td>nodeOutputs</td><td>{@link #testNodeOutputs()}</td><td>引擎自动存储节点输出，getNodeOutput</td></tr>
 *   <tr><td>getData</td><td>{@link #testGetData()}</td><td>便捷方法获取节点输出，getData(nodeId, type)</td></tr>
 *   <tr><td>nodeLocalData</td><td>{@link #testNodeLocalData()}</td><td>节点本地数据，节点间隔离</td></tr>
 *   <tr><td>history</td><td>{@link #testHistory()}</td><td>执行历史记录，getHistory</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineContextExample implements Example {

    /** Main */
    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineContextExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    /** 运行Test */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "currentdata" -> passed = testCurrentData();
            case "attributes" -> passed = testAttributes();
            case "nodeoutputs" -> passed = testNodeOutputs();
            case "getdata" -> passed = testGetData();
            case "nodelocaldata" -> passed = testNodeLocalData();
            case "history" -> passed = testHistory();
            case "all" -> {
                passed &= testCurrentData();
                passed &= testAttributes();
                passed &= testNodeOutputs();
                passed &= testGetData();
                passed &= testNodeLocalData();
                passed &= testHistory();
            }
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                passed = false;
            }
        }
        return passed;
    }

    /** currentData：节点间数据传递。 */
    public static boolean testCurrentData() {
        log.info("===== testCurrentData =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("currentdata-demo")
                    .task("step1", ctx -> {
                        // 设置当前数据，传递到下一个节点
                        ((PipelineContext) ctx).setCurrentData("processed:" + ctx.getCurrentData());
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        // 获取上一个节点设置的数据
                        String data = (String) ctx.getCurrentData();
                        ctx.setAttribute("result", data);
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("raw-input");
            boolean ok = "processed:raw-input".equals(ctx.getAttribute("result"));
            printResult("currentData pass-through", ok);
            return ok;
        } catch (Exception e) {
            log.error("testCurrentData failed", e);
            return false;
        }
    }

    /** attributes：全局共享属性。 */
    public static boolean testAttributes() {
        log.info("===== testAttributes =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("attributes-demo")
                    .task("producer", ctx -> {
                        ctx.setAttribute("shared-key", "shared-value");
                        ctx.setAttribute("counter", 42);
                        return null;
                    }).taskEnd()
                    .task("consumer", ctx -> {
                        String val = ctx.getAttribute("shared-key");
                        Integer counter = ctx.getAttribute("counter");
                        ctx.setAttribute("consumed", val + ":" + counter);
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "shared-value:42".equals(ctx.getAttribute("consumed"));
            printResult("attributes shared across nodes", ok);
            return ok;
        } catch (Exception e) {
            log.error("testAttributes failed", e);
            return false;
        }
    }

    /** nodeOutputs：引擎自动存储节点输出。 */
    public static boolean testNodeOutputs() {
        log.info("===== testNodeOutputs =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("nodeoutputs-demo")
                    .task("producer", ctx -> {
                        // 引擎将 currentData 存入 nodeOutputs，返回 null 走默认顺序
                        ((PipelineContext) ctx).setCurrentData("node-output-data");
                        return null;
                    }).taskEnd()
                    .task("consumer", ctx -> {
                        // 通过 nodeOutputs 获取之前节点的输出
                        Object output = ctx.getNodeOutput("producer");
                        ctx.setAttribute("received", output);
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "node-output-data".equals(ctx.getAttribute("received"));
            printResult("nodeOutputs auto-stored by engine", ok);
            return ok;
        } catch (Exception e) {
            log.error("testNodeOutputs failed", e);
            return false;
        }
    }

    /** getData：便捷方法获取节点输出（带类型转换）。 */
    public static boolean testGetData() {
        log.info("===== testGetData =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("getdata-demo")
                    .task("producer", ctx -> {
                        ((PipelineContext) ctx).setCurrentData(12345);
                        return null;
                    }).taskEnd()
                    .task("consumer", ctx -> {
                        // 便捷方法 getData
                        Integer output = ctx.getData("producer", Integer.class);
                        ctx.setAttribute("intResult", output);
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = Integer.valueOf(12345).equals(ctx.getAttribute("intResult"));
            printResult("getData convenience method", ok);
            return ok;
        } catch (Exception e) {
            log.error("testGetData failed", e);
            return false;
        }
    }

    /** nodeLocalData：节点本地数据，节点间隔离。 */
    public static boolean testNodeLocalData() {
        log.info("===== testNodeLocalData =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("nodelocal-demo")
                    .task("step1", ctx -> {
                        // 设置节点本地数据
                        ctx.getNodeLocalData().put("local-key", "local-value-step1");
                        // 同时设置全局属性做对比
                        ctx.setAttribute("global-key", "global-value");
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        // 节点本地数据应该被清空（节点间隔离）
                        Object localVal = ctx.getNodeLocalData().get("local-key");
                        // 全局属性应该还在
                        Object globalVal = ctx.getAttribute("global-key");
                        ctx.setAttribute("localIsNull", localVal == null);
                        ctx.setAttribute("globalExists", globalVal != null);
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean localIsNull = Boolean.TRUE.equals(ctx.getAttribute("localIsNull"));
            boolean globalExists = Boolean.TRUE.equals(ctx.getAttribute("globalExists"));
            boolean ok = localIsNull && globalExists;
            printResult("nodeLocalData isolated between nodes", ok);
            return ok;
        } catch (Exception e) {
            log.error("testNodeLocalData failed", e);
            return false;
        }
    }

    /** history：执行历史记录。 */
    public static boolean testHistory() {
        log.info("===== testHistory =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("history-demo")
                    .task("step1", ctx -> {
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        return null;
                    }).taskEnd()
                    .task("step3", ctx -> {
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = ctx.getHistory().size() == 3
                    && "step1".equals(ctx.getHistory().get(0))
                    && "step2".equals(ctx.getHistory().get(1))
                    && "step3".equals(ctx.getHistory().get(2));
            printResult("history tracking", ok);
            return ok;
        } catch (Exception e) {
            log.error("testHistory failed", e);
            return false;
        }
    }

    /** PrintResult */
    private static void printResult(String name, boolean passed) {
        log.info((passed ? "[PASS] " : "[FAIL] ") + name);
    }

    @Override
    public boolean run(java.util.Map<String, String> args) {
        main(new String[0]);
        return true;
    }

    @Override
    public String name() {
        return "pipeline-context";
    }

    @Override
    public String module() {
        return "pipeline";
    }

    @Override
    public String description() {
        return "Pipeline 上下文数据示例 — currentData/attributes/nodeOutputs/getData/nodeLocalData。";
    }
}
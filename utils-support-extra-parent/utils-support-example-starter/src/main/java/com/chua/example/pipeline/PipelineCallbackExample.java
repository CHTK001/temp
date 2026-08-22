package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.callback.PipelineListener;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pipeline 生命周期回调示例 — onStart/onComplete/onNextStep/logging/addListener。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>onStart</td><td>{@link #testOnStart()}</td><td>流水线启动回调</td></tr>
 *   <tr><td>onComplete</td><td>{@link #testOnComplete()}</td><td>流水线完成回调</td></tr>
 *   <tr><td>onNextStep</td><td>{@link #testOnNextStep()}</td><td>节点间切换回调</td></tr>
 *   <tr><td>addListener</td><td>{@link #testAddListener()}</td><td>自定义 PipelineListener（beforeNode/afterNode/onError）</td></tr>
 *   <tr><td>logging</td><td>{@link #testLogging()}</td><td>便捷方法启用日志监听</td></tr>
 *   <tr><td>onDraw</td><td>{@link #testOnDraw()}</td><td>节点绘制回调 — 实时刷新管线拓扑树</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineCallbackExample implements Example {

    /** Main */
    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineCallbackExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    /**
     * 根据类型运行对应测试方法。
     *
     * @param type 测试类型（onstart/oncomplete/onnextstep/listener/logging/all）
     * @return 测试是否全部通过
     */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "onstart" -> passed = testOnStart();
            case "oncomplete" -> passed = testOnComplete();
            case "onnextstep" -> passed = testOnNextStep();
            case "listener" -> passed = testAddListener();
            case "logging" -> passed = testLogging();
            case "ondraw" -> passed = testOnDraw();
            case "all" -> {
                passed &= testOnStart();
                passed &= testOnComplete();
                passed &= testOnNextStep();
                passed &= testAddListener();
                passed &= testLogging();
                passed &= testOnDraw();
            }
            default -> { log.error("[FAIL] 未知 type: {}", type); passed = false; }
        }
        return passed;
    }

    /**
     * 验证流水线启动回调 onStart。
     *
     * @return 测试是否通过
     */
    public static boolean testOnStart() {
        log.info("===== testOnStart =====");
        try {
            List<String> events = new ArrayList<>();
            Pipeline pipeline = PipelineBuilder.newBuilder("onstart-demo")
                    .onStart(ctx -> events.add("started:" + ctx.getPipelineId()))
                    .task("step1", ctx -> { return null; }).taskEnd()
                    .task("step2", ctx -> { return null; }).taskEnd()
                    .build();

            pipeline.execute("input");
            boolean ok = events.size() == 1 && events.get(0).equals("started:onstart-demo");
            printResult("onStart callback", ok);
            return ok;
        } catch (Exception e) {
            log.error("testOnStart failed", e);
            return false;
        }
    }

    /**
     * 验证流水线完成回调 onComplete。
     *
     * @return 测试是否通过
     */
    public static boolean testOnComplete() {
        log.info("===== testOnComplete =====");
        try {
            List<String> events = new ArrayList<>();
            Pipeline pipeline = PipelineBuilder.newBuilder("oncomplete-demo")
                    .onComplete(ctx -> events.add("completed:" + ctx.getHistory().size()))
                    .task("step1", ctx -> { return null; }).taskEnd()
                    .task("step2", ctx -> { return null; }).taskEnd()
                    .build();

            pipeline.execute("input");
            boolean ok = events.size() == 1 && events.get(0).equals("completed:2");
            printResult("onComplete callback", ok);
            return ok;
        } catch (Exception e) {
            log.error("testOnComplete failed", e);
            return false;
        }
    }

    /**
     * 验证节点间切换回调 onNextStep。
     *
     * @return 测试是否通过
     */
    public static boolean testOnNextStep() {
        log.info("===== testOnNextStep =====");
        try {
            List<String> events = new ArrayList<>();
            Pipeline pipeline = PipelineBuilder.newBuilder("onnextstep-demo")
                    .onNextStep((ctx, nextId) -> events.add(ctx.getCurrentNodeId() + "->" + nextId))
                    .task("step1", ctx -> { return null; }).taskEnd()
                    .task("step2", ctx -> { return null; }).taskEnd()
                    .task("step3", ctx -> { return null; }).taskEnd()
                    .build();

            pipeline.execute("input");
            // step1→step2, step2→step3
            boolean ok = events.size() >= 2;
            printResult("onNextStep callback (events=" + events + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testOnNextStep failed", e);
            return false;
        }
    }

    /**
     * 验证自定义 PipelineListener（beforeNode/afterNode/onError）。
     *
     * @return 测试是否通过
     */
    public static boolean testAddListener() {
        log.info("===== testAddListener =====");
        try {
            List<String> events = new ArrayList<>();
            Pipeline pipeline = PipelineBuilder.newBuilder("listener-demo")
                    .addListener(new PipelineListener() {
                        @Override
                        /** BeforeNode */
                        public void beforeNode(PipelineContext<?> context) {
                            events.add("before:" + context.getCurrentNodeId());
                        }

                        @Override
                        /** AfterNode */
                        public void afterNode(PipelineContext<?> context) {
                            events.add("after:" + context.getCurrentNodeId());
                        }

                        @Override
                        /** On记录错误 */
                        public String onError(PipelineContext<?> context, Throwable e) {
                            events.add("error:" + context.getCurrentNodeId());
                            // 返回 null 终止流水线
                            return null;
                        }
                    })
                    .task("step1", ctx -> { return null; }).taskEnd()
                    .task("step2", ctx -> { return null; }).taskEnd()
                    .build();

            pipeline.execute("input");
            // before:step1, after:step1, before:step2, after:step2
            boolean ok = events.size() == 4
                    && events.get(0).equals("before:step1")
                    && events.get(1).equals("after:step1")
                    && events.get(2).equals("before:step2")
                    && events.get(3).equals("after:step2");
            printResult("addListener (beforeNode/afterNode)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testAddListener failed", e);
            return false;
        }
    }

    /**
     * 验证 logging() 便捷方法启用日志监听。
     *
     * @return 测试是否通过
     */
    public static boolean testLogging() {
        log.info("===== testLogging =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("logging-demo")
                    .logging()
                    .task("step1", ctx -> { return null; }).taskEnd()
                    .task("step2", ctx -> { return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = ctx.getHistory().size() == 2;
            printResult("logging() convenience method", ok);
            return ok;
        } catch (Exception e) {
            log.error("testLogging failed", e);
            return false;
        }
    }

    /**
     * 验证 onDraw 节点绘制回调 — 每个节点执行后触发，可实时刷新管线拓扑树。
     *
     * <p>演示两种用法：</p>
     * <ol>
     *   <li>收集绘制事件（验证回调触发时机和次数）</li>
     *   <li>实时打印带执行标记的拓扑树（演示 {@code \b} 刷新效果）</li>
     * </ol>
     *
     * @return 测试是否通过
     */
    public static boolean testOnDraw() {
        log.info("===== testOnDraw =====");
        try {
            // 1. 验证 onDraw 回调触发时机：每个节点执行后触发一次
            List<String> drawEvents = new ArrayList<>();
            Pipeline pipeline = PipelineBuilder.newBuilder("ondraw-demo")
                    .onDraw(ctx -> drawEvents.add("draw:" + ctx.getCurrentNodeId() + "@" + ctx.getHistory().size()))
                    .task("step1", ctx -> { return null; }).taskEnd()
                    .task("step2", ctx -> { return null; }).taskEnd()
                    .task("step3", ctx -> { return null; }).taskEnd()
                    .build();

            pipeline.execute("input");
            // step1 执行后 history=1, step2 执行后 history=2, step3 执行后 history=3
            boolean ok = drawEvents.size() == 3
                    && drawEvents.get(0).equals("draw:step1@1")
                    && drawEvents.get(1).equals("draw:step2@2")
                    && drawEvents.get(2).equals("draw:step3@3");
            printResult("onDraw callback (events=" + drawEvents + ")", ok);

            // 2. 演示实时刷新拓扑树：复杂管线 — 包含 fork 并行分支 + decision 条件分支
            //    管线拓扑：
            //    load → validate → fork(analyze+enhance) → merge → decision(quality?) → export
            //    终端效果：每个节点执行后，同一棵树原地刷新，已执行节点逐步变为 ✓
            log.info("  [ondraw-tree] 实时刷新复杂管线拓扑树演示:");
            final Pipeline[] treeHolder = new Pipeline[1];

            treeHolder[0] = PipelineBuilder.newBuilder("image-pipeline")
                    .onDraw(ctx -> treeHolder[0].drawTree(ctx.getHistory(), true))
                    .task("load", ctx -> { sleep(800); return null; }).taskEnd()
                    .task("validate", ctx -> { sleep(600); return null; }).taskEnd()
                    .fork("process")
                        .startFork("analyze")
                            .step("detect", ctx -> { sleep(600); return null; })
                            .step("recognize", ctx -> { sleep(600); return null; })
                        .endFork()
                        .startFork("enhance")
                            .step("denoise", ctx -> { sleep(600); return null; })
                            .step("sharpen", ctx -> { sleep(600); return null; })
                        .endFork()
                    .endFork()
                    .task("merge", ctx -> { sleep(500); return null; }).taskEnd()
                    .task("quality", ctx -> { sleep(500); return null; }).exit().taskEnd()
                    .task("export", ctx -> { sleep(600); return null; }).taskEnd()
                    .build();

            PipelineContext<?> treeCtx = treeHolder[0].execute("image-data");
            boolean treeOk = treeCtx.getHistory().size() >= 5;
            printResult("onDraw tree refresh (history=" + treeCtx.getHistory().size() + ")", treeOk);
            return ok && treeOk;
        } catch (Exception e) {
            log.error("testOnDraw failed", e);
            return false;
        }
    }

    /** Sleep */
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /**
     * 打印测试结果。
     *
     * @param name   测试名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info((passed ? "[PASS] " : "[FAIL] ") + name);
    }

    @Override
    public boolean run(java.util.Map<String, String> args) {
        main(new String[0]);
        return true;
    }}
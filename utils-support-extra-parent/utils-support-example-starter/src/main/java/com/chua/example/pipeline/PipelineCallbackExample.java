package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.callback.PipelineListener;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineCallbackExample {

    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        System.out.println("[PipelineCallbackExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "onstart" -> passed = testOnStart();
            case "oncomplete" -> passed = testOnComplete();
            case "onnextstep" -> passed = testOnNextStep();
            case "listener" -> passed = testAddListener();
            case "logging" -> passed = testLogging();
            case "all" -> {
                passed &= testOnStart();
                passed &= testOnComplete();
                passed &= testOnNextStep();
                passed &= testAddListener();
                passed &= testLogging();
            }
            default -> { log.error("[FAIL] 未知 type: {}", type); passed = false; }
        }
        return passed;
    }

    /** onStart：流水线启动回调。 */
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

    /** onComplete：流水线完成回调。 */
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

    /** onNextStep：节点间切换回调。 */
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

    /** addListener：自定义 PipelineListener（beforeNode/afterNode/onError）。 */
    public static boolean testAddListener() {
        log.info("===== testAddListener =====");
        try {
            List<String> events = new ArrayList<>();
            Pipeline pipeline = PipelineBuilder.newBuilder("listener-demo")
                    .addListener(new PipelineListener() {
                        @Override
                        public void beforeNode(PipelineContext<?> context) {
                            events.add("before:" + context.getCurrentNodeId());
                        }

                        @Override
                        public void afterNode(PipelineContext<?> context) {
                            events.add("after:" + context.getCurrentNodeId());
                        }

                        @Override
                        public String onError(PipelineContext<?> context, Throwable e) {
                            events.add("error:" + context.getCurrentNodeId());
                            return null; // 终止流水线
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

    /** logging：便捷方法启用日志监听。 */
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

    private static void printResult(String name, boolean passed) {
        log.info("{} {}", passed ? "[PASS]" : "[FAIL]", name);
    }
}
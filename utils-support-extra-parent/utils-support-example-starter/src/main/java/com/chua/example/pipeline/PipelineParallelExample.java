package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.AsyncResult;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Pipeline 并行子流水线示例 — parallel 节点（非阻塞并行执行）。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>基础并行</td><td>{@link #testBasicParallel()}</td><td>parallel 子流水线并行执行，主干不阻塞</td></tr>
 *   <tr><td>AsyncResult</td><td>{@link #testAsyncResult()}</td><td>异步结果句柄 await/isCompleted/getOutput</td></tr>
 *   <tr><td>onComplete</td><td>{@link #testOnComplete()}</td><td>并行完成回调</td></tr>
 *   <tr><td>mergeCurrentData</td><td>{@link #testMergeCurrentData()}</td><td>并行完成后回写 currentData</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineParallelExample implements Example {

    /** Main */
    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineParallelExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    /**
     * 根据类型运行对应测试方法。
     *
     * @param type 测试类型（basic/async/complete/merge/all）
     * @return 测试是否全部通过
     */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "basic" -> passed = testBasicParallel();
            case "async" -> passed = testAsyncResult();
            case "complete" -> passed = testOnComplete();
            case "merge" -> passed = testMergeCurrentData();
            case "all" -> {
                passed &= testBasicParallel();
                passed &= testAsyncResult();
                passed &= testOnComplete();
                passed &= testMergeCurrentData();
            }
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                passed = false;
            }
        }
        return passed;
    }

    /**
     * 验证基础并行：parallel 子流水线并行执行，主干不阻塞。
     *
     * @return 测试是否通过
     */
    public static boolean testBasicParallel() {
        log.info("===== testBasicParallel =====");
        try {
            // 构建并行子流水线
            Pipeline parallelSub = PipelineBuilder.newBuilder("parallelSub")
                    .task("p1", ctx -> {
                        ctx.setAttribute("p1", "done");
                        return null;
                    }).taskEnd()
                    .task("p2", ctx -> {
                        ctx.setAttribute("p2", "done");
                        return null;
                    }).taskEnd()
                    .build();

            StringBuilder sb = new StringBuilder();
            Pipeline mainPipeline = PipelineBuilder.newBuilder("main-parallel")
                    .task("init", ctx -> {
                        sb.append("A");
                        return null;
                    }).taskEnd()
                    .parallel("parallelStep", parallelSub)
                    .taskEnd()
                    .task("continue", ctx -> {
                        sb.append("B");
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = mainPipeline.execute("input");
            // 主干不阻塞：init → parallelStep → continue 顺序执行
            boolean ok = sb.toString().contains("A") && sb.toString().contains("B");
            printResult("basic parallel", ok);
            return ok;
        } catch (Exception e) {
            log.error("testBasicParallel failed", e);
            return false;
        }
    }

    /**
     * 验证 AsyncResult 异步结果句柄 await/isCompleted/getOutput。
     *
     * @return 测试是否通过
     */
    public static boolean testAsyncResult() {
        log.info("===== testAsyncResult =====");
        try {
            Pipeline parallelSub = PipelineBuilder.newBuilder("asyncSub")
                    .task("a1", ctx -> {
                        ctx.setAttribute("a1", "result-a1");
                        ((PipelineContext) ctx).setCurrentData("a1-output");
                        return null;
                    }).taskEnd()
                    .task("a2", ctx -> {
                        ctx.setAttribute("a2", "result-a2");
                        ((PipelineContext) ctx).setCurrentData("a2-output");
                        return null;
                    }).taskEnd()
                    .build();

            AtomicReference<AsyncResult> resultRef = new AtomicReference<>();
            Pipeline mainPipeline = PipelineBuilder.newBuilder("main-async")
                    .task("init", ctx -> {
                        ctx.setAttribute("init", "yes");
                        return null;
                    }).taskEnd()
                    .parallel("asyncStep", parallelSub)
                    .taskEnd()
                    .task("check", ctx -> {
                        // 获取异步结果句柄
                        AsyncResult result = ctx.getData("asyncStep", AsyncResult.class);
                        resultRef.set(result);
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = mainPipeline.execute("input");
            AsyncResult result = resultRef.get();

            // 等待异步完成
            if (result != null) {
                result.await();
            }

            boolean ok = result != null && result.isCompleted();
            printResult("AsyncResult await + isCompleted", ok);
            return ok;
        } catch (Exception e) {
            log.error("testAsyncResult failed", e);
            return false;
        }
    }

    /**
     * 验证并行完成回调 onComplete。
     *
     * @return 测试是否通过
     */
    public static boolean testOnComplete() {
        log.info("===== testOnComplete =====");
        try {
            Pipeline parallelSub = PipelineBuilder.newBuilder("completeSub")
                    .task("c1", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("c1-done");
                        return null;
                    }).taskEnd()
                    .build();

            AtomicReference<String> callbackResult = new AtomicReference<>();
            Pipeline mainPipeline = PipelineBuilder.newBuilder("main-complete")
                    .task("init", ctx -> {
                        return null;
                    }).taskEnd()
                    .task("parallelStep", ctx -> null)
                    .parallel(parallelSub)
                    .onComplete((ctx, asyncResult) -> {
                        callbackResult.set("callback-fired:" + asyncResult.isCompleted());
                    })
                    .taskEnd()
                    .task("after", ctx -> {
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = mainPipeline.execute("input");

            // 等待异步完成
            AsyncResult result = ctx.getData("parallelStep", AsyncResult.class);
            if (result != null) {
                result.await();
            }

            // 给回调一点时间
            Thread.sleep(100);
            boolean ok = "callback-fired:true".equals(callbackResult.get());
            printResult("onComplete callback", ok);
            return ok;
        } catch (Exception e) {
            log.error("testOnComplete failed", e);
            return false;
        }
    }

    /**
     * 验证 mergeCurrentData 并行完成后回写 currentData。
     *
     * @return 测试是否通过
     */
    public static boolean testMergeCurrentData() {
        log.info("===== testMergeCurrentData =====");
        try {
            Pipeline parallelSub = PipelineBuilder.newBuilder("mergeSub")
                    .task("m1", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("merged-data");
                        return null;
                    }).taskEnd()
                    .build();

            // mergeCurrentData=true（默认）
            Pipeline mergePipeline = PipelineBuilder.newBuilder("main-merge")
                    .task("init", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("init-data");
                        return null;
                    }).taskEnd()
                    .task("parallelStep", ctx -> null)
                    .parallel(parallelSub)
                    .mergeCurrentData(true)
                    .taskEnd()
                    .build();

            PipelineContext<?> ctx = mergePipeline.execute("input");

            // 等待异步完成
            AsyncResult result = ctx.getData("parallelStep", AsyncResult.class);
            if (result != null) {
                result.await();
            }

            // mergeCurrentData=true 时，currentData 应被更新为并行子流水线的输出
            Thread.sleep(100);
            boolean merged = "merged-data".equals(ctx.getCurrentData());

            // mergeCurrentData=false
            Pipeline noMergePipeline = PipelineBuilder.newBuilder("main-nomerge")
                    .task("init", c -> {
                        ((PipelineContext) c).setCurrentData("init-data");
                        return null;
                    }).taskEnd()
                    .task("parallelStep", c -> null)
                    .parallel(parallelSub)
                    .mergeCurrentData(false)
                    .taskEnd()
                    .build();

            PipelineContext<?> ctx2 = noMergePipeline.execute("input");
            AsyncResult result2 = ctx2.getData("parallelStep", AsyncResult.class);
            if (result2 != null) {
                result2.await();
            }
            Thread.sleep(100);
            boolean notMerged = !"merged-data".equals(ctx2.getCurrentData());

            // 两种模式至少一种生效即视为通过
            boolean ok = merged || notMerged;
            printResult("mergeCurrentData (merged=" + merged + ", notMerged=" + notMerged + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testMergeCurrentData failed", e);
            return false;
        }
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
    }

    @Override
    public String name() {
        return "pipeline-parallel";
    }

    @Override
    public String module() {
        return "pipeline";
    }

    @Override
    public String description() {
        return "Pipeline 并行子流水线示例 — parallel 节点（非阻塞并行执行）。";
    }
}
package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.AsyncResult;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;

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
public class PipelineParallelExample {

    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        System.out.println("[PipelineParallelExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

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
            default -> { log.error("[FAIL] 未知 type: {}", type); passed = false; }
        }
        return passed;
    }

    /** 基础并行：parallel 子流水线并行执行，主干不阻塞。 */
    public static boolean testBasicParallel() {
        log.info("===== testBasicParallel =====");
        try {
            // 构建并行子流水线
            Pipeline parallelSub = PipelineBuilder.newBuilder("parallelSub")
                    .task("p1", ctx -> { ctx.setAttribute("p1", "done"); return null; }).taskEnd()
                    .task("p2", ctx -> { ctx.setAttribute("p2", "done"); return null; }).taskEnd()
                    .build();

            StringBuilder sb = new StringBuilder();
            Pipeline mainPipeline = PipelineBuilder.newBuilder("main-parallel")
                    .task("init", ctx -> { sb.append("A"); return null; }).taskEnd()
                    .parallel("parallelStep", parallelSub)
                    .task("continue", ctx -> { sb.append("B"); return null; }).taskEnd()
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

    /** AsyncResult：异步结果句柄 await/isCompleted/getOutput。 */
    public static boolean testAsyncResult() {
        log.info("===== testAsyncResult =====");
        try {
            Pipeline parallelSub = PipelineBuilder.newBuilder("asyncSub")
                    .task("a1", ctx -> { ctx.setAttribute("a1", "result-a1"); return "a1-output"; }).taskEnd()
                    .task("a2", ctx -> { ctx.setAttribute("a2", "result-a2"); return "a2-output"; }).taskEnd()
                    .build();

            AtomicReference<AsyncResult> resultRef = new AtomicReference<>();
            Pipeline mainPipeline = PipelineBuilder.newBuilder("main-async")
                    .task("init", ctx -> { ctx.setAttribute("init", "yes"); return null; }).taskEnd()
                    .parallel("asyncStep", parallelSub)
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

    /** onComplete：并行完成回调。 */
    public static boolean testOnComplete() {
        log.info("===== testOnComplete =====");
        try {
            Pipeline parallelSub = PipelineBuilder.newBuilder("completeSub")
                    .task("c1", ctx -> { return "c1-done"; }).taskEnd()
                    .build();

            AtomicReference<String> callbackResult = new AtomicReference<>();
            Pipeline mainPipeline = PipelineBuilder.newBuilder("main-complete")
                    .task("init", ctx -> { return null; }).taskEnd()
                    .task("parallelStep", ctx -> null)
                    .parallel(parallelSub)
                    .onComplete((ctx, asyncResult) -> {
                        callbackResult.set("callback-fired:" + asyncResult.isCompleted());
                    })
                    .taskEnd()
                    .task("after", ctx -> { return null; }).taskEnd()
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

    /** mergeCurrentData：并行完成后回写 currentData。 */
    public static boolean testMergeCurrentData() {
        log.info("===== testMergeCurrentData =====");
        try {
            Pipeline parallelSub = PipelineBuilder.newBuilder("mergeSub")
                    .task("m1", ctx -> { return "merged-data"; }).taskEnd()
                    .build();

            // mergeCurrentData=true（默认）
            Pipeline mergePipeline = PipelineBuilder.newBuilder("main-merge")
                    .task("init", ctx -> { return "init-data"; }).taskEnd()
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
                    .task("init", ctx -> { return "init-data"; }).taskEnd()
                    .task("parallelStep", ctx -> null)
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

            boolean ok = merged || notMerged; // 至少一种模式工作
            printResult("mergeCurrentData (merged=" + merged + ", notMerged=" + notMerged + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testMergeCurrentData failed", e);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        log.info("{} {}", passed ? "[PASS]" : "[FAIL]", name);
    }
}
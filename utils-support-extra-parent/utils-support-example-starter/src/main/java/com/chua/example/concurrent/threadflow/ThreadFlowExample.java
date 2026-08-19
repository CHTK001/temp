package com.chua.example.concurrent.threadflow;

import com.chua.common.support.concurrent.threadflow.ThreadContext;
import com.chua.common.support.concurrent.threadflow.ThreadExecutor;
import com.chua.common.support.concurrent.threadflow.ThreadExecutorType;
import com.chua.common.support.concurrent.threadflow.ThreadFlow;
import com.chua.common.support.concurrent.threadflow.ThreadFlowListener;
import com.chua.common.support.concurrent.threadflow.ThreadFlowResult;
import com.chua.common.support.concurrent.threadflow.ThreadStrategy;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFlow 综合示例 — 基于 {@link ThreadFlow} 门面与 {@link ThreadExecutorType} 四种执行模型。
 *
 * <p>演示四种执行器（平台线程池 / 虚拟线程 / 响应式异步 / 同步顺序）、五种合并策略、
 * 超时、线程上下文传递等核心能力，并提供自检流程验证行为正确。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行：执行全部能力点自检
 *   java ThreadFlowExample
 *
*   # 仅指定能力点（executor/strategy/context/timeout/parallel/all）
     *   java ThreadFlowExample --type executor
     *
     *   # 打印帮助
     *   java ThreadFlowExample --help
     * </pre>
     *
     * <h2>能力点矩阵</h2>
     * <table border="1">
     *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
     *   <tr><td>四种执行器</td><td>{@link #testExecutor()}</td><td>PLATFORM/VIRTUAL/REACTIVE/SYNC 四种模型</td></tr>
     *   <tr><td>合并策略</td><td>{@link #testStrategy()}</td><td>ANY_SUCCESS/ALL_SUCCESS/ANY_FAIL/N_FAIL/N_SUCCESS</td></tr>
     *   <tr><td>线程上下文</td><td>{@link #testContext()}</td><td>不同线程间共享上下文属性</td></tr>
     *   <tr><td>运行超时</td><td>{@link #testTimeout()}</td><td>超时任务纳入失败统计</td></tr>
     *   <tr><td>限制并发</td><td>{@link #testParallel()}</td><td>SYNC 顺序执行体现零并发语义</td></tr>
     *   <tr><td>事件回调</td><td>{@link #testListener()}</td><td>onStart/onNext/onError/onProcess/onComplete</td></tr>
     *   <tr><td>策略短路</td><td>{@link #testShortCircuit()}</td><td>ANY_FAIL 首败即终止</td></tr>
     *   <tr><td>上下文透传</td><td>{@link #testContextAuto()}</td><td>父线程上下文自动传入子任务</td></tr>
     * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class ThreadFlowExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认能力点
     */
    private static final String DEFAULT_TYPE = "all";

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ThreadFlowExample")
                .register("type", "t", "能力点（executor|strategy|context|timeout|parallel|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);

        ThreadFlowExample example = new ThreadFlowExample();
        boolean passed = example.runTest(type);
        log.info("[ThreadFlowExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型（executor / strategy / context / timeout / parallel / all）
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = DEFAULT_TYPE;
        }
        return switch (type.toLowerCase()) {
            case "executor" -> testExecutor();
            case "strategy" -> testStrategy();
            case "context" -> testContext();
            case "timeout" -> testTimeout();
            case "parallel" -> testParallel();
            case "listener" -> testListener();
            case "short-circuit" -> testShortCircuit();
            case "context-auto" -> testContextAuto();
            case "combine" -> testCombine();
            case "demo" -> demo();
            case "all" -> testExecutor() && testStrategy() && testContext() && testTimeout() && testParallel()
                    && testListener() && testShortCircuit() && testContextAuto() && testCombine() && demo();
            default -> {
                log.error("[ThreadFlowExample] 未知 type: {}", type);
                yield false;
            }
        };
    }

    /**
     * 能力 1：四种执行器 — 依次用 PLATFORM / VIRTUAL / REACTIVE / SYNC 各跑 3 个任务。
     */
    public static boolean testExecutor() {
        log.info("===== executor =====");
        boolean ok = true;
        for (ThreadExecutorType type : ThreadExecutorType.values()) {
            boolean passed = runExecutorType(type);
            ok &= passed;
            printResult("executor " + type, passed);
        }
        return ok;
    }

    /**
     * 运行指定执行器类型并校验结果。
     *
     * @param type 执行器类型
     * @return true 通过
     */
    private static boolean runExecutorType(ThreadExecutorType type) {
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("executor-" + type)
                    .executorType(type)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .addCallable(() -> "a")
                    .addCallable(() -> "b")
                    .addTask(() -> log.debug("fire-and-forget task"))
                    .timeout(5, TimeUnit.SECONDS)
                    .execute();
            return result.isSuccess() && result.getSuccessCount() == 3 && result.getTotalCount() == 3
                    && result.getResults().contains("a") && result.getResults().contains("b");
        } catch (Exception e) {
            log.error("executor {} 执行异常", type, e);
            return false;
        }
    }

    /**
     * 能力 2：合并策略 — 对五种策略逐一用成功/失败任务组合自检。
     */
    public static boolean testStrategy() {
        log.info("===== strategy =====");
        boolean ok = true;

        // ANY_SUCCESS：只要有 1 个成功即整体成功
        ok &= runStrategy(ThreadStrategy.ANY_SUCCESS, 1, 1, true);
        // ALL_SUCCESS：3 个任务全部成功才算成功
        ok &= runStrategy(ThreadStrategy.ALL_SUCCESS, 3, 0, true);
        // ALL_SUCCESS + 有失败 => 整体失败
        ok &= runStrategy(ThreadStrategy.ALL_SUCCESS, 0, 1, false);
        // ANY_FAIL：任一失败即失败
        ok &= runStrategy(ThreadStrategy.ANY_FAIL, 2, 1, false);
        // N_FAIL：阈值 2，2 个失败 >= 2 失败
        ok &= runStrategy(ThreadStrategy.N_FAIL, 1, 2, false);
        // N_SUCCESS：阈值 2，2 个成功 >= 2 成功
        ok &= runStrategy(ThreadStrategy.N_SUCCESS, 2, 1, true);

        printResult("strategy matrix", ok);
        return ok;
    }

    /**
     * 按指定策略构造 successCount 个成功任务 + failCount 个失败任务并校验整体结果。
     *
     * @param strategy     策略
     * @param successCount 成功任务数
     * @param failCount    失败任务数
     * @param expect       期望整体结果
     * @return true 通过
     */
    private static boolean runStrategy(ThreadStrategy strategy, int successCount, int failCount, boolean expect) {
        try {
            ThreadFlow flow = ThreadFlow.of("strategy-" + strategy)
                    .executorType(ThreadExecutorType.SYNC)
                    .strategy(strategy)
                    .threshold(2);
            for (int i = 0; i < successCount; i++) {
                flow.addCallable(() -> 1);
            }
            for (int i = 0; i < failCount; i++) {
                flow.addCallable(() -> {
                    throw new IllegalStateException("boom");
                });
            }
            boolean actual = flow.execute().isSuccess();
            boolean ok = actual == expect;
            printResult("strategy " + strategy + "(ok=" + successCount + ",fail=" + failCount + ") expect=" + expect, ok);
            return ok;
        } catch (Exception e) {
            log.error("strategy {} 执行异常", strategy, e);
            return false;
        }
    }

    /**
     * 能力 3：线程上下文 — 在父线程设置属性，工作线程内读取到同一上下文。
     */
    public static boolean testContext() {
        log.info("===== context =====");
        ThreadContext.set(ThreadContext.current().setAttribute("traceId", "ctx-12345"));
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("context")
                    .executorType(ThreadExecutorType.PLATFORM)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .addTask(() -> {
                        String traceId = ThreadContext.current().getAttribute("traceId");
                        ThreadContext.current().setAttribute("worker", "done").bind();
                        log.debug("worker sees traceId={}", traceId);
                    })
                    .execute();
            boolean ok = result.isSuccess();
            printResult("context propagate", ok);
            return ok;
        } catch (Exception e) {
            log.error("context 执行异常", e);
            return false;
        } finally {
            ThreadContext.clear();
        }
    }

    /**
     * 能力 4：运行超时 — 任务休眠超过超时时间，纳入失败统计。
     */
    public static boolean testTimeout() {
        log.info("===== timeout =====");
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("timeout")
                    .executorType(ThreadExecutorType.VIRTUAL)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .timeout(100, TimeUnit.MILLISECONDS)
                    .addCallable(() -> {
                        Thread.sleep(2000);
                        return "too-slow";
                    })
                    .addCallable(() -> "fast")
                    .execute();
            boolean ok = !result.isSuccess() && result.getFailCount() == 1;
            printResult("timeout task counted as fail", ok);
            return ok;
        } catch (Exception e) {
            log.error("timeout 执行异常", e);
            return false;
        }
    }

    /**
     * 能力 5：限制并发 — SYNC 顺序执行下的执行顺序与相加结果保持一致。
     */
    public static boolean testParallel() {
        log.info("===== parallel =====");
        AtomicInteger counter = new AtomicInteger(0);
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("parallel")
                    .executorType(ThreadExecutorType.SYNC)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .addTask(counter::incrementAndGet)
                    .addTask(counter::incrementAndGet)
                    .addTask(counter::incrementAndGet)
                    .addTask(counter::incrementAndGet)
                    .execute();
            boolean ok = result.isSuccess() && counter.get() == 4;
            printResult("sync sequential count = 4", ok);
            return ok;
        } catch (Exception e) {
            log.error("parallel 执行异常", e);
            return false;
        }
    }

    /**
     * 能力 6：事件回调 + 并发限制 — 注册 listener 观察全生命周期，并用 maxConcurrent 限制并发。
     */
    public static boolean testListener() {
        log.info("===== listener =====");
        AtomicInteger onStartCount = new AtomicInteger(0);
        AtomicInteger onTaskStartCount = new AtomicInteger(0);
        AtomicInteger onNextCount = new AtomicInteger(0);
        AtomicInteger onProcessLast = new AtomicInteger(-1);
        AtomicInteger onCompleteCount = new AtomicInteger(0);
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("listener")
                    .executorType(ThreadExecutorType.VIRTUAL)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .maxConcurrent(2)
                    .listener(new ThreadFlowListener() {
                        @Override
                        /** On开始 */
                        public void onStart(ThreadExecutor<?> executor) {
                            onStartCount.incrementAndGet();
                        }

                        @Override
                        /** OnTask开始 */
                        public void onTaskStart(int index) {
                            onTaskStartCount.incrementAndGet();
                        }

                        @Override
                        /** OnNext */
                        public void onNext(int index, Object value) {
                            onNextCount.incrementAndGet();
                        }

                        @Override
                        /** On处理 */
                        public void onProcess(int completed, int total) {
                            onProcessLast.set(completed);
                        }

                        @Override
                        /** OnComplete */
                        public void onComplete(ThreadFlowResult<?> r) {
                            onCompleteCount.incrementAndGet();
                        }
                    })
                    .addCallable(() -> "a")
                    .addCallable(() -> "b")
                    .addCallable(() -> "c")
                    .execute();
            boolean ok = result.isSuccess()
                    && onStartCount.get() == 1
                    && onTaskStartCount.get() == 3
                    && onNextCount.get() == 3
                    && onProcessLast.get() >= 3
                    && onCompleteCount.get() == 1;
            printResult("listener lifecycle", ok);
            return ok;
        } catch (Exception e) {
            log.error("listener 执行异常", e);
            return false;
        }
    }

    /**
     * 能力 7：策略短路 — ANY_FAIL 首个任务失败即终止，不再等待后续任务。
     */
    public static boolean testShortCircuit() {
        log.info("===== short-circuit =====");
        long start = System.currentTimeMillis();
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("short-circuit")
                    .executorType(ThreadExecutorType.VIRTUAL)
                    .strategy(ThreadStrategy.ANY_FAIL)
                    .addCallable(() -> {
                        throw new IllegalStateException("first-fail");
                    })
                    .addCallable(() -> {
                        Thread.sleep(3000);
                        return "slow";
                    })
                    .execute();
            long cost = System.currentTimeMillis() - start;
            boolean ok = !result.isSuccess()
                    && result.getFailCount() == 1
                    && cost < 2000;
            printResult("any-fail short-circuit (cost=" + cost + "ms)", ok);
            return ok;
        } catch (Exception e) {
            log.error("short-circuit 执行异常", e);
            return false;
        }
    }

    /**
     * 能力 8：上下文自动透传 — 父线程设置上下文后，无需手动 bind 即可在任务内读取。
     */
    public static boolean testContextAuto() {
        log.info("===== context-auto =====");
        ThreadContext ctx = ThreadContext.current().setAttribute("reqId", "req-888");
        ThreadContext.set(ctx);
        AtomicInteger seen = new AtomicInteger(0);
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("context-auto")
                    .executorType(ThreadExecutorType.PLATFORM)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .addTask(() -> {
                        String reqId = ThreadContext.current().getAttribute("reqId");
                        if ("req-888".equals(reqId)) {
                            seen.incrementAndGet();
                        }
                    })
                    .addTask(() -> {
                        String reqId = ThreadContext.current().getAttribute("reqId");
                        if ("req-888".equals(reqId)) {
                            seen.incrementAndGet();
                        }
                    })
                    .execute();
            boolean ok = result.isSuccess() && seen.get() == 2;
            printResult("context auto-propagate", ok);
            return ok;
        } catch (Exception e) {
            log.error("context-auto 执行异常", e);
            return false;
        } finally {
            ThreadContext.clear();
        }
    }

    /**
     * 能力 9：结果自动合并 + 并发限制 — 并行 Callable 结果自动收集到一个 List，并用 maxConcurrent 限流。
     */
    public static boolean testCombine() {
        log.info("===== combine =====");
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("combine")
                    .executorType(ThreadExecutorType.VIRTUAL)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .maxConcurrent(2)
                    .addCallable(() -> "user-1")
                    .addCallable(() -> "user-2")
                    .addCallable(() -> "user-3")
                    .addCallable(() -> "user-4")
                    .addCallable(() -> "user-5")
                    .execute();
            boolean ok = result.isSuccess()
                    && result.getSuccessCount() == 5
                    && result.getResults().size() == 5
                    && result.getResults().containsAll(java.util.List.of("user-1", "user-2", "user-3", "user-4", "user-5"));
            printResult("combine results into one list", ok);
            return ok;
        } catch (Exception e) {
            log.error("combine 执行异常", e);
            return false;
        }
    }

    /**
     * 能力 10：综合场景 — 模拟优惠券批量核销：并发 + 上下文透传 + 事件回调 + 结果合并。
     */
    public static boolean demo() {
        log.info("===== demo: 优惠券批量核销 =====");
        ThreadContext ctx = ThreadContext.current().setAttribute("op", "batch-redeem");
        ThreadContext.set(ctx);
        AtomicInteger redeemed = new AtomicInteger();
        try {
            ThreadFlowResult<Object> result = ThreadFlow.of("coupon-redeem")
                    .executorType(ThreadExecutorType.VIRTUAL)
                    .strategy(ThreadStrategy.ALL_SUCCESS)
                    .timeout(5, TimeUnit.SECONDS)
                    .listener(new ThreadFlowListener() {
                        @Override
                        /** On处理 */
                        public void onProcess(int completed, int total) {
                            log.info("进度: {}/{}", completed, total);
                        }

                        @Override
                        /** OnComplete */
                        public void onComplete(ThreadFlowResult<?> r) {
                            log.info("核销完成: success={}, cost={}ms", r.isSuccess(), r.getCostMillis());
                        }
                    })
                    .addTask(() -> {
                        String op = ThreadContext.current().getAttribute("op");
                        if (redeemed.incrementAndGet() <= 3) {
                            log.info("线程[{}] 核销第 1 张券, op={}", Thread.currentThread().getName(), op);
                        }
                    })
                    .addTask(() -> {
                        log.info("线程[{}] 核销第 2 张券", Thread.currentThread().getName());
                    })
                    .addTask(() -> {
                        log.info("线程[{}] 核销第 3 张券", Thread.currentThread().getName());
                    })
                    .addTask(() -> {
                        log.info("线程[{}] 核销第 4 张券", Thread.currentThread().getName());
                    })
                    .addTask(() -> {
                        log.info("线程[{}] 核销第 5 张券", Thread.currentThread().getName());
                    })
                    .execute();
            boolean ok = result.isSuccess();
            printResult("coupon batch-redeem demo", ok);
            return ok;
        } catch (Exception e) {
            log.error("demo 执行异常", e);
            return false;
        } finally {
            ThreadContext.clear();
        }
    }

    /** PrintResult */
    private static void printResult(String name, boolean passed) {
        log.info("{}{}", (passed ? "[PASS]" : "[FAIL]"), name);
    }
}
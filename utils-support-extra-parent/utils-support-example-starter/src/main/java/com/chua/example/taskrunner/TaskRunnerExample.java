package com.chua.example.taskrunner;

import com.chua.common.support.task.taskrunner.CompletionPolicy;
import com.chua.common.support.task.taskrunner.RunResult;
import com.chua.common.support.task.taskrunner.RunnerEvent;
import com.chua.common.support.task.taskrunner.TaskDefinition;
import com.chua.common.support.task.taskrunner.TaskResult;
import com.chua.common.support.task.taskrunner.TaskRunner;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link TaskRunner} 轻量级 DAG 任务编排全场景自检示例。
 *
 * <p>覆盖串行依赖（afterNode/dependsNode）、最外层并行、五种完成策略、
 * 单任务超时隔离、全局重试、熔断降级、环检测、前置校验、三种执行出口与事件流。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java TaskRunnerExample                     # 运行全部场景
 *   java TaskRunnerExample --type=serial       # 仅串行/依赖场景
 *   java TaskRunnerExample --type=policy       # 仅完成策略场景
 *   java TaskRunnerExample --type=reliability  # 超时/重试/熔断场景
 *   java TaskRunnerExample --type=reactive     # 异步/响应式/事件流场景
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class TaskRunnerExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认场景类型：全部
     */
    private static final String TYPE_ALL = "all";

    /**
     * 防止实例化工具类。
     */
    private TaskRunnerExample() {
    }

    // ==================== main ====================

    /**
     * 独立入口：按 --type 运行对应场景组，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 --type=all|serial|parallel|policy|reliability|reactive
     */
    public static void main(String[] args) {
        var type = parseArg(args, "--type", TYPE_ALL);
        var passed = true;

        if (TYPE_ALL.equals(type) || "serial".equals(type)) {
            passed &= serialChainAndDataDependency();
            passed &= duplicateIdRejected();
            passed &= cycleDetectionRejected();
        }
        if (TYPE_ALL.equals(type) || "parallel".equals(type)) {
            passed &= parallelTopLevelOverlap();
            passed &= allSuccessFailFastSkipsDownstream();
        }
        if (TYPE_ALL.equals(type) || "policy".equals(type)) {
            passed &= anySuccessEarlyExitCancelsRest();
            passed &= successRateThresholds();
            passed &= thresholdPolicies();
        }
        if (TYPE_ALL.equals(type) || "reliability".equals(type)) {
            passed &= perTaskTimeoutIsolated();
            passed &= globalRetryRecoversFlakyTask();
            passed &= circuitBreakerFallbackDegradation();
        }
        if (TYPE_ALL.equals(type) || "reactive".equals(type)) {
            passed &= asyncExecutionCompletes();
            passed &= reactiveExecutionEmitsResult();
            passed &= watchStreamCarriesLifecycleEvents();
        }
        if (TYPE_ALL.equals(type)) {
            passed &= preconditionsEnforced();
            passed &= parameterValidation();
        }

        if (!passed) {
            System.out.println("[FAIL] TaskRunner 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.println("[PASS] TaskRunner 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }

    /**
     * 解析 --key=value 或 --key value 形式的命令行参数。
     *
     * @param args         参数数组
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    private static String parseArg(String[] args, String key, String defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        for (var i = 0; i < args.length; i++) {
            if (args[i].startsWith(key + "=")) {
                return args[i].substring(key.length() + 1);
            }
            if (key.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    // ==================== 串行/结构场景 ====================

    /**
     * 场景：afterNode 串行链按拓扑序执行，dependsNode 校验前置结果非空。
     *
     * @return true 表示通过
     */
    private static boolean serialChainAndDataDependency() {
        try {
            var runner = TaskRunner.of("ex-serial-chain")
                    .policy(CompletionPolicy.allSuccess())
                    .task("a", ctx -> 1)
                    .task("b", ctx -> ctx.get("a", Integer.class) + 10).afterNode("a")
                    .task("c", ctx -> ctx.get("b", Integer.class) + 100).dependsNode("b");
            var result = runner.executeSync(null);
            var ok = result.success()
                    && result.findNode("c").map(r -> Integer.valueOf(111).equals(r.data())).orElse(false);
            print("serialChainAndDataDependency", ok);
            return ok;
        } catch (Exception e) {
            return fail("serialChainAndDataDependency", e);
        }
    }

    /**
     * 场景：重复节点 ID 在构建期被拒绝。
     *
     * @return true 表示通过
     */
    private static boolean duplicateIdRejected() {
        try {
            TaskRunner.of("ex-dup")
                    .policy(CompletionPolicy.allSuccess())
                    .task("x", ctx -> "v")
                    .task("x", ctx -> "v2");
            return fail("duplicateIdRejected", "未拒绝重复 ID");
        } catch (IllegalStateException expected) {
            var ok = expected.getMessage().contains("已注册");
            print("duplicateIdRejected", ok);
            return ok;
        }
    }

    /**
     * 场景：循环依赖在执行前被拒绝且消息指明环路。
     *
     * @return true 表示通过
     */
    private static boolean cycleDetectionRejected() {
        try {
            TaskRunner.of("ex-cycle")
                    .policy(CompletionPolicy.allSuccess())
                    .task("p", ctx -> 1).afterNode("r")
                    .task("q", ctx -> 2).afterNode("p")
                    .task("r", ctx -> 3).afterNode("q")
                    .executeSync(null);
            return fail("cycleDetectionRejected", "未拒绝循环依赖");
        } catch (IllegalStateException expected) {
            var ok = expected.getMessage().contains("循环依赖");
            print("cycleDetectionRejected", ok);
            return ok;
        }
    }

    // ==================== 并行/失败策略场景 ====================

    /**
     * 场景：最外层无依赖任务并行执行，双闩锁验证时间重叠。
     *
     * @return true 表示通过
     */
    private static boolean parallelTopLevelOverlap() {
        var releaseA = new CountDownLatch(1);
        var releaseB = new CountDownLatch(1);
        var bothStarted = new CountDownLatch(2);
        try {
            var runner = TaskRunner.of("ex-parallel-top")
                    .policy(CompletionPolicy.allSuccess())
                    .task("slow-a", ctx -> {
                        bothStarted.countDown();
                        awaitQuietly(releaseA);
                        return "A";
                    })
                    .task("slow-b", ctx -> {
                        bothStarted.countDown();
                        awaitQuietly(releaseB);
                        return "B";
                    });
            var future = runner.execute(null);
            var overlapped = bothStarted.await(3, TimeUnit.SECONDS);
            releaseA.countDown();
            releaseB.countDown();
            var result = future.get(5, TimeUnit.SECONDS);
            var ok = overlapped && result.success()
                    && result.countByStatus(TaskResult.Status.SUCCESS) == 2;
            print("parallelTopLevelOverlap", ok);
            return ok;
        } catch (Exception e) {
            releaseA.countDown();
            releaseB.countDown();
            return fail("parallelTopLevelOverlap", e);
        }
    }

    /**
     * 场景：allSuccess 策略下任一失败即整体失败，下游级联 SKIPPED。
     *
     * @return true 表示通过
     */
    private static boolean allSuccessFailFastSkipsDownstream() {
        try {
            var runner = TaskRunner.of("ex-fail-fast")
                    .policy(CompletionPolicy.allSuccess())
                    .task("boom", ctx -> {
                        throw new IllegalArgumentException("bad input");
                    })
                    .task("downstream", ctx -> "never").afterNode("boom");
            var result = runner.executeSync(null);
            var ok = !result.success()
                    && result.findNode("boom").map(r -> r.status() == TaskResult.Status.FAILED).orElse(false)
                    && result.findNode("downstream").map(r -> r.status() == TaskResult.Status.SKIPPED).orElse(false)
                    && result.error() != null;
            print("allSuccessFailFastSkipsDownstream", ok);
            return ok;
        } catch (Exception e) {
            return fail("allSuccessFailFastSkipsDownstream", e);
        }
    }

    /**
     * 场景：anySuccess 策略下首个成功即提前取消阻塞任务并整体通过。
     *
     * @return true 表示通过
     */
    private static boolean anySuccessEarlyExitCancelsRest() {
        var release = new CountDownLatch(1);
        try {
            var runner = TaskRunner.of("ex-any-success")
                    .policy(CompletionPolicy.anySuccess())
                    .task("quick-ok", ctx -> "fast")
                    .task("blocked-1", ctx -> {
                        awaitQuietly(release);
                        return "late-1";
                    })
                    .task("blocked-2", ctx -> {
                        awaitQuietly(release);
                        return "late-2";
                    });
            var start = System.currentTimeMillis();
            var result = runner.executeSync(null);
            var elapsed = System.currentTimeMillis() - start;
            release.countDown();
            var ok = result.success()
                    && result.findNode("quick-ok").map(r -> r.status() == TaskResult.Status.SUCCESS).orElse(false)
                    && elapsed < 2500;
            print("anySuccessEarlyExitCancelsRest (" + elapsed + "ms)", ok);
            return ok;
        } catch (Exception e) {
            release.countDown();
            return fail("anySuccessEarlyExitCancelsRest", e);
        }
    }

    /**
     * 场景：成功率策略 80% 达标与 60% 不达标两个分支。
     *
     * @return true 表示通过
     */
    private static boolean successRateThresholds() {
        try {
            var passedRun = buildRateRunner("ex-rate-pass", 1).executeSync(null);
            var rejectedRun = buildRateRunner("ex-rate-reject", 2).executeSync(null);
            var ok = passedRun.success() && !rejectedRun.success();
            print("successRateThresholds", ok);
            return ok;
        } catch (Exception e) {
            return fail("successRateThresholds", e);
        }
    }

    /**
     * 构建成功率验证 runner：5 个并行任务中固定数量计划性失败。
     *
     * @param name      运行名称
     * @param failCount 计划失败的任务数（1 个失败=4/5=80% 达标，2 个=3/5=60% 不达标）
     * @return 配置完成的 runner
     */
    private static TaskRunner buildRateRunner(String name, int failCount) {
        var runner = TaskRunner.of(name).policy(CompletionPolicy.successRate(0.8));
        for (var i = 0; i < 5; i++) {
            final var idx = i;
            final var shouldFail = idx < failCount;
            runner.task("t-" + idx, ctx -> {
                if (shouldFail) {
                    throw new IllegalStateException("planned-fail-" + idx);
                }
                return "ok-" + idx;
            });
        }
        return runner;
    }

    /**
     * 场景：successAtLeast 与 failAtLeast 阈值边界判定。
     *
     * @return true 表示通过
     */
    private static boolean thresholdPolicies() {
        try {
            var atLeast = TaskRunner.of("ex-at-least")
                    .policy(CompletionPolicy.successAtLeast(2))
                    .task("s1", ctx -> "ok")
                    .task("f1", ctx -> {
                        throw new IllegalStateException("x");
                    })
                    .task("s2", ctx -> "ok")
                    .executeSync(null);

            var failLimit = TaskRunner.of("ex-fail-limit")
                    .policy(CompletionPolicy.failAtLeast(2))
                    .task("f1", ctx -> {
                        throw new IllegalStateException("x");
                    })
                    .task("f2", ctx -> {
                        throw new IllegalStateException("y");
                    })
                    .task("s1", ctx -> "ok")
                    .executeSync(null);

            var ok = atLeast.success() && !failLimit.success();
            print("thresholdPolicies", ok);
            return ok;
        } catch (Exception e) {
            return fail("thresholdPolicies", e);
        }
    }

    // ==================== 可靠性场景 ====================

    /**
     * 场景：单任务超时判失败且不影响同层健康任务。
     *
     * @return true 表示通过
     */
    private static boolean perTaskTimeoutIsolated() {
        try {
            var runner = TaskRunner.of("ex-timeout")
                    .policy(CompletionPolicy.anySuccess())
                    .task("slow-timeout-demo", ctx -> {
                        sleepMillis(600);
                        return "never";
                    }).timeout(Duration.ofMillis(120))
                    .task("healthy", ctx -> "fine");
            var result = runner.executeSync(null);
            var ok = result.success()
                    && result.findNode("slow-timeout-demo").map(r -> r.status() == TaskResult.Status.FAILED).orElse(false)
                    && result.findNode("healthy").map(r -> r.status() == TaskResult.Status.SUCCESS).orElse(false);
            print("perTaskTimeoutIsolated", ok);
            return ok;
        } catch (Exception e) {
            return fail("perTaskTimeoutIsolated", e);
        }
    }

    /**
     * 场景：全局重试使瞬时故障在第 3 次尝试恢复。
     *
     * @return true 表示通过
     */
    private static boolean globalRetryRecoversFlakyTask() {
        var attempts = new AtomicInteger();
        try {
            var runner = TaskRunner.of("ex-retry")
                    .retry(5)
                    .policy(CompletionPolicy.allSuccess())
                    .task("flaky", ctx -> {
                        if (attempts.incrementAndGet() < 3) {
                            throw new IllegalStateException("transient");
                        }
                        return "recovered";
                    });
            var result = runner.executeSync(null);
            var ok = result.success() && attempts.get() == 3
                    && "recovered".equals(result.findNode("flaky").map(TaskResult::data).orElse(null));
            print("globalRetryRecoversFlakyTask", ok);
            return ok;
        } catch (Exception e) {
            return fail("globalRetryRecoversFlakyTask", e);
        }
    }

    /**
     * 场景：熔断降级 — 失败走 fallback；再次执行熔断打开仍返回降级值。
     *
     * @return true 表示通过
     */
    private static boolean circuitBreakerFallbackDegradation() {
        try {
            var runner = TaskRunner.of("ex-cb-" + System.nanoTime())
                    .policy(CompletionPolicy.allSuccess())
                    .task("unstable-cb-demo", ctx -> {
                        throw new IllegalStateException("remote down");
                    })
                    .failureThreshold(2)
                    .waitDuration(60_000)
                    .circuitBreaker()
                    .fallback(ctx -> "cached-value");

            var first = runner.executeSync(null);
            var second = runner.executeSync(null);
            var firstOk = first.success()
                    && "cached-value".equals(first.findNode("unstable-cb-demo").map(TaskResult::data).orElse(null));
            var secondOk = second.success()
                    && "cached-value".equals(second.findNode("unstable-cb-demo").map(TaskResult::data).orElse(null));
            print("circuitBreakerFallbackDegradation", firstOk && secondOk);
            return firstOk && secondOk;
        } catch (Exception e) {
            return fail("circuitBreakerFallbackDegradation", e);
        }
    }

    // ==================== 出口/事件流场景 ====================

    /**
     * 场景：异步出口 CompletableFuture 正常回填并透传初始输入。
     *
     * @return true 表示通过
     */
    private static boolean asyncExecutionCompletes() {
        try {
            var runner = TaskRunner.of("ex-async-out")
                    .policy(CompletionPolicy.allSuccess())
                    .task("work", ctx -> ctx.getInput() + "-done");
            CompletableFuture<RunResult> future = runner.execute("payload");
            var result = future.get(5, TimeUnit.SECONDS);
            var ok = result.success()
                    && "payload-done".equals(result.findNode("work").map(TaskResult::data).orElse(null));
            print("asyncExecutionCompletes", ok);
            return ok;
        } catch (Exception e) {
            return fail("asyncExecutionCompletes", e);
        }
    }

    /**
     * 场景：响应式出口 Mono 惰性订阅并发出结果。
     *
     * @return true 表示通过
     */
    private static boolean reactiveExecutionEmitsResult() {
        try {
            var runner = TaskRunner.of("ex-reactive-out")
                    .policy(CompletionPolicy.allSuccess())
                    .task("rx-task", ctx -> "rx-value");
            Mono<RunResult> mono = runner.executeReactor(null);
            var result = mono.block(Duration.ofSeconds(5));
            var ok = result != null && result.success()
                    && "rx-value".equals(result.findNode("rx-task").map(TaskResult::data).orElse(null));
            print("reactiveExecutionEmitsResult", ok);
            return ok;
        } catch (Exception e) {
            return fail("reactiveExecutionEmitsResult", e);
        }
    }

    /**
     * 场景：watch() 缓冲回放首尾生命周期事件完整。
     *
     * @return true 表示通过
     */
    private static boolean watchStreamCarriesLifecycleEvents() {
        try {
            var runner = TaskRunner.of("ex-watch-events")
                    .policy(CompletionPolicy.allSuccess())
                    .task("evt-node", ctx -> "ok");
            runner.executeSync(null);
            // 事件先入缓冲，单次订阅经 takeUntil 在 RUN_COMPLETED 处收尾
            var events = runner.watch()
                    .takeUntil(e -> e.type() == RunnerEvent.Type.RUN_COMPLETED)
                    .collectList()
                    .block(Duration.ofSeconds(5));
            var ok = events != null && !events.isEmpty()
                    && events.getFirst().type() == RunnerEvent.Type.RUN_STARTED
                    && events.stream().anyMatch(e -> e.type() == RunnerEvent.Type.NODE_COMPLETED)
                    && events.getLast().type() == RunnerEvent.Type.RUN_COMPLETED;
            print("watchStreamCarriesLifecycleEvents", ok);
            return ok;
        } catch (Exception e) {
            return fail("watchStreamCarriesLifecycleEvents", e);
        }
    }

    // ==================== 校验场景 ====================

    /**
     * 场景：未设置策略 / 未注册任务时拒绝执行。
     *
     * @return true 表示通过
     */
    private static boolean preconditionsEnforced() {
        try {
            var policyError = catchMessage(() -> TaskRunner.of("ex-no-policy")
                    .task("a", ctx -> 1).executeSync(null));
            var taskError = catchMessage(() -> TaskRunner.of("ex-no-task")
                    .policy(CompletionPolicy.allSuccess()).executeSync(null));
            var ok = policyError != null && policyError.contains("policy")
                    && taskError != null && taskError.contains("task");
            print("preconditionsEnforced", ok);
            return ok;
        } catch (Exception e) {
            return fail("preconditionsEnforced", e);
        }
    }

    /**
     * 场景：非法参数校验 — 空名称、负重试、零时长、非法阈值、空依赖数组。
     *
     * @return true 表示通过
     */
    private static boolean parameterValidation() {
        try {
            var count = 0;
            count += expectIllegalArgument(() -> TaskRunner.of(" "));
            count += expectIllegalArgument(() -> TaskRunner.of("ex-validation").retry(-1));
            count += expectIllegalArgument(() -> CompletionPolicy.successAtLeast(0));
            count += expectIllegalArgument(() -> CompletionPolicy.failAtLeast(0));
            count += expectIllegalArgument(() -> CompletionPolicy.successRate(1.5));
            count += expectIllegalArgument(() -> CompletionPolicy.successRate(0));
            count += expectIllegalArgument(() -> TaskRunner.of("ex-validation")
                    .task("dep-test", ctx -> 1).afterNode());
            var ok = count == 7;
            print("parameterValidation", ok);
            return ok;
        } catch (Exception e) {
            return fail("parameterValidation", e);
        }
    }

    /**
     * 执行代码片段并返回其抛出的异常消息。
     *
     * @param runnable 待执行片段
     * @return 异常消息；未抛异常时为 null
     */
    private static String catchMessage(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    /**
     * 断言片段抛出 IllegalArgumentException。
     *
     * @param runnable 待执行片段
     * @return 命中预期返回 1，否则 0
     */
    private static int expectIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            return 0;
        } catch (IllegalArgumentException expected) {
            return 1;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 输出单场景结果标记。
     *
     * @param name 场景名
     * @param ok   是否通过
     */
    private static void print(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    /**
     * 输出异常失败信息。
     *
     * @param name 场景名
     * @param e    异常
     * @return 恒为 false
     */
    private static boolean fail(String name, Exception e) {
        System.out.println("[FAIL] " + name + " 异常: " + e);
        return false;
    }

    /**
     * 输出失败信息。
     *
     * @param name 场景名
     * @param msg  失败原因
     * @return 恒为 false
     */
    private static boolean fail(String name, String msg) {
        System.out.println("[FAIL] " + name + ": " + msg);
        return false;
    }

    /**
     * 忽略中断的闩锁等待。
     *
     * @param latch 目标闩锁
     */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 可中断睡眠（仅用于超时模拟）。
     *
     * @param millis 毫秒数
     */
    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

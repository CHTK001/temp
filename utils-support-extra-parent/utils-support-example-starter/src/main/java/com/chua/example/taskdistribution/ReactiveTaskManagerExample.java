package com.chua.example.taskdistribution;

import com.chua.common.support.taskdistribution.manager.ReactiveTaskManager;
import com.chua.common.support.taskdistribution.manager.TaskManager;
import com.chua.common.support.taskdistribution.task.Task;
import com.chua.common.support.taskdistribution.task.TaskPriority;
import com.chua.common.support.taskdistribution.task.TaskResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 响应式任务门面综合示例 — 基于 {@link ReactiveTaskManager}。
 *
 * <p>演示任务分发框架的响应式用法：链式构建、Mono 等待结果、
 * Flux 订阅完成流、取消联动、批量编排。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ReactiveTaskManagerExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class ReactiveTaskManagerExample {

    /**
     * 模拟工作端延迟（毫秒）
     */
    private static final long WORKER_DELAY_MS = 50;

    /**
     * 单项测试超时（秒）
     */
    private static final int TEST_TIMEOUT_SECONDS = 5;

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    // ==================== main ====================

    public static void main(String[] args) {
        ReactiveTaskManagerExample example = new ReactiveTaskManagerExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 运行全部自检场景。
     *
     * @return 全部通过返回 true
     */
    public boolean runTest() {
        log.info("===== 响应式任务门面示例开始 =====");
        try {
            boolean allPassed = true;
            allPassed &= testFluentSubmit();
            allPassed &= testWatchStream();
            allPassed &= testCancelLinkage();
            allPassed &= testBatchOrchestration();
            log.info("===== 响应式任务门面示例结束 =====");
            return allPassed;
        } catch (Exception e) {
            log.error("示例异常: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== 场景 1：链式提交 ====================

    /**
     * 场景 1：链式构建任务并等待结果。
     *
     * <p>展示 {@code reactive.task(...).tag(...).timeout(...).submit()} 链式 API，
     * 以及工作端异步回调后 Mono 完成的时序。</p>
     *
     * @return 通过返回 true
     */
    public boolean testFluentSubmit() {
        String name = "链式提交";
        log.info("--- 场景: {} ---", name);
        TaskManager manager = new TaskManager(false);
        ReactiveTaskManager reactive = new ReactiveTaskManager(manager);

        try {
            // 模拟工作端：延迟后回传结果
            simulateWorker(manager, "email-send");

            // 链式构建 + 提交 + 等待
            TaskResult<String> result = reactive.task("email-send", "hello@chua.com")
                    .tag("scene", "fluent")
                    .priority(TaskPriority.HIGH)
                    .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                    .maxRetries(2)
                    .submit()
                    .block(Duration.ofSeconds(TEST_TIMEOUT_SECONDS));

            boolean passed = result != null && result.isSuccess();
            log.info("[{}] 结果={}", name, passed ? "PASS" : "FAIL");
            return passed;
        } catch (Exception e) {
            log.error("[{}] 异常: {}", name, e.getMessage());
            return false;
        } finally {
            reactive.close();
        }
    }

    // ==================== 场景 2：完成事件流 ====================

    /**
     * 场景 2：订阅任务完成事件流（watch）。
     *
     * <p>展示 Flux 流监听：工作端完成后流自动发射结果并完成。</p>
     *
     * @return 通过返回 true
     */
    public boolean testWatchStream() {
        String name = "完成事件流";
        log.info("--- 场景: {} ---", name);
        TaskManager manager = new TaskManager(false);
        ReactiveTaskManager reactive = new ReactiveTaskManager(manager);

        try {
            // 先注册任务占位，拿到 taskId 后再启动模拟工作端与监听
            Task<String> task = reactive.task("report-gen", "daily-report")
                    .build();

            CompletableFuture<TaskResult<String>> received = new CompletableFuture<>();
            Thread worker = simulateWorkerAsync(manager, task.getTaskId(), "report-ok");

            Flux<TaskResult<String>> stream = reactive.watch(task.getTaskId());
            // 先注册监听再触发结果，保证时序
            manager.addTask(task, null);
            worker.start();
            stream.subscribe(received::complete);

            TaskResult<String> result = received.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean passed = result.isSuccess() && "report-ok".equals(result.getData());
            log.info("[{}] 结果={}", name, passed ? "PASS" : "FAIL");
            return passed;
        } catch (Exception e) {
            log.error("[{}] 异常: {}", name, e.getMessage());
            return false;
        } finally {
            reactive.close();
        }
    }

    // ==================== 场景 3：取消联动 ====================

    /**
     * 场景 3：取消任务并验证取消联动。
     *
     * <p>展示：submit 返回的 Mono 在任务被取消后以异常完成，
     * 调用方可通过 onErrorResume 统一处理。</p>
     *
     * @return 通过返回 true
     */
    public boolean testCancelLinkage() {
        String name = "取消联动";
        log.info("--- 场景: {} ---", name);
        TaskManager manager = new TaskManager(false);
        ReactiveTaskManager reactive = new ReactiveTaskManager(manager);

        try {
            // 链式构建但不提交，先拿到 taskId
            com.chua.common.support.taskdistribution.task.Task<String> task =
                    reactive.task("long-job", "big-data")
                            .timeout(Duration.ofSeconds(60))
                            .build();

            Mono<TaskResult<String>> submitting = reactive.submit(task);

            Boolean cancelled = reactive.cancel(task.getTaskId())
                    .block(Duration.ofSeconds(TEST_TIMEOUT_SECONDS));

            // 取消后 submit Mono 应以异常完成
            String errorHint = submitting
                    .map(r -> "unexpected-value")
                    .onErrorResume(e -> Mono.just("cancelled-as-expected"))
                    .block(Duration.ofSeconds(TEST_TIMEOUT_SECONDS));

            boolean passed = Boolean.TRUE.equals(cancelled)
                    && "cancelled-as-expected".equals(errorHint);
            log.info("[{}] 取消={}, 联动={}, 结果={}", name, cancelled, errorHint,
                    passed ? "PASS" : "FAIL");
            return passed;
        } catch (Exception e) {
            log.error("[{}] 异常: {}", name, e.getMessage());
            return false;
        } finally {
            reactive.close();
        }
    }

    // ==================== 场景 4：批量编排 ====================

    /**
     * 场景 4：批量提交多个任务并用 Reactor 编织汇总。
     *
     * <p>展示响应式的真正威力：3 个任务并发执行，
     * 用 {@code Flux.merge} 聚合结果并统计成功数。</p>
     *
     * @return 通过返回 true
     */
    public boolean testBatchOrchestration() {
        String name = "批量编排";
        log.info("--- 场景: {} ---", name);
        TaskManager manager = new TaskManager(false);
        ReactiveTaskManager reactive = new ReactiveTaskManager(manager);

        try {
            // 三个任务类型共用一个模拟工作端
            for (String type : List.of("job-a", "job-b", "job-c")) {
                simulateWorker(manager, type);
            }

            // 链式构建三个任务，合并为统一结果流
            List<Mono<TaskResult<String>>> monos = List.of(
                    reactive.task("job-a", "payload-a").submit(),
                    reactive.task("job-b", "payload-b").submit(),
                    reactive.task("job-c", "payload-c").submit());

            Long successCount = Flux.merge(monos)
                    .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                    .filter(TaskResult::isSuccess)
                    .count()
                    .block(Duration.ofSeconds(TEST_TIMEOUT_SECONDS));

            boolean passed = successCount != null && successCount == 3L;
            log.info("[{}] 成功数={}/3, 结果={}", name, successCount, passed ? "PASS" : "FAIL");
            return passed;
        } catch (Exception e) {
            log.error("[{}] 异常: {}", name, e.getMessage());
            return false;
        } finally {
            reactive.close();
        }
    }

    // ==================== 工作端模拟辅助 ====================

    /**
     * 启动后台线程模拟指定类型的工作端：
     * 监听 PENDING 任务，命中即回传成功结果。
     *
     * <p>采用轮询实现（简单可靠），生产环境由真实 Dispatcher 驱动。</p>
     *
     * @param manager  任务管理器
     * @param taskType 匹配的任务类型
     */
    private void simulateWorker(TaskManager manager, String taskType) {
        Thread worker = new Thread(() -> pollAndExecute(manager, taskType));
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 启动后台线程模拟工作端，返回线程引用供调用方控制时序。
     *
     * @param manager 任务管理器
     * @param taskId  目标任务 ID（null 表示按 taskType 匹配）
     * @param data    回传的结果数据
     * @return 已启动的工作线程
     */
    private Thread simulateWorkerAsync(TaskManager manager, String taskId, String data) {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(WORKER_DELAY_MS);
                manager.handleResult(TaskResult.success(taskId, data, "worker-demo"));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        worker.setDaemon(true);
        return worker;
    }

    /**
     * 轮询待派发任务，命中指定类型后延迟回传成功结果。
     *
     * @param manager  任务管理器
     * @param taskType 目标任务类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pollAndExecute(TaskManager manager, String taskType) {
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline) {
            for (com.chua.common.support.taskdistribution.task.Task<?> pending : manager.getPendingTasks()) {
                if (taskType.equals(pending.getTaskType())) {
                    try {
                        Thread.sleep(WORKER_DELAY_MS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    TaskResult result = TaskResult.success(
                            pending.getTaskId(), "done-by-worker", "worker-demo");
                    manager.handleResult(result);
                    return;
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

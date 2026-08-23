package com.chua.example.taskdistribution;

import com.chua.common.support.taskdistribution.dispatcher.DispatcherListener;
import com.chua.common.support.taskdistribution.dispatcher.InMemoryDispatcherProvider;
import com.chua.common.support.taskdistribution.manager.ResultBuffer;
import com.chua.common.support.taskdistribution.manager.TaskManager;
import com.chua.common.support.taskdistribution.node.NodeMeta;
import com.chua.common.support.taskdistribution.node.NodeTable;
import com.chua.common.support.taskdistribution.spi.TaskExecutor;
import com.chua.common.support.taskdistribution.spi.TaskExecutorRegistry;
import com.chua.common.support.taskdistribution.strategy.DispatchStrategy;
import com.chua.common.support.taskdistribution.task.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 任务分发综合示例。
 *
 * <p>演示单机 InMemory 模式下完整的任务分发流程：
 * 发布任务 → 中间件派发 → 工作端执行 → 结果回传。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java TaskDistributionExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TaskDistributionExample {

    /**
     * 测试超时时间（秒）
     */
    private static final int TEST_TIMEOUT_SECONDS = 10;

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
        TaskDistributionExample example = new TaskDistributionExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /** 运行Test */
    public boolean runTest() {
        log.info("===== 任务分发示例开始 =====");
        try {
            boolean allPassed = true;
            allPassed &= testBasicDispatch();
            allPassed &= testStrategySelect();
            allPassed &= testTaskTimeout();
            log.info("===== 任务分发示例结束 =====");
            return allPassed;
        } catch (Exception e) {
            log.error("示例异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试基本任务派发流程。
     */
    public boolean testBasicDispatch() {
        log.info("--- 测试: 基本任务派发 ---");
        try {
            NodeTable nodeTable = new NodeTable();
            TaskManager taskManager = new TaskManager(false);
            ResultBuffer resultBuffer = new ResultBuffer();
            TaskExecutorRegistry registry = new TaskExecutorRegistry();
            CountDownLatch latch = new CountDownLatch(1);

            // 注册工作端
            NodeMeta worker = NodeMeta.builder()
                    .nodeId("worker-1")
                    .tags(Map.of("cap", "echo"))
                    .weight(1)
                    .build();
            nodeTable.register(worker);

            // 注册执行器
            registry.register(new TaskExecutor<String>() {
                @Override
                /** TaskType */
                public String taskType() {
                    return "echo";
                }

                @Override
                /** 执行 */
                public TaskResult<String> execute(Task<String> task) {
                    return TaskResult.success(task.getTaskId(), "Hello: " + task.getPayload(), "worker-1");
                }
            });

            // 创建派发器
            InMemoryDispatcherProvider dispatcher = new InMemoryDispatcherProvider();
            dispatcher.listener(new DispatcherListener() {
                @Override
                /** OnTask */
                @SuppressWarnings({"unchecked", "rawtypes"})
                public void onTask(Task<?> task) {
                    /* 通配符捕获限制：执行器类型参数与任务无法静态对齐，使用 raw 类型桥接 */
                    TaskExecutor executor = registry.findExecutor(task);
                    if (executor != null) {
                        TaskResult<?> result = executor.execute((Task) task);
                        taskManager.handleResult(result);
                        resultBuffer.cacheResult(task.getTaskId(), result);
                        dispatcher.receive(result);
                    }
                }

                @Override
                /** OnResult */
                public void onResult(TaskResult<?> result) {
                    latch.countDown();
                }
            });
            dispatcher.start();

            // 发布任务
            Task<String> task = Task.<String>builder()
                    .taskId(TaskIdGenerator.generateId("publisher"))
                    .traceId(TaskIdGenerator.generateTraceId("publisher"))
                    .taskType("echo")
                    .payload("World")
                    .tags(Map.of("cap", "echo"))
                    .timeoutMs(5000)
                    .build();

            taskManager.addTask(task, new TaskCallback() {
                @Override
                /** OnResult */
                public void onResult(TaskResult<?> result) {
                    log.info("收到结果: {} -> {}", result.getTaskId(), result.getData());
                }
            });

            nodeTable.selectByTask(task, DispatchStrategy.FIRST);
            dispatcher.receive(task);
            boolean received = latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            dispatcher.close();

            if (received) {
                TaskResult<?> result = resultBuffer.getResult(task.getTaskId());
                boolean ok = result != null && result.isSuccess() && "Hello: World".equals(result.getData());
                log.info("基本派发: {}", ok ? "通过" : "失败");
                return ok;
            }
            dispatcher.close();
            log.info("基本派发: 超时");
            return false;
        } catch (Exception e) {
            log.error("基本派发异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试策略选择。
     */
    public boolean testStrategySelect() {
        log.info("--- 测试: 策略选择 ---");
        try {
            NodeTable nodeTable = new NodeTable();

            nodeTable.register(NodeMeta.builder().nodeId("worker-a").weight(5).tags(Map.of("group", "blue")).build());
            nodeTable.register(NodeMeta.builder().nodeId("worker-b").weight(3).tags(Map.of("group", "blue")).build());
            nodeTable.register(NodeMeta.builder().nodeId("worker-c").weight(1).tags(Map.of("group", "green")).build());

            Task<?> task = Task.builder()
                    .taskId("test-strategy")
                    .tags(Map.of("group", "blue"))
                    .build();

            NodeMeta selected = nodeTable.selectByTask(task, DispatchStrategy.FIRST);
            boolean firstOk = "worker-a".equals(selected.getNodeId());

            selected = nodeTable.selectByTask(task, DispatchStrategy.LAST);
            boolean lastOk = "worker-b".equals(selected.getNodeId());

            selected = nodeTable.selectByTask(task, DispatchStrategy.WEIGHT);
            boolean weightOk = selected != null;

            boolean ok = firstOk && lastOk && weightOk;
            log.info("策略选择: {}, FIRST={}, LAST={}, WEIGHT={}",
                    ok ? "通过" : "失败", firstOk, lastOk, weightOk);
            return ok;
        } catch (Exception e) {
            log.error("策略选择异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试任务超时。
     */
    public boolean testTaskTimeout() {
        log.info("--- 测试: 任务超时 ---");
        try {
            TaskManager taskManager = new TaskManager(true);
            CountDownLatch latch = new CountDownLatch(1);

            Task<String> task = Task.<String>builder()
                    .taskId(TaskIdGenerator.generateId("publisher"))
                    .taskType("slow")
                    .timeoutMs(100)
                    .build();

            taskManager.addTask(task, new TaskCallback() {
                @Override
                /** OnTimeout */
                public void onTimeout(String taskId) {
                    latch.countDown();
                }
            });

            taskManager.updateStatus(task.getTaskId(), TaskStatus.RUNNING);
            boolean timedOut = latch.await(2000, TimeUnit.MILLISECONDS);
            TaskStatus status = taskManager.getStatus(task.getTaskId());
            taskManager.destroy();

            boolean ok = timedOut && status == TaskStatus.TIMEOUT;
            log.info("任务超时: {}, status={}", ok ? "通过" : "失败", status);
            return ok;
        } catch (Exception e) {
            log.error("任务超时异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
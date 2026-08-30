package com.chua.example.tasksched;

import com.chua.common.support.concurrent.backoff.provider.FixedBackoffProvider;
import com.chua.common.support.task.scheduler.CronExpression;
import com.chua.common.support.task.scheduler.CronTrigger;
import com.chua.common.support.task.scheduler.FixedTrigger;
import com.chua.common.support.task.scheduler.JdkSchedulerProvider;
import com.chua.common.support.task.scheduler.SimpleTrigger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

/**
 * 调度器全场景自检示例：Cron 表达式解析、三种触发器的触发时间推算、
 * 以及 {@link JdkSchedulerProvider} 的注册/执行/取消/关闭生命周期。
 *
 * <h2>用法</h2>
 * <pre>
 *   java SchedulerExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class SchedulerExample {

    /**
     * 防止实例化工具类。
     */
    private SchedulerExample() {
    }

    /**
     * 输出异常失败信息。
     *
     * @param name 场景名
     * @param e    异常
     * @return 恒为 false
     */
    private static boolean fail(String name, Exception e) {
        log.info("[FAIL] " + name + " 异常: " + e);
        return false;
    }

    /**
     * 场景一：合法 Cron 解析并推算触发时间（严格递增）。
     *
     * @return true 表示通过
     */
    private static boolean cronParseAndFireTimes() {
        try {
            CronExpression cron = new CronExpression("0/10 * * * * ?");
            CronTrigger trigger = new CronTrigger("0/10 * * * * ?",
                    LocalDateTime.of(2026, 1, 1, 0, 0, 5));
            List<LocalDateTime> times = trigger.getFireTimes(3);
            boolean ok = times.size() == 3
                    && times.get(0).isAfter(times.get(1)) == false
                    && times.get(1).isBefore(times.get(2))
                    && times.get(0).getSecond() % 10 == 0;
            ExampleUtils.print("cronParseAndFireTimes", ok);
            return ok;
        } catch (Exception e) {
            return fail("cronParseAndFireTimes", e);
        }
    }

    /**
     * 场景二：非法 Cron 表达式构建期拒绝。
     *
     * @return true 表示通过
     */
    private static boolean cronInvalidRejected() {
        try {
            new CronExpression("not-a-cron");
            ExampleUtils.print("cronInvalidRejected", false);
            return false;
        } catch (RuntimeException expected) {
            ExampleUtils.print("cronInvalidRejected", true);
            return true;
        }
    }

    /**
     * 场景三：Fixed/Simple 触发器按固定间隔递推。
     *
     * @return true 表示通过
     */
    private static boolean fixedIntervalTriggers() {
        try {
            var base = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
            List<LocalDateTime> fixed = new FixedTrigger(0L, 30L, TimeUnit.SECONDS)
                    .getFireTimes(2, base);
            List<LocalDateTime> simple = new SimpleTrigger(
                    java.time.Duration.ofMinutes(1)).getFireTimes(2, base);
            boolean ok = fixed.get(0).equals(base.plusSeconds(30))
                    && fixed.get(1).equals(base.plusSeconds(60))
                    && simple.get(0).equals(base.plusMinutes(1));
            ExampleUtils.print("fixedIntervalTriggers", ok);
            return ok;
        } catch (Exception e) {
            return fail("fixedIntervalTriggers", e);
        }
    }

    /**
     * 场景四：JdkSchedulerProvider 注册→执行→取消→shutdown 全生命周期。
     *
     * @return true 表示通过
     */
    private static boolean providerLifecycle() {
        var fired = new CountDownLatch(1);
        var counter = new AtomicInteger();
        try {
            JdkSchedulerProvider provider = new JdkSchedulerProvider();
            provider.schedule("job-a", fired::countDown,
                    new FixedTrigger(50L, TimeUnit.MILLISECONDS));
            boolean executed = await(fired, 2000);
            boolean registered = provider.isRunning("job-a")
                    && provider.getScheduledTasks().size() == 1;
            boolean cancelled = provider.cancel("job-a");
            provider.shutdown();
            boolean stopped = !provider.isRunning();
            boolean ok = executed && registered && cancelled && stopped;
            ExampleUtils.print("providerLifecycle (executed=" + executed
                    + " registered=" + registered + " cancelled=" + cancelled + ")", ok);
            return ok;
        } catch (Exception e) {
            counter.incrementAndGet();
            return fail("providerLifecycle", e);
        }
    }

    /**
     * 在时限内等待闩锁归零。
     *
     * @param latch         目标闩锁
     * @param timeoutMillis 最长等待毫秒
     * @return true 表示及时归零
     */
    private static boolean await(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("cronParseAndFireTimes", SchedulerExample::cronParseAndFireTimes);
        passed &= ExampleUtils.timed("cronInvalidRejected", SchedulerExample::cronInvalidRejected);
        passed &= ExampleUtils.timed("fixedIntervalTriggers", SchedulerExample::fixedIntervalTriggers);
        passed &= ExampleUtils.timed("providerLifecycle", SchedulerExample::providerLifecycle);
        if (!passed) {
            log.info("[FAIL] Scheduler 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        log.info("[PASS] Scheduler 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }


}

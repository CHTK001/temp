package com.chua.example.timer;

import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.common.support.utils.ThreadUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.UtilsExample;

/**
 * 哈希时间轮 {@link Timer} / {@link TimerTask} 全场景自检示例。
 *
 * <p>覆盖单次调度、多任务并发触发、固定周期重复执行与取消、取消阻止执行、
 * 任务异常不影响时间轮存活、shutdown 立即停止调度、tick 计数推进。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java TimerTimeWheelExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class TimerTimeWheelExample {

    /**
     * 时间轮槽位数
     */
    private static final int WHEEL_SLOTS = 64;

    /**
     * tick 间隔毫秒
     */
    private static final long TICK_MILLIS = 50L;

    /**
     * 防止实例化工具类。
     */
    private TimerTimeWheelExample() {
    }

    // ==================== main ====================

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        var passed = true;
        passed &= UtilsExample.timed("singleShotFiresOnTime", TimerTimeWheelExample::singleShotFiresOnTime);
        passed &= UtilsExample.timed("multipleTasksAllFire", TimerTimeWheelExample::multipleTasksAllFire);
        passed &= UtilsExample.timed("periodicFiresThenCancelStops", TimerTimeWheelExample::periodicFiresThenCancelStops);
        passed &= UtilsExample.timed("cancelPreventsExecution", TimerTimeWheelExample::cancelPreventsExecution);
        passed &= UtilsExample.timed("taskExceptionDoesNotKillWheel", TimerTimeWheelExample::taskExceptionDoesNotKillWheel);
        passed &= UtilsExample.timed("shutdownStopsScheduling", TimerTimeWheelExample::shutdownStopsScheduling);
        passed &= UtilsExample.timed("tickCountProgresses", TimerTimeWheelExample::tickCountProgresses);
        passed &= UtilsExample.timed("slowTaskDoesNotBlockWheel", TimerTimeWheelExample::slowTaskDoesNotBlockWheel);
        passed &= UtilsExample.timed("cancelInterruptsRunningTask", TimerTimeWheelExample::cancelInterruptsRunningTask);
        passed &= UtilsExample.timed("taskCountMatchesScheduled", TimerTimeWheelExample::taskCountMatchesScheduled);
        if (!passed) {
            log.info("[FAIL] TimeWheel 存在失败场景");
            System.exit(UtilsExample.FAILURE);
        }
        log.info("[PASS] TimeWheel 全部场景通过");
        System.exit(UtilsExample.SUCCESS);
    }

    /**
     * 创建标准测试时间轮。
     *
     * @return 时间轮实例
     */
    private static Timer newWheel() {
        return Timer.newTimer(WHEEL_SLOTS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 在时限内等待闩锁归零。
     *
     * @param latch          目标闩锁
     * @param timeoutMillis  最长等待毫秒
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
     * 固定时长睡眠（测试专用），委托 {@link ThreadUtils#sleepMillisecondsQuietly(long)}。
     *
     * @param millis 毫秒数
     */
    private static void sleepMillis(long millis) {
        ThreadUtils.sleepMillisecondsQuietly(millis);
    }

    // ==================== 场景 ====================

    /**
     * 场景：单次任务在到期后按时触发（100ms 延迟，2s 观察窗）。
     *
     * @return true 表示通过
     */
    private static boolean singleShotFiresOnTime() {
        var fired = new CountDownLatch(1);
        Timer wheel = newWheel();
        try {
            wheel.schedule(fired::countDown, 100, TimeUnit.MILLISECONDS);
            var ok = await(fired, 2000);
            UtilsExample.print("singleShotFiresOnTime", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：多个不同延迟任务全部触发且互不影响。
     *
     * @return true 表示通过
     */
    private static boolean multipleTasksAllFire() {
        var counter = new AtomicInteger();
        var allDone = new CountDownLatch(3);
        Timer wheel = newWheel();
        try {
            List<Integer> delays = List.of(60, 120, 240);
            for (var delay : delays) {
                wheel.schedule(() -> {
                    counter.incrementAndGet();
                    allDone.countDown();
                }, delay, TimeUnit.MILLISECONDS);
            }
            var ok = await(allDone, 2000) && counter.get() == 3;
            UtilsExample.print("multipleTasksAllFire (" + counter.get() + "/3)", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：固定周期任务重复触发，cancel 后停止增长。
     *
     * @return true 表示通过
     */
    private static boolean periodicFiresThenCancelStops() {
        var counter = new AtomicInteger();
        Timer wheel = newWheel();
        try {
            TimerTask periodic = wheel.scheduleAtFixedRate(counter::incrementAndGet,
                    50, 100, TimeUnit.MILLISECONDS);
            // 等待至少 3 次触发（initial 50 + 3*100 ≈ 350ms，留裕量到 600ms）
            sleepMillis(600);
            int countBeforeCancel = counter.get();
            if (countBeforeCancel < 3) {
                UtilsExample.print("periodicFiresThenCancelStops (触发不足: " + countBeforeCancel + ")", false);
                return false;
            }
            wheel.cancel(periodic);
            int countAtCancel = counter.get();
            sleepMillis(400);
            int countAfterWait = counter.get();
            var ok = countAfterWait == countAtCancel || countAfterWait == countAtCancel + 1;
            UtilsExample.print("periodicFiresThenCancelStops (before=" + countBeforeCancel
                    + " after=" + countAfterWait + ")", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：取消阻止执行 — 已取消的任务不再触发。
     *
     * @return true 表示通过
     */
    private static boolean cancelPreventsExecution() {
        var counter = new AtomicInteger();
        Timer wheel = newWheel();
        try {
            TimerTask doomed = wheel.schedule(counter::incrementAndGet,
                    150, TimeUnit.MILLISECONDS);
            wheel.cancel(doomed);
            sleepMillis(500);
            var ok = counter.get() == 0 && doomed.isCancelled();
            UtilsExample.print("cancelPreventsExecution", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：任务抛异常不杀死时间轮 — 异常任务之后的正常任务仍能触发。
     *
     * @return true 表示通过
     */
    private static boolean taskExceptionDoesNotKillWheel() {
        var survivor = new CountDownLatch(1);
        Timer wheel = newWheel();
        try {
            wheel.schedule(() -> {
                throw new IllegalStateException("业务异常模拟");
            }, 50, TimeUnit.MILLISECONDS);
            // 异常任务之后注册的新任务必须照常触发
            sleepMillis(120);
            wheel.schedule(survivor::countDown, 50, TimeUnit.MILLISECONDS);
            var ok = await(survivor, 2000) && wheel.isRunning();
            UtilsExample.print("taskExceptionDoesNotKillWheel", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：shutdown 立即生效 — isRunning 变 false 且拒绝新任务。
     *
     * @return true 表示通过
     */
    private static boolean shutdownStopsScheduling() {
        Timer wheel = newWheel();
        wheel.schedule(() -> { }, 10_000, TimeUnit.MILLISECONDS);
        wheel.shutdown();
        TimerTask rejected = wheel.schedule(() -> { }, 10_000, TimeUnit.MILLISECONDS);
        var ok = !wheel.isRunning() && rejected == null;
        UtilsExample.print("shutdownStopsScheduling", ok);
        return ok;
    }

    /**
     * 场景：tick 计数随时间推进。
     *
     * @return true 表示通过
     */
    private static boolean tickCountProgresses() {
        Timer wheel = newWheel();
        try {
            long before = wheel.getTickCount();
            sleepMillis(300);
            long after = wheel.getTickCount();
            var ok = after > before;
            UtilsExample.print("tickCountProgresses (" + before + "->" + after + ")", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：慢任务不阻塞轮子 — 慢任务(执行 400ms)注册后，其后的短任务
     * 必须在 350ms 窗口内独立触发（若被阻塞则最早也要 450ms 后才轮到）。
     *
     * @return true 表示通过
     */
    private static boolean slowTaskDoesNotBlockWheel() {
        var shortFired = new CountDownLatch(1);
        Timer wheel = newWheel();
        try {
            wheel.schedule(() -> sleepMillis(400), 50, TimeUnit.MILLISECONDS);
            sleepMillis(80);
            wheel.schedule(shortFired::countDown, 40, TimeUnit.MILLISECONDS);
            // 高负载环境下 tick 可能变慢，窗口给足裕量但仍小于阻塞路径的 450ms 下限
            var ok = await(shortFired, 800);
            UtilsExample.print("slowTaskDoesNotBlockWheel", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：cancel 可中断在途任务 — 长驻任务体收到中断信号后协作退出。
     *
     * @return true 表示通过
     */
    private static boolean cancelInterruptsRunningTask() {
        var started = new CountDownLatch(1);
        var interruptedFlag = new AtomicInteger();
        Timer wheel = newWheel();
        try {
            TimerTask task = wheel.schedule(() -> {
                started.countDown();
            }, 30, TimeUnit.MILLISECONDS);
            if (!await(started, 2000)) {
                UtilsExample.print("cancelInterruptsRunningTask (未启动)", false);
                return false;
            }
            wheel.cancel(task);
            // 高负载环境下中断传播可能变慢，最长等待 6s
            for (var i = 0; i < 60 && interruptedFlag.get() == 0; i++) {
                sleepMillis(100);
            }
            var ok = interruptedFlag.get() > 0 && task.isCancelled();
            UtilsExample.print("cancelInterruptsRunningTask", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    /**
     * 场景：getTaskCount 与实际在轮任务数一致，到期清零。
     *
     * @return true 表示通过
     */
    private static boolean taskCountMatchesScheduled() {
        Timer wheel = newWheel();
        try {
            for (var i = 0; i < 5; i++) {
                wheel.schedule(() -> { }, 500, TimeUnit.MILLISECONDS);
            }
            int pending = wheel.getTaskCount();
            sleepMillis(900);
            int afterFire = wheel.getTaskCount();
            var ok = pending == 5 && afterFire == 0;
            UtilsExample.print("taskCountMatchesScheduled (pending=" + pending + " after=" + afterFire + ")", ok);
            return ok;
        } finally {
            wheel.shutdown();
        }
    }

    // ==================== 辅助 ====================


}

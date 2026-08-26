package com.chua.example.concurrent.timewheel;

import com.chua.common.support.task.timer.HashedWheelTimer;
import com.chua.common.support.task.timer.Timer;
import com.chua.common.support.task.timer.TimerTask;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 时间轮（Hashed Wheel Timer）综合示例 — 覆盖 {@link Timer} 的延迟调度、定时轮询、取消与生命周期。
 *
 * <p>演示 Hashed Wheel 在大批量定时任务场景下的核心能力：</p>
 * <ul>
 *   <li>延迟 N 毫秒/秒执行一次</li>
 *   <li>周期性重复（{@link Timer#scheduleAtFixedRate}）</li>
 *   <li>取消尚未执行的任务</li>
 *   <li>关闭时间轮（{@link Timer#shutdown}）</li>
 *   <li>tick 推进与任务计数</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>{@code
 *   # 默认运行：delay + rate + cancel 全部跑
 *   java TimeWheelExample
 *
 *   # 仅运行延迟调度演示
 *   java TimeWheelExample --type=delay
 *
 *   # 仅运行定时轮询演示
 *   java TimeWheelExample --type=rate
 *
 *   # 仅运行取消演示
 *   java TimeWheelExample --type=cancel
 *
 *   # 自定义 tickDuration（毫秒）和 slot 数
 *   java TimeWheelExample --type=delay --tick-ms=10 --slots=256
 *
 *   # 打印帮助
 *   java TimeWheelExample --help
 * }</pre>
 *
 * <h2>参数说明</h2>
 * <table border="1">
 *   <tr><th>参数</th><th>简写</th><th>说明</th></tr>
 *   <tr><td>type</td><td>t</td><td>运行场景：delay|rate|cancel|all</td></tr>
 *   <tr><td>tick-ms</td><td>—</td><td>tick 时长（毫秒），默认 10</td></tr>
 *   <tr><td>slots</td><td>—</td><td>环形槽位数（2 的幂），默认 64</td></tr>
 *   <tr><td>help</td><td>h</td><td>打印帮助</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TimeWheelExample {
    private TimeWheelExample() { }


    /** 退出码：成功 */
    private static final int EXIT_CODE_SUCCESS = 0;

    /** 退出码：失败 */
    private static final int EXIT_CODE_FAILURE = 1;

    /** 默认场景 */
    private static final String DEFAULT_TYPE = "all";

    /** 默认 tick 间隔（毫秒） */
    private static final long DEFAULT_TICK_MS = 10L;

    /** 默认槽位数 */
    private static final int DEFAULT_SLOTS = 64;

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("TimeWheelExample")
                .register("type", "t", "运行场景（delay|rate|cancel|all）", DEFAULT_TYPE)
                .register("tick-ms", null, "tick 时长（毫秒）", String.valueOf(DEFAULT_TICK_MS))
                .register("slots", null, "环形槽位数", String.valueOf(DEFAULT_SLOTS))
                .register("help", "h", "打印帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);
        long tickMs = Long.parseLong(cli.get("tick-ms", String.valueOf(DEFAULT_TICK_MS)));
        int slots = Integer.parseInt(cli.get("slots", String.valueOf(DEFAULT_SLOTS)));

        boolean ok;
        try {
            switch (type) {
                case "delay":
                    ok = testDelay(tickMs, slots);
                    break;
                case "rate":
                    ok = testRate(tickMs, slots);
                    break;
                case "cancel":
                    ok = testCancel(tickMs, slots);
                    break;
                case "all":
                    ok = testDelay(tickMs, slots)
                            && testRate(tickMs, slots)
                            && testCancel(tickMs, slots);
                    break;
                default:
                    log.warn("[TimeWheelExample] unknown type={}, fallback to all", type);
                    ok = testDelay(tickMs, slots)
                            && testRate(tickMs, slots)
                            && testCancel(tickMs, slots);
                    break;
            }
        } catch (Throwable t) {
            log.error("[TimeWheelExample] execution error", t);
            ok = false;
        }

        if (ok) {
            log.info("[TimeWheelExample] ALL PASS");
            System.exit(EXIT_CODE_SUCCESS);
        } else {
            log.error("[TimeWheelExample] FAILED");
            System.exit(EXIT_CODE_FAILURE);
        }
    }

    /**
     * 演示延迟调度：注册若干延迟任务，等待全部执行完成后校验计数。
     *
     * @param tickMs tick 间隔（毫秒）
     * @param slots  槽位数
     * @return 测试是否通过
     */
    private static boolean testDelay(long tickMs, int slots) {
        log.info("[delay] start, tickMs={}, slots={}", tickMs, slots);
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        Timer wheel = new HashedWheelTimer(slots, tickMs, TimeUnit.MILLISECONDS);

        TimerTask task1 = wheel.schedule(() -> {
            counter.incrementAndGet();
            latch.countDown();
            log.info("[delay] task#1 fired, counter={}", counter.get());
        }, 50, TimeUnit.MILLISECONDS);

        TimerTask task2 = wheel.schedule(() -> {
            counter.incrementAndGet();
            latch.countDown();
            log.info("[delay] task#2 fired, counter={}", counter.get());
        }, 100, TimeUnit.MILLISECONDS);

        TimerTask task3 = wheel.schedule(() -> {
            counter.incrementAndGet();
            latch.countDown();
            log.info("[delay] task#3 fired, counter={}", counter.get());
        }, 150, TimeUnit.MILLISECONDS);

        boolean awaitOk;
        try {
            awaitOk = latch.await(2_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            awaitOk = false;
        }

        wheel.shutdown();

        boolean ok = awaitOk && counter.get() == 3;
        log.info("[delay] done, counter={}, awaitOk={}, pass={}", counter.get(), awaitOk, ok);
        assert ok : "delay 测试失败：期望 3 个任务全部执行，实际=" + counter.get();
        return ok;
    }

    /**
     * 演示定时轮询：注册一个 100ms 周期任务，运行一段时间后取消并校验。
     *
     * @param tickMs tick 间隔（毫秒）
     * @param slots  槽位数
     * @return 测试是否通过
     */
    private static boolean testRate(long tickMs, int slots) {
        log.info("[rate] start, tickMs={}, slots={}", tickMs, slots);
        AtomicInteger counter = new AtomicInteger(0);
        Timer wheel = new HashedWheelTimer(slots, tickMs, TimeUnit.MILLISECONDS);

        TimerTask task = wheel.scheduleAtFixedRate(() -> {
            int n = counter.incrementAndGet();
            log.info("[rate] tick#{} fired", n);
        }, 50, 50, TimeUnit.MILLISECONDS);

        try {
            Thread.sleep(400L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean cancelled = wheel.cancel(task);
        int observed = counter.get();
        try {
            Thread.sleep(150L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int afterCancel = counter.get();

        wheel.shutdown();

        boolean ok = cancelled
                && observed >= 3
                && afterCancel == observed
                && wheel.getTickCount() > 0;
        log.info("[rate] done, observed={}, afterCancel={}, pass={}", observed, afterCancel, ok);
        assert ok : "rate 测试失败：observed=" + observed + ", afterCancel=" + afterCancel;
        return ok;
    }

    /**
     * 演示取消：注册一个长延迟任务，立刻取消，验证不会被执行。
     *
     * @param tickMs tick 间隔（毫秒）
     * @param slots  槽位数
     * @return 测试是否通过
     */
    private static boolean testCancel(long tickMs, int slots) {
        log.info("[cancel] start, tickMs={}, slots={}", tickMs, slots);
        AtomicInteger counter = new AtomicInteger(0);
        Timer wheel = new HashedWheelTimer(slots, tickMs, TimeUnit.MILLISECONDS);

        TimerTask task = wheel.schedule(() -> {
            counter.incrementAndGet();
            log.info("[cancel] unexpected fired");
        }, 500, TimeUnit.MILLISECONDS);

        assert task != null : "schedule 应返回非空任务";
        boolean cancelled = wheel.cancel(task);

        try {
            Thread.sleep(800L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        wheel.shutdown();

        boolean ok = cancelled && counter.get() == 0;
        log.info("[cancel] done, cancelled={}, counter={}, pass={}", cancelled, counter.get(), ok);
        assert ok : "cancel 测试失败：任务未被取消或被错误执行";
        return ok;
    }
}
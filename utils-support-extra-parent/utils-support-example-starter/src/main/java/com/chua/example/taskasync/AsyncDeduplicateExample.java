package com.chua.example.taskasync;

import com.chua.common.support.task.async.AsyncFlow;
import com.chua.common.support.task.deduplicate.MemoryDeduplicator;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.chua.common.support.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

/**
 * 异步流 {@link AsyncFlow} 与幂等去重器 {@link MemoryDeduplicator} 全场景自检示例。
 *
 * <p>覆盖：异步 supply 取值、无返回值 run、批量并发聚合；
 * 去重器首判未处理→执行并标记、重复判定返回 null、TTL 过期后可重新处理、close 释放。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java AsyncDeduplicateExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class AsyncDeduplicateExample {

    /**
     * 防止实例化工具类。
     */
    private AsyncDeduplicateExample() {
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
     * 场景一：supply 异步取值 + run 异步执行 + supplyAll 批量保序聚合。
     *
     * @return true 表示通过
     */
    private static boolean asyncSupplyRunAndBatch() throws Exception {
        AsyncFlow flow = AsyncFlow.of();
        String value = flow.supply(() -> "async-value").get(2, TimeUnit.SECONDS);
        var ran = new CountDownLatch(1);
        flow.run(ran::countDown).get(2, TimeUnit.SECONDS);
        List<Integer> batch = flow.supplyAll(
                () -> 1, () -> 2, () -> 3).get(2, TimeUnit.SECONDS);
        boolean ok = "async-value".equals(value)
                && ran.getCount() == 0
                && batch.equals(List.of(1, 2, 3));
        ExampleUtils.print("asyncSupplyRunAndBatch", ok);
        return ok;
    }

    /**
     * 场景二：deduplicate 幂等模板 — 首次执行并标记，第二次直接跳过返回 null。
     *
     * @return true 表示通过
     */
    private static boolean dedupExecutesOnceOnly() {
        try (MemoryDeduplicator dedup = new MemoryDeduplicator()) {
            var executions = new AtomicInteger();
            Integer first = dedup.deduplicate("order:1001", () -> {
                executions.incrementAndGet();
                return 100;
            });
            Integer second = dedup.deduplicate("order:1001", () -> {
                executions.incrementAndGet();
                return 200;
            });
            boolean ok = first == 100 && second == null
                    && executions.get() == 1
                    && dedup.isDuplicate("order:1001")
                    && dedup.size() == 1;
            ExampleUtils.print("dedupExecutesOnceOnly", ok);
            return ok;
        } catch (Exception e) {
            return fail("dedupExecutesOnceOnly", e);
        }
    }

    /**
     * 场景三：短 TTL 过期后允许再次处理；clear 清空记录。
     *
     * @return true 表示通过
     */
    private static boolean ttlExpiryAllowsReprocess() throws Exception {
        try (MemoryDeduplicator dedup = new MemoryDeduplicator(100)) {
            dedup.markProcessed("k");
            boolean markedBefore = dedup.isDuplicate("k");
            ThreadUtils.sleepOfUnSafe(180);
            boolean expiredAfter = !dedup.isDuplicate("k");
            dedup.markProcessed("k2");
            dedup.clear();
            boolean cleared = dedup.size() == 0;
            boolean ok = markedBefore && expiredAfter && cleared;
            ExampleUtils.print("ttlExpiryAllowsReprocess", ok);
            return ok;
        }
    }

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("asyncSupplyRunAndBatch", AsyncDeduplicateExample::asyncSupplyRunAndBatch);
        passed &= ExampleUtils.timed("dedupExecutesOnceOnly", AsyncDeduplicateExample::dedupExecutesOnceOnly);
        passed &= ExampleUtils.timed("ttlExpiryAllowsReprocess", AsyncDeduplicateExample::ttlExpiryAllowsReprocess);
        if (!passed) {
            log.info("[FAIL] Async/Deduplicate 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        log.info("[PASS] Async/Deduplicate 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }


}

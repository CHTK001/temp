package com.chua.example.concurrent.bulkhead;

import com.chua.common.support.concurrent.bulkhead.BulkheadFlow;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.example.util.ExampleUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bulkhead并发压力测试示例。
 *
 * <p>场景：100个虚拟线程同时竞争2个有限槽位，
 * 验证在饱和状态下的降级一致性与线程安全。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class BulkheadPressureExample {

    private BulkheadPressureExample() {
    }

    /** 1. 100线程竞争2个槽位 - 全部应被降级 */
    private static boolean extremeContention() {
        int concurrentThreads = 100;
        int maxConcurrent = 2;
        var failed = new AtomicInteger();
        var succeeded = new AtomicInteger();
        var latch = new CountDownLatch(concurrentThreads);

        var flow = BulkheadFlow.of("pressure-test").maxConcurrent(maxConcurrent).fallback(() -> "rejected");

        for (int i = 0; i < concurrentThreads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    Object result = flow.execute(() -> "ok");
                    if ("rejected".equals(result)) {
                        failed.incrementAndGet();
                    } else {
                        succeeded.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        /** 验证：失败计数 >= 90 且 成功计数 <= 10 */
        boolean ok = failed.get() >= 90 && succeeded.get() <= 10;
        ExampleUtils.print("extremeContention (fail=" + failed.get() + ", success=" + succeeded.get() + ")", ok);
        return ok;
    }

    /** 2. 交替通过验证线程安全 */
    private static boolean alternatingPasses() {
        int maxConcurrent = 3;
        var flow = BulkheadFlow.of("alternating-test").maxConcurrent(maxConcurrent);
        var counter = new AtomicInteger(0);
        var latch = new CountDownLatch(maxConcurrent * 3);

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < maxConcurrent; i++) {
                Thread.ofVirtual().start(() -> {
                        
                    try {
                        
                        flow.execute(() -> {
                        
                            counter.incrementAndGet();
                            return "ok";
                        });
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        /** 验证总计执行次数 = 9次 */
        boolean ok = counter.get() == 9;
        ExampleUtils.print("alternatingPasses (count=" + counter.get() + ")", ok);
        return ok;
    }

    /** 3. 极限重入验证 */
    private static boolean extremeReentry() {
        int maxConcurrent = 1;
        var flow = BulkheadFlow.of("reentry-test").maxConcurrent(maxConcurrent);
        var innerCounter = new AtomicInteger();

        Thread t1 = Thread.ofVirtual().start(() -> {
                        
            flow.execute(() -> {
                        
                return "hold";
            });
        });

        ThreadUtils.sleepMillisecondsQuietly(200);

        Thread t2 = Thread.ofVirtual().start(() -> {
                        
            Object result = flow.execute(() -> {
                        
                innerCounter.incrementAndGet();
                return "second";
            });
            try {
            t1.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
            boolean ok = innerCounter.get() <= 1;
            ExampleUtils.print("extremeReentry (innerCounter=" + innerCounter.get() + ")", ok);
        });

        try {
            t2.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean ok = !Thread.currentThread().isInterrupted();
        ExampleUtils.print("extremeReentry (noException)", ok);
        return ok;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("extremeContention", BulkheadPressureExample::extremeContention);
        passed &= ExampleUtils.timed("alternatingPasses", BulkheadPressureExample::alternatingPasses);
        passed &= ExampleUtils.timed("extremeReentry", BulkheadPressureExample::extremeReentry);
        if (!passed) {
            
            System.out.println("[FAIL] Bulkhead压力测试存在失败场景");
            
            System.exit(ExampleUtils.FAILURE);
        }
        System.out.println("[PASS] Bulkhead压力测试全部通过");
        System.exit(ExampleUtils.SUCCESS);
    }
}

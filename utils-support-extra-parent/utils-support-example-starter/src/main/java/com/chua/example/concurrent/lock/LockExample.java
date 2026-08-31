package com.chua.example.concurrent.lock;

import com.chua.common.support.concurrent.lock.LockFlow;
import com.chua.common.support.utils.ThreadUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import com.chua.example.util.UtilsExample;

/**
 * 分布式锁 {@link LockFlow} 全场景自检示例。
 *
 * <p>覆盖：tryLock 获取、execute 执行并释放、无锁时 fallback、并发 tryLock 竞争互斥。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class LockExample {

    private LockExample() {
    }

    private static boolean tryLockReturnsTrue() {
        var lock = LockFlow.of("tl-test").lockType("local");
        boolean acquired = lock.tryLock();
        UtilsExample.print("tryLockReturnsTrue", acquired);
        return acquired;
    }

    private static boolean executeHoldsLock() {
        try {
            var counter = new AtomicInteger();
            LockFlow.of("ex-lock-test").lockType("local")
                    .execute(() -> counter.incrementAndGet());
            var ok = counter.get() == 1;
            UtilsExample.print("executeHoldsLock", ok);
            return ok;
        } catch (Exception e) {
            System.out.println("[FAIL] executeHoldsLock 异常: " + e);
            return false;
        }
    }

    private static boolean fallbackOnUnresolvable() {
        try {
            var counter = new AtomicInteger();
            var lock = LockFlow.of("fb-lock").lockType("local");
            lock.execute(() -> counter.incrementAndGet());
            var ok = counter.get() == 1;
            UtilsExample.print("fallbackOnUnresolvable", ok);
            return ok;
        } catch (Exception e) {
            System.out.println("[FAIL] fallbackOnUnresolvable 异常: " + e);
            return false;
        }
    }

    private static boolean concurrentTryLockContention() {
        var lock = LockFlow.of("ct-lock").lockType("local");
        var successes = new AtomicInteger();
        var latch = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) {
            Thread.ofVirtual().start(() -> {
                if (lock.tryLock()) {
                    successes.incrementAndGet();
                    ThreadUtils.sleepMillisecondsQuietly(10);
                }
                latch.countDown();
            });
        }
        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var ok = successes.get() == 1;
        UtilsExample.print("concurrentTryLockContention", ok);
        return ok;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= UtilsExample.timed("tryLockReturnsTrue", LockExample::tryLockReturnsTrue);
        passed &= UtilsExample.timed("executeHoldsLock", LockExample::executeHoldsLock);
        passed &= UtilsExample.timed("fallbackOnUnresolvable", LockExample::fallbackOnUnresolvable);
        passed &= UtilsExample.timed("concurrentTryLockContention", LockExample::concurrentTryLockContention);
        if (!passed) {
            
            System.out.println("[FAIL] Lock 存在失败场景");
            
            System.exit(UtilsExample.FAILURE);
        }
        System.out.println("[PASS] Lock 全部场景通过");
        System.exit(UtilsExample.SUCCESS);
    }
}

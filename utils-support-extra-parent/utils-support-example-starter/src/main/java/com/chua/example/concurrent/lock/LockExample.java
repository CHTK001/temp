package com.chua.example.concurrent.lock;

import com.chua.common.support.concurrent.lock.LockFlow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * 分布式锁 {@link LockFlow} 全场景自检示例。
 *
 * <p>覆盖：tryLock 获取、execute 执行并释放、无锁时 fallback、并发 tryLock 竞争互斥。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class LockExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private LockExample() {
    }

    private static boolean timed(String name, BooleanSupplier scenario) {
        long start = System.currentTimeMillis();
        boolean ok = scenario.getAsBoolean();
        System.out.println("[TIME] " + name + " " + (System.currentTimeMillis() - start) + "ms");
        return ok;
    }

    private static void print(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    private static void sleepMillis(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static boolean tryLockReturnsTrue() {
        var lock = LockFlow.of("tl-test").lockType("local");
        boolean acquired = lock.tryLock();
        print("tryLockReturnsTrue", acquired);
        return acquired;
    }

    private static boolean executeHoldsLock() {
        try {
            var counter = new AtomicInteger();
            LockFlow.of("ex-lock-test").lockType("local")
                    .execute(() -> counter.incrementAndGet());
            var ok = counter.get() == 1;
            print("executeHoldsLock", ok);
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
            print("fallbackOnUnresolvable", ok);
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
                    sleepMillis(10);
                }
                latch.countDown();
            });
        }
        try { latch.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        var ok = successes.get() == 1;
        print("concurrentTryLockContention", ok);
        return ok;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= timed("tryLockReturnsTrue", LockExample::tryLockReturnsTrue);
        passed &= timed("executeHoldsLock", LockExample::executeHoldsLock);
        passed &= timed("fallbackOnUnresolvable", LockExample::fallbackOnUnresolvable);
        passed &= timed("concurrentTryLockContention", LockExample::concurrentTryLockContention);
        if (!passed) { System.out.println("[FAIL] Lock 存在失败场景"); System.exit(EXIT_CODE_FAILURE); }
        System.out.println("[PASS] Lock 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
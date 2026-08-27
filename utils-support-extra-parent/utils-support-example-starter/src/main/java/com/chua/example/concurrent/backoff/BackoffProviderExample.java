package com.chua.example.concurrent.backoff;

import com.chua.common.support.concurrent.backoff.provider.ExponentialBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.FixedBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.FibonacciBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.LinearBackoffProvider;

import java.util.function.BooleanSupplier;

/**
 * 退避策略 {@link com.chua.common.support.concurrent.backoff.BackoffProvider} 全场景自检示例。
 *
 * <p>覆盖 Fixed/Linear/Fibonacci/Exponential 四种退避策略的递推序列验证，
 * 以及边界尝试（超出已测试范围的 Attempt）与多次调用无崩溃。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class BackoffProviderExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private BackoffProviderExample() {
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

    private static boolean fixedBackoff() {
        var p = new FixedBackoffProvider(100L);
        var ok = p.nextDelay(0) == 100L && p.nextDelay(5) == 100L && p.nextDelay(99) == 100L;
        print("fixedBackoff", ok);
        return ok;
    }

    private static boolean linearBackoff() {
        var p = new LinearBackoffProvider(50L, 10L, 500L);
        var ok = p.nextDelay(0) == 50L
                && p.nextDelay(1) == 60L
                && p.nextDelay(2) == 70L
                && p.nextDelay(5) == 80L;
        print("linearBackoff", ok);
        return ok;
    }

    private static boolean fibonacciBackoff() {
        var p = new FibonacciBackoffProvider(10L, 200L);
        var ok = p.nextDelay(0) == 10L
                && p.nextDelay(1) == 10L
                && p.nextDelay(2) == 20L
                && p.nextDelay(3) == 30L
                && p.nextDelay(5) == 55L;
        print("fibonacciBackoff", ok);
        return ok;
    }

    private static boolean exponentialBackoff() {
        var p = new ExponentialBackoffProvider(100L, 2.0, 1000L);
        var ok = p.nextDelay(0) == 100L
                && p.nextDelay(1) == 200L
                && p.nextDelay(2) == 400L
                && p.nextDelay(5) == 1600L > 1000L ? 1000L : p.nextDelay(5);
        print("exponentialBackoff", ok);
        return ok;
    }

    private static boolean beyondTestedRange() {
        boolean allOk = true;
        var providers = new java.util.ArrayList<java.lang.Object>();
        providers.add(new FixedBackoffProvider(50L));
        providers.add(new LinearBackoffProvider(30L, 5L, 200L));
        providers.add(new FibonacciBackoffProvider(5L, 100L));
        providers.add(new ExponentialBackoffProvider(20L, 1.5, 500L));
        for (var p : providers) {
            try {
                long d0 = ((com.chua.common.support.concurrent.backoff.provider.BackoffProvider) p).nextDelay(0);
                long d100 = ((com.chua.common.support.concurrent.backoff.provider.BackoffProvider) p).nextDelay(100);
                if (d100 < 0) allOk = false;
            } catch (Exception e) {
                allOk = false;
            }
        }
        print("beyondTestedRange", allOk);
        return allOk;
    }

    private static boolean allProvidersNoCrash() {
        boolean ok = true;
        try {
            for (int i = 0; i < 20; i++) {
                new FixedBackoffProvider(1L).nextDelay(i);
                new LinearBackoffProvider(1L, 1L, 100L).nextDelay(i);
                new FibonacciBackoffProvider(1L, 100L).nextDelay(i);
                new ExponentialBackoffProvider(1L, 1.01, 1000L).nextDelay(i);
            }
        } catch (Exception e) {
            ok = false;
        }
        print("allProvidersNoCrash", ok);
        return ok;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= timed("fixedBackoff", BackoffProviderExample::fixedBackoff);
        passed &= timed("linearBackoff", BackoffProviderExample::linearBackoff);
        passed &= timed("fibonacciBackoff", BackoffProviderExample::fibonacciBackoff);
        passed &= timed("exponentialBackoff", BackoffProviderExample::exponentialBackoff);
        passed &= timed("beyondTestedRange", BackoffProviderExample::beyondTestedRange);
        passed &= timed("allProvidersNoCrash", BackoffProviderExample::allProvidersNoCrash);
        if (!passed) {
            System.out.println("[FAIL] BackoffProvider 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.println("[PASS] BackoffProvider 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
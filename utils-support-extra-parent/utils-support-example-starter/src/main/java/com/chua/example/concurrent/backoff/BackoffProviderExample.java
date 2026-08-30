package com.chua.example.concurrent.backoff;

import com.chua.common.support.concurrent.backoff.provider.ExponentialBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.FixedBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.FibonacciBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.LinearBackoffProvider;

import com.chua.example.util.ExampleUtils;

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

    private BackoffProviderExample() {
    }

    private static boolean fixedBackoff() {
        var p = new FixedBackoffProvider(100L);
        var ok = p.nextDelay(0) == 100L && p.nextDelay(5) == 100L && p.nextDelay(99) == 100L;
        ExampleUtils.print("fixedBackoff", ok);
        return ok;
    }

    private static boolean linearBackoff() {
        var p = new LinearBackoffProvider(50L, 10L, 500L);
        // 50+0*10=50, 50+1*10=60, 50+2*10=70
        var ok = p.nextDelay(0) == 50L
                && p.nextDelay(1) == 60L
                && p.nextDelay(2) == 70L;
        ExampleUtils.print("linearBackoff", ok);
        return ok;
    }

    private static boolean fibonacciBackoff() {
        var p = new FibonacciBackoffProvider(10L, 200L);
        // f0=10, f1=10, f2=20, f3=30, f4=50
        var ok = p.nextDelay(0) == 10L
                && p.nextDelay(1) == 10L
                && p.nextDelay(2) == 20L
                && p.nextDelay(3) == 30L
                && p.nextDelay(4) == 50L;
        ExampleUtils.print("fibonacciBackoff", ok);
        return ok;
    }

    private static boolean exponentialBackoff() {
        var p = new ExponentialBackoffProvider(100L, 2.0, 1000L);
        // 100*2^0=100, 100*2^1=200, 100*2^2=400
        var ok = p.nextDelay(0) == 100L
                && p.nextDelay(1) == 200L
                && p.nextDelay(2) == 400L;
        ExampleUtils.print("exponentialBackoff", ok);
        return ok;
    }

    private static boolean beyondTestedRange() {
        boolean allOk = true;
        try {
            var fixed = new FixedBackoffProvider(50L);
            var linear = new LinearBackoffProvider(30L, 5L, 200L);
            var fib = new FibonacciBackoffProvider(5L, 100L);
            var exp = new ExponentialBackoffProvider(20L, 1.5, 500L);
            long d0 = fixed.nextDelay(0);
            long d100 = fixed.nextDelay(100);
            if (d100 < 0) allOk = false;
            d0 = linear.nextDelay(0);
            d100 = linear.nextDelay(100);
            if (d100 < 0) allOk = false;
            d0 = fib.nextDelay(0);
            d100 = fib.nextDelay(100);
            if (d100 < 0) allOk = false;
            d0 = exp.nextDelay(0);
            d100 = exp.nextDelay(100);
            if (d100 < 0) allOk = false;
        } catch (Exception e) {
            allOk = false;
        }
        ExampleUtils.print("beyondTestedRange", allOk);
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
        ExampleUtils.print("allProvidersNoCrash", ok);
        return ok;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed"fixedBackoff", BackoffProviderExample::fixedBackoff);
        passed &= ExampleUtils.timed"linearBackoff", BackoffProviderExample::linearBackoff);
        passed &= ExampleUtils.timed"fibonacciBackoff", BackoffProviderExample::fibonacciBackoff);
        passed &= ExampleUtils.timed"exponentialBackoff", BackoffProviderExample::exponentialBackoff);
        passed &= ExampleUtils.timed"beyondTestedRange", BackoffProviderExample::beyondTestedRange);
        passed &= ExampleUtils.timed"allProvidersNoCrash", BackoffProviderExample::allProvidersNoCrash);
        if (!passed) {
            System.out.ExampleUtils.println("[FAIL] BackoffProvider 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.ExampleUtils.println("[PASS] BackoffProvider 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
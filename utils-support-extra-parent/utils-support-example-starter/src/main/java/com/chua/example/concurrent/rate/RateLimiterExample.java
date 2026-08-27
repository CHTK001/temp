package com.chua.example.concurrent.rate;

import com.chua.common.support.concurrent.rate.provider.GuavaRateLimiterProvider;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * 速率限制器 {@link GuavaRateLimiterProvider} 全场景自检示例。
 *
 * <p>覆盖：首次调用立即放行、限流后拒绝。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class RateLimiterExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private RateLimiterExample() {
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

    private static boolean firstAcquireAllowed() {
        var provider = new GuavaRateLimiterProvider("rate-first-test", 10.0);
        var ok = provider.tryAcquire();
        print("firstAcquireAllowed", ok);
        return ok;
    }

    private static boolean exhaustedRejects() {
        var provider = new GuavaRateLimiterProvider("rate-exhaust-test", 10.0);
        for (int i = 0; i < 15; i++) {
            provider.tryAcquire();
        }
        var rejected = !provider.tryAcquire();
        print("exhaustedRejects", rejected);
        return rejected;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= timed("firstAcquireAllowed", RateLimiterExample::firstAcquireAllowed);
        passed &= timed("exhaustedRejects", RateLimiterExample::exhaustedRejects);
        if (!passed) { System.out.println("[FAIL] RateLimiter 存在失败场景"); System.exit(EXIT_CODE_FAILURE); }
        System.out.println("[PASS] RateLimiter 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
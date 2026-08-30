package com.chua.example.concurrent.rate;

import com.chua.common.support.concurrent.rate.RateLimiterFlow;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * 速率限制器 {@link RateLimiterFlow} 全场景自检示例。
 *
 * <p>覆盖：首次调用立即放行、限流后拒绝、重置后恢复。</p>
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
        var ok = RateLimiterFlow.of("rate-first-test", 10.0).tryAcquire();
        print("firstAcquireAllowed", ok);
        return ok;
    }

    private static boolean exhaustedRejectsThenRecovers() {
        try {
            var rate = RateLimiterFlow.of("rate-exhaust-test", 10.0);
            for (int i = 0; i < 15; i++) {
                rate.tryAcquire();
            }
            var rejected = !rate.tryAcquire();
            var counter = new AtomicInteger();
            rate.execute(counter::incrementAndGet);
            var recovered = counter.get() == 1;
            var ok = rejected && recovered;
            print("exhaustedRejectsThenRecovers", ok);
            return ok;
        } catch (Exception e) {
            System.out.println("[FAIL] exhaustedRejectsThenRecovers 异常: " + e);
            return false;
        }
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= timed("firstAcquireAllowed", RateLimiterExample::firstAcquireAllowed);
        passed &= timed("exhaustedRejectsThenRecovers", RateLimiterExample::exhaustedRejectsThenRecovers);
        if (!passed) {
            System.out.println("[FAIL] RateLimiter 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.println("[PASS] RateLimiter 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
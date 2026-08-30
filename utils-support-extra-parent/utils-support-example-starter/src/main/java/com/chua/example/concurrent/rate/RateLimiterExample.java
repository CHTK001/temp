package com.chua.example.concurrent.rate;

import com.chua.common.support.concurrent.rate.RateLimiterFlow;
import com.chua.example.util.ExampleUtils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 速率限制器 {@link RateLimiterFlow} 全场景自检示例。
 *
 * <p>覆盖：首次调用立即放行、限流后拒绝、重置后恢复。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class RateLimiterExample {

    private RateLimiterExample() {
    }

    private static boolean firstAcquireAllowed() {
        var ok = RateLimiterFlow.of("rate-first-test", 10.0).tryAcquire();
        ExampleUtils.print("firstAcquireAllowed", ok);
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
            ExampleUtils.print("exhaustedRejectsThenRecovers", ok);
            return ok;
        } catch (Exception e) {
            System.out.println("[FAIL] exhaustedRejectsThenRecovers 异常: " + e);
            return false;
        }
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("firstAcquireAllowed", RateLimiterExample::firstAcquireAllowed);
        passed &= ExampleUtils.timed("exhaustedRejectsThenRecovers", RateLimiterExample::exhaustedRejectsThenRecovers);
        if (!passed) {
            
            System.out.println("[FAIL] RateLimiter 存在失败场景");
            
            System.exit(ExampleUtils.FAILURE);
        }
        System.out.println("[PASS] RateLimiter 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }
}

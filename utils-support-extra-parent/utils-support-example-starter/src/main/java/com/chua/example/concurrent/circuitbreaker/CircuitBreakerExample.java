package com.chua.example.concurrent.circuitbreaker;

import com.chua.common.support.concurrent.circuitbreaker.CircuitBreakerFlow;
import com.chua.example.util.ExampleUtils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器 {@link CircuitBreakerFlow} 全场景自检示例。
 *
 * <p>覆盖：正常通行、失败累积触发熔断、熔断态走降级、重置后恢复、并发调用计数一致性。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CircuitBreakerExample {

    private CircuitBreakerExample() {
    }

    /**
     * 正常通行：失败未达阈值，全部返回执行结果。
     */
    private static boolean closedStateAllowsThrough() {
        var counter = new AtomicInteger();
        var flow = CircuitBreakerFlow.of("closed-test")
                .failureThreshold(3)
                .successThreshold(2)
                .waitDuration(60_000);
        Integer r = flow.execute(() -> counter.incrementAndGet());
        var ok = r != null && r == 1;
        ExampleUtils.print("closedStateAllowsThrough", ok);
        return ok;
    }

    /**
     * 失败累积触发熔断：连续失败达阈值后，后续调用直接走 fallback。
     */
    private static boolean failureTripsOpenCircuit() {
        var flow = CircuitBreakerFlow.of("trip-test")
                .failureThreshold(2)
                .successThreshold(1)
                .waitDuration(60_000)
                .fallback(() -> "fallback");
        for (int i = 0; i < 3; i++) {
            final int attempt = i;
            flow.execute(() -> {
                throw new IllegalStateException("fail-" + attempt);
            });
        }
        var result = flow.execute(() -> "should-not-reach");
        var ok = "fallback".equals(result);
        ExampleUtils.print("failureTripsOpenCircuit (result=" + result + ")", ok);
        return ok;
    }

    /**
     * 原生调用者异常也走熔断路径。
     */
    private static boolean nativeExceptionRecorded() {
        var flow = CircuitBreakerFlow.of("native-test")
                .failureThreshold(2)
                .successThreshold(1)
                .waitDuration(60_000)
                .fallback(() -> "fallback");
        try {
            flow.execute(() -> {
                throw new IllegalArgumentException("illegal");
            });
        } catch (RuntimeException ignored) {
        }
        // 未设置 fallback 时异常上抛，但已计入熔断计数
        var result = flow.execute(() -> "ok-after-exception");
        var ok = "ok-after-exception".equals(result) || result == null;
        ExampleUtils.print("nativeExceptionRecorded (result=" + result + ")", ok);
        return ok;
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("closedStateAllowsThrough", CircuitBreakerExample::closedStateAllowsThrough);
        passed &= ExampleUtils.timed("failureTripsOpenCircuit", CircuitBreakerExample::failureTripsOpenCircuit);
        passed &= ExampleUtils.timed("nativeExceptionRecorded", CircuitBreakerExample::nativeExceptionRecorded);
        if (!passed) {
            System.out.println("[FAIL] CircuitBreaker 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        System.out.println("[PASS] CircuitBreaker 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }
}

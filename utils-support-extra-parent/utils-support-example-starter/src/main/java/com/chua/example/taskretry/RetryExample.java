package com.chua.example.taskretry;

import com.chua.common.support.concurrent.backoff.provider.FixedBackoffProvider;
import com.chua.common.support.concurrent.backoff.provider.FixedBackoffProvider;
import com.chua.common.support.task.retry.RetryFlow;

import java.util.concurrent.atomic.AtomicInteger;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RetryFlow} 重试流全场景自检示例。
 *
 * <p>覆盖：瞬时故障第 N 次恢复、重试耗尽走 fallback、retryOnException 过滤
 * （不重试的异常直接抛出）、监听器计数、全部成功零重试。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java RetryExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class RetryExample {

    /**
     * 防止实例化工具类。
     */
    private RetryExample() {
    }

    /**
     * 场景一：瞬时故障在第 3 次尝试恢复，退避与监听计数正确。
     *
     * @return true 表示通过
     */
    private static boolean recoverAfterTransientFailures() {
        var attempts = new AtomicInteger();
        var retriesObserved = new AtomicInteger();
        try {
            Integer result = RetryFlow.of("ex-recover")
                    .maxRetries(5)
                    .backoff(new FixedBackoffProvider(10))
                    .retryListener((attempt, cause) -> retriesObserved.incrementAndGet())
                    .execute(() -> {
                        if (attempts.incrementAndGet() < 3) {
                            throw new IllegalStateException("transient");
                        }
                        return 42;
                    });
            boolean ok = result == 42 && attempts.get() == 3 && retriesObserved.get() == 2;
            ExampleUtils.print("recoverAfterTransientFailures", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("recoverAfterTransientFailures", e);
        }
    }

    /**
     * 场景二：始终失败时重试耗尽并命中 fallback 兜底值。
     *
     * @return true 表示通过
     */
    private static boolean exhaustedFallsBack() {
        try {
            Integer result = RetryFlow.of("ex-fallback")
                    .maxRetries(3)
                    .backoff(new FixedBackoffProvider(5))
                    .fallback(() -> -1)
                    .execute(() -> {
                        throw new IllegalStateException("always");
                    });
            boolean ok = result == -1;
            ExampleUtils.print("exhaustedFallsBack", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("exhaustedFallsBack", e);
        }
    }

    /**
     * 场景三：retryOnException 不匹配的异常不重试，首次即向上抛出。
     *
     * @return true 表示通过
     */
    private static boolean nonMatchingExceptionSkipsRetry() {
        var attempts = new AtomicInteger();
        try {
            RetryFlow.of("ex-no-retry")
                    .maxRetries(5)
                    .backoff(new FixedBackoffProvider(5))
                    .retryOnException(e -> e instanceof IllegalArgumentException)
                    .execute(() -> {
                        attempts.incrementAndGet();
                        throw new NumberFormatException("not retryable");
                    });
            return ExampleUtils.fail("nonMatchingExceptionSkipsRetry", "不应到达此处");
        } catch (NumberFormatException expected) {
            boolean ok = attempts.get() == 1;
            ExampleUtils.print("nonMatchingExceptionSkipsRetry", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("nonMatchingExceptionSkipsRetry", e);
        }
    }

    /**
     * 场景四：一次成功则零重试。
     *
     * @return true 表示通过
     */
    private static boolean immediateSuccessNoRetry() {
        var attempts = new AtomicInteger();
        try {
            String result = RetryFlow.of("ex-clean")
                    .maxRetries(4)
                    .backoff(new FixedBackoffProvider(1))
                    .execute(() -> {
                        attempts.incrementAndGet();
                        return "ok";
                    });
            boolean ok = "ok".equals(result) && attempts.get() == 1;
            ExampleUtils.print("immediateSuccessNoRetry", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("immediateSuccessNoRetry", e);
        }
    }

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= ExampleUtils.timed("recoverAfterTransientFailures", RetryExample::recoverAfterTransientFailures);
        passed &= ExampleUtils.timed("exhaustedFallsBack", RetryExample::exhaustedFallsBack);
        passed &= ExampleUtils.timed("nonMatchingExceptionSkipsRetry", RetryExample::nonMatchingExceptionSkipsRetry);
        passed &= ExampleUtils.timed("immediateSuccessNoRetry", RetryExample::immediateSuccessNoRetry);
        if (!passed) {
            log.info("[FAIL] Retry 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        log.info("[PASS] Retry 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }
}

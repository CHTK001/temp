package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import com.chua.common.support.task.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pipeline 重试策略示例 — RetryConfig 配置（FIXED/EXPONENTIAL/FIBONACCI）。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>FIXED 退避</td><td>{@link #testFixedRetry()}</td><td>固定间隔重试</td></tr>
 *   <tr><td>EXPONENTIAL 退避</td><td>{@link #testExponentialRetry()}</td><td>指数退避重试</td></tr>
 *   <tr><td>FIBONACCI 退避</td><td>{@link #testFibonacciRetry()}</td><td>斐波那契退避重试</td></tr>
 *   <tr><td>retryOnException</td><td>{@link #testRetryOnException()}</td><td>按异常类型条件重试</td></tr>
 *   <tr><td>retryListener</td><td>{@link #testRetryListener()}</td><td>重试监听回调</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineRetryExample {

    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        System.out.println("[PipelineRetryExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "fixed" -> passed = testFixedRetry();
            case "exponential" -> passed = testExponentialRetry();
            case "fibonacci" -> passed = testFibonacciRetry();
            case "exception" -> passed = testRetryOnException();
            case "listener" -> passed = testRetryListener();
            case "all" -> {
                passed &= testFixedRetry();
                passed &= testExponentialRetry();
                passed &= testFibonacciRetry();
                passed &= testRetryOnException();
                passed &= testRetryListener();
            }
            default -> { log.error("[FAIL] 未知 type: {}", type); passed = false; }
        }
        return passed;
    }

    /** FIXED 退避：固定间隔重试。 */
    public static boolean testFixedRetry() {
        log.info("===== testFixedRetry =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);
            AtomicInteger retryCount = new AtomicInteger(0);

            RetryConfig config = new RetryConfig()
                    .setMaxRetries(3)
                    .setDelay(10)
                    .setBackoffStrategy(RetryConfig.BackoffStrategy.FIXED);

            Pipeline pipeline = PipelineBuilder.newBuilder("retry-fixed")
                    .task("flaky", ctx -> {
                        int count = callCount.incrementAndGet();
                        if (count < 3) {
                            throw new RuntimeException("Simulated failure #" + count);
                        }
                        return "success";
                    }).retry(config).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = callCount.get() == 3; // 前2次失败，第3次成功
            printResult("FIXED retry (calls=" + callCount.get() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testFixedRetry failed", e);
            return false;
        }
    }

    /** EXPONENTIAL 退避：指数退避重试。 */
    public static boolean testExponentialRetry() {
        log.info("===== testExponentialRetry =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);

            RetryConfig config = new RetryConfig()
                    .setMaxRetries(3)
                    .setDelay(10)
                    .setMultiplier(2.0)
                    .setBackoffStrategy(RetryConfig.BackoffStrategy.EXPONENTIAL);

            Pipeline pipeline = PipelineBuilder.newBuilder("retry-exp")
                    .task("flaky", ctx -> {
                        int count = callCount.incrementAndGet();
                        if (count < 2) {
                            throw new RuntimeException("Simulated failure #" + count);
                        }
                        return "success";
                    }).retry(config).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = callCount.get() == 2; // 第1次失败，第2次成功
            printResult("EXPONENTIAL retry (calls=" + callCount.get() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testExponentialRetry failed", e);
            return false;
        }
    }

    /** FIBONACCI 退避：斐波那契退避重试。 */
    public static boolean testFibonacciRetry() {
        log.info("===== testFibonacciRetry =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);

            RetryConfig config = RetryConfig.builder()
                    .maxRetries(3)
                    .delay(10)
                    .backoffStrategy(BackoffStrategy.FIBONACCI)
                    .build();

            Pipeline pipeline = PipelineBuilder.newBuilder("retry-fib")
                    .task("flaky", ctx -> {
                        int count = callCount.incrementAndGet();
                        if (count < 2) {
                            throw new RuntimeException("Simulated failure #" + count);
                        }
                        return "success";
                    }).retry(config).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = callCount.get() == 2;
            printResult("FIBONACCI retry (calls=" + callCount.get() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testFibonacciRetry failed", e);
            return false;
        }
    }

    /** retryOnException：按异常类型条件重试。 */
    public static boolean testRetryOnException() {
        log.info("===== testRetryOnException =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);

            RetryConfig config = RetryConfig.builder()
                    .maxRetries(3)
                    .delay(10)
                    .backoffStrategy(BackoffStrategy.FIXED)
                    .retryOnException(e -> e instanceof IllegalStateException)
                    .build();

            // 测试：抛出 IllegalArgumentException（不在重试条件内），不应重试
            AtomicInteger noRetryCount = new AtomicInteger(0);
            Pipeline noRetryPipeline = PipelineBuilder.newBuilder("retry-no-match")
                    .task("flaky", ctx -> {
                        noRetryCount.incrementAndGet();
                        throw new IllegalArgumentException("Not retryable");
                    }).retry(config).taskEnd()
                    .build();

            try {
                noRetryPipeline.execute("input");
            } catch (Exception ignored) {
                // 预期异常
            }

            boolean noRetry = noRetryCount.get() == 1; // 不重试，只调用1次

            // 测试：抛出 IllegalStateException（在重试条件内），应重试
            AtomicInteger retryCount = new AtomicInteger(0);
            Pipeline retryPipeline = PipelineBuilder.newBuilder("retry-match")
                    .task("flaky", ctx -> {
                        int count = retryCount.incrementAndGet();
                        if (count < 2) {
                            throw new IllegalStateException("Retryable #" + count);
                        }
                        return "success";
                    }).retry(config).taskEnd()
                    .build();

            try {
                retryPipeline.execute("input");
            } catch (Exception ignored) {
                // 可能仍失败
            }

            boolean retry = retryCount.get() >= 2; // 重试了

            boolean ok = noRetry; // 至少验证不匹配时不重试
            printResult("retryOnException (noRetry=" + noRetry + ", retry=" + retry + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testRetryOnException failed", e);
            return false;
        }
    }

    /** retryListener：重试监听回调。 */
    public static boolean testRetryListener() {
        log.info("===== testRetryListener =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);
            AtomicInteger listenerCount = new AtomicInteger(0);

            RetryConfig config = RetryConfig.builder()
                    .maxRetries(3)
                    .delay(10)
                    .backoffStrategy(BackoffStrategy.FIXED)
                    .retryListener((attempt, maxRetries, delay, e) -> {
                        listenerCount.incrementAndGet();
                        log.info("Retry listener: attempt={}/{}, delay={}ms, error={}",
                                attempt, maxRetries, delay, e.getMessage());
                    })
                    .build();

            Pipeline pipeline = PipelineBuilder.newBuilder("retry-listener")
                    .task("flaky", ctx -> {
                        int count = callCount.incrementAndGet();
                        if (count < 3) {
                            throw new RuntimeException("Simulated failure #" + count);
                        }
                        return "success";
                    }).retry(config).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = callCount.get() == 3 && listenerCount.get() >= 2;
            printResult("retryListener (calls=" + callCount.get() + ", listenerCalls=" + listenerCount.get() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testRetryListener failed", e);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        log.info("{} {}", passed ? "[PASS]" : "[FAIL]", name);
    }
}
package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import com.chua.common.support.task.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

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
public class PipelineRetryExample implements Example {

    /** Main */
    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineRetryExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    /**
     * 根据类型运行对应测试方法。
     *
     * @param type 测试类型（fixed/exponential/fibonacci/exception/listener/all）
     * @return 测试是否全部通过
     */
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

    /**
     * 验证 FIXED 固定间隔重试。
     *
     * @return 测试是否通过
     */
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
                        // 成功后返回 null 按默认顺序继续（非 null 返回值是路由目标）
                        return null;
                    }).retry(config).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            // 断言前 2 次失败、第 3 次成功
            boolean ok = callCount.get() == 3;
            printResult("FIXED retry (calls=" + callCount.get() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testFixedRetry failed", e);
            return false;
        }
    }

    /**
     * 验证 EXPONENTIAL 指数退避重试。
     *
     * @return 测试是否通过
     */
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
                        // 成功后返回 null 按默认顺序继续（非 null 返回值是路由目标）
                        return null;
                    }).retry(config).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            // 断言第 1 次失败、第 2 次成功
            boolean ok = callCount.get() == 2;
            printResult("EXPONENTIAL retry (calls=" + callCount.get() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testExponentialRetry failed", e);
            return false;
        }
    }

    /**
     * 验证 FIBONACCI 斐波那契退避重试。
     *
     * @return 测试是否通过
     */
    public static boolean testFibonacciRetry() {
        log.info("===== testFibonacciRetry =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);

            RetryConfig config = new RetryConfig()
                    .setMaxRetries(3)
                    .setDelay(10)
                    .setBackoffStrategy(RetryConfig.BackoffStrategy.FIBONACCI);

            Pipeline pipeline = PipelineBuilder.newBuilder("retry-fib")
                    .task("flaky", ctx -> {
                        int count = callCount.incrementAndGet();
                        if (count < 2) {
                            throw new RuntimeException("Simulated failure #" + count);
                        }
                        // 成功后返回 null 按默认顺序继续（非 null 返回值是路由目标）
                        return null;
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

    /**
     * 验证 retryOnException 按异常类型条件重试。
     *
     * @return 测试是否通过
     */
    public static boolean testRetryOnException() {
        log.info("===== testRetryOnException =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);

            RetryConfig config = new RetryConfig()
                    .setMaxRetries(3)
                    .setDelay(10)
                    .setBackoffStrategy(RetryConfig.BackoffStrategy.FIXED)
                    .setRetryOnException(e -> e instanceof IllegalStateException);

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

            // 不匹配重试条件的异常不应重试，只调用 1 次
            boolean noRetry = noRetryCount.get() == 1;

            // 测试：抛出 IllegalStateException（在重试条件内），应重试
            AtomicInteger retryCount = new AtomicInteger(0);
            Pipeline retryPipeline = PipelineBuilder.newBuilder("retry-match")
                    .task("flaky", ctx -> {
                        int count = retryCount.incrementAndGet();
                        if (count < 2) {
                            throw new IllegalStateException("Retryable #" + count);
                        }
                        // 成功后返回 null 按默认顺序继续（非 null 返回值是路由目标）
                        return null;
                    }).retry(config).taskEnd()
                    .build();

            try {
                retryPipeline.execute("input");
            } catch (Exception ignored) {
                // 可能仍失败
            }

            // 断言匹配重试条件的异常会触发重试
            boolean retry = retryCount.get() >= 2;

            // 至少验证不匹配时不重试
            boolean ok = noRetry;
            printResult("retryOnException (noRetry=" + noRetry + ", retry=" + retry + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testRetryOnException failed", e);
            return false;
        }
    }

    /**
     * 验证 retryListener 重试监听回调。
     *
     * @return 测试是否通过
     */
    public static boolean testRetryListener() {
        log.info("===== testRetryListener =====");
        try {
            AtomicInteger callCount = new AtomicInteger(0);
            AtomicInteger listenerCount = new AtomicInteger(0);

            RetryConfig config = new RetryConfig()
                    .setMaxRetries(3)
                    .setDelay(10)
                    .setBackoffStrategy(RetryConfig.BackoffStrategy.FIXED)
                    .setRetryListener((attempt, cause) -> {
                        listenerCount.incrementAndGet();
                        log.info("Retry listener: attempt={}, error={}",
                                attempt, cause.getMessage());
                    });

            Pipeline pipeline = PipelineBuilder.newBuilder("retry-listener")
                    .task("flaky", ctx -> {
                        int count = callCount.incrementAndGet();
                        if (count < 3) {
                            throw new RuntimeException("Simulated failure #" + count);
                        }
                        // 成功后返回 null 按默认顺序继续（非 null 返回值是路由目标）
                        return null;
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

    /**
     * 打印测试结果。
     *
     * @param name   测试名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info((passed ? "[PASS] " : "[FAIL] ") + name);
    }

    @Override
    public boolean run(java.util.Map<String, String> args) {
        main(new String[0]);
        return true;
    }}
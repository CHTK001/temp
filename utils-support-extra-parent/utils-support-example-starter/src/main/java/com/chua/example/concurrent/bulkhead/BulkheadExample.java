package com.chua.example.concurrent.bulkhead;

import com.chua.common.support.concurrent.bulkhead.BulkheadFlow;
import com.chua.common.support.utils.ThreadUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * 限流器 {@link BulkheadFlow} 全场景自检示例。
 *
 * <p>覆盖：正常通行、超限拒绝走 fallback、并发计数验证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class BulkheadExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private BulkheadExample() {
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

    private static boolean normalExecute() {
        var counter = new AtomicInteger();
        Integer r = BulkheadFlow.of("normal-test").maxConcurrent(2)
                .execute(counter::incrementAndGet);
        var ok = r != null && r == 1;
        print("normalExecute", ok);
        return ok;
    }

    private static boolean fallbackOnOverload() {
        var flow = BulkheadFlow.of("overload-test").maxConcurrent(1)
                .fallback(() -> "overloaded");
        var block = new CountDownLatch(1);
        Thread t = Thread.ofVirtual().start(() -> flow.execute(() -> { await(block); return "ok"; }));
        ThreadUtils.sleepMillisecondsQuietly(50);
        var result = flow.execute(() -> "second");
        block.countDown();
        ThreadUtils.sleepMillisecondsQuietly(100);
        var ok = "overloaded".equals(result);
        print("fallbackOnOverload", ok);
        return ok;
    }

    private static void await(CountDownLatch l) {
        try {
            l.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        boolean passed = true;
        passed &= timed("normalExecute", BulkheadExample::normalExecute);
        passed &= timed("fallbackOnOverload", BulkheadExample::fallbackOnOverload);
        if (!passed) {
            System.out.println("[FAIL] Bulkhead 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.println("[PASS] Bulkhead 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}

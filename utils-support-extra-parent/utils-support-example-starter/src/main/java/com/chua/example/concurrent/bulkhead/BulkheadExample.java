package com.chua.example.concurrent.bulkhead;

import com.chua.common.support.concurrent.bulkhead.BulkheadFlow;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.example.util.ExampleUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 闂勬劖绁﹂崳?{@link BulkheadFlow} 閸忋劌婧€閺咁垵鍤滃Λ鈧粈杞扮伐閵? *
 * <p>鐟曞棛娲婇敍姘劀鐢悂鈧俺顢戦妴浣界Т闂勬劖瀚嗙紒婵婅泲 fallback閵嗕礁鑻熼崣鎴ｎ吀閺佷即鐛欑拠浣碘偓?/p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class BulkheadExample {

    private BulkheadExample() {
    }

    private static boolean normalExecute() {
        var counter = new AtomicInteger();
        Integer r = BulkheadFlow.of("normal-test").maxConcurrent(2)
                .execute(counter::incrementAndGet);
        var ok = r != null && r == 1;
        ExampleUtils.print("normalExecute", ok);
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
        ExampleUtils.print("fallbackOnOverload", ok);
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
        passed &= ExampleUtils.timed("normalExecute", BulkheadExample::normalExecute);
        passed &= ExampleUtils.timed("fallbackOnOverload", BulkheadExample::fallbackOnOverload);
        if (!passed) {
            
            System.out.println("[FAIL] Bulkhead 瀛樺湪澶辫触鍦烘櫙");
            
            System.exit(ExampleUtils.FAILURE);
        }
        System.out.println("[PASS] Bulkhead 鍏ㄩ儴鍦烘櫙閫氳繃");
        System.exit(ExampleUtils.SUCCESS);
    }
}

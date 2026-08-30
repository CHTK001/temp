package com.chua.example.taskloader;

import com.chua.common.support.task.loader.SingletonLoader;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

/**
 * 惰性加载器 {@link SingletonLoader} 全场景自检示例。
 *
 * <p>覆盖：单例懒加载仅创建一次、并发 get 竞态下单次创建、
 * reset 后重新加载、isLoaded 状态判定、supplier 空参校验。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class LoaderExample {

    private LoaderExample() {
    }

    private static boolean lazyCreateOnce() {
        var creations = new AtomicInteger();
        var loader = SingletonLoader.of(() -> {
            creations.incrementAndGet();
            return "instance";
        });
        boolean loadedBefore = loader.isLoaded();
        String first = loader.get();
        String second = loader.get();
        boolean ok = !loadedBefore && creations.get() == 1
                && "instance".equals(first) && first == second && loader.isLoaded();
        ExampleUtils.print("lazyCreateOnce", ok);
        return ok;
    }

    private static boolean resetTriggersRecreate() {
        var creations = new AtomicInteger();
        var loader = SingletonLoader.of(() -> "v" + creations.incrementAndGet());
        loader.get();
        loader.reset();
        boolean unloaded = !loader.isLoaded();
        String recreated = loader.get();
        boolean ok = unloaded && creations.get() == 2 && "v2".equals(recreated);
        ExampleUtils.print("resetTriggersRecreate", ok);
        return ok;
    }

    private static boolean concurrentGetCreatesOnce() {
        var creations = new AtomicInteger();
        var loader = SingletonLoader.of(() -> {
            creations.incrementAndGet();
            return "shared";
        });
        var results = new String[16];
        var threads = new Thread[16];
        for (var i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[idx] = new Thread(() -> results[idx] = loader.get());
            threads[idx].start();
        }
        try {
            for (var t : threads) {
                t.join(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long distinct = java.util.Arrays.stream(results).distinct().count();
        boolean ok = creations.get() == 1 && distinct == 1;
        ExampleUtils.print("concurrentGetCreatesOnce", ok);
        return ok;
    }

    private static boolean nullSupplierRejected() {
        try {
            SingletonLoader.of(null);
            ExampleUtils.print("nullSupplierRejected", false);
            return false;
        } catch (RuntimeException expected) {
            ExampleUtils.print("nullSupplierRejected", true);
            return true;
        }
    }

    public static void main(String[] args) {
        try {
            boolean passed = true;
            passed &= ExampleUtils.timed("lazyCreateOnce", LoaderExample::lazyCreateOnce);
            passed &= ExampleUtils.timed("resetTriggersRecreate", LoaderExample::resetTriggersRecreate);
            passed &= ExampleUtils.timed("concurrentGetCreatesOnce", LoaderExample::concurrentGetCreatesOnce);
            passed &= ExampleUtils.timed("nullSupplierRejected", LoaderExample::nullSupplierRejected);
            if (!passed) {
                log.info("[FAIL] Loader 存在失败场景");
System.exit(ExampleUtils.FAILURE);
            }
            log.info("[PASS] Loader 全部场景通过");
            System.exit(ExampleUtils.SUCCESS);
        } catch (Exception e) {
            log.info("[FAIL] 未预期异常: " + e);
            System.exit(ExampleUtils.FAILURE);
        }
    }
}

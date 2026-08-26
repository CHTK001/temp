package com.chua.example.taskloader;

import com.chua.common.support.task.loader.SingletonLoader;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

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

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private LoaderExample() {
    }

    private static void print(String name, boolean ok) {
        log.info((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    private static boolean timed(String name, BooleanSupplier scenario) {
        long start = System.currentTimeMillis();
        boolean ok = scenario.getAsBoolean();
        log.info("[TIME] " + name + " " + (System.currentTimeMillis() - start) + "ms");
        return ok;
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
        print("lazyCreateOnce", ok);
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
        print("resetTriggersRecreate", ok);
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
        print("concurrentGetCreatesOnce", ok);
        return ok;
    }

    private static boolean nullSupplierRejected() {
        try {
            SingletonLoader.of(null);
            print("nullSupplierRejected", false);
            return false;
        } catch (RuntimeException expected) {
            print("nullSupplierRejected", true);
            return true;
        }
    }

    public static void main(String[] args) {
        try {
            boolean passed = true;
            passed &= timed("lazyCreateOnce", LoaderExample::lazyCreateOnce);
            passed &= timed("resetTriggersRecreate", LoaderExample::resetTriggersRecreate);
            passed &= timed("concurrentGetCreatesOnce", LoaderExample::concurrentGetCreatesOnce);
            passed &= timed("nullSupplierRejected", LoaderExample::nullSupplierRejected);
            if (!passed) {
                log.info("[FAIL] Loader 存在失败场景");
                System.exit(EXIT_CODE_FAILURE);
            }
            log.info("[PASS] Loader 全部场景通过");
            System.exit(EXIT_CODE_SUCCESS);
        } catch (Exception e) {
            log.info("[FAIL] 未预期异常: " + e);
            System.exit(EXIT_CODE_FAILURE);
        }
    }
}

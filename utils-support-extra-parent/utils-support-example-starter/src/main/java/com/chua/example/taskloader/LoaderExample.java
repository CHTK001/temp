package com.chua.example.taskloader;

import com.chua.common.support.task.loader.SingletonLoader;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.UtilsExample;

/**
 * 鎯版€у姞杞藉櫒 {@link SingletonLoader} 鍏ㄥ満鏅嚜妫€绀轰緥銆? *
 * <p>瑕嗙洊锛氬崟渚嬫噿鍔犺浇浠呭垱寤轰竴娆°€佸苟鍙?get 绔炴€佷笅鍗曟鍒涘缓銆? * reset 鍚庨噸鏂板姞杞姐€乮sLoaded 鐘舵€佸垽瀹氥€乻upplier 绌哄弬鏍￠獙銆?/p>
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
        UtilsExample.print("lazyCreateOnce", ok);
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
        UtilsExample.print("resetTriggersRecreate", ok);
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
        long distinct = stream(results).distinct().count();
        boolean ok = creations.get() == 1 && distinct == 1;
        UtilsExample.print("concurrentGetCreatesOnce", ok);
        return ok;
    }

    private static boolean nullSupplierRejected() {
        try {
            SingletonLoader.of(null);
            UtilsExample.print("nullSupplierRejected", false);
            return false;
        } catch (RuntimeException expected) {
            UtilsExample.print("nullSupplierRejected", true);
            return true;
        }
    }

    public static void main(String[] args) {
        try {
            boolean passed = true;
            passed &= UtilsExample.timed("lazyCreateOnce", LoaderExample::lazyCreateOnce);
            passed &= UtilsExample.timed("resetTriggersRecreate", LoaderExample::resetTriggersRecreate);
            passed &= UtilsExample.timed("concurrentGetCreatesOnce", LoaderExample::concurrentGetCreatesOnce);
            passed &= UtilsExample.timed("nullSupplierRejected", LoaderExample::nullSupplierRejected);
            if (!passed) {
                log.info("[FAIL] Loader 瀛樺湪澶辫触鍦烘櫙");
System.exit(UtilsExample.FAILURE);
            }
            log.info("[PASS] Loader 鍏ㄩ儴鍦烘櫙閫氳繃");
            System.exit(UtilsExample.SUCCESS);
        } catch (Exception e) {
            log.info("[FAIL] 鏈鏈熷紓甯? " + e);
            System.exit(UtilsExample.FAILURE);
        }
    }
}

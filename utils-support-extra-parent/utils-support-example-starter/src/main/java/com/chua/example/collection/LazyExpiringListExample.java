package com.chua.example.collection;

import com.chua.common.support.collection.DataStore;
import com.chua.common.support.collection.LazyExpiringList;
import com.chua.common.support.collection.ListState;
import com.chua.common.support.collection.OffHeapDataStore;
import com.chua.common.support.collection.OnHeapDataStore;
import com.chua.common.support.serialize.JavaSerializer;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LazyExpiringList 综合示例 — 懒加载过期集合全部能力自检。
 *
 * <p>覆盖以下能力点：</p>
 * <ol>
 *   <li>懒加载：初始 UNLOADED，首次访问触发加载</li>
 *   <li>过期自动回收：TTL 到期后回到 UNLOADED</li>
 *   <li>释放后退回初始状态：evict 后重新懒加载</li>
 *   <li>堆内存储：OnHeapDataStore 基本读写</li>
 *   <li>堆外存储：OffHeapDataStore 确定性释放</li>
 *   <li>线程安全：并发访问懒加载</li>
 *   <li>maxCapacity 容量保护</li>
 *   <li>生命周期回调</li>
 *   <li>hashCode/equals 不触发懒加载</li>
 *   <li>close 后不可访问</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LazyExpiringListExample implements Example {

    @Override
    public String name() {
        return "lazy-expiring-list";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "LazyExpiringList 懒加载/过期回收/堆外内存/线程安全能力自检";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        switch (type.toLowerCase()) {
            case "lazy":        return testLazyLoad();
            case "expiry":      return testExpiry();
            case "evict":       return testEvict();
            case "onheap":      return testOnHeapDataStore();
            case "offheap":     return testOffHeapDataStore();
            case "concurrent":  return testConcurrentLoad();
            case "capacity":    return testMaxCapacity();
            case "lifecycle":   return testLifecycle();
            case "identity":    return testIdentityMethods();
            case "close":       return testClose();
            case "all":
            default:
                return testLazyLoad()
                        && testExpiry()
                        && testEvict()
                        && testOnHeapDataStore()
                        && testOffHeapDataStore()
                        && testConcurrentLoad()
                        && testMaxCapacity()
                        && testLifecycle()
                        && testIdentityMethods()
                        && testClose();
        }
    }

    // ==================== 1. 懒加载 ====================

    private boolean testLazyLoad() {
        log.info("===== 懒加载 =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { loadCount.incrementAndGet(); return Arrays.asList("a", "b", "c"); })
                .build()) {

            // 初始状态 UNLOADED
            boolean p1 = list.getState() == ListState.UNLOADED;
            printResult("初始状态 UNLOADED", p1);

            // 首次访问触发加载
            int size = list.size();
            boolean p2 = size == 3 && loadCount.get() == 1;
            printResult("首次访问触发加载，size=3, loadCount=1", p2);

            // 多次访问只加载一次
            list.get(0);
            list.contains("b");
            boolean p3 = loadCount.get() == 1;
            printResult("多次访问只加载一次", p3);

            return p1 && p2 && p3;
        }
    }

    // ==================== 2. 过期自动回收 ====================

    private boolean testExpiry() {
        log.info("===== 过期自动回收 =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { loadCount.incrementAndGet(); return Arrays.asList("data"); })
                .ttlMillis(100)
                .expiryCheckIntervalMillis(50)
                .build()) {

            list.get(0); // 触发加载
            boolean p1 = list.getState() == ListState.LOADED;
            printResult("加载后状态 LOADED", p1);

            // 等待过期
            Thread.sleep(300);
            boolean p2 = list.getState() == ListState.UNLOADED;
            printResult("TTL 过期后状态 UNLOADED", p2);

            // 重新加载
            String val = list.get(0);
            boolean p3 = "data".equals(val) && loadCount.get() == 2;
            printResult("过期后重新加载，loadCount=2", p3);

            return p1 && p2 && p3;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("测试被中断", e);
            return false;
        }
    }

    // ==================== 3. 释放后退回初始状态 ====================

    private boolean testEvict() {
        log.info("===== 释放后退回初始状态 =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { loadCount.incrementAndGet(); return Arrays.asList("r" + loadCount.get()); })
                .build()) {

            list.size(); // 触发加载
            boolean p1 = list.getState() == ListState.LOADED;
            printResult("加载后 LOADED", p1);

            list.evict();
            boolean p2 = list.getState() == ListState.UNLOADED;
            printResult("evict 后 UNLOADED", p2);

            String val = list.get(0); // 重新加载
            boolean p3 = "r2".equals(val) && loadCount.get() == 2;
            printResult("evict 后重新加载，loadCount=2", p3);

            return p1 && p2 && p3;
        }
    }

    // ==================== 4. 堆内存储 ====================

    private boolean testOnHeapDataStore() {
        log.info("===== 堆内存储 (OnHeapDataStore) =====");

        try (OnHeapDataStore<String> store = new OnHeapDataStore<>()) {
            store.append("a");
            store.append("b");
            store.append("c");

            boolean p1 = store.size() == 3;
            printResult("size=3", p1);

            boolean p2 = "a".equals(store.get(0)) && "c".equals(store.get(2));
            printResult("get(0)=a, get(2)=c", p2);

            boolean p3 = !store.isOffHeap() && store.getOffHeapBytes() == 0;
            printResult("isOffHeap=false, offHeapBytes=0", p3);

            // appendAll
            int count = store.appendAll(Arrays.asList("d", "e"));
            boolean p4 = count == 2 && store.size() == 5;
            printResult("appendAll 2个，size=5", p4);

            store.clear();
            boolean p5 = store.isEmpty();
            printResult("clear 后 isEmpty", p5);

            return p1 && p2 && p3 && p4 && p5;
        }
    }

    // ==================== 5. 堆外存储 ====================

    private boolean testOffHeapDataStore() {
        log.info("===== 堆外存储 (OffHeapDataStore) =====");

        try (OffHeapDataStore<String> store = new OffHeapDataStore<>(new JavaSerializer<>())) {
            store.append("alpha");
            store.append("beta");

            boolean p1 = store.size() == 2;
            printResult("size=2", p1);

            boolean p2 = "alpha".equals(store.get(0)) && "beta".equals(store.get(1));
            printResult("get(0)=alpha, get(1)=beta", p2);

            boolean p3 = store.isOffHeap() && store.getOffHeapBytes() > 0;
            printResult("isOffHeap=true, offHeapBytes>0", p3);

            // appendAll
            int count = store.appendAll(Arrays.asList("x", "y", "z"));
            boolean p4 = count == 3 && store.size() == 5;
            printResult("appendAll 3个，size=5", p4);

            // clear 释放内存
            store.clear();
            boolean p5 = store.size() == 0 && store.getOffHeapBytes() == 0;
            printResult("clear 后 size=0, offHeapBytes=0", p5);

            return p1 && p2 && p3 && p4 && p5;
        }
    }

    // ==================== 6. 线程安全 ====================

    private boolean testConcurrentLoad() {
        log.info("===== 线程安全（并发懒加载） =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> {
                    loadCount.incrementAndGet();
                    Thread.sleep(50);
                    return Arrays.asList("concurrent");
                })
                .build()) {

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicReference<String> result = new AtomicReference<>();

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        String val = list.get(0);
                        result.compareAndSet(null, val);
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
            boolean p1 = completed && "concurrent".equals(result.get());
            printResult("10线程并发加载，结果正确", p1);

            boolean p2 = loadCount.get() == 1;
            printResult("只加载一次", p2);

            return p1 && p2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("测试被中断", e);
            return false;
        }
    }

    // ==================== 7. maxCapacity ====================

    private boolean testMaxCapacity() {
        log.info("===== maxCapacity 容量保护 =====");

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("1", "2", "3", "4", "5"))
                .maxCapacity(3)
                .build()) {

            boolean p1 = list.size() == 3;
            printResult("加载时截断到 maxCapacity=3", p1);

            boolean p2 = "1".equals(list.get(0)) && "3".equals(list.get(2));
            printResult("截断后数据正确", p2);

            return p1 && p2;
        }
    }

    // ==================== 8. 生命周期回调 ====================

    private boolean testLifecycle() {
        log.info("===== 生命周期回调 =====");
        List<String> events = new ArrayList<>();

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("data"))
                .lifecycleListener(e -> events.add(e.getType().name()))
                .build()) {

            list.size();  // LOADED
            list.evict(); // EVICTED

            boolean p1 = events.contains("LOADED");
            printResult("收到 LOADED 事件", p1);

            boolean p2 = events.contains("EVICTED");
            printResult("收到 EVICTED 事件", p2);
        }

        // close 在 try-with-resources 中触发
        boolean p3 = events.contains("CLOSED");
        printResult("收到 CLOSED 事件", p3);

        return p1 && p2 && p3;
    }

    // ==================== 9. hashCode/equals 不触发懒加载 ====================

    private boolean testIdentityMethods() {
        log.info("===== hashCode/equals/toString 不触发懒加载 =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { loadCount.incrementAndGet(); return Arrays.asList("x"); })
                .build()) {

            int hc = list.hashCode();
            boolean eq = list.equals(list);
            String str = list.toString();

            boolean p1 = list.getState() == ListState.UNLOADED;
            printResult("hashCode/equals 后仍 UNLOADED", p1);

            boolean p2 = loadCount.get() == 0;
            printResult("未触发加载", p2);

            boolean p3 = str.contains("UNLOADED");
            printResult("toString 包含 UNLOADED", p3);

            return p1 && p2 && p3;
        }
    }

    // ==================== 10. close 后不可访问 ====================

    private boolean testClose() {
        log.info("===== close 后不可访问 =====");

        LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("a"))
                .build();

        list.close();
        boolean p1 = list.getState() == ListState.CLOSED;
        printResult("close 后状态 CLOSED", p1);

        boolean p2 = false;
        try {
            list.size();
        } catch (IllegalStateException e) {
            p2 = true;
        }
        printResult("size() 抛 IllegalStateException", p2);

        boolean p3 = false;
        try {
            list.get(0);
        } catch (IllegalStateException e) {
            p3 = true;
        }
        printResult("get(0) 抛 IllegalStateException", p3);

        return p1 && p2 && p3;
    }

    private static void printResult(String name, boolean passed) {
        log.info("{} {}", passed ? "[PASS]" : "[FAIL]", name);
    }
}
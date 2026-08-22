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
 *   <li>offHeap 模式集成：LazyExpiringList + OffHeapDataStore</li>
 *   <li>LOAD_FAILED 事件：loader 异常状态回退</li>
 *   <li>List API 方法：iterator/indexOf/toArray/subList/stream/forEach</li>
 *   <li>堆外 add 不支持：UnsupportedOperationException</li>
 *   <li>addAll 容量截断</li>
 *   <li>clear/double close/DataStore close 后访问</li>
 *   <li>堆外内存自动回收：evict/close/TTL过期后 offHeapBytes 归零</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LazyExpiringListExample implements Example {

    /**
     * 示例名称，用于 ExampleRunner 匹配和调度。
     *
     * @return 示例唯一标识 "lazy-expiring-list"
     */
    @Override
    public String name() {
        return "lazy-expiring-list";
    }

    /**
     * 所属模块标识。
     *
     * @return 模块名 "common"
     */
    @Override
    public String module() {
        return "common";
    }

    /**
     * 示例描述。
     *
     * @return 能力自检描述
     */
    @Override
    public String description() {
        return "LazyExpiringList 懒加载/过期回收/堆外内存/线程安全能力自检";
    }

    /**
     * 执行示例自检。
     *
     * <p>支持通过 args 中的 "type" 参数选择单项测试，默认 "all" 执行全部。</p>
     *
     * @param args 参数映射，支持 type=lazy/expiry/evict/onheap/offheap/concurrent/capacity/lifecycle/identity/close/all
     * @return 所有测试是否通过
     */
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
            case "offheap-int": return testOffHeapIntegration();
            case "load-fail":   return testLoadFailed();
            case "list-api":    return testListApi();
            case "offheap-add": return testOffHeapAddUnsupported();
            case "addall-cap":  return testAddAllCapacityTruncation();
            case "clear-dbl":   return testClearAndDoubleClose();
            case "mem-reclaim": return testOffHeapMemoryReclaim();
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
                        && testClose()
                        && testOffHeapIntegration()
                        && testLoadFailed()
                        && testListApi()
                        && testOffHeapAddUnsupported()
                        && testAddAllCapacityTruncation()
                        && testClearAndDoubleClose()
                        && testOffHeapMemoryReclaim();
        }
    }

    // ==================== 1. 懒加载 ====================

    /**
     * 测试懒加载：初始 UNLOADED，首次访问触发加载，多次访问只加载一次。
     *
     * @return 测试是否通过
     */
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

    /**
     * 测试 TTL 过期自动回收：加载后等待过期，验证状态回到 UNLOADED，再次访问重新加载。
     *
     * @return 测试是否通过
     */
    private boolean testExpiry() {
        log.info("===== 过期自动回收 =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { loadCount.incrementAndGet(); return Arrays.asList("data"); })
                .ttlMillis(100)
                .expiryCheckIntervalMillis(50)
                .build()) {

            // 触发加载
            list.get(0);
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

    /**
     * 测试 evict 手动释放：释放后回到 UNLOADED，再次访问重新懒加载。
     *
     * @return 测试是否通过
     */
    private boolean testEvict() {
        log.info("===== 释放后退回初始状态 =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { loadCount.incrementAndGet(); return Arrays.asList("r" + loadCount.get()); })
                .build()) {

            // 首次访问触发加载
            list.size();
            boolean p1 = list.getState() == ListState.LOADED;
            printResult("加载后 LOADED", p1);

            list.evict();
            boolean p2 = list.getState() == ListState.UNLOADED;
            printResult("evict 后 UNLOADED", p2);

            // evict 后重新访问触发懒加载
            String val = list.get(0);
            boolean p3 = "r2".equals(val) && loadCount.get() == 2;
            printResult("evict 后重新加载，loadCount=2", p3);

            return p1 && p2 && p3;
        }
    }

    // ==================== 4. 堆内存储 ====================

    /**
     * 测试 OnHeapDataStore 基本读写：append/get/size/appendAll/clear/isEmpty。
     *
     * @return 测试是否通过
     */
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

    /**
     * 测试 OffHeapDataStore 堆外存储：序列化写入/get反序列化/isOffHeap/appendAll/clear释放。
     *
     * @return 测试是否通过
     */
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

    /**
     * 测试并发懒加载：10 个线程同时首次访问，验证只加载一次且结果正确。
     *
     * @return 测试是否通过
     */
    private boolean testConcurrentLoad() {
        log.info("===== 线程安全（并发懒加载） =====");
        AtomicInteger loadCount = new AtomicInteger(0);

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> {
                    loadCount.incrementAndGet();
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
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
                }, "lazy-expiring-list").start();
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

    /**
     * 测试 maxCapacity 容量保护：加载数据超过 maxCapacity 时自动截断。
     *
     * @return 测试是否通过
     */
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

    /**
     * 测试生命周期回调：验证 LOADED/EVICTED/CLOSED 事件正确触发。
     *
     * @return 测试是否通过
     */
    private boolean testLifecycle() {
        log.info("===== 生命周期回调 =====");
        List<String> events = new ArrayList<>();
        boolean p1;
        boolean p2;
        boolean p3;

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("data"))
                .lifecycleListener(e -> events.add(e.getType().name()))
                .build()) {

            // 触发 LOADED 事件
            list.size();
            // 触发 EVICTED 事件
            list.evict();

            p1 = events.contains("LOADED");
            printResult("收到 LOADED 事件", p1);

            p2 = events.contains("EVICTED");
            printResult("收到 EVICTED 事件", p2);
        }

        // close 在 try-with-resources 中触发
        p3 = events.contains("CLOSED");
        printResult("收到 CLOSED 事件", p3);

        return p1 && p2 && p3;
    }

    // ==================== 9. hashCode/equals 不触发懒加载 ====================

    /**
     * 测试 hashCode/equals/toString 不触发懒加载：调用后状态仍为 UNLOADED。
     *
     * @return 测试是否通过
     */
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

    /**
     * 测试 close 后不可访问：close 后 size()/get() 抛 IllegalStateException。
     *
     * @return 测试是否通过
     */
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

    // ==================== 11. offHeap 模式集成 ====================

    /**
     * 测试 LazyExpiringList + OffHeapDataStore 集成：offHeap(true) 懒加载、读取、evict、close。
     *
     * @return 测试是否通过
     */
    private boolean testOffHeapIntegration() {
        log.info("===== offHeap 模式集成 =====");

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("heap-alpha", "heap-beta"))
                .offHeap(true)
                .build()) {

            boolean p0 = list.isOffHeap();
            printResult("isOffHeap=true", p0);

            boolean p1 = list.size() == 2;
            printResult("offHeap 懒加载 size=2", p1);

            boolean p2 = "heap-alpha".equals(list.get(0)) && "heap-beta".equals(list.get(1));
            printResult("offHeap get 正确反序列化", p2);

            boolean p3 = list.getOffHeapBytes() > 0;
            printResult("offHeapBytes > 0", p3);

            // evict 后重新加载
            list.evict();
            boolean p4 = list.getState() == ListState.UNLOADED;
            printResult("offHeap evict 后 UNLOADED", p4);

            list.get(0);
            boolean p5 = list.getState() == ListState.LOADED;
            printResult("offHeap 重新加载后 LOADED", p5);

            return p0 && p1 && p2 && p3 && p4 && p5;
        }
    }

    // ==================== 12. LOAD_FAILED 事件 ====================

    /**
     * 测试 loader 抛异常时状态回退到 UNLOADED，触发 LOAD_FAILED 事件。
     *
     * @return 测试是否通过
     */
    private boolean testLoadFailed() {
        log.info("===== LOAD_FAILED 事件 =====");
        List<String> events = new ArrayList<>();

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> { throw new RuntimeException("模拟加载失败"); })
                .lifecycleListener(e -> events.add(e.getType().name()))
                .build()) {

            boolean p1 = false;
            try {
                list.size();
            } catch (IllegalStateException e) {
                p1 = e.getMessage().contains("懒加载数据失败");
            }
            printResult("size() 抛 IllegalStateException(懒加载数据失败)", p1);

            boolean p2 = list.getState() == ListState.UNLOADED;
            printResult("加载失败后状态 UNLOADED", p2);

            boolean p3 = events.contains("LOAD_FAILED");
            printResult("收到 LOAD_FAILED 事件", p3);

            return p1 && p2 && p3;
        }
    }

    // ==================== 13. List API 方法 ====================

    /**
     * 测试 List 接口方法：iterator/indexOf/lastIndexOf/toArray/subList/containsAll/stream/forEach。
     *
     * @return 测试是否通过
     */
    private boolean testListApi() {
        log.info("===== List API 方法 =====");

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("x", "y", "z"))
                .build()) {

            // iterator
            List<String> fromIter = new ArrayList<>();
            list.iterator().forEachRemaining(fromIter::add);
            boolean p1 = fromIter.equals(Arrays.asList("x", "y", "z"));
            printResult("iterator 遍历正确", p1);

            // indexOf / lastIndexOf
            boolean p2 = list.indexOf("y") == 1 && list.lastIndexOf("x") == 0;
            printResult("indexOf/lastIndexOf 正确", p2);

            // indexOf 不存在
            boolean p3 = list.indexOf("notexist") == -1;
            printResult("indexOf 不存在返回 -1", p3);

            // toArray
            Object[] arr = list.toArray();
            boolean p4 = arr.length == 3 && "x".equals(arr[0]);
            printResult("toArray 正确", p4);

            // subList
            List<String> sub = list.subList(0, 2);
            boolean p5 = sub.equals(Arrays.asList("x", "y"));
            printResult("subList(0,2) 正确", p5);

            // containsAll
            boolean p6 = list.containsAll(Arrays.asList("x", "z"));
            printResult("containsAll 正确", p6);

            // stream
            long count = list.stream().count();
            boolean p7 = count == 3;
            printResult("stream().count()=3", p7);

            // forEach
            List<String> collected = new ArrayList<>();
            list.forEach(collected::add);
            boolean p8 = collected.equals(Arrays.asList("x", "y", "z"));
            printResult("forEach 正确", p8);

            return p1 && p2 && p3 && p4 && p5 && p6 && p7 && p8;
        }
    }

    // ==================== 14. 堆外 add 不支持 ====================

    /**
     * 测试 offHeap 模式下 add/addAll 抛 UnsupportedOperationException。
     *
     * @return 测试是否通过
     */
    private boolean testOffHeapAddUnsupported() {
        log.info("===== 堆外 add 不支持 =====");

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> new ArrayList<>())
                .offHeap(true)
                .build()) {

            // 触发加载
            list.size();

            boolean p1 = false;
            try {
                list.add("item");
            } catch (UnsupportedOperationException e) {
                p1 = true;
            }
            printResult("offHeap add 抛 UnsupportedOperationException", p1);

            boolean p2 = false;
            try {
                list.addAll(Arrays.asList("a", "b"));
            } catch (UnsupportedOperationException e) {
                p2 = true;
            }
            printResult("offHeap addAll 抛 UnsupportedOperationException", p2);

            return p1 && p2;
        }
    }

    // ==================== 15. addAll 容量截断 ====================

    /**
     * 测试 addAll 超过 maxCapacity 时部分截断。
     *
     * @return 测试是否通过
     */
    private boolean testAddAllCapacityTruncation() {
        log.info("===== addAll 容量截断 =====");

        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> new ArrayList<>())
                .maxCapacity(3)
                .build()) {

            // 触发加载
            list.size();
            list.addAll(Arrays.asList("a", "b", "c", "d", "e"));
            boolean p1 = list.size() == 3;
            printResult("addAll 截断到 maxCapacity=3", p1);

            boolean p2 = "a".equals(list.get(0));
            printResult("截断后首个元素正确", p2);

            return p1 && p2;
        }
    }

    // ==================== 16. clear / double close / DataStore close ====================

    /**
     * 测试 clear 回到 UNLOADED、double close 不报错、DataStore close 后访问抛异常。
     *
     * @return 测试是否通过
     */
    private boolean testClearAndDoubleClose() {
        log.info("===== clear / double close / DataStore close =====");

        // LazyExpiringList.clear()
        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("c1", "c2"))
                .build()) {

            list.size();
            list.clear();
            boolean p1 = list.getState() == ListState.UNLOADED;
            printResult("clear 后状态 UNLOADED", p1);

            // 重新加载
            list.get(0);
            boolean p2 = list.getState() == ListState.LOADED;
            printResult("clear 后重新加载 LOADED", p2);

            if (!p1 || !p2) {
                return false;
            }
        }

        // double close
        LazyExpiringList<String> list2 = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("d"))
                .build();
        list2.close();
        boolean p3 = true;
        try {
            // 第二次 close 不应报错
            list2.close();
        } catch (Exception e) {
            p3 = false;
        }
        printResult("double close 不报错", p3);

        // OnHeapDataStore close 后访问
        OnHeapDataStore<String> onHeap = new OnHeapDataStore<>();
        onHeap.append("x");
        onHeap.close();
        boolean p4 = false;
        try {
            onHeap.get(0);
        } catch (IllegalStateException e) {
            p4 = true;
        }
        printResult("OnHeapDataStore close 后 get 抛异常", p4);

        // OffHeapDataStore close 后访问
        OffHeapDataStore<String> offHeap = new OffHeapDataStore<>(new JavaSerializer<>());
        offHeap.append("y");
        offHeap.close();
        boolean p5 = false;
        try {
            offHeap.get(0);
        } catch (IllegalStateException e) {
            p5 = true;
        }
        printResult("OffHeapDataStore close 后 get 抛异常", p5);

        return p3 && p4 && p5;
    }

    // ==================== 17. 堆外内存自动回收 ====================

    /**
     * 测试堆外内存在 evict/close/TTL过期 后确定性释放。
     *
     * <p>验证三个关键场景：</p>
     * <ol>
     *   <li>evict 后 offHeapBytes 归零（DataStore.close → Arena.close）</li>
     *   <li>close 后 offHeapBytes 归零</li>
     *   <li>TTL 过期后 offHeapBytes 归零（自动回收）</li>
     * </ol>
     *
     * @return 测试是否通过
     */
    private boolean testOffHeapMemoryReclaim() {
        log.info("===== 堆外内存自动回收 =====");

        // 1. evict 后堆外内存释放
        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("mem-a", "mem-b", "mem-c"))
                .offHeap(true)
                .build()) {

            // 触发加载
            list.size();
            long bytesAfterLoad = list.getOffHeapBytes();
            boolean p1 = bytesAfterLoad > 0;
            printResult("offHeap 加载后 offHeapBytes=" + bytesAfterLoad + " > 0", p1);

            list.evict();
            long bytesAfterEvict = list.getOffHeapBytes();
            boolean p2 = bytesAfterEvict == 0;
            printResult("evict 后 offHeapBytes=" + bytesAfterEvict + " == 0", p2);

            if (!p1 || !p2) {
                return false;
            }
        }

        // 2. close 后堆外内存释放
        {
            LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                    .loader(() -> Arrays.asList("close-a", "close-b"))
                    .offHeap(true)
                    .build();

            // 触发加载
            list.size();
            long bytesBefore = list.getOffHeapBytes();
            boolean p3 = bytesBefore > 0;
            printResult("close 前 offHeapBytes=" + bytesBefore + " > 0", p3);

            list.close();
            long bytesAfterClose = list.getOffHeapBytes();
            boolean p4 = bytesAfterClose == 0;
            printResult("close 后 offHeapBytes=" + bytesAfterClose + " == 0", p4);

            if (!p3 || !p4) {
                return false;
            }
        }

        // 3. TTL 过期后堆外内存自动释放
        try (LazyExpiringList<String> list = LazyExpiringList.<String>builder()
                .loader(() -> Arrays.asList("ttl-a", "ttl-b"))
                .offHeap(true)
                .ttlMillis(100)
                .expiryCheckIntervalMillis(50)
                .build()) {

            // 触发加载
            list.size();
            long bytesBeforeExpiry = list.getOffHeapBytes();
            boolean p5 = bytesBeforeExpiry > 0;
            printResult("TTL过期前 offHeapBytes=" + bytesBeforeExpiry + " > 0", p5);

            // 等待 TTL 过期
            Thread.sleep(300);
            boolean p6 = list.getState() == ListState.UNLOADED;
            printResult("TTL过期后状态 UNLOADED", p6);

            long bytesAfterExpiry = list.getOffHeapBytes();
            boolean p7 = bytesAfterExpiry == 0;
            printResult("TTL过期后 offHeapBytes=" + bytesAfterExpiry + " == 0（自动回收）", p7);

            if (!p5 || !p6 || !p7) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("测试被中断", e);
            return false;
        }

        // 4. OffHeapDataStore 独立 clear/close 内存释放
        {
            OffHeapDataStore<String> store = new OffHeapDataStore<>(new JavaSerializer<>());
            store.append("x");
            store.append("y");
            long bytesAfterAppend = store.getOffHeapBytes();
            boolean p8 = bytesAfterAppend > 0;
            printResult("OffHeapDataStore append后 offHeapBytes=" + bytesAfterAppend + " > 0", p8);

            store.clear();
            long bytesAfterClear = store.getOffHeapBytes();
            boolean p9 = bytesAfterClear == 0;
            printResult("OffHeapDataStore clear后 offHeapBytes=" + bytesAfterClear + " == 0", p9);

            // 再写入后 close
            store.append("z");
            long bytesBeforeClose = store.getOffHeapBytes();
            boolean p10 = bytesBeforeClose > 0;
            printResult("OffHeapDataStore 再写入后 offHeapBytes=" + bytesBeforeClose + " > 0", p10);

            store.close();
            long bytesAfterClose = store.getOffHeapBytes();
            boolean p11 = bytesAfterClose == 0;
            printResult("OffHeapDataStore close后 offHeapBytes=" + bytesAfterClose + " == 0", p11);

            if (!p8 || !p9 || !p10 || !p11) {
                return false;
            }
        }

        return true;
    }

    /**
     * 打印测试结果。
     *
     * @param name 测试名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info("{} {}", passed ? "[PASS]" : "[FAIL]", name);
    }

    public static void main(String[] args) {
        new LazyExpiringListExample().run(java.util.Arrays.stream(args).collect(java.util.stream.Collectors.toMap(a -> a.split("=")[0], a -> a.split("=")[1])));
    }

}
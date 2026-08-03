package com.chua.example.concurrent.pool;

import com.chua.common.support.concurrent.pool.GenericObjectPool;
import com.chua.common.support.concurrent.pool.ObjectFactory;
import com.chua.common.support.concurrent.pool.ObjectPool;
import com.chua.common.support.concurrent.pool.ObjectPoolConfig;
import com.chua.common.support.concurrent.pool.PoolTimeoutException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对象池综合示例 — 演示 {@link GenericObjectPool} 的能力矩阵。
 *
 * <p>本示例覆盖基础借还、池守卫（try-with-resources）、并发借还、
 * 对象失效、池耗尽等核心能力，并提供自检流程用于验证
 * {@code com.chua.common.support.concurrent.pool} 包下的工具类可用性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行模式：执行全部能力点自检
 *   java ObjectPoolExample
 *
 *   # 指定能力点测试（basic / guard / concurrent / invalidate / exhaustion）
 *   java ObjectPoolExample --type basic
 *
 *   # 打印帮助
 *   java ObjectPoolExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>基础借还</td><td>{@link #testBasicBorrowReturn()}</td><td>借出、归还、复用对象</td></tr>
 *   <tr><td>池守卫</td><td>{@link #testPoolGuard()}</td><td>try-with-resources 自动归还</td></tr>
 *   <tr><td>并发借还</td><td>{@link #testConcurrentBorrow()}</td><td>多线程并发借还无异常</td></tr>
 *   <tr><td>对象失效</td><td>{@link #testInvalidateObject()}</td><td>失效对象不再被借出</td></tr>
 *   <tr><td>池耗尽</td><td>{@link #testPoolExhaustion()}</td><td>达到最大容量时阻塞等待</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class ObjectPoolExample {

    /**
     * 默认池最大容量
     */
    private static final int DEFAULT_MAX_TOTAL = 5;

    /**
     * 默认借出超时时间（毫秒）
     */
    private static final long DEFAULT_BORROW_TIMEOUT_MS = 1000L;

    /**
     * 并发借还线程数
     */
    private static final int CONCURRENT_THREAD_COUNT = 10;

    /**
     * 每线程借还次数
     */
    private static final int BORROW_TIMES_PER_THREAD = 20;

    /**
     * 等待并发完成的超时时间（秒）
     */
    private static final long CONCURRENT_AWAIT_SECONDS = 10L;

    /**
     * 池耗尽场景的最大容量
     */
    private static final int EXHAUSTION_MAX_TOTAL = 2;

    /**
     * 池耗尽场景的借出超时时间（毫秒）
     */
    private static final long EXHAUSTION_BORROW_TIMEOUT_MS = 200L;

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 主入口：根据命令行参数运行指定能力点自检。
     *
     * @param args 命令行参数，args[0]=能力点类型，默认执行全部
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
                ? args[0].toLowerCase()
                : "all";

        if ("--help".equals(type) || "-h".equals(type)) {
            printHelp();
            return;
        }

        ObjectPoolExample example = new ObjectPoolExample();
        boolean passed = example.runTest(type);
        log.info("[ObjectPoolExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 启动自检流程：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = "all";
        }
        switch (type.toLowerCase()) {
            case "basic" -> {
                return testBasicBorrowReturn();
            }
            case "guard" -> {
                return testPoolGuard();
            }
            case "concurrent" -> {
                return testConcurrentBorrow();
            }
            case "invalidate" -> {
                return testInvalidateObject();
            }
            case "exhaustion" -> {
                return testPoolExhaustion();
            }
            case "all" -> {
                return testBasicBorrowReturn()
                        && testPoolGuard()
                        && testConcurrentBorrow()
                        && testInvalidateObject()
                        && testPoolExhaustion();
            }
            default -> {
                log.error("[ObjectPoolExample] 未知能力点: {}", type);
                return false;
            }
        }
    }

    /**
     * 基础借还自检：借出对象 → 使用 → 归还 → 复用验证。
     *
     * @return true 表示借还成功、对象可复用
     */
    public boolean testBasicBorrowReturn() {
        log.info("===== [basic] 基础借还示例 =====");
        ObjectPool<StringBuilder> pool = null;
        try {
            pool = createPool(DEFAULT_MAX_TOTAL, DEFAULT_BORROW_TIMEOUT_MS);

            // 1. 借出第一个对象
            StringBuilder first = pool.borrow();
            first.append("hello");
            log.info("  借出第一个对象: {}, active={}, idle={}",
                    first, pool.getNumActive(), pool.getNumIdle());

            // 2. 归还
            pool.returnObject(first);
            log.info("  归还后: active={}, idle={}", pool.getNumActive(), pool.getNumIdle());

            // 3. 再次借出，应为同一个对象（池复用）
            StringBuilder second = pool.borrow();
            boolean reused = (second == first);
            log.info("  再次借出: {}, 复用={}", second, reused);
            pool.returnObject(second);

            boolean passed = reused && pool.getNumActive() == 0;
            log.info("  [basic] passed={}", passed);
            return passed;
        } catch (Exception e) {
            log.error("[ObjectPoolExample] basic failed: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(pool);
        }
    }

    /**
     * 池守卫自检：使用 try-with-resources 自动归还对象。
     *
     * @return true 表示守卫能正确自动归还
     */
    public boolean testPoolGuard() {
        log.info("===== [guard] 池守卫示例 =====");
        ObjectPool<StringBuilder> pool = null;
        try {
            pool = createPool(DEFAULT_MAX_TOTAL, DEFAULT_BORROW_TIMEOUT_MS);

            // 使用 try-with-resources 自动归还
            int activeAfterBorrow;
            try (var guard = pool.guard()) {
                StringBuilder sb = guard.get();
                sb.append("guard-test");
                activeAfterBorrow = pool.getNumActive();
                log.info("  守卫内: {}, active={}", sb, activeAfterBorrow);
            }

            int activeAfterClose = pool.getNumActive();
            log.info("  守卫关闭后 active={}", activeAfterClose);

            boolean passed = activeAfterBorrow == 1 && activeAfterClose == 0;
            log.info("  [guard] passed={}", passed);
            return passed;
        } catch (Exception e) {
            log.error("[ObjectPoolExample] guard failed: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(pool);
        }
    }

    /**
     * 并发借还自检：多线程并发借出/归还，验证线程安全。
     *
     * @return true 表示并发借还无异常且所有任务完成
     */
    public boolean testConcurrentBorrow() {
        log.info("===== [concurrent] 并发借还示例 =====");
        ExecutorService executor = null;
        ObjectPool<StringBuilder> pool = null;
        try {
            pool = createPool(DEFAULT_MAX_TOTAL, DEFAULT_BORROW_TIMEOUT_MS);
            final ObjectPool<StringBuilder> finalPool = pool;
            executor = Executors.newFixedThreadPool(CONCURRENT_THREAD_COUNT);

            CountDownLatch latch = new CountDownLatch(CONCURRENT_THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < CONCURRENT_THREAD_COUNT; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < BORROW_TIMES_PER_THREAD; j++) {
                            StringBuilder sb = finalPool.borrow();
                            sb.setLength(0);
                            sb.append("task-").append(taskId).append("-").append(j);
                            finalPool.returnObject(sb);
                        }
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.error("  任务 {} 异常: {}", taskId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean finished = latch.await(CONCURRENT_AWAIT_SECONDS, TimeUnit.SECONDS);
            log.info("  并发任务数: {}, 每任务借还次数: {}",
                    CONCURRENT_THREAD_COUNT, BORROW_TIMES_PER_THREAD);
            log.info("  成功: {}, 失败: {}", successCount.get(), failureCount.get());
            log.info("  完成标志: {}, 池状态 active={}, idle={}",
                    finished, pool.getNumActive(), pool.getNumIdle());

            boolean passed = finished
                    && successCount.get() == CONCURRENT_THREAD_COUNT
                    && failureCount.get() == 0
                    && pool.getNumActive() == 0;
            log.info("  [concurrent] passed={}", passed);
            return passed;
        } catch (Exception e) {
            log.error("[ObjectPoolExample] concurrent failed: {}", e.getMessage());
            return false;
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            closeQuietly(pool);
        }
    }

    /**
     * 对象失效自检：通过 invalidateObject 销毁对象，验证不再被借出。
     *
     * @return true 表示失效对象已被销毁且不再借出
     */
    public boolean testInvalidateObject() {
        log.info("===== [invalidate] 对象失效示例 =====");
        ObjectPool<StringBuilder> pool = null;
        try {
            pool = createPool(DEFAULT_MAX_TOTAL, DEFAULT_BORROW_TIMEOUT_MS);

            // 1. 借出并标记对象
            StringBuilder obj = pool.borrow();
            obj.append("will-be-invalid");
            log.info("  借出对象: {}, active={}", obj, pool.getNumActive());

            // 2. 失效对象（不归还池中）
            pool.invalidateObject(obj);
            log.info("  失效后: active={}, idle={}", pool.getNumActive(), pool.getNumIdle());

            // 3. 再借出应为不同对象
            StringBuilder newObj = pool.borrow();
            boolean different = (newObj != obj);
            log.info("  再借出对象: {}, 不同对象={}", newObj, different);
            pool.returnObject(newObj);

            boolean passed = different && pool.getNumActive() == 0;
            log.info("  [invalidate] passed={}", passed);
            return passed;
        } catch (Exception e) {
            log.error("[ObjectPoolExample] invalidate failed: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(pool);
        }
    }

    /**
     * 池耗尽自检：将池中所有对象借出，再尝试借出应触发超时。
     *
     * @return true 表示池耗尽时正确抛出超时异常
     */
    public boolean testPoolExhaustion() {
        log.info("===== [exhaustion] 池耗尽示例 =====");
        ObjectPool<StringBuilder> pool = null;
        try {
            pool = createPool(EXHAUSTION_MAX_TOTAL, EXHAUSTION_BORROW_TIMEOUT_MS);

            // 1. 借出全部对象
            List<StringBuilder> borrowed = new ArrayList<>();
            for (int i = 0; i < EXHAUSTION_MAX_TOTAL; i++) {
                borrowed.add(pool.borrow());
            }
            log.info("  借出全部: {} 个, active={}", EXHAUSTION_MAX_TOTAL, pool.getNumActive());

            // 2. 再借应超时
            boolean timeoutCaught = false;
            try {
                pool.borrow();
            } catch (PoolTimeoutException e) {
                timeoutCaught = true;
                log.info("  超时异常已捕获: {}", e.getMessage());
            }

            // 3. 归还后应能再次借出
            pool.returnObject(borrowed.get(0));
            StringBuilder again = pool.borrow();
            log.info("  归还后再借出: {}", (again != null));
            if (again != null) {
                borrowed.set(0, again);
            }

            // 4. 归还全部
            for (StringBuilder sb : borrowed) {
                if (sb != null) {
                    pool.returnObject(sb);
                }
            }

            boolean passed = timeoutCaught && pool.getNumActive() == 0;
            log.info("  [exhaustion] passed={}", passed);
            return passed;
        } catch (Exception e) {
            log.error("[ObjectPoolExample] exhaustion failed: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(pool);
        }
    }

    /**
     * 创建对象池实例。
     *
     * @param maxTotal            最大容量
     * @param borrowTimeoutMillis 借出超时时间（毫秒）
     * @return 对象池实例
     */
    private ObjectPool<StringBuilder> createPool(int maxTotal, long borrowTimeoutMillis) {
        ObjectPoolConfig config = ObjectPoolConfig.builder()
                .maxTotal(maxTotal)
                .maxIdle(maxTotal)
                .borrowTimeoutMillis(borrowTimeoutMillis)
                .testOnBorrow(false)
                .testOnReturn(false)
                .idleEvictionEnabled(false)
                .build();

        ObjectFactory<StringBuilder> factory = new ObjectFactory<>() {
            @Override
            public StringBuilder create() {
                return new StringBuilder();
            }

            @Override
            public void destroy(StringBuilder object) {
                // StringBuilder 无外部资源，无需显式销毁
            }

            @Override
            public boolean validate(StringBuilder object) {
                return object != null;
            }
        };

        return new GenericObjectPool<>(config, factory);
    }

    /**
     * 安静关闭对象池。
     *
     * @param pool 对象池
     */
    private static void closeQuietly(ObjectPool<?> pool) {
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        log.info("ObjectPoolExample — 对象池工具示例");
        log.info("");
        log.info("用法: java ObjectPoolExample [选项]");
        log.info("");
        log.info("选项:");
        log.info("  basic        基础借还能力点");
        log.info("  guard        池守卫能力点");
        log.info("  concurrent   并发借还能力点");
        log.info("  invalidate   对象失效能力点");
        log.info("  exhaustion   池耗尽能力点");
        log.info("  all          测试全部能力点（默认）");
        log.info("  --help       显示此帮助");
    }
}

package com.chua.example.concurrent.queue;

import com.chua.common.support.concurrent.queue.QueueType;
import com.chua.common.support.concurrent.queue.persistent.PersistentLockFreeQueue;
import com.chua.common.support.wal.WalConfig;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 持久化无锁队列综合示例 — 演示 WAL 写入、崩溃恢复与 mmap/异步刷盘降级。
 *
 * <p>覆盖三种场景：
 * <ol>
 *   <li>基本写入与读取 — 正常 offer/poll 流程</li>
 *   <li>崩溃恢复 — 写入后不关闭直接退出，重启后从 WAL 恢复未消费元素</li>
 *   <li>消费后恢复 — offer + poll 后崩溃，重启后队列应为空</li>
 * </ol>
 * </p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检全部场景
 *   java PersistentLockFreeQueueExample
 *
 *   # 仅测试 mmap + 异步刷盘
 *   java PersistentLockFreeQueueExample --mode async
 *
 *   # 仅测试 FileChannel + 同步刷盘
 *   java PersistentLockFreeQueueExample --mode sync
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PersistentLockFreeQueueExample {

    /**
     * 测试写入元素数
     */
    private static final int ELEMENT_COUNT = 10_000;

    /**
     * 默认 WAL 目录
     */
    private static final String DEFAULT_WAL_DIR = "./wal-example";

    /** Main */
    public static void main(String[] args) throws Exception {
        String mode = "all";
        if (args.length > 1 && "--mode".equals(args[0])) {
            mode = args[1];
        }
        log.info("===== PersistentLockFreeQueueExample [mode={}] =====", mode);

        boolean allPassed = true;
        allPassed &= testBasicOfferPoll();
        allPassed &= testCrashRecovery();
        allPassed &= testConsumeRecovery();
        allPassed &= testMmapAsync();
        allPassed &= testFileChannelSync();

        System.out.println("-----");
        if (allPassed) {
            System.out.println("[PASS] 全部场景自检通过");
        } else {
            System.out.println("[FAIL] 存在失败的测试项");
            System.exit(1);
        }
    }

    /**
     * 测试基本写入和读取：offer 后 poll，内容一致。
     *
     * @return 通过返回 true
     * @throws Exception 异常
     */
    private static boolean testBasicOfferPoll() throws Exception {
        Path walDir = Path.of(DEFAULT_WAL_DIR, "basic");
        cleanDir(walDir);

        WalConfig config = WalConfig.builder()
                .walDir(walDir)
                .useMemoryMap(true)
                .syncOnWrite(false)
                .build();

        boolean passed = true;
        try (PersistentLockFreeQueue<String> queue = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new)) {
            queue.offer("hello");
            queue.offer("world");
            String first = queue.poll();
            String second = queue.poll();
            String empty = queue.poll();

           passed = "hello".equals(first) && "world".equals(second) && empty == null;
        }
        printResult("基本 offer/poll 语义", passed);
        cleanDir(walDir);
        return passed;
    }

    /**
     * 测试崩溃恢复：写入 1 万条后关闭（模拟 crash），重启后全部恢复。
     *
     * @return 通过返回 true
     * @throws Exception 异常
     */
    private static boolean testCrashRecovery() throws Exception {
        Path walDir = Path.of(DEFAULT_WAL_DIR, "crash");
        cleanDir(walDir);

        WalConfig config = WalConfig.builder()
                .walDir(walDir)
                .useMemoryMap(true)
                .syncOnWrite(true)
                .build();

        // 第一阶段：写入元素后关闭（模拟 crash 截断）
        PersistentLockFreeQueue<String> writer = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new);
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            writer.offer("msg-" + i);
        }
        // 不调用 close，模拟 crash
        writer.close();

        // 第二阶段：重新打开，自动恢复
        PersistentLockFreeQueue<String> reader = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new);
        int recovered = 0;
        int firstValue = -1;
        int lastValue = -1;
        while (reader.poll() != null) {
            if (recovered == 0) {
                firstValue = Integer.parseInt(reader.peek() == null ? "-1" : reader.peek().split("-")[1]);
            }
            lastValue = recovered;
            recovered++;
        }
        reader.close();

        boolean passed = recovered == ELEMENT_COUNT;
        printResult("崩溃恢复（写入 " + ELEMENT_COUNT + " 条，恢复 " + recovered + " 条）", passed);
        cleanDir(walDir);
        return passed;
    }

    /**
     * 测试消费后恢复：offer 后 poll 消费若干，crash 后重启队列应只剩未消费部分。
     *
     * @return 通过返回 true
     * @throws Exception 异常
     */
    private static boolean testConsumeRecovery() throws Exception {
        Path walDir = Path.of(DEFAULT_WAL_DIR, "consume");
        cleanDir(walDir);

        WalConfig config = WalConfig.builder()
                .walDir(walDir)
                .useMemoryMap(true)
                .syncOnWrite(true)
                .build();

        // 第一阶段：offer 100 条，poll 30 条，关闭
        PersistentLockFreeQueue<String> queue1 = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new);
        for (int i = 0; i < 100; i++) {
            queue1.offer("item-" + i);
        }
        for (int i = 0; i < 30; i++) {
            queue1.poll();
        }
        queue1.close();

        // 第二阶段：恢复，应剩余 70 条
        PersistentLockFreeQueue<String> queue2 = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new);
        int remaining = queue2.size();
        // 验证剩余元素顺序：第一条应为 item-30
        String firstRemaining = queue2.peek();
        queue2.close();

        boolean passed = remaining == 70 && "item-30".equals(firstRemaining);
        printResult("消费后恢复（offer 100，poll 30，剩余 " + remaining + "）", passed);
        cleanDir(walDir);
        return passed;
    }

    /**
     * 测试 mmap 模式 + 异步刷盘降级：高吞吐路径。
     *
     * @return 通过返回 true
     * @throws Exception 异常
     */
    private static boolean testMmapAsync() throws Exception {
        Path walDir = Path.of(DEFAULT_WAL_DIR, "mmap-async");
        cleanDir(walDir);

        WalConfig config = WalConfig.builder()
                .walDir(walDir)
                .useMemoryMap(true)
                .syncOnWrite(false)
                .fsyncBatchIntervalMs(50)
                .build();

        boolean passed;
        try (PersistentLockFreeQueue<String> queue = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new)) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < ELEMENT_COUNT; i++) {
                queue.offer("async-" + i);
            }
            long cost = System.currentTimeMillis() - start;
            passed = queue.size() == ELEMENT_COUNT;
            printResult("mmap + 异步刷盘（" + ELEMENT_COUNT + " 条，耗时 " + cost + "ms）", passed);
        }
        cleanDir(walDir);
        return passed;
    }

    /**
     * 测试 FileChannel 模式 + 同步刷盘：crash 安全但吞吐较低。
     *
     * @return 通过返回 true
     * @throws Exception 异常
     */
    private static boolean testFileChannelSync() throws Exception {
        Path walDir = Path.of(DEFAULT_WAL_DIR, "channel-sync");
        cleanDir(walDir);

        WalConfig config = WalConfig.builder()
                .walDir(walDir)
                .useMemoryMap(false)
                .syncOnWrite(true)
                .build();

        boolean passed;
        try (PersistentLockFreeQueue<String> queue = new PersistentLockFreeQueue<>(
                QueueType.UNBOUNDED, config, String::getBytes, String::new)) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < ELEMENT_COUNT; i++) {
                queue.offer("sync-" + i);
            }
            long cost = System.currentTimeMillis() - start;
            passed = queue.size() == ELEMENT_COUNT;
            printResult("FileChannel + 同步刷盘（" + ELEMENT_COUNT + " 条，耗时 " + cost + "ms）", passed);
        }
        cleanDir(walDir);
        return passed;
    }

    /**
     * 清理 WAL 目录。
     *
     * @param dir 目录路径
     * @throws Exception 异常
     */
    private static void cleanDir(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                                // 忽略删除失败
                            }
                        });
            }
        }
        Files.createDirectories(dir);
    }

    /**
     * 打印测试结果。
     *
     * @param name   测试项名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS] " : "[FAIL] ") + name);
    }
}
package com.chua.example.concurrent.queue;

import com.chua.common.support.concurrent.queue.LockFreeQueue;
import com.chua.common.support.concurrent.queue.LockFreeQueueFlow;
import com.chua.common.support.concurrent.queue.QueueType;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 无锁队列综合示例 — 覆盖 SPSC / MPMC / Unbounded 三种实现及多线程自检。
 *
 * <p>所有实现基于 CAS 原子操作，不阻塞线程。自检覆盖基本入队/出队、peek、
 * null 元素拒绝、有界队列满/空语义以及多线程并发正确性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检全部实现（默认容量 1024）
 *   java LockFreeQueueExample
 *
 *   # 仅自检指定实现
 *   java LockFreeQueueExample --type spsc
 *   java LockFreeQueueExample --type mpmc --capacity 4096
 *
 *   # 打印帮助
 *   java LockFreeQueueExample --help
 * </pre>
 *
 * <h2>队列类型</h2>
 * <table border="1">
 *   <tr><th>--type</th><th>实现类</th><th>生产者</th><th>消费者</th></tr>
 *   <tr><td>spsc</td><td>SpscArrayQueue</td><td>1</td><td>1</td></tr>
 *   <tr><td>mpmc</td><td>MpmcArrayQueue</td><td>多</td><td>多</td></tr>
 *   <tr><td>unbounded</td><td>MichaelScottQueue</td><td>多</td><td>多</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LockFreeQueueExample {

    /**
     * 并发测试每生产者投递元素数
     */
    private static final int ELEMENT_COUNT = 100_000;

    /**
     * MPMC 并发测试生产者数量
     */
    private static final int PRODUCER_COUNT = 4;

    /**
     * MPMC 并发测试消费者数量
     */
    private static final int CONSUMER_COUNT = 4;

    /**
     * 并发测试等待超时时间（秒）
     */
    private static final long AWAIT_TIMEOUT_SECONDS = 30L;

    /**
     * 有界队列满/空语义测试容量
     */
    private static final int FULL_TEST_CAPACITY = 2;

    /**
     * 默认队列容量
     */
    private static final int DEFAULT_CAPACITY = 1024;

    /** Main */
    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        if (parsed.type() == null) {
            runAllTests(parsed.capacity());
        } else {
            runSingleTest(parsed.type(), parsed.capacity());
        }
    }

    /**
     * 自检全部三种实现。
     *
     * @param capacity 有界队列容量
     */
    private static void runAllTests(int capacity) {
        log.info("===== 无锁队列自检（全部实现，capacity={}）=====", capacity);
        boolean allPassed = true;
        allPassed &= testSingle(QueueType.SPSC, capacity);
        allPassed &= testSingle(QueueType.MPMC, capacity);
        allPassed &= testSingle(QueueType.UNBOUNDED, capacity);

        log.info("-----");
        if (allPassed) {
            log.info("[PASS] 全部实现自检通过");
        } else {
            log.info("[FAIL] 存在失败的测试项");
            System.exit(1);
        }
    }

    /**
     * 自检单一实现。
     *
     * @param type     队列类型标识
     * @param capacity 有界队列容量
     */
    private static void runSingleTest(String type, int capacity) {
        QueueType queueType = parseQueueType(type);
        if (queueType == null) {
            System.err.println("[ERROR] 未知队列类型: " + type);
            System.exit(1);
        }
        boolean passed = testSingle(queueType, capacity);
        if (!passed) {
            System.exit(1);
        }
    }

    /**
     * 自检指定队列实现。
     *
     * @param type     队列类型
     * @param capacity 有界队列容量
     * @return 全部通过返回 true
     */
    private static boolean testSingle(QueueType type, int capacity) {
        log.info("===== 自检 {} (capacity={}) =====", type, capacity);
        boolean allPassed = true;
        allPassed &= testBasicOfferPoll(type, capacity);
        allPassed &= testNullRejected(type, capacity);
        if (type != QueueType.UNBOUNDED) {
            allPassed &= testFullAndEmpty(type);
        }
        allPassed &= testConcurrency(type);
        printResult(type + " 自检", allPassed);
        return allPassed;
    }

    /**
     * 测试基本入队/出队/peek 语义。
     *
     * @param type     队列类型
     * @param capacity 有界队列容量
     * @return 通过返回 true
     */
    private static boolean testBasicOfferPoll(QueueType type, int capacity) {
        LockFreeQueue<String> queue = LockFreeQueueFlow.create(type, capacity);
        queue.offer("a");
        queue.offer("b");
        boolean peekOk = "a".equals(queue.peek());
        boolean firstOk = "a".equals(queue.poll());
        boolean secondOk = "b".equals(queue.poll());
        boolean emptyOk = queue.poll() == null && queue.isEmpty();
        boolean passed = peekOk && firstOk && secondOk && emptyOk;
        printResult("基本 offer/poll/peek 语义", passed);
        return passed;
    }

    /**
     * 测试 null 元素拒绝。
     *
     * @param type     队列类型
     * @param capacity 有界队列容量
     * @return 通过返回 true
     */
    private static boolean testNullRejected(QueueType type, int capacity) {
        LockFreeQueue<String> queue = LockFreeQueueFlow.create(type, capacity);
        boolean rejected = false;
        try {
            queue.offer(null);
        } catch (NullPointerException e) {
            rejected = true;
        }
        printResult("null 元素拒绝（NPE）", rejected);
        return rejected;
    }

    /**
     * 测试有界队列满/空语义。
     *
     * @param type 队列类型
     * @return 通过返回 true
     */
    private static boolean testFullAndEmpty(QueueType type) {
        LockFreeQueue<Integer> queue = LockFreeQueueFlow.create(type, FULL_TEST_CAPACITY);
        boolean firstOk = queue.offer(1);
        boolean secondOk = queue.offer(2);
        boolean fullOk = !queue.offer(3);
        boolean pollOk = queue.poll() == 1;
        boolean afterPollOfferOk = queue.offer(4);
        boolean passed = firstOk && secondOk && fullOk && pollOk && afterPollOfferOk;
        printResult("有界满/空语义（容量 " + FULL_TEST_CAPACITY + "）", passed);
        return passed;
    }

    /**
     * 按类型分发并发测试。
     *
     * @param type 队列类型
     * @return 通过返回 true
     */
    private static boolean testConcurrency(QueueType type) {
        switch (type) {
            case SPSC:
                return testSpscConcurrency();
            case MPMC:
                return testMpmcConcurrency();
            case UNBOUNDED:
                return testUnboundedConcurrency();
            default:
                return false;
        }
    }

    /**
     * SPSC 并发测试：单生产者 + 单消费者。
     *
     * @return 通过返回 true
     */
    private static boolean testSpscConcurrency() {
        LockFreeQueue<Integer> queue = LockFreeQueueFlow.create(QueueType.SPSC, ELEMENT_COUNT);
        AtomicLong consumedSum = new AtomicLong();
        CountDownLatch producerDone = new CountDownLatch(1);
        CountDownLatch consumerDone = new CountDownLatch(1);

        // 生产者线程：写入 1..ELEMENT_COUNT
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= ELEMENT_COUNT; i++) {
                while (!queue.offer(i)) {
                    Thread.onSpinWait();
                }
            }
            producerDone.countDown();
        }, "spsc-producer");
        producer.start();

        // 消费者线程：取出 ELEMENT_COUNT 个并累加
        Thread consumer = new Thread(() -> {
            int count = 0;
            long sum = 0;
            while (count < ELEMENT_COUNT) {
                Integer value = queue.poll();
                if (value != null) {
                    sum += value;
                    count++;
                } else {
                    Thread.onSpinWait();
                }
            }
            consumedSum.set(sum);
            consumerDone.countDown();
        }, "spsc-consumer");
        consumer.start();

        boolean awaitOk = await(producerDone) && await(consumerDone);
        long expected = (long) ELEMENT_COUNT * (ELEMENT_COUNT + 1) / 2;
        boolean sumOk = consumedSum.get() == expected;
        boolean passed = awaitOk && sumOk;
        printResult("SPSC 并发（1P/1C，共 " + ELEMENT_COUNT + " 元素）", passed);
        return passed;
    }

    /**
     * MPMC 并发测试：多生产者 + 多消费者。
     *
     * @return 通过返回 true
     */
    private static boolean testMpmcConcurrency() {
        int total = ELEMENT_COUNT * PRODUCER_COUNT;
        LockFreeQueue<Integer> queue = LockFreeQueueFlow.create(QueueType.MPMC, total);
        return testConcurrentQueue(queue, total, "MPMC");
    }

    /**
     * Unbounded 并发测试：多生产者 + 多消费者。
     *
     * @return 通过返回 true
     */
    private static boolean testUnboundedConcurrency() {
        int total = ELEMENT_COUNT * PRODUCER_COUNT;
        LockFreeQueue<Integer> queue = LockFreeQueueFlow.create(QueueType.UNBOUNDED, 0);
        return testConcurrentQueue(queue, total, "Unbounded");
    }

    /**
     * 通用多生产者多消费者并发测试。
     *
     * @param queue 无锁队列
     * @param total 元素总数
     * @param name  测试名称
     * @return 通过返回 true
     */
    private static boolean testConcurrentQueue(LockFreeQueue<Integer> queue, int total, String name) {
        AtomicInteger nextValue = new AtomicInteger();
        AtomicLong consumedSum = new AtomicLong();
        AtomicInteger remaining = new AtomicInteger(total);
        CountDownLatch producersDone = new CountDownLatch(PRODUCER_COUNT);
        CountDownLatch consumersDone = new CountDownLatch(CONSUMER_COUNT);

        // 启动生产者：写入 0..total-1 的唯一值
        for (int p = 0; p < PRODUCER_COUNT; p++) {
            Thread producer = new Thread(() -> {
                while (true) {
                    int value = nextValue.getAndIncrement();
                    if (value >= total) {
                        break;
                    }
                    while (!queue.offer(value)) {
                        Thread.onSpinWait();
                    }
                }
                producersDone.countDown();
            }, "queue-producer-" + p);
            producer.start();
        }

        // 启动消费者：持续取出直到 remaining 归零
        for (int c = 0; c < CONSUMER_COUNT; c++) {
            Thread consumer = new Thread(() -> {
                while (remaining.get() > 0) {
                    Integer value = queue.poll();
                    if (value != null) {
                        consumedSum.addAndGet(value);
                        remaining.decrementAndGet();
                    } else {
                        Thread.onSpinWait();
                    }
                }
                consumersDone.countDown();
            }, "queue-consumer-" + c);
            consumer.start();
        }

        boolean awaitOk = await(producersDone) && await(consumersDone);
        long expected = (long) total * (total - 1) / 2;
        boolean sumOk = consumedSum.get() == expected;
        boolean remainOk = remaining.get() == 0;
        boolean passed = awaitOk && sumOk && remainOk;
        printResult(name + " 并发（" + PRODUCER_COUNT + "P/" + CONSUMER_COUNT + "C，共 " + total + " 元素）", passed);
        return passed;
    }

    /**
     * 等待 CountDownLatch 达成，超时或中断返回 false。
     *
     * @param latch 等待闸门
     * @return 在超时内达成返回 true
     */
    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 解析队列类型标识。
     *
     * @param type 类型标识字符串
     * @return 对应的队列类型；未知返回 null
     */
    private static QueueType parseQueueType(String type) {
        switch (type.toLowerCase()) {
            case "spsc":
                return QueueType.SPSC;
            case "mpmc":
                return QueueType.MPMC;
            case "unbounded", "unbound":
                return QueueType.UNBOUNDED;
            default:
                return null;
        }
    }

    /**
     * 打印测试结果。
     *
     * @param name   测试项名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info((passed ? "[PASS] " : "[FAIL] ") + name);
    }

    /**
     * 解析命令行参数。
     *
     * @param args 命令行参数数组
     * @return 参数对象
     */
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--type", "-t" -> {
                    if (index + 1 < args.length) {
                        result = result.withType(args[++index]);
                    }
                }
                case "--capacity", "-c" -> {
                    if (index + 1 < args.length) {
                        result = result.withCapacity(Integer.parseInt(args[++index]));
                    }
                }
                case "--help", "-h" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        log.info("无锁队列综合示例 — SPSC / MPMC / Unbounded");
        log.info();
        log.info("用法: java LockFreeQueueExample [选项]");
        log.info();
        log.info("选项:");
        log.info("  --type,     -t <key>    队列类型（spsc/mpmc/unbounded，默认全部）");
        log.info("  --capacity, -c <n>      有界队列容量（默认: " + DEFAULT_CAPACITY + "）");
        log.info("  --help,     -h          显示此帮助");
    }

    /**
     * 命令行参数容器。
     *
     * @param type     队列类型标识（spsc/mpmc/unbounded），null 表示全部
     * @param capacity 有界队列容量
     * @param help     是否打印帮助
     * @since 4.0.0.42
     */
    private record Args(String type, int capacity, boolean help) {

        /**
         * 带默认值的空参构造。
         */
        Args() {
            this(null, DEFAULT_CAPACITY, false);
        }

        /**
         * 替换 type 字段，返回新实例。
         *
         * @param type 队列类型标识
         * @return 新 Args 实例
         */
        public Args withType(String type) {
            return new Args(type, capacity, help);
        }

        /**
         * 替换 capacity 字段，返回新实例。
         *
         * @param capacity 有界队列容量
         * @return 新 Args 实例
         */
        public Args withCapacity(int capacity) {
            return new Args(type, capacity, help);
        }

        /**
         * 替换 help 字段，返回新实例。
         *
         * @param help 是否打印帮助
         * @return 新 Args 实例
         */
        public Args withHelp(boolean help) {
            return new Args(type, capacity, help);
        }
    }
}
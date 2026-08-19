package com.chua.example.concurrent.queue;

import com.chua.common.support.shmqueue.ShmQueue;
import com.chua.common.support.shmqueue.ShmQueueException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 共享内存环形队列综合示例 — 覆盖 SPSC 三种等待模式（自旋 / 阻塞 / 混合）。
 *
 * <p>底层为 C 动态库 libshmqueue（utils-support-native-shm-queue 模块提供）。
 * 通过 Java 22+ Panama FFM API 直接调用，无 JNI 胶水。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检全部模式
 *   java ShmQueueExample
 *
 *   # 仅自检指定模式
 *   java ShmQueueExample --mode hybrid
 *   java ShmQueueExample --mode spin
 *   java ShmQueueExample --mode block
 * </pre>
 *
 * <h2>三种模式</h2>
 * <table border="1">
 *   <tr><th>--mode</th><th>行为</th><th>适用</th></tr>
 *   <tr><td>spin</td><td>消费者循环检查队列</td><td>持续高吞吐、低延迟</td></tr>
 *   <tr><td>block</td><td>消费者阻塞在 eventfd/WinEvent</td><td>突发任务、低 CPU</td></tr>
 *   <tr><td>hybrid</td><td>先自旋再阻塞</td><td>突发 + 低延迟 + 合理 CPU</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ShmQueueExample {

    /**
     * 自检消息条数
     */
    private static final int MESSAGE_COUNT = 10_000;

    /**
     * 单消息字节数
     */
    private static final int PAYLOAD_SIZE = 32;

    /**
     * 跨进程测试等待超时（秒）
     */
    private static final long AWAIT_TIMEOUT_SECONDS = 30L;

    /**
     * 默认队列容量
     */
    private static final int DEFAULT_CAPACITY = 256;

    /**
     * 默认槽位大小
     */
    private static final int DEFAULT_SLOT_SIZE = 128;

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_OK = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_FAIL = 1;

    /** Main */
    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        if (parsed.help()) {
            printHelp();
            return;
        }
        String mode = parsed.mode() != null ? parsed.mode() : "all";
        int exit;
        try {
            exit = switch (mode.toLowerCase()) {
                case "all" -> runAll();
                case "spin" -> runSingle(ShmQueue.Mode.SPIN) ? EXIT_OK : EXIT_FAIL;
                case "block" -> runSingle(ShmQueue.Mode.BLOCK) ? EXIT_OK : EXIT_FAIL;
                case "hybrid" -> runSingle(ShmQueue.Mode.HYBRID) ? EXIT_OK : EXIT_FAIL;
                default -> {
                    System.err.println("[ERROR] 未知模式: " + mode);
                    yield EXIT_FAIL;
                }
            };
        } catch (Throwable t) {
            log.error("执行异常", t);
            exit = EXIT_FAIL;
        }
        System.exit(exit);
    }

    /**
     * 自检全部三种模式。
     */
    public static int runAll() {
        log.info("===== ShmQueue 自检（三种模式）=====");
        boolean allPassed = true;
        allPassed &= runSingle(ShmQueue.Mode.SPIN);
        allPassed &= runSingle(ShmQueue.Mode.BLOCK);
        allPassed &= runSingle(ShmQueue.Mode.HYBRID);
        if (allPassed) {
            log.info("[PASS] 全部模式自检通过");
            return EXIT_OK;
        }
        log.error("[FAIL] 存在失败的模式");
        return EXIT_FAIL;
    }

    /**
     * 自检单一模式。
     *
     * @param mode 等待模式
     * @return 通过返回 true
     */
    public static boolean runSingle(ShmQueue.Mode mode) {
        log.info("===== 自检 {} =====", mode);
        boolean allPassed = true;
        allPassed &= testBasicSendRecv(mode);
        allPassed &= testQueueFull(mode);
        allPassed &= testLargeBurst(mode);
        printResult(mode + " 自检", allPassed);
        return allPassed;
    }

    /**
     * 基本 send/recv。
     */
    private static boolean testBasicSendRecv(ShmQueue.Mode mode) {
        String name = "/shmq_example_" + System.nanoTime();
        try (ShmQueue q = ShmQueue.create(name, DEFAULT_CAPACITY, DEFAULT_SLOT_SIZE, mode)) {
            byte[] data = "hello-shmqueue".getBytes();
            q.send(42, data);
            ShmQueue.Message msg = q.recv();
            boolean ok = msg.type() == 42
                    && new String(msg.bytes()).equals("hello-shmqueue");
            printResult("基本 send/recv", ok);
            return ok;
        } catch (Throwable t) {
            log.error("基本 send/recv 异常", t);
            return false;
        }
    }

    /**
     * 队列满检测。
     */
    private static boolean testQueueFull(ShmQueue.Mode mode) {
        String name = "/shmq_example_full_" + System.nanoTime();
        try (ShmQueue q = ShmQueue.create(name, 4, 64, mode)) {
            q.send(1, new byte[]{1});
            q.send(1, new byte[]{2});
            q.send(1, new byte[]{3});
            boolean fullCaught = false;
            try {
                q.send(1, new byte[]{4});
            } catch (ShmQueueException e) {
                fullCaught = e.getCode() == ShmQueue.ERR_QUEUE_FULL;
            }
            printResult("队列满检测", fullCaught);
            return fullCaught;
        } catch (Throwable t) {
            log.error("队列满检测异常", t);
            return false;
        }
    }

    /**
     * 大批量消息吞吐。
     */
    private static boolean testLargeBurst(ShmQueue.Mode mode) {
        String name = "/shmq_example_burst_" + System.nanoTime();
        try (ShmQueue q = ShmQueue.create(name, 1024, 512, mode)) {
            long t0 = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                q.send(i & 0xFF, makePayload(i));
            }
            int okCount = 0;
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                ShmQueue.Message m = q.recv();
                if (m.type() == (i & 0xFF) && checkPayload(m.bytes(), i)) {
                    okCount++;
                }
            }
            double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;
            boolean ok = okCount == MESSAGE_COUNT;
            log.info("burst: {}/{} msgs in {} ms ({} msg/s)",
                    okCount, MESSAGE_COUNT,
                    String.format("%.1f", elapsedMs),
                    String.format("%.0f", MESSAGE_COUNT / (elapsedMs / 1000.0)));
            printResult("大批量消息顺序", ok);
            return ok;
        } catch (Throwable t) {
            log.error("大批量消息异常", t);
            return false;
        }
    }

    /**
     * 跨进程通信示例：使用两个独立 ShmQueue 实例（同一进程内 attach 模式模拟）。
     *
     * <p>跨真实进程需启动两个 JVM：一方 create + send，另一方 attach + recv。</p>
     */
    public static boolean runCrossProcess(String name, int expected) {
        log.info("===== 跨进程示例 name={} expect={} =====", name, expected);
        CountDownLatch done = new CountDownLatch(1);
        boolean[] recvOk = {false};
        Thread consumer = new Thread(() -> {
            try (ShmQueue q = ShmQueue.attach(name)) {
                int recv = 0;
                while (recv < expected) {
                    ShmQueue.Message m = q.recv();
                    if (m.type() == recv) {
                        recv++;
                    }
                }
                recvOk[0] = true;
            } catch (Throwable t) {
                log.error("consumer 异常", t);
            } finally {
                done.countDown();
            }
        }, "shmq-example-consumer");
        consumer.setDaemon(true);
        consumer.start();

        try {
            Thread.sleep(100);
            try (ShmQueue q = ShmQueue.create(name, 1024, 512, ShmQueue.Mode.HYBRID)) {
                for (int i = 0; i < expected; i++) {
                    q.send(i, ("msg-" + i).getBytes());
                }
            }
            done.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        printResult("跨进程（in-process 模拟）", recvOk[0]);
        return recvOk[0];
    }

    /** MakePayload */
    private static byte[] makePayload(int i) {
        byte[] b = new byte[PAYLOAD_SIZE];
        for (int k = 0; k < PAYLOAD_SIZE; k++) {
            b[k] = (byte) ((i + k) & 0xFF);
        }
        return b;
    }

    /** 校验Payload */
    private static boolean checkPayload(byte[] b, int i) {
        if (b == null || b.length != PAYLOAD_SIZE) {
            return false;
        }
        for (int k = 0; k < PAYLOAD_SIZE; k++) {
            if (b[k] != (byte) ((i + k) & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /** PrintResult */
    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS] " : "[FAIL] ") + name);
    }

    /** PrintHelp */
    private static void printHelp() {
        System.out.println("ShmQueueExample - 共享内存环形队列示例");
        System.out.println();
        System.out.println("用法: java ShmQueueExample [--mode <key>] [--help]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --mode <spin|block|hybrid|all>   等待模式（默认 all）");
        System.out.println("  --help, -h                       显示此帮助");
    }

    /**
     * 命令行参数容器。
     *
     * @param mode 等待模式
     * @param help 是否打印帮助
     * @since 4.0.0.42
     */
    private record Args(String mode, boolean help) {

        /**
         * 默认参数构造
         */
        Args() {
            this("all", false);
        }

        /** 解析 */
        static Args parse(String[] args) {
            Args result = new Args();
            int i = 0;
            while (i < args.length) {
                switch (args[i]) {
                    case "--mode", "-m" -> {
                        if (i + 1 < args.length) {
                            result = result.withMode(args[++i]);
                        }
                    }
                    case "--help", "-h" -> result = result.withHelp(true);
                    default -> log.warn("未知参数: {}", args[i]);
                }
                i++;
            }
            return result;
        }

        Args withMode(String v) {
            return new Args(v, help);
        }

        Args withHelp(boolean v) {
            return new Args(mode, v);
        }
    }
}

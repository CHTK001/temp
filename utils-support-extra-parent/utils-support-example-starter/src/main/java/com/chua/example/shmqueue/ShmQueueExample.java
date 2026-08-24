package com.chua.example.shmqueue;

import com.chua.common.support.shmqueue.ShmQueue;
import com.chua.common.support.shmqueue.ShmQueueException;

import java.util.ArrayList;
import java.util.List;

/**
 * ShmQueue 抽象 API 层示例：覆盖单条收发、顺序性、队列满、数据过大、超时、attach、大批量等场景。
 *
 * <p>改写自 common-starter 测试代码 shmqueue/ShmQueueTest，共 7 个场景：
 * create+send/recv、200 条消息不丢不乱、队列满错误码、数据过大错误码、recv 超时及时长边界、
 * attach 已存在队列、5000 条大批量消息。</p>
 *
 * <p>前置条件：classpath 中需存在至少一个 {@link com.chua.common.support.shmqueue.ShmQueueProvider}
 * SPI 实现（通常由 utils-support-native-shm-queue 模块提供）。native 库加载失败时打印
 * {@code [SKIP] native-unavailable} 并正常退出（退出码 0），不计为 FAIL。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.shmqueue.ShmQueueExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ShmQueueExample {

    /**
     * 私有构造，防止实例化
     */
    private ShmQueueExample() {
    }

    /**
     * 入口：先探测 native 可用性，随后依次执行 7 个自检场景，任一失败立即退出非零。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        if (!probeNative()) {
            return;
        }
        System.out.println("[PASS] native-ready");
        if (!runCreateAndSendRecv()) {
            System.out.println("[FAIL] create-send-recv");
            System.exit(1);
        } else {
            System.out.println("[PASS] create-send-recv");
        }
        if (!runOrderPreserved()) {
            System.out.println("[FAIL] order-preserved");
            System.exit(1);
        } else {
            System.out.println("[PASS] order-preserved");
        }
        if (!runQueueFull()) {
            System.out.println("[FAIL] queue-full-error-code");
            System.exit(1);
        } else {
            System.out.println("[PASS] queue-full-error-code");
        }
        if (!runDataTooLarge()) {
            System.out.println("[FAIL] data-too-large-error-code");
            System.exit(1);
        } else {
            System.out.println("[PASS] data-too-large-error-code");
        }
        if (!runRecvTimeout()) {
            System.out.println("[FAIL] recv-timeout-boundary");
            System.exit(1);
        } else {
            System.out.println("[PASS] recv-timeout-boundary");
        }
        if (!runAttachExisting()) {
            System.out.println("[FAIL] attach-existing");
            System.exit(1);
        } else {
            System.out.println("[PASS] attach-existing");
        }
        if (!runLargeBurst()) {
            System.out.println("[FAIL] large-burst-5000");
            System.exit(1);
        } else {
            System.out.println("[PASS] large-burst-5000");
        }
        System.out.println("[PASS] shm-queue 全部 7 个场景通过");
    }

    /**
     * 探测 native 库与 Provider 是否可用。
     *
     * <p>创建一个最小队列验证链路；任何 Throwable（含 UnsatisfiedLinkError、
     * Provider 缺失的 IllegalStateException）均视为环境不可用，
     * 打印 {@code [SKIP] native-unavailable} 后正常返回 false，进程以 0 退出。</p>
     *
     * @return 可用返回 true；不可用返回 false（跳过而非失败）
     */
    private static boolean probeNative() {
        String name = "/shmq_ex_probe_" + System.nanoTime();
        try (ShmQueue q = ShmQueue.create(name, 2, 64, ShmQueue.Mode.SPIN)) {
            return q != null;
        } catch (Throwable t) {
            System.out.println("[SKIP] native-unavailable " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * 场景 1：创建队列后单条 send/recv 往返一致。
     *
     * @return 通过返回 true
     */
    private static boolean runCreateAndSendRecv() {
        String name = uniqueName();
        byte[] data = "hello".getBytes();
        try (ShmQueue q = ShmQueue.create(name, 16, 128, ShmQueue.Mode.HYBRID)) {
            q.send(7, data);
            ShmQueue.Message msg = q.recv();
            return msg.type() == 7 && java.util.Arrays.equals(data, msg.bytes());
        } catch (Throwable t) {
            return detail("create-send-recv", t);
        }
    }

    /**
     * 场景 2：200 条消息按序不丢不乱。
     *
     * @return 通过返回 true
     */
    private static boolean runOrderPreserved() {
        String name = uniqueName();
        int count = 200;
        try (ShmQueue q = ShmQueue.create(name, 64, 64, ShmQueue.Mode.SPIN)) {
            for (int i = 0; i < count; i++) {
                q.send(0, intToBytes(i));
            }
            for (int i = 0; i < count; i++) {
                ShmQueue.Message msg = q.recv();
                if (bytesToInt(msg.bytes()) != i) {
                    System.out.println("  fail order at index=" + i);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return detail("order-preserved", t);
        }
    }

    /**
     * 场景 3：容量 4 的队列发满后再发应抛 ERR_QUEUE_FULL。
     *
     * @return 通过返回 true
     */
    private static boolean runQueueFull() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, 4, 64, ShmQueue.Mode.SPIN)) {
            q.send(1, new byte[]{1});
            q.send(1, new byte[]{2});
            q.send(1, new byte[]{3});
            boolean fullCaught = false;
            try {
                q.send(1, new byte[]{4});
            } catch (ShmQueueException e) {
                fullCaught = e.getCode() == ShmQueue.ERR_QUEUE_FULL;
                if (!fullCaught) {
                    System.out.println("  fail unexpected code=" + e.getCode());
                }
            }
            return fullCaught;
        } catch (Throwable t) {
            return detail("queue-full", t);
        }
    }

    /**
     * 场景 4：超过槽位大小的数据应抛 ERR_DATA_TOO_LARGE。
     *
     * @return 通过返回 true
     */
    private static boolean runDataTooLarge() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, 8, 16, ShmQueue.Mode.HYBRID)) {
            byte[] big = new byte[64];
            boolean caught = false;
            try {
                q.send(1, big);
            } catch (ShmQueueException e) {
                caught = e.getCode() == ShmQueue.ERR_DATA_TOO_LARGE;
                if (!caught) {
                    System.out.println("  fail unexpected code=" + e.getCode());
                }
            }
            return caught;
        } catch (Throwable t) {
            return detail("data-too-large", t);
        }
    }

    /**
     * 场景 5：空队列 recvTimeout(50ms) 应抛 ERR_TIMEOUT 且耗时在 40ms~2s 区间。
     *
     * @return 通过返回 true
     */
    private static boolean runRecvTimeout() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, 8, 64, ShmQueue.Mode.BLOCK)) {
            long start = System.nanoTime();
            boolean timeoutCaught = false;
            try {
                q.recvTimeout(50_000_000L);
            } catch (ShmQueueException e) {
                timeoutCaught = e.getCode() == ShmQueue.ERR_TIMEOUT;
                if (!timeoutCaught) {
                    System.out.println("  fail unexpected code=" + e.getCode());
                }
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (!timeoutCaught) {
                return false;
            }
            if (elapsedMs < 40 || elapsedMs >= 2000) {
                System.out.println("  fail elapsed=" + elapsedMs + "ms out of range");
                return false;
            }
            System.out.println("  ok elapsed=" + elapsedMs + "ms");
            return true;
        } catch (Throwable t) {
            return detail("recv-timeout", t);
        }
    }

    /**
     * 场景 6：attach 已存在队列并读取创建方写入的消息。
     *
     * @return 通过返回 true
     */
    private static boolean runAttachExisting() {
        String name = uniqueName();
        byte[] payload = "from-creator".getBytes();
        try (ShmQueue creator = ShmQueue.create(name, 8, 128, ShmQueue.Mode.HYBRID)) {
            creator.send(100, payload);
            try (ShmQueue attacher = ShmQueue.attach(name)) {
                ShmQueue.Message msg = attacher.recv();
                return msg.type() == 100 && java.util.Arrays.equals(payload, msg.bytes());
            }
        } catch (Throwable t) {
            return detail("attach-existing", t);
        }
    }

    /**
     * 场景 7：1024 槽 × 4096 字节队列写入并读回 5000 条消息。
     *
     * @return 通过返回 true
     */
    private static boolean runLargeBurst() {
        String name = uniqueName();
        int count = 5000;
        List<Integer> sent = new ArrayList<>(count);
        try (ShmQueue q = ShmQueue.create(name, 1024, 4096, ShmQueue.Mode.HYBRID)) {
            for (int i = 0; i < count; i++) {
                q.send(0xAB, intToBytes(i));
                sent.add(i);
            }
            for (int i = 0; i < count; i++) {
                ShmQueue.Message m = q.recv();
                if (m.type() != 0xAB || bytesToInt(m.bytes()) != sent.get(i)) {
                    System.out.println("  fail burst at index=" + i);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return detail("large-burst", t);
        }
    }

    /**
     * 输出异常明细并返回 false。
     *
     * @param scene 场景名
     * @param t     异常
     * @return 固定返回 false
     */
    private static boolean detail(String scene, Throwable t) {
        System.out.println("  fail " + scene + " exception: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        return false;
    }

    /**
     * 生成唯一的共享内存对象名。
     *
     * @return 形如 /shmq_ex_<纳秒时间戳> 的名称
     */
    private static String uniqueName() {
        return "/shmq_ex_" + System.nanoTime();
    }

    /**
     * Int 转 4 字节大端数组。
     *
     * @param v 整数值
     * @return 4 字节数组
     */
    private static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) ((v >> 24) & 0xFF),
                (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }

    /**
     * 4 字节大端数组转 Int。
     *
     * @param b 字节数组
     * @return 整数值；长度非法返回 -1
     */
    private static int bytesToInt(byte[] b) {
        if (b == null || b.length != 4) {
            return -1;
        }
        return ((b[0] & 0xFF) << 24)
                | ((b[1] & 0xFF) << 16)
                | ((b[2] & 0xFF) << 8)
                | (b[3] & 0xFF);
    }
}

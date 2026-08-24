package com.chua.example.shmqueue;

import com.chua.common.support.shmqueue.ShmQueue;
import com.chua.common.support.shmqueue.ShmQueueException;
import com.chua.common.support.shmqueue.ShmQueueProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * ShmQueue 抽象 API 层示例。
 *
 * <p>改写自 ShmQueueTest，针对 common-starter 的抽象 {@link ShmQueue} 接口，
 * 要求 classpath 中至少存在一个 {@link ShmQueueProvider} SPI 实现
 * （通常由 utils-support-native-shm-queue 模块提供）。</p>
 *
 * <p>若 SPI 缺失或 native 库加载失败，打印 {@code [SKIP] native-unavailable}
 * 后以退出码 0 结束，不计为 FAIL。</p>
 *
 * <h2>覆盖场景</h2>
 * <ol>
 *   <li>创建 + 单条 send/recv</li>
 *   <li>顺序：200 条消息不丢不乱</li>
 *   <li>队列满返回 ERR_QUEUE_FULL</li>
 *   <li>数据过大返回 ERR_DATA_TOO_LARGE</li>
 *   <li>recv 超时返回 ERR_TIMEOUT</li>
 *   <li>attach 已存在的队列</li>
 *   <li>大批量消息（5000 条）</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ShmQueueExample {

    /**
     * 顺序场景消息条数
     */
    private static final int ORDER_COUNT = 200;

    /**
     * 大批量场景消息条数
     */
    private static final int BURST_COUNT = 5000;

    /**
     * 队列满场景容量（与源测试一致：3 条可写入，第 4 条触发 ERR_QUEUE_FULL）
     */
    private static final int FULL_CAPACITY = 4;

    /**
     * 超时场景等待时长（纳秒，约 50ms）
     */
    private static final long TIMEOUT_NANOS = 50_000_000L;

    /**
     * 超时窗口下限（毫秒）
     */
    private static final long TIMEOUT_FLOOR_MILLIS = 40L;

    /**
     * 超时窗口上限（毫秒）
     */
    private static final long TIMEOUT_CEILING_MILLIS = 2_000L;

    /**
     * Main 入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        if (!isNativeAvailable()) {
            log.info("[SKIP] native-unavailable");
            System.exit(0);
            return;
        }
        boolean passed = true;
        try {
            passed &= testCreateAndSendRecv();
            passed &= testOrderPreserved();
            passed &= testQueueFullThrows();
            passed &= testDataTooLargeThrows();
            passed &= testRecvTimeoutThrows();
            passed &= testAttachExisting();
            passed &= testLargeBurst();
        } catch (Throwable t) {
            log.info("[FAIL] 示例执行异常: {}", t.getMessage());
            passed = false;
        }
        if (!passed) {
            log.info("[FAIL] ShmQueue 存在失败场景");
            System.exit(1);
        }
        log.info("[PASS] ShmQueue 全部场景通过");
        System.exit(0);
    }

    /**
     * 探测 classpath 是否存在可用 Provider 且 native 库可正常加载。
     *
     * @return 可用返回 true
     */
    private static boolean isNativeAvailable() {
        if (!ServiceLoader.load(ShmQueueProvider.class).iterator().hasNext()) {
            return false;
        }
        String name = "/shmq_probe_" + System.nanoTime();
        try (ShmQueue probe = ShmQueue.create(name, 2, 64, ShmQueue.Mode.HYBRID)) {
            return probe != null;
        } catch (IllegalStateException | LinkageError | ShmQueueException e) {
            log.info("[SKIP] 探测创建失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景一：创建 + 单条 send/recv。
     *
     * @return 通过返回 true
     */
    private static boolean testCreateAndSendRecv() {
        String name = uniqueName();
        byte[] data = "hello".getBytes();
        try (ShmQueue q = ShmQueue.create(name, 16, 128, ShmQueue.Mode.HYBRID)) {
            q.send(7, data);
            ShmQueue.Message msg = q.recv();
            if (msg.type() != 7 || !java.util.Arrays.equals(data, msg.bytes())) {
                log.info("[FAIL] 单条消息 type={} bytes={}", msg.type(), java.util.Arrays.toString(msg.bytes()));
                return false;
            }
            log.info("[PASS] 创建 + 单条 send/recv");
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] 单条 send/recv 异常: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 场景二：200 条消息顺序不丢不乱。
     *
     * <p>容量放大到 256 以避免发送侧触发队列满。</p>
     *
     * @return 通过返回 true
     */
    private static boolean testOrderPreserved() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, ORDER_COUNT + 64, 64, ShmQueue.Mode.SPIN)) {
            for (int i = 0; i < ORDER_COUNT; i++) {
                q.send(0, intToBytes(i));
            }
            for (int i = 0; i < ORDER_COUNT; i++) {
                ShmQueue.Message msg = q.recv();
                if (bytesToInt(msg.bytes()) != i) {
                    log.info("[FAIL] 第 {} 条消息乱序: {}", i, bytesToInt(msg.bytes()));
                    return false;
                }
            }
            log.info("[PASS] {} 条消息顺序保持", ORDER_COUNT);
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] 顺序场景异常: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 场景三：队列满时抛出 ERR_QUEUE_FULL。
     *
     * @return 通过返回 true
     */
    private static boolean testQueueFullThrows() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, FULL_CAPACITY, 64, ShmQueue.Mode.SPIN)) {
            q.send(1, new byte[]{1});
            q.send(1, new byte[]{2});
            q.send(1, new byte[]{3});
            int code = Integer.MIN_VALUE;
            try {
                q.send(1, new byte[]{4});
            } catch (ShmQueueException ex) {
                code = ex.getCode();
            }
            if (code != ShmQueue.ERR_QUEUE_FULL) {
                log.info("[FAIL] 队列满应抛 ERR_QUEUE_FULL({})，实际 {}",
                        ShmQueue.ERR_QUEUE_FULL, code);
                return false;
            }
            log.info("[PASS] 队列满错误码");
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] 队列满场景异常: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 场景四：数据超过槽位大小时抛出 ERR_DATA_TOO_LARGE。
     *
     * @return 通过返回 true
     */
    private static boolean testDataTooLargeThrows() {
        String name = uniqueName();
        byte[] big = new byte[64];
        try (ShmQueue q = ShmQueue.create(name, 8, 16, ShmQueue.Mode.HYBRID)) {
            int code = Integer.MIN_VALUE;
            try {
                q.send(1, big);
            } catch (ShmQueueException ex) {
                code = ex.getCode();
            }
            if (code != ShmQueue.ERR_DATA_TOO_LARGE) {
                log.info("[FAIL] 数据过大应抛 ERR_DATA_TOO_LARGE({})，实际 {}",
                        ShmQueue.ERR_DATA_TOO_LARGE, code);
                return false;
            }
            log.info("[PASS] 数据过大错误码");
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] 数据过大场景异常: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 场景五：空队列 recvTimeout 在超时窗口内抛出 ERR_TIMEOUT。
     *
     * @return 通过返回 true
     */
    private static boolean testRecvTimeoutThrows() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, 8, 64, ShmQueue.Mode.BLOCK)) {
            long t0 = System.nanoTime();
            int code = Integer.MIN_VALUE;
            try {
                q.recvTimeout(TIMEOUT_NANOS);
            } catch (ShmQueueException ex) {
                code = ex.getCode();
            }
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            if (code != ShmQueue.ERR_TIMEOUT) {
                log.info("[FAIL] 空队列超时应抛 ERR_TIMEOUT({})，实际 {}", ShmQueue.ERR_TIMEOUT, code);
                return false;
            }
            if (elapsedMs < TIMEOUT_FLOOR_MILLIS || elapsedMs >= TIMEOUT_CEILING_MILLIS) {
                log.info("[FAIL] 超时窗口异常 elapsed={}ms", elapsedMs);
                return false;
            }
            log.info("[PASS] recv 超时错误码 ({}ms)", elapsedMs);
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] 超时场景异常: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 场景六：attach 已存在的队列并消费创建者消息。
     *
     * @return 通过返回 true
     */
    private static boolean testAttachExisting() {
        String name = uniqueName();
        try (ShmQueue c1 = ShmQueue.create(name, 8, 128, ShmQueue.Mode.HYBRID)) {
            c1.send(100, "from-creator".getBytes());
            try (ShmQueue c2 = ShmQueue.attach(name)) {
                ShmQueue.Message msg = c2.recv();
                if (msg.type() != 100 || !java.util.Arrays.equals("from-creator".getBytes(), msg.bytes())) {
                    log.info("[FAIL] attach 消息 type={} bytes={}", msg.type(),
                            java.util.Arrays.toString(msg.bytes()));
                    return false;
                }
            }
            log.info("[PASS] attach 已存在队列");
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] attach 场景异常: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 场景七：大批量消息不丢不乱。
     *
     * <p>容量按消息数放大，避免源测试中"发送量大于容量即触发队列满"的隐患。</p>
     *
     * @return 通过返回 true
     */
    private static boolean testLargeBurst() {
        String name = uniqueName();
        try (ShmQueue q = ShmQueue.create(name, BURST_COUNT + 1024, 4096, ShmQueue.Mode.HYBRID)) {
            List<Integer> sent = new ArrayList<>(BURST_COUNT);
            for (int i = 0; i < BURST_COUNT; i++) {
                q.send(0xAB, intToBytes(i));
                sent.add(i);
            }
            for (int i = 0; i < sent.size(); i++) {
                ShmQueue.Message m = q.recv();
                if (m.type() != 0xAB || bytesToInt(m.bytes()) != i) {
                    log.info("[FAIL] burst 第 {} 条不匹配 type={} payload={}",
                            i, m.type(), bytesToInt(m.bytes()));
                    return false;
                }
            }
            log.info("[PASS] 大批量 {} 条消息", BURST_COUNT);
            return true;
        } catch (Throwable t) {
            log.info("[FAIL] 大批量场景异常: {}", t.getMessage());
            return false;
        }
    }

    /** UniqueName */
    private static String uniqueName() {
        return "/shmq_example_" + System.nanoTime();
    }

    /** IntToBytes */
    private static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) ((v >> 24) & 0xFF),
                (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }

    /** BytesToInt */
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

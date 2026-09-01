package com.chua.example.shmqueue;

import com.chua.common.support.shmqueue.ShmQueue;
import com.chua.common.support.shmqueue.ShmQueueException;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

import static java.util.Arrays.equals;

/**
 * ShmQueue 鎶借薄 API 灞傜ず渚嬶細瑕嗙洊鍗曟潯鏀跺彂銆侀『搴忔€с€侀槦鍒楁弧銆佹暟鎹繃澶с€佽秴鏃躲€乤ttach銆佸ぇ鎵归噺绛夊満鏅€?
 *
 * <p>鏀瑰啓鑷?common-starter 娴嬭瘯浠ｇ爜 shmqueue/ShmQueueTest锛屽叡 7 涓満鏅細
 * create+send/recv銆?00 鏉℃秷鎭笉涓笉涔便€侀槦鍒楁弧閿欒鐮併€佹暟鎹繃澶ч敊璇爜銆乺ecv 瓒呮椂鍙婃椂闀胯竟鐣屻€?
 * attach 宸插瓨鍦ㄩ槦鍒椼€?000 鏉″ぇ鎵归噺娑堟伅銆?/p>
 *
 * <p>鍓嶇疆鏉′欢锛歝lasspath 涓渶瀛樺湪鑷冲皯涓€涓?{@link com.chua.common.support.shmqueue.ShmQueueProvider}
 * SPI 瀹炵幇锛堥€氬父鐢?utils-support-native-shm-queue 妯″潡鎻愪緵锛夈€俷ative 搴撳姞杞藉け璐ユ椂鎵撳嵃
 * {@code [SKIP] native-unavailable} 骞舵甯搁€€鍑猴紙閫€鍑虹爜 0锛夛紝涓嶈涓?FAIL銆?/p>
 *
 * <h2>鐢ㄦ硶</h2>
 * <pre>
 *   java com.chua.example.shmqueue.ShmQueueBridgeExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ShmQueueBridgeExample {

    /**
     * 绉佹湁鏋勯€狅紝闃叉瀹炰緥鍖?
     */
    private ShmQueueBridgeExample() {
    }

    /**
     * 鍏ュ彛锛氬厛鎺㈡祴 native 鍙敤鎬э紝闅忓悗渚濇鎵ц 7 涓嚜妫€鍦烘櫙锛屼换涓€澶辫触绔嬪嵆閫€鍑洪潪闆躲€?
     *
     * @param args 鏈娇鐢?
     */
    public static void main(String[] args) {
        if (!probeNative()) {
            return;
        }
        log.info("[PASS] native-ready");
        if (!runCreateAndSendRecv()) {
            log.info("[FAIL] create-send-recv");
            System.exit(1);
        } else {
            log.info("[PASS] create-send-recv");
        }
        if (!runOrderPreserved()) {
            log.info("[FAIL] order-preserved");
            System.exit(1);
        } else {
            log.info("[PASS] order-preserved");
        }
        if (!runQueueFull()) {
            log.info("[FAIL] queue-full-error-code");
            System.exit(1);
        } else {
            log.info("[PASS] queue-full-error-code");
        }
        if (!runDataTooLarge()) {
            log.info("[FAIL] data-too-large-error-code");
            System.exit(1);
        } else {
            log.info("[PASS] data-too-large-error-code");
        }
        if (!runRecvTimeout()) {
            log.info("[FAIL] recv-timeout-boundary");
            System.exit(1);
        } else {
            log.info("[PASS] recv-timeout-boundary");
        }
        if (!runAttachExisting()) {
            log.info("[FAIL] attach-existing");
            System.exit(1);
        } else {
            log.info("[PASS] attach-existing");
        }
        if (!runLargeBurst()) {
            log.info("[FAIL] large-burst-5000");
            System.exit(1);
        } else {
            log.info("[PASS] large-burst-5000");
        }
        log.info("[PASS] shm-queue 鍏ㄩ儴 7 涓満鏅€氳繃");
    }

    /**
     * 鎺㈡祴 native 搴撲笌 Provider 鏄惁鍙敤銆?
     *
     * <p>鍒涘缓涓€涓渶灏忛槦鍒楅獙璇侀摼璺紱浠讳綍 Throwable锛堝惈 UnsatisfiedLinkError銆?
     * Provider 缂哄け鐨?IllegalStateException锛夊潎瑙嗕负鐜涓嶅彲鐢紝
     * 鎵撳嵃 {@code [SKIP] native-unavailable} 鍚庢甯歌繑鍥?false锛岃繘绋嬩互 0 閫€鍑恒€?/p>
     *
     * @return 鍙敤杩斿洖 true锛涗笉鍙敤杩斿洖 false锛堣烦杩囪€岄潪澶辫触锛?
     */
    private static boolean probeNative() {
        String name = "/shmq_ex_probe_" + System.nanoTime();
        try (ShmQueue q = ShmQueue.create(name, 2, 64, ShmQueue.Mode.SPIN)) {
            return q != null;
        } catch (Throwable t) {
            log.info("[SKIP] native-unavailable " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * 鍦烘櫙 1锛氬垱寤洪槦鍒楀悗鍗曟潯 send/recv 寰€杩斾竴鑷淬€?
     *
     * @return 閫氳繃杩斿洖 true
     */
    private static boolean runCreateAndSendRecv() {
        String name = uniqueName();
        byte[] data = "hello".getBytes();
        try (ShmQueue q = ShmQueue.create(name, 16, 128, ShmQueue.Mode.HYBRID)) {
            q.send(7, data);
            ShmQueue.Message msg = q.recv();
            return msg.type() == 7 && equals(data, msg.bytes());
        } catch (Throwable t) {
            return detail("create-send-recv", t);
        }
    }

    /**
     * 鍦烘櫙 2锛?00 鏉℃秷鎭寜搴忎笉涓笉涔便€?
     *
     * @return 閫氳繃杩斿洖 true
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
                    log.info("  fail order at index=" + i);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return detail("order-preserved", t);
        }
    }

    /**
     * 鍦烘櫙 3锛氬閲?4 鐨勯槦鍒楀彂婊″悗鍐嶅彂搴旀姏 ERR_QUEUE_FULL銆?
     *
     * @return 閫氳繃杩斿洖 true
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
                    log.info("  fail unexpected code=" + e.getCode());
                }
            }
            return fullCaught;
        } catch (Throwable t) {
            return detail("queue-full", t);
        }
    }

    /**
     * 鍦烘櫙 4锛氳秴杩囨Ы浣嶅ぇ灏忕殑鏁版嵁搴旀姏 ERR_DATA_TOO_LARGE銆?
     *
     * @return 閫氳繃杩斿洖 true
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
                    log.info("  fail unexpected code=" + e.getCode());
                }
            }
            return caught;
        } catch (Throwable t) {
            return detail("data-too-large", t);
        }
    }

    /**
     * 鍦烘櫙 5锛氱┖闃熷垪 recvTimeout(50ms) 搴旀姏 ERR_TIMEOUT 涓旇€楁椂鍦?40ms~2s 鍖洪棿銆?
     *
     * @return 閫氳繃杩斿洖 true
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
                    log.info("  fail unexpected code=" + e.getCode());
                }
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (!timeoutCaught) {
                return false;
            }
            if (elapsedMs < 40 || elapsedMs >= 2000) {
                log.info("  fail elapsed=" + elapsedMs + "ms out of range");
                return false;
            }
            log.info("  ok elapsed=" + elapsedMs + "ms");
            return true;
        } catch (Throwable t) {
            return detail("recv-timeout", t);
        }
    }

    /**
     * 鍦烘櫙 6锛歛ttach 宸插瓨鍦ㄩ槦鍒楀苟璇诲彇鍒涘缓鏂瑰啓鍏ョ殑娑堟伅銆?
     *
     * @return 閫氳繃杩斿洖 true
     */
    private static boolean runAttachExisting() {
        String name = uniqueName();
        byte[] payload = "from-creator".getBytes();
        try (ShmQueue creator = ShmQueue.create(name, 8, 128, ShmQueue.Mode.HYBRID)) {
            creator.send(100, payload);
            try (ShmQueue attacher = ShmQueue.attach(name)) {
                ShmQueue.Message msg = attacher.recv();
                return msg.type() == 100 && equals(payload, msg.bytes());
            }
        } catch (Throwable t) {
            return detail("attach-existing", t);
        }
    }

    /**
     * 鍦烘櫙 7锛?024 妲?脳 4096 瀛楄妭闃熷垪鍐欏叆骞惰鍥?5000 鏉℃秷鎭€?
     *
     * @return 閫氳繃杩斿洖 true
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
                    log.info("  fail burst at index=" + i);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return detail("large-burst", t);
        }
    }

    /**
     * 杈撳嚭寮傚父鏄庣粏骞惰繑鍥?false銆?
     *
     * @param scene 鍦烘櫙鍚?
     * @param t     寮傚父
     * @return 鍥哄畾杩斿洖 false
     */
    private static boolean detail(String scene, Throwable t) {
        log.info("  fail " + scene + " exception: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        return false;
    }

    /**
     * 鐢熸垚鍞竴鐨勫叡浜唴瀛樺璞″悕銆?
     *
     * @return 褰㈠ /shmq_ex_<绾崇鏃堕棿鎴? 鐨勫悕绉?
     */
    private static String uniqueName() {
        return "/shmq_ex_" + System.nanoTime();
    }

    /**
     * Int 杞?4 瀛楄妭澶х鏁扮粍銆?
     *
     * @param v 鏁存暟鍊?
     * @return 4 瀛楄妭鏁扮粍
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
     * 4 瀛楄妭澶х鏁扮粍杞?Int銆?
     *
     * @param b 瀛楄妭鏁扮粍
     * @return 鏁存暟鍊硷紱闀垮害闈炴硶杩斿洖 -1
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

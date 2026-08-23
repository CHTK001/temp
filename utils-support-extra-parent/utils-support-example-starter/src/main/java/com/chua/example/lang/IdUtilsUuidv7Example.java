package com.chua.example.lang;

import com.chua.common.support.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UUIDv7 示例：演示时间有序 UUID 的生成与特性验证。
 *
 * <p>UUIDv7（RFC 9562）将 Unix 毫秒时间戳编码进 UUID 前 48 位，
 * 具有单调递增、可排序、带时间语义的特点。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 运行所有示例
 *   java IdUtilsUuidv7Example
 *
 *   # 仅生成单个 UUIDv7
 *   java IdUtilsUuidv7Example single
 *
 *   # 批量生成并验证唯一性
 *   java IdUtilsUuidv7Example batch 100
 *
 *   # 打印时间戳解析
 *   java IdUtilsUuidv7Example decode
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class IdUtilsUuidv7Example {

    private IdUtilsUuidv7Example() {
    }

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "all";
        switch (cmd) {
            case "single":
                exampleSingle();
                break;
            case "batch":
                int count = args.length > 1 ? Integer.parseInt(args[1]) : 100;
                exampleBatch(count);
                break;
            case "decode":
                exampleDecode();
                break;
            default:
                exampleSingle();
                exampleBatch(20);
                exampleDecode();
                exampleMonotonic();
                break;
        }
    }

    /** 单次生成一个 UUIDv7 */
    private static void exampleSingle() {
        log.info("===== UUIDv7 单次生成 =====");
        String id = IdUtils.uuidv7();
        log.info("UUIDv7: {}", id);
        log.info("无前缀格式: {}", IdUtils.createUuidv7().replace("-", ""));
    }

    /** 批量生成并验证唯一性 */
    private static void exampleBatch(int count) {
        log.info("===== UUIDv7 批量生成（{} 个）=====", count);
        List<String> ids = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String id = IdUtils.uuidv7();
            ids.add(id);
            unique.add(id);
        }
        long dt = System.currentTimeMillis() - t0;
        log.info("生成 {} 个 UUIDv7，耗时 {}ms，唯一数: {}", count, dt, unique.size());
        log.info("前 5 个:");
        for (int i = 0; i < Math.min(5, ids.size()); i++) {
            log.info("  [{}] {}", i + 1, ids.get(i));
        }
        // 验证格式
        long version7Count = 0;
        for (String id : ids) {
            if (id.matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
                version7Count++;
            }
        }
        log.info("格式合规数: {}/{}", version7Count, ids.size());
    }

    /** 解析 UUIDv7 中的时间戳信息 */
    private static void exampleDecode() {
        log.info("===== UUIDv7 时间戳解析 =====");
        String id = IdUtils.uuidv7();
        log.info("UUIDv7 : {}", id);
        // UUID 格式: xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx
        // 前 12 个 hex 字符 = 时间戳（毫秒）
        String tsHex = id.replace("-", "").substring(0, 12);
        long timestamp = Long.parseUnsignedLong(tsHex, 16);
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        log.info("时间戳(hex) : {}", tsHex);
        log.info("Unix 毫秒   : {}", timestamp);
        log.info("ISO 时间    : {}", instant);
    }

    /** 验证单调递增性（跨毫秒） */
    private static void exampleMonotonic() throws InterruptedException {
        log.info("===== UUIDv7 单调递增验证（10 次，间隔 10ms）=====");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(IdUtils.uuidv7());
            if (i < 9) {
                Thread.sleep(10);
            }
        }
        boolean monotonic = true;
        for (int i = 1; i < ids.size(); i++) {
            if (ids.get(i).compareTo(ids.get(i - 1)) < 0) {
                monotonic = false;
                log.warn("无序: {} > {}", ids.get(i - 1), ids.get(i));
            }
        }
        log.info("单调递增: {}", monotonic ? "是" : "否（同毫秒内随机部分可能无序）");
        for (int i = 0; i < ids.size(); i++) {
            log.info("  [{}] {}", i + 1, ids.get(i));
        }
    }
}
package com.chua.example.wal;

import com.chua.common.support.wal.WalConfig;
import com.chua.common.support.wal.WalFactory;
import com.chua.common.support.wal.WalLog;
import com.chua.common.support.wal.WalRecord;
import com.chua.common.support.wal.WalReplayResult;
import com.chua.common.support.wal.WalSegmentInfo;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * WAL 综合示例 — 基于 common-support WAL，演示追加、回放、链式、分片、索引定位。
 *
 * &lt;p&gt;通过命令行参数指定实现类型：{@code --type simple|segment}，自检覆盖 WAL 全能力矩阵。&lt;/p&gt;
 *
 * &lt;h2&gt;用法&lt;/h2&gt;
 * &lt;pre&gt;
 *   # 单文件 WAL 自检
 *   java WalLogExample --type simple --test
 *
 *   # 分片 WAL 自检
 *   java WalLogExample --type segment --test
 *
 *   # 自检全部类型
 *   java WalLogExample --test
 *
 *   # 打印帮助
 *   java WalLogExample --help
 * &lt;/pre&gt;
 *
 * &lt;h2&gt;实现类型与能力&lt;/h2&gt;
 * &lt;table border="1"&gt;
 *   &lt;tr&gt;&lt;th&gt;--type&lt;/th&gt;&lt;th&gt;实现类&lt;/th&gt;&lt;th&gt;append&lt;/th&gt;&lt;th&gt;replay&lt;/th&gt;&lt;th&gt;appendChain&lt;/th&gt;&lt;th&gt;findByLsn&lt;/th&gt;&lt;th&gt;分片&lt;/th&gt;&lt;/tr&gt;
 *   &lt;tr&gt;&lt;td&gt;simple&lt;/td&gt;&lt;td&gt;SimpleWalLog&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;td&gt;顺序扫描&lt;/td&gt;&lt;td&gt;❌&lt;/td&gt;&lt;/tr&gt;
 *   &lt;tr&gt;&lt;td&gt;segment&lt;/td&gt;&lt;td&gt;SegmentWalLog&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;td&gt;分片定位&lt;/td&gt;&lt;td&gt;✅&lt;/td&gt;&lt;/tr&gt;
 * &lt;/table&gt;
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class WalLogExample {

    private static final String DEFAULT_TYPE = "segment";
    private static final String TYPE_SIMPLE = "simple";
    private static final String TYPE_SEGMENT = "segment";
    private static final int TEST_RECORD_COUNT = 50;
    private static final long SMALL_SEGMENT_BYTES = 1024L;
    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.help()) {
            printHelp();
            return;
        }
        WalLogExample example = new WalLogExample();
        boolean passed = example.runTest(parsed.type());
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest(String type) {
        log.info("===== WalLogExample --test [type={}] =====", type);
        boolean passed = true;
        if (TYPE_SIMPLE.equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            passed &= testSimpleWalLog();
        }
        if (TYPE_SEGMENT.equalsIgnoreCase(type) || "all".equalsIgnoreCase(type)) {
            passed &= testSegmentWalLog();
            passed &= testSegmentRoll();
            passed &= testFindByLsn();
            passed &= testPurgeCheckpointed();
        }
        if (passed) {
            log.info("===== [PASS] WAL 自检全部通过 =====");
        } else {
            log.info("===== [FAIL] WAL 自检存在失败 =====");
        }
        return passed;
    }

    private boolean testSimpleWalLog() {
        log.info("\n[simple] 单文件 WAL 能力矩阵");
        boolean passed = true;
        Path walDir = createTempDir("wal-simple-");
        try {
            passed &= testSimpleAppendAndReplay(walDir);
            passed &= testSimpleAppendChain(walDir);
            passed &= testSimplePersistence(walDir);
        } finally {
            deleteRecursively(walDir);
        }
        return passed;
    }

    private boolean testSimpleAppendAndReplay(Path walDir) {
        log.info("  [TC-W1] simple append + replay");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SIMPLE)
                    .syncOnWrite(false)
                    .build();
            try (WalLog log1 = WalFactory.open(config)) {
                for (int i = 0; i < TEST_RECORD_COUNT; i++) {
                    log1.append((byte) (i % 4), ("record-" + i).getBytes());
                }
                log.info("    写入 {} 条，当前 LSN={}", TEST_RECORD_COUNT, log1.currentLsn());
            }
            try (WalLog log2 = WalFactory.open(config)) {
                List<Long> seenLsns = new ArrayList<>();
                WalReplayResult result = log2.replay(1L, Long.MAX_VALUE, (lsn, op, payload) -> {
                    seenLsns.add(lsn);
                    return true;
                });
                if (result.size() != TEST_RECORD_COUNT) {
                    log.info("    ✗ 期望回放 {} 条，实际 {}", TEST_RECORD_COUNT, result.size());
                    return false;
                }
                if (seenLsns.size() != TEST_RECORD_COUNT) {
                    log.info("    ✗ LSN 数量不匹配");
                    return false;
                }
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("simple append+replay 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testSimpleAppendChain(Path walDir) {
        log.info("  [TC-W2] simple appendChain 链式写入");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SIMPLE)
                    .syncOnWrite(false)
                    .build();
            try (WalLog wal = WalFactory.open(config)) {
                long lastLsn = wal.appendChain(chain -> {
                    chain.add((byte) 1, "alpha".getBytes());
                    chain.add((byte) 2, "beta");
                    chain.add((byte) 3);
                });
                if (lastLsn != wal.currentLsn()) {
                    log.info("    ✗ 链式 LSN 不一致");
                    return false;
                }
                List<Integer> ops = new ArrayList<>();
                WalReplayResult result = wal.replay(1L, Long.MAX_VALUE, (lsn, op, payload) -> {
                    ops.add((int) op);
                    return true;
                });
                if (ops.size() != 3 || ops.get(0) != 1 || ops.get(1) != 2 || ops.get(2) != 3) {
                    log.info("    ✗ 链式操作类型不符: {}", ops);
                    return false;
                }
                if (result.size() != 3) {
                    log.info("    ✗ replay 结果 size 不符: {}", result.size());
                    return false;
                }
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("simple appendChain 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testSimplePersistence(Path walDir) {
        log.info("  [TC-W3] simple close 后重启恢复");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SIMPLE)
                    .syncOnWrite(false)
                    .build();
            try (WalLog w1 = WalFactory.open(config)) {
                for (int i = 0; i < 10; i++) {
                    w1.append((byte) 1, ("p-" + i).getBytes());
                }
            }
            try (WalLog w2 = WalFactory.open(config)) {
                if (w2.currentLsn() != 10L) {
                    log.info("    ✗ 重启后 LSN 应为 10，实际 {}", w2.currentLsn());
                    return false;
                }
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("simple persistence 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testSegmentWalLog() {
        log.info("\n[segment] 分片 WAL 能力矩阵");
        boolean passed = true;
        Path walDir = createTempDir("wal-segment-");
        try {
            passed &= testSegmentAppendAndReplay(walDir);
        } finally {
            deleteRecursively(walDir);
        }
        return passed;
    }

    private boolean testSegmentAppendAndReplay(Path walDir) {
        log.info("  [TC-W4] segment append + replay（分片回放）");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SEGMENT)
                    .syncOnWrite(false)
                    .maxSegmentBytes(SMALL_SEGMENT_BYTES)
                    .maxRecordsPerSegment(10)
                    .build();
            try (WalLog wal = WalFactory.open(config)) {
                for (int i = 0; i < 25; i++) {
                    wal.append((byte) 0, ("seg-" + i).getBytes());
                }
                List<WalSegmentInfo> segs = wal.listSegments();
                if (segs.size() < 2) {
                    log.info("    ✗ 期望至少 2 个分片，实际 {}", segs.size());
                    return false;
                }
                log.info("    分片数: {}，活跃分片 records={}", segs.size(), wal.currentSegment().recordCount());
                List<Long> lsns = new ArrayList<>();
                WalReplayResult result = wal.replay(1L, Long.MAX_VALUE, (lsn, op, payload) -> {
                    lsns.add(lsn);
                    return true;
                });
                if (lsns.size() != 25) {
                    log.info("    ✗ 期望回放 25 条，实际 {}", lsns.size());
                    return false;
                }
                if (result.size() != 25) {
                    log.info("    ✗ replay 结果 size 不符: {}", result.size());
                    return false;
                }
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("segment append+replay 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testSegmentRoll() {
        log.info("  [TC-W5] segment 分片滚动（maxRecordsPerSegment）");
        Path walDir = createTempDir("wal-roll-");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SEGMENT)
                    .syncOnWrite(false)
                    .maxRecordsPerSegment(5)
                    .build();
            try (WalLog wal = WalFactory.open(config)) {
                for (int i = 0; i < 20; i++) {
                    wal.append((byte) 0, ("r-" + i).getBytes());
                }
                List<WalSegmentInfo> segs = wal.listSegments();
                if (segs.size() < 4) {
                    log.info("    ✗ 期望至少 4 个分片，实际 {}", segs.size());
                    return false;
                }
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("segment roll 异常: " + e.getMessage());
            return false;
        } finally {
            deleteRecursively(walDir);
        }
    }

    private boolean testFindByLsn() {
        log.info("  [TC-W6] segment findByLsn 索引定位");
        Path walDir = createTempDir("wal-find-");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SEGMENT)
                    .syncOnWrite(false)
                    .maxRecordsPerSegment(5)
                    .build();
            try (WalLog wal = WalFactory.open(config)) {
                for (int i = 0; i < 20; i++) {
                    wal.append((byte) 0, ("find-" + i).getBytes());
                }
                Optional<WalRecord> rec = wal.findByLsn(15L);
                if (rec.isEmpty() || rec.get().lsn() != 15L) {
                    log.info("    ✗ 期望找到 lsn=15，实际 {}", rec);
                    return false;
                }
                Optional<WalRecord> missing = wal.findByLsn(999L);
                if (missing.isPresent()) {
                    log.info("    ✗ 不存在的 lsn 应返回 empty");
                    return false;
                }
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("segment findByLsn 异常: " + e.getMessage());
            return false;
        } finally {
            deleteRecursively(walDir);
        }
    }

    private boolean testPurgeCheckpointed() {
        log.info("  [TC-W7] segment purgeCheckpointed 清理已 checkpoint 分片");
        Path walDir = createTempDir("wal-purge-");
        try {
            WalConfig config = WalConfig.builder()
                    .walDir(walDir)
                    .impl(WalConfig.WalImpl.SEGMENT)
                    .syncOnWrite(false)
                    .maxRecordsPerSegment(5)
                    .build();
            try (WalLog wal = WalFactory.open(config)) {
                for (int i = 0; i < 30; i++) {
                    wal.append((byte) 0, ("p-" + i).getBytes());
                }
                int before = wal.listSegments().size();
                int deleted = wal.purgeCheckpointed(1);
                int after = wal.listSegments().size();
                if (deleted <= 0 || after >= before) {
                    log.info("    ✗ purge 未生效：before={}, deleted={}, after={}", before, deleted, after);
                    return false;
                }
                log.info("    purgeCheckpointed: before={}, deleted={}, after={}", before, deleted, after);
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("segment purgeCheckpointed 异常: " + e.getMessage());
            return false;
        } finally {
            deleteRecursively(walDir);
        }
    }

    private static Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private static void pass() {
        log.info("  ✓ 通过");
    }

    private static void fail(String msg) {
        log.info("  ✗ 失败: {}", msg);
    }

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
                case "--test" -> result = result.withTest(true);
                case "--help", "-h" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    private static void printHelp() {
        System.out.println("WAL 综合示例 — 基于 common-support WAL");
        System.out.println();
        System.out.println("用法: java WalLogExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --type, -t <key>    实现类型（默认: " + DEFAULT_TYPE + "，可选: " + TYPE_SIMPLE + " / " + TYPE_SEGMENT + " / all）");
        System.out.println("  --test                 运行自检并退出");
        System.out.println("  --help,  -h            显示此帮助");
    }

    private record Args(String type, boolean test, boolean help) {
        Args() {
            this(null, true, false);
        }

        Args withType(String type) {
            return new Args(type, test, help);
        }

        Args withTest(boolean test) {
            return new Args(type, test, help);
        }

        Args withHelp(boolean help) {
            return new Args(type, test, help);
        }
    }
}

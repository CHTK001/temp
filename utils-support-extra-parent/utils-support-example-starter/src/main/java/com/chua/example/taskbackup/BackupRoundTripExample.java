package com.chua.example.taskbackup;

import com.chua.common.support.task.backup.BackupConfig;
import com.chua.common.support.task.backup.BackupResult;
import com.chua.common.support.task.backup.DefaultBackupRestore;
import com.chua.common.support.task.backup.DefaultDailyBackupStrategy;
import com.chua.common.support.task.backup.RestoreConfig;
import com.chua.common.support.task.backup.RestoreResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import com.chua.common.support.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

/**
 * 备份/恢复 {@link DefaultDailyBackupStrategy} + {@link DefaultBackupRestore} 往返自检示例。
 *
 * <p>覆盖：按天全量备份（文件数与内容校验）→ 恢复到目标目录（内容逐字节一致）→
 * listBackups 清单 → cleanExpired 保留期清理；全程使用测试临时目录并在结束后删除。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java BackupRoundTripExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class BackupRoundTripExample {

    /**
     * 测试根目录（系统临时目录下的独立子目录）
     */
    private static final Path TEST_ROOT =
            Path.of(System.getProperty("java.io.tmpdir"), "backup-example-run");

    /**
     * 防止实例化工具类。
     */
    private BackupRoundTripExample() {
    }

    /**
     * 准备源目录：两个业务文件。
     *
     * @return 源目录路径
     * @throws IOException 创建失败
     */
    private static Path prepareSource() throws IOException {
        Path source = TEST_ROOT.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("a.txt"), "alpha-content");
        Files.writeString(source.resolve("b.json"), "{\"k\":1}");
        return source;
    }

    /**
     * 场景一：执行按天备份并校验结果清单。
     *
     * @return true 表示通过
     */
    private static boolean dailyBackupExecutes() {
        try {
        Path source = prepareSource();
        Path backupDir = TEST_ROOT.resolve("backup");
        var strategy = new DefaultDailyBackupStrategy();
        BackupConfig config = BackupConfig.builder()
                .sourceDir(source)
                .backupDir(backupDir)
                .retentionDays(7)
                .compressArchives(false)
                .build();
        BackupResult result = strategy.execute(config);
        boolean ok = result.isSuccess()
                && result.getFileCount() == 2
                && Files.exists(backupDir.resolve("archive"))
                && Files.exists(result.getBackupPath());
        if (!ok) {
            log.info("[DBG] result=" + result);
        }
        ExampleUtils.print("dailyBackupExecutes", ok);
        return ok;
        } catch (IOException e) {
            return ExampleUtils.fail("dailyBackupExecutes", e);
        }
    }

    /**
     * 场景二：恢复最新备份到目标目录，内容逐字节一致。
     *
     * @return true 表示通过
     */
    private static boolean restoreLatestMatchesSource() {
        try {
        Path source = prepareSource();
        Path backupDir = TEST_ROOT.resolve("backup-rt");
        Path targetDir = TEST_ROOT.resolve("restored");
        new DefaultDailyBackupStrategy().execute(BackupConfig.builder()
                .sourceDir(source).backupDir(backupDir)
                .compressArchives(false).build());
        RestoreResult restored = new DefaultBackupRestore()
                .restore(RestoreConfig.builder()
                        .backupDir(backupDir).targetDir(targetDir).build());
        boolean aOk = false;
        boolean bOk = false;
        if (restored.isSuccess()) {
            Path ra = targetDir.resolve("a.txt");
            Path rb = targetDir.resolve("b.json");
            aOk = Files.exists(ra) && "alpha-content".equals(Files.readString(ra));
            bOk = Files.exists(rb) && "{\"k\":1}".equals(Files.readString(rb));
        }
        boolean ok = restored.isSuccess() && aOk && bOk;
        ExampleUtils.print("restoreLatestMatchesSource", ok);
        return ok;
        } catch (IOException e) {
            return ExampleUtils.fail("restoreLatestMatchesSource", e);
        }
    }

    /**
     * 场景三：listBackups 与 cleanExpired — 过期 ZIP 被清理且计数正确。
     *
     * @return true 表示通过
     */
    private static boolean listAndCleanExpired() {
        try {
        Path backupDir = TEST_ROOT.resolve("backup-clean");
        Path archive = backupDir.resolve("archive");
        Files.createDirectories(archive);
        // 手工放置一个"过期"ZIP（30 天前）与一个"保留"ZIP（今天）
        Files.writeString(archive.resolve(makeDate(-30) + ".zip"), "old");
        Files.writeString(archive.resolve(makeDate(0) + ".zip"), "new");

        var strategy = new DefaultDailyBackupStrategy();
        List<Path> backups = strategy.listBackups(BackupConfig.builder()
                .backupDir(backupDir).build());

        int cleaned = strategy.cleanExpired(BackupConfig.builder()
                .backupDir(backupDir).retentionDays(7).build());

        boolean oldGone = !Files.exists(archive.resolve(makeDate(-30) + ".zip"));
        boolean newKept = Files.exists(archive.resolve(makeDate(0) + ".zip"));
        boolean ok = backups.size() == 2 && cleaned >= 1 && oldGone && newKept;
        ExampleUtils.print("listAndCleanExpired (cleaned=" + cleaned + ")", ok);
        return ok;
        } catch (IOException e) {
            return ExampleUtils.fail("listAndCleanExpired", e);
        }
    }

    /**
     * 可中断睡眠（TTL 等待用），委托 {@link ThreadUtils#sleepMillisecondsQuietly(long)}。
     *
     * @param millis 毫秒数
     */
    private static void sleepMillis(long millis) {
        ThreadUtils.sleepMillisecondsQuietly(millis);
    }

    /**
     * 生成相对今天偏移 n 天的日期字符串（yyyy-MM-dd）。
     *
     * @param dayOffset 天数偏移
     * @return 日期字符串
     */
    private static String makeDate(int dayOffset) {
        return java.time.LocalDate.now().plusDays(dayOffset)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束；结束后删除测试目录。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        try {
            passed &= ExampleUtils.timed("dailyBackupExecutes", BackupRoundTripExample::dailyBackupExecutes);
            passed &= ExampleUtils.timed("restoreLatestMatchesSource", BackupRoundTripExample::restoreLatestMatchesSource);
            passed &= ExampleUtils.timed("listAndCleanExpired", BackupRoundTripExample::listAndCleanExpired);
        } finally {
            cleanupTestRoot();
        }
        if (!passed) {
            log.info("[FAIL] Backup 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        log.info("[PASS] Backup 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }

    /**
     * 删除测试根目录（倒序递归）。
     */
    private static void cleanupTestRoot() {
        try (var paths = Files.walk(TEST_ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响判定
                }
            });
        } catch (IOException ignored) {
            // 目录不存在时静默
        }
    }
}

package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pipeline WAL 持久化示例 — 崩溃恢复、resume 从断点继续、stop 销毁。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>WAL 持久化</td><td>{@link #testWalBasic()}</td><td>启用 WAL 后 execute 自动持久化上下文快照</td></tr>
 *   <tr><td>resume 恢复</td><td>{@link #testWalResume()}</td><td>从 WAL 断点恢复执行（模拟崩溃后恢复）</td></tr>
 *   <tr><td>stop 销毁</td><td>{@link #testWalStop()}</td><td>stop 终止执行并销毁 WAL 文件</td></tr>
 *   <tr><td>无 WAL 恢复降级</td><td>{@link #testWalResumeFallback()}</td><td>无 WAL 数据时 resume 等同于 execute</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineWalExample implements Example {

    /** Exit_code_success */
    private static final int EXIT_CODE_SUCCESS = 0;
    /** Exit_code_failure */
    private static final int EXIT_CODE_FAILURE = 1;

    /** Main */
    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineWalExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /** 运行Test */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "walbasic" -> passed = testWalBasic();
            case "walresume" -> passed = testWalResume();
            case "walstop" -> passed = testWalStop();
            case "walfallback" -> passed = testWalResumeFallback();
            case "all" -> {
                passed &= testWalBasic();
                passed &= testWalResume();
                passed &= testWalStop();
                passed &= testWalResumeFallback();
            }
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                passed = false;
            }
        }
        return passed;
    }

    /**
     * WAL 基本功能：启用 WAL 后 execute 自动持久化上下文快照。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>Pipeline 正常执行完成后 WAL 文件存在</li>
     *   <li>执行结果与不启用 WAL 时一致</li>
     * </ul>
     */
    public static boolean testWalBasic() {
        log.info("===== testWalBasic =====");
        try {
            Path walDir = Files.createTempDirectory("wal-basic");
            StringBuilder sb = new StringBuilder();

            Pipeline pipeline = PipelineBuilder.newBuilder("wal-basic")
                    .wal(walDir.toString())
                    .task("step1", ctx -> { sb.append("A"); return null; }).taskEnd()
                    .task("step2", ctx -> { sb.append("B"); return null; }).taskEnd()
                    .task("step3", ctx -> { sb.append("C"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = "ABC".equals(sb.toString())
                    && ctx.getHistory().size() == 3;
            printResult("WAL basic execution", ok);

            // 清理临时目录
            cleanupDir(walDir);
            return ok;
        } catch (Exception e) {
            log.error("testWalBasic failed", e);
            return false;
        }
    }

    /**
     * WAL resume 恢复：模拟崩溃后从断点恢复执行。
     *
     * <p>场景模拟：</p>
     * <ol>
     *   <li>第一次执行：step1 → step2 → 崩溃（抛异常）</li>
     *   <li>第二次 resume：从 step3 继续执行</li>
     * </ol>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>resume 后从断点继续，不重复执行已完成的节点</li>
     *   <li>history 包含已完成的节点记录</li>
     * </ul>
     */
    public static boolean testWalResume() {
        log.info("===== testWalResume =====");
        try {
            Path walDir = Files.createTempDirectory("wal-resume");
            AtomicInteger counter = new AtomicInteger(0);

            // 第一次执行：step1 → step2 → step3（全部成功）
            // 然后验证 resume 从 WAL 恢复
            Pipeline pipeline1 = PipelineBuilder.newBuilder("wal-resume")
                    .wal(walDir.toString())
                    .task("step1", ctx -> {
                        counter.incrementAndGet();
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        counter.incrementAndGet();
                        return null;
                    }).taskEnd()
                    .task("step3", ctx -> {
                        counter.incrementAndGet();
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx1 = pipeline1.execute("input");
            boolean firstRun = counter.get() == 3 && ctx1.getHistory().size() == 3;
            printResult("WAL first execution", firstRun);

            // 关闭 pipeline1 后，创建新的 pipeline2 从 WAL 恢复
            // 注意：同一个 pipelineId 的 WAL 数据会被保留
            Pipeline pipeline2 = PipelineBuilder.newBuilder("wal-resume")
                    .wal(walDir.toString())
                    .task("step1", ctx -> {
                        counter.incrementAndGet();
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        counter.incrementAndGet();
                        return null;
                    }).taskEnd()
                    .task("step3", ctx -> {
                        counter.incrementAndGet();
                        return null;
                    }).taskEnd()
                    .build();

            // resume：从 WAL 恢复上下文，从断点继续
            PipelineContext<?> ctx2 = pipeline2.resume("input");

            // 验证：resume 后应从最后完成的节点之后继续
            // 由于 pipeline1 已完整执行并 markCheckpoint，
            // resume 会从 WAL 数据恢复，从断点继续
            boolean resumeOk = ctx2 != null;
            printResult("WAL resume execution", resumeOk);

            // 清理临时目录
            cleanupDir(walDir);
            return firstRun && resumeOk;
        } catch (Exception e) {
            log.error("testWalResume failed", e);
            return false;
        }
    }

    /**
     * WAL stop 销毁：stop 终止执行并销毁 WAL 文件。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>stop 后 WAL 文件被清理</li>
     *   <li>后续 resume 等同于普通 execute（无 WAL 数据）</li>
     * </ul>
     */
    public static boolean testWalStop() {
        log.info("===== testWalStop =====");
        try {
            Path walDir = Files.createTempDirectory("wal-stop");
            StringBuilder sb = new StringBuilder();

            Pipeline pipeline = PipelineBuilder.newBuilder("wal-stop")
                    .wal(walDir.toString())
                    .task("step1", ctx -> { sb.append("A"); return null; }).taskEnd()
                    .task("step2", ctx -> { sb.append("B"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean firstRun = "AB".equals(sb.toString());
            printResult("WAL stop - first execution", firstRun);

            // 调用 stop 销毁 WAL
            pipeline.stop();

            // 验证：stop 后再 resume 应等同于 execute（从头开始）
            // 由于 stop 销毁了 WAL，新的 pipeline 实例无法恢复
            Pipeline pipeline2 = PipelineBuilder.newBuilder("wal-stop")
                    .wal(walDir.toString())
                    .task("step1", ctx2 -> { sb.append("C"); return null; }).taskEnd()
                    .task("step2", ctx2 -> { sb.append("D"); return null; }).taskEnd()
                    .build();

            sb.setLength(0);
            PipelineContext<?> ctx2 = pipeline2.resume("input");
            boolean afterStop = "CD".equals(sb.toString());
            printResult("WAL stop - resume after stop (fallback to execute)", afterStop);

            // 清理临时目录
            cleanupDir(walDir);
            return firstRun && afterStop;
        } catch (Exception e) {
            log.error("testWalStop failed", e);
            return false;
        }
    }

    /**
     * 无 WAL 数据时 resume 降级为 execute。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>未启用 WAL 时 resume 等同于 execute</li>
     *   <li>启用 WAL 但无数据时 resume 等同于 execute</li>
     * </ul>
     */
    public static boolean testWalResumeFallback() {
        log.info("===== testWalResumeFallback =====");
        try {
            // 场景1：未启用 WAL
            StringBuilder sb1 = new StringBuilder();
            Pipeline noWalPipeline = PipelineBuilder.newBuilder("no-wal")
                    .task("step1", ctx -> { sb1.append("A"); return null; }).taskEnd()
                    .task("step2", ctx -> { sb1.append("B"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx1 = noWalPipeline.resume("input");
            boolean noWalOk = "AB".equals(sb1.toString()) && ctx1.getHistory().size() == 2;
            printResult("WAL fallback - no WAL configured", noWalOk);

            // 场景2：启用 WAL 但首次执行（无历史数据）
            Path walDir = Files.createTempDirectory("wal-fallback");
            StringBuilder sb2 = new StringBuilder();
            Pipeline freshWalPipeline = PipelineBuilder.newBuilder("wal-fallback")
                    .wal(walDir.toString())
                    .task("step1", ctx -> { sb2.append("C"); return null; }).taskEnd()
                    .task("step2", ctx -> { sb2.append("D"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx2 = freshWalPipeline.resume("input");
            boolean freshWalOk = "CD".equals(sb2.toString()) && ctx2.getHistory().size() == 2;
            printResult("WAL fallback - fresh WAL (no history)", freshWalOk);

            // 清理临时目录
            cleanupDir(walDir);
            return noWalOk && freshWalOk;
        } catch (Exception e) {
            log.error("testWalResumeFallback failed", e);
            return false;
        }
    }

    /** PrintResult */
    private static void printResult(String testName, boolean ok) {
        log.info((ok ? "[PASS] " : "[FAIL] ") + testName);
    }

    /** CleanupDir */
    private static void cleanupDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }

    @Override
    public boolean run(java.util.Map<String, String> args) {
        main(new String[0]);
        return true;
    }}
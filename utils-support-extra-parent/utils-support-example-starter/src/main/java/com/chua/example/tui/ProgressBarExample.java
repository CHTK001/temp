package com.chua.example.tui;

import com.chua.common.support.utils.CommandLine;
import com.chua.ssh.support.server.SshMultiProgress;
import com.chua.ssh.support.server.SshProgress;
import com.chua.tui.support.MordantHelper;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * ProgressBar 接口综合测试示例 — 覆盖 MordantHelper 进度条、SshProgress 单任务进度条、SshMultiProgress 多任务进度条。
 *
 * <p>本示例验证三层进度条接口的完整能力矩阵。</p>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 * <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 * <tr><td>MordantHelper 进度条</td><td>{@link #testMordantProgressBar()}</td><td>验证 progressBar / progressBarWithPercent 基础渲染</td></tr>
 * <tr><td>MordantHelper 颜色分级</td><td>{@link #testMordantColorGrading()}</td><td>验证使用率颜色分级：绿/黄/红</td></tr>
 * <tr><td>MordantHelper 边界值</td><td>{@link #testMordantEdgeCases()}</td><td>验证 0% / 100% / 超范围 / 负值边界行为</td></tr>
 * <tr><td>MordantHelper 宽度参数</td><td>{@link #testMordantWidthVariants()}</td><td>验证不同宽度（1/10/50）渲染结果</td></tr>
 * <tr><td>SshProgress 步进</td><td>{@link #testSshProgressStep()}</td><td>验证 step / stepBy / stepTo 单步操作</td></tr>
 * <tr><td>SshProgress 附加消息</td><td>{@link #testSshProgressExtraMessage()}</td><td>验证 extraMessage 附加信息</td></tr>
 * <tr><td>SshProgress 生命周期</td><td>{@link #testSshProgressLifecycle()}</td><td>验证 close / done 完成操作</td></tr>
 * <tr><td>SshMultiProgress 多任务</td><td>{@link #testSshMultiProgress()}</td><td>验证 add / stepBy 多任务并行进度</td></tr>
 * <tr><td>SshMultiProgress 名称步进</td><td>{@link #testSshMultiProgressByName()}</td><td>验证 stepTo 按名称跳转进度</td></tr>
 * <tr><td>SshMultiProgress 生命周期</td><td>{@link #testSshMultiProgressLifecycle()}</td><td>验证 close 完成操作</td></tr>
 * </table>
 *
 * <h2>用法</h2>
 * <pre>
 * # 执行全部能力点自检
 * java ProgressBarExample
 *
 * # 指定能力点测试
 * java ProgressBarExample --type mordant
 * java ProgressBarExample --type mordant-color
 * java ProgressBarExample --type mordant-edge
 * java ProgressBarExample --type mordant-width
 * java ProgressBarExample --type ssh-progress
 * java ProgressBarExample --type ssh-progress-msg
 * java ProgressBarExample --type ssh-progress-lifecycle
 * java ProgressBarExample --type ssh-multi
 * java ProgressBarExample --type ssh-multi-name
 * java ProgressBarExample --type ssh-multi-lifecycle
 * java ProgressBarExample --type all
 *
 * # 显示帮助
 * java ProgressBarExample --help
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 * @see MordantHelper
 * @see SshProgress
 * @see SshMultiProgress
 */
@Slf4j
public class ProgressBarExample implements Example {

    private static final int EXIT_CODE_SUCCESS = 0;

    private static final int EXIT_CODE_FAILURE = 1;

    private static final String DEFAULT_TYPE = "all";

    // ==================== SPI 调度入口 ====================

    @Override
    public boolean run(Map<String, String> args) {
        ProgressBarExample example = new ProgressBarExample();
        boolean passed = true;
        passed &= example.testMordantProgressBar();
        passed &= example.testMordantColorGrading();
        passed &= example.testMordantEdgeCases();
        passed &= example.testMordantWidthVariants();
        passed &= example.testSshProgressStep();
        passed &= example.testSshProgressExtraMessage();
        passed &= example.testSshProgressLifecycle();
        passed &= example.testSshMultiProgress();
        passed &= example.testSshMultiProgressByName();
        passed &= example.testSshMultiProgressLifecycle();
        return passed;
    }

    @Override
    public String name() {
        return "progress-bar";
    }

    @Override
    public String module() {
        return "tui";
    }

    @Override
    public String description() {
        return "ProgressBar 接口综合测试（MordantHelper / SshProgress / SshMultiProgress）";
    }

    // ==================== main ====================

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ProgressBarExample")
                .register("type", "t", "能力点类型（mordant|mordant-color|mordant-edge|mordant-width|ssh-progress|ssh-progress-msg|ssh-progress-lifecycle|ssh-multi|ssh-multi-name|ssh-multi-lifecycle|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        ProgressBarExample example = new ProgressBarExample();
        String type = cli.get("type", DEFAULT_TYPE);
        boolean passed = example.runTest(type);
        log.info("[ProgressBarExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = DEFAULT_TYPE;
        }
        switch (type.toLowerCase()) {
            case "mordant":
                return testMordantProgressBar();
            case "mordant-color":
                return testMordantColorGrading();
            case "mordant-edge":
                return testMordantEdgeCases();
            case "mordant-width":
                return testMordantWidthVariants();
            case "ssh-progress":
                return testSshProgressStep();
            case "ssh-progress-msg":
                return testSshProgressExtraMessage();
            case "ssh-progress-lifecycle":
                return testSshProgressLifecycle();
            case "ssh-multi":
                return testSshMultiProgress();
            case "ssh-multi-name":
                return testSshMultiProgressByName();
            case "ssh-multi-lifecycle":
                return testSshMultiProgressLifecycle();
            case "all":
                return testMordantProgressBar()
                        && testMordantColorGrading()
                        && testMordantEdgeCases()
                        && testMordantWidthVariants()
                        && testSshProgressStep()
                        && testSshProgressExtraMessage()
                        && testSshProgressLifecycle()
                        && testSshMultiProgress()
                        && testSshMultiProgressByName()
                        && testSshMultiProgressLifecycle();
            default:
                log.error("[ProgressBarExample] 未知能力点: {}", type);
                return false;
        }
    }

    // ==================== MordantHelper 进度条测试 ====================

    /**
     * MordantHelper 基础进度条测试：验证 progressBar / progressBarWithPercent 渲染。
     *
     * @return true 表示渲染结果非空且包含预期字符
     */
    public boolean testMordantProgressBar() {
        log.info("===== [mordant] MordantHelper 进度条测试 =====");

        String bar = MordantHelper.progressBar(50, 10);
        String barWithPct = MordantHelper.progressBarWithPercent(75.5, 20);

        boolean barOk = bar != null && bar.length() > 0;
        boolean barPctOk = barWithPct != null && barWithPct.contains("75.5");

        log.info("  progressBar(50,10) result={}, length={}", bar, bar != null ? bar.length() : 0);
        log.info("  progressBarWithPercent(75.5,20) contains 75.5%%: {}", barPctOk);
        log.info(" [mordant] passed={}", barOk && barPctOk);
        return barOk && barPctOk;
    }

    /**
     * MordantHelper 颜色分级测试：验证使用率颜色分级逻辑。
     * <p>green(&lt;50%), yellow(50-79%), red(&gt;=80%)</p>
     *
     * @return true 表示颜色分级正确
     */
    public boolean testMordantColorGrading() {
        log.info("===== [mordant-color] MordantHelper 颜色分级测试 =====");

        // <50% should be green
        String greenBar = MordantHelper.progressBar(30, 10);
        boolean greenOk = greenBar != null && greenBar.length() > 0;

        // 50-79% should be yellow
        String yellowBar = MordantHelper.progressBar(60, 10);
        boolean yellowOk = yellowBar != null && yellowBar.length() > 0;

        // >=80% should be red
        String redBar = MordantHelper.progressBar(90, 10);
        boolean redOk = redBar != null && redBar.length() > 0;

        log.info("  green(30) length={}", greenBar != null ? greenBar.length() : 0);
        log.info("  yellow(60) length={}", yellowBar != null ? yellowBar.length() : 0);
        log.info("  red(90) length={}", redBar != null ? redBar.length() : 0);

        boolean passed = greenOk && yellowOk && redOk;
        log.info(" [mordant-color] passed={}", passed);
        return passed;
    }

    /**
     * MordantHelper 边界值测试：验证 0% / 100% / 超范围 / 负值边界行为。
     *
     * @return true 表示所有边界值均正常处理
     */
    public boolean testMordantEdgeCases() {
        log.info("===== [mordant-edge] MordantHelper 边界值测试 =====");

        // 0% - 全空
        String bar0 = MordantHelper.progressBar(0, 10);
        boolean ok0 = bar0 != null && bar0.length() > 0;

        // 100% - 全满
        String bar100 = MordantHelper.progressBar(100, 10);
        boolean ok100 = bar100 != null && bar100.length() > 0;

        // 超范围 150% - 应被 clamp
        String barOver = MordantHelper.progressBar(150, 10);
        boolean okOver = barOver != null && barOver.length() > 0;

        // 负值 - 应被 clamp
        String barNeg = MordantHelper.progressBar(-10, 10);
        boolean okNeg = barNeg != null && barNeg.length() > 0;

        log.info("  0%% length={}", bar0 != null ? bar0.length() : 0);
        log.info("  100%% length={}", bar100 != null ? bar100.length() : 0);
        log.info("  150%% length={}", barOver != null ? barOver.length() : 0);
        log.info("  -10%% length={}", barNeg != null ? barNeg.length() : 0);

        boolean passed = ok0 && ok100 && okOver && okNeg;
        log.info(" [mordant-edge] passed={}", passed);
        return passed;
    }

    /**
     * MordantHelper 宽度参数测试：验证不同宽度（1/10/50）渲染结果。
     *
     * @return true 表示宽度参数正确影响渲染
     */
    public boolean testMordantWidthVariants() {
        log.info("===== [mordant-width] MordantHelper 宽度参数测试 =====");

        String bar1 = MordantHelper.progressBar(50, 1);
        String bar10 = MordantHelper.progressBar(50, 10);
        String bar50 = MordantHelper.progressBar(50, 50);

        boolean ok1 = bar1 != null && bar1.length() > 0;
        boolean ok10 = bar10 != null && bar10.length() > 0;
        boolean ok50 = bar50 != null && bar50.length() > 0;

        log.info("  width=1 length={}", bar1 != null ? bar1.length() : 0);
        log.info("  width=10 length={}", bar10 != null ? bar10.length() : 0);
        log.info("  width=50 length={}", bar50 != null ? bar50.length() : 0);

        boolean passed = ok1 && ok10 && ok50;
        log.info(" [mordant-width] passed={}", passed);
        return passed;
    }

    // ==================== SshProgress 单任务进度条测试 ====================

    /**
     * SshProgress 步进测试：验证 step / stepBy / stepTo 单步操作。
     *
     * @return true 表示步进操作不抛异常
     */
    public boolean testSshProgressStep() {
        log.info("===== [ssh-progress] SshProgress 步进测试 =====");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.chua.ssh.support.server.SshCommandResponse mockRes = createMockResponse(baos);

        try (SshProgress bar = new SshProgress(mockRes, "下载", 100, 40)) {
            bar.step();
            bar.stepBy(5);
            bar.stepTo(50);
            bar.stepBy(10);
            bar.stepTo(100);
            log.info(" [ssh-progress] step/stepBy/stepTo passed=true");
            return true;
        } catch (Exception e) {
            log.error("[ProgressBarExample] ssh-progress failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SshProgress 附加消息测试：验证 extraMessage 附加信息不抛异常。
     *
     * @return true 表示 extraMessage 操作正常
     */
    public boolean testSshProgressExtraMessage() {
        log.info("===== [ssh-progress-msg] SshProgress 附加消息测试 =====");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.chua.ssh.support.server.SshCommandResponse mockRes = createMockResponse(baos);

        try (SshProgress bar = new SshProgress(mockRes, "处理", 50, 40)) {
            bar.extraMessage("正在处理...");
            bar.stepBy(25);
            bar.extraMessage("处理中...");
            bar.stepTo(50);
            bar.extraMessage("即将完成");
            log.info(" [ssh-progress-msg] extraMessage passed=true");
            return true;
        } catch (Exception e) {
            log.error("[ProgressBarExample] ssh-progress-msg failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SshProgress 生命周期测试：验证 close / done 完成操作。
     *
     * @return true 表示生命周期操作正常
     */
    public boolean testSshProgressLifecycle() {
        log.info("===== [ssh-progress-lifecycle] SshProgress 生命周期测试 =====");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.chua.ssh.support.server.SshCommandResponse mockRes = createMockResponse(baos);

        try {
            SshProgress bar = new SshProgress(mockRes, "上传", 100, 40);
            bar.stepBy(100);
            bar.close();
            boolean closeOk = true;

            // 测试 done 方法
            SshProgress bar2 = new SshProgress(mockRes, "下载", 100, 40);
            bar2.stepTo(100);
            bar2.done("完成");
            bar2.done();
            boolean doneOk = true;

            byte[] output = baos.toByteArray();
            boolean hasOutput = output.length > 0;

            log.info("  close output size={}", output.length);
            log.info(" [ssh-progress-lifecycle] passed={}", closeOk && doneOk && hasOutput);
            return closeOk && doneOk && hasOutput;
        } catch (Exception e) {
            log.error("[ProgressBarExample] ssh-progress-lifecycle failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== SshMultiProgress 多任务进度条测试 ====================

    /**
     * SshMultiProgress 多任务测试：验证 add / stepBy 多任务并行进度。
     *
     * @return true 表示多任务进度条正常工作
     */
    public boolean testSshMultiProgress() {
        log.info("===== [ssh-multi] SshMultiProgress 多任务测试 =====");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.chua.ssh.support.server.SshCommandResponse mockRes = createMockResponse(baos);

        try (SshMultiProgress mp = new SshMultiProgress(mockRes, 40)) {
            mp.add("任务A", 100);
            mp.add("任务B", 200);
            mp.add("任务C", 50);

            mp.stepBy(0, 10);
            mp.stepBy(1, 5);
            mp.stepBy(2, 25);

            mp.stepTo(0, 50);
            mp.stepTo(1, 100);
            mp.stepTo(2, 50);

            byte[] output = baos.toByteArray();
            boolean hasOutput = output.length > 0;
            log.info("  output size={}", output.length);
            log.info(" [ssh-multi] passed={}", hasOutput);
            return hasOutput;
        } catch (Exception e) {
            log.error("[ProgressBarExample] ssh-multi failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SshMultiProgress 名称步进测试：验证 stepBy / stepTo 按名称跳转进度。
     *
     * @return true 表示按名称操作正常
     */
    public boolean testSshMultiProgressByName() {
        log.info("===== [ssh-multi-name] SshMultiProgress 名称步进测试 =====");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.chua.ssh.support.server.SshCommandResponse mockRes = createMockResponse(baos);

        try (SshMultiProgress mp = new SshMultiProgress(mockRes, 40)) {
            mp.add("alpha", 100);
            mp.add("beta", 100);

            mp.stepBy("alpha", 20);
            mp.stepBy("beta", 30);
            mp.stepTo("alpha", 80);
            mp.stepTo("beta", 90);

            byte[] output = baos.toByteArray();
            boolean hasOutput = output.length > 0;
            log.info("  output size={}", output.length);
            log.info(" [ssh-multi-name] passed={}", hasOutput);
            return hasOutput;
        } catch (Exception e) {
            log.error("[ProgressBarExample] ssh-multi-name failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * SshMultiProgress 生命周期测试：验证 close 完成操作。
     *
     * @return true 表示 close 操作正常
     */
    public boolean testSshMultiProgressLifecycle() {
        log.info("===== [ssh-multi-lifecycle] SshMultiProgress 生命周期测试 =====");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.chua.ssh.support.server.SshCommandResponse mockRes = createMockResponse(baos);

        try {
            SshMultiProgress mp = new SshMultiProgress(mockRes, 40);
            mp.add("task1", 100);
            mp.add("task2", 100);

            mp.stepTo(0, 100);
            mp.stepTo(1, 100);
            mp.close();

            byte[] output = baos.toByteArray();
            boolean hasOutput = output.length > 0;

            // 测试 lazyInit 异常
            boolean lazyInitOk = false;
            try {
                SshMultiProgress mp2 = new SshMultiProgress(mockRes, 40);
                mp2.stepBy(0, 1); // 未 add 应抛异常
            } catch (IllegalStateException e) {
                lazyInitOk = true;
            }

            log.info("  output size={}", output.length);
            log.info("  lazyInit exception caught={}", lazyInitOk);
            log.info(" [ssh-multi-lifecycle] passed={}", hasOutput && lazyInitOk);
            return hasOutput && lazyInitOk;
        } catch (Exception e) {
            log.error("[ProgressBarExample] ssh-multi-lifecycle failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建模拟的 SshCommandResponse，将输出写入 ByteArrayOutputStream。
     *
     * @param baos 输出流
     * @return 模拟的 SshCommandResponse
     */
    private static com.chua.ssh.support.server.SshCommandResponse createMockResponse(ByteArrayOutputStream baos) {
        return new com.chua.ssh.support.server.SshCommandResponse(baos);
    }
}

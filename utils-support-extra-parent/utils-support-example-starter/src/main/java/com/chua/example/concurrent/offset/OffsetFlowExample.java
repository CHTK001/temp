package com.chua.example.concurrent.offset;

import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.common.support.concurrent.offset.OffsetStore;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import com.chua.example.util.UtilsExample;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * OffsetFlow 综合示例 — 基于 {@link OffsetFlow} 门面与 {@link OffsetStore} SPI。
 *
 * <p>演示文件实现（{@code file}）的持久化、推进、重置、清空等核心能力，
 * 并提供自检流程验证 OffsetFlow 行为正确。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行：执行全部能力点自检
 *   java OffsetFlowExample
 *
 *   # 仅指定能力点（advance/reset/persist/spi/clear）
 *   java OffsetFlowExample --type advance
 *
 *   # 打印帮助
 *   java OffsetFlowExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>advance 推进</td><td>{@link #testAdvance()}</td><td>原子递增 offset 并返回新值</td></tr>
 *   <tr><td>reset 重置</td><td>{@link #testReset()}</td><td>offset 强制覆盖为指定值</td></tr>
 *   <tr><td>持久化</td><td>{@link #testPersist()}</td><td>新建实例后从文件加载之前的 offset</td></tr>
 *   <tr><td>SPI 加载</td><td>{@link #testSpiLoad()}</td><td>通过 SPI 解析 provider 实现</td></tr>
 *   <tr><td>truncate 清空</td><td>{@link #testTruncate()}</td><td>删除所有 offset 文件</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class OffsetFlowExample {

    /** 私有构造，防止实例化 */
    public OffsetFlowExample() { }

    /**
     * 默认能力点
     */
    private static final String DEFAULT_TYPE = "all";

    /**
     * 自检用临时目录
     */
    private static final String TEST_DIR = System.getProperty("java.io.tmpdir") + "/offset-example-" + System.currentTimeMillis();

    /**
     * 测试用 subscriberId
     */
    private static final String SUB_ID = "test-subscriber-1";

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("OffsetFlowExample")
                .register("type", "t", "能力点（advance|reset|persist|spi|truncate|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);

        OffsetFlowExample example = new OffsetFlowExample();
        boolean passed = example.runTest(type);
        log.info("[OffsetFlowExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? UtilsExample.SUCCESS : UtilsExample.FAILURE);
    }

    /**
     * 自检入口：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型（advance / reset / persist / spi / truncate / all）
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = DEFAULT_TYPE;
        }
        switch (type.toLowerCase()) {
            case "advance":
                return testAdvance();
            case "reset":
                return testReset();
            case "persist":
                return testPersist();
            case "spi":
                return testSpiLoad();
            case "truncate":
                return testTruncate();
            case "all":
                return testAdvance() && testReset() && testPersist() && testSpiLoad() && testTruncate();
            default:
                log.error("[OffsetFlowExample] 未知 type: {}", type);
                return false;
        }
    }

    /**
     * 能力 1：advance 推进 — 单次 advance 返回新值、两次返回不同。
     */
    public static boolean testAdvance() {
        log.info("===== advance =====");
        Path dir = Path.of(TEST_DIR + "-advance");
        try (OffsetFlow flow = OffsetFlow.create().basePath(dir.toString()).persistent(true).start()) {
            long v1 = flow.advance(SUB_ID);
            long v2 = flow.advance(SUB_ID);
            long v3 = flow.advance(SUB_ID);
            boolean ok = (v1 == 1L) && (v2 == 2L) && (v3 == 3L);
            printResult("advance returns 1, 2, 3", ok);
            return ok;
        }
    }

    /**
     * 能力 2：reset 重置 — 把已推进的 offset 强制覆盖回 0。
     */
    public static boolean testReset() {
        log.info("===== reset =====");
        Path dir = Path.of(TEST_DIR + "-reset");
        try (OffsetFlow flow = OffsetFlow.create().basePath(dir.toString()).persistent(true).start()) {
            flow.advance(SUB_ID);
            flow.advance(SUB_ID);
            flow.reset(SUB_ID, 0L);
            long current = flow.current(SUB_ID);
            boolean ok = current == 0L;
            printResult("reset to 0 then current = 0", ok);
            return ok;
        }
    }

    /**
     * 能力 3：持久化 — 关闭并重启实例，从文件恢复 offset。
     */
    public static boolean testPersist() {
        log.info("===== persist =====");
        Path dir = Path.of(TEST_DIR + "-persist");
        try (OffsetFlow flow1 = OffsetFlow.create().basePath(dir.toString()).persistent(true).start()) {
            flow1.advance(SUB_ID);
            flow1.advance(SUB_ID);
            flow1.advance(SUB_ID);
        }
        try (OffsetFlow flow2 = OffsetFlow.create().basePath(dir.toString()).persistent(true).start()) {
            long current = flow2.current(SUB_ID);
            boolean ok = current == 3L;
            printResult("reload from disk returns 3", ok);
            return ok;
        }
    }

    /**
     * 能力 4：SPI 加载 — 通过 ServiceProvider 解析 "file" provider。
     */
    public static boolean testSpiLoad() {
        log.info("===== spi =====");
        try {
            OffsetStore store = ServiceProvider.of(OffsetStore.class)
                    .getNewExtension("file", com.chua.common.support.concurrent.offset.OffsetConfig.createDefault());
            boolean ok = store != null;
            printResult("load file provider", ok);
            return ok;
        } catch (Exception e) {
            printResult("load file provider", false);
            log.error("SPI load failed", e);
            return false;
        }
    }

    /**
     * 能力 5：truncate 清空 — 清空后所有 subscriberId 不再存在。
     */
    public static boolean testTruncate() {
        log.info("===== truncate =====");
        Path dir = Path.of(TEST_DIR + "-truncate");
        try (OffsetFlow flow = OffsetFlow.create().basePath(dir.toString()).persistent(true).start()) {
            flow.advance(SUB_ID);
            flow.advance("other-sub");
            flow.truncate();
            // truncate 后再访问：会重新创建 offset，从 0 起步
            long v = flow.advance(SUB_ID);
            boolean ok = v == 1L;
            printResult("truncate clears, next advance = 1", ok);
            return ok;
        }
    }

    /** PrintResult */
    private static void printResult(String name, boolean passed) {
        log.info("{}{}", (passed ? "[PASS]" : "[FAIL]"), name);
    }
}

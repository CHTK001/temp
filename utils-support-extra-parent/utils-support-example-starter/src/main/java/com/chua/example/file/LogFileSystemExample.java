package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;
import com.chua.log.support.file.impl.LogWriteBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * LogFileSystem 日志文件系统独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>追加写入 (append mode)</li>
 *   <li>时间戳前缀 (withTimestamp)</li>
 *   <li>自定义前缀 / 后缀 (withPrefix / withSuffix)</li>
 *   <li>多条日志写入 (write)</li>
 *   <li>读取日志 (lines / asString)</li>
 *   <li>覆盖写入 (overwrite)</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.LogFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LogFileSystemExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 临时工作目录
     */
    private File workDir;

    /**
     * 全部测试是否通过
     */
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        LogFileSystemExample example = new LogFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testBasicAppend();
        testTimestamp();
        testPrefixSuffix();
        testMultipleWrites();
        testReadAndFilter();
        testOverwrite();
        cleanUp();

        log.info("========================================");
        log.info("[LogFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("log-example-").toFile();
        log.info("[Log] 工作目录: {}", workDir);
    }

    /**
     * 辅助获取 LogWriteBuilder
     */
    private LogWriteBuilder logWrite(File file) {
        WriteBuilder wb = FileSystem.create("log").write(file);
        if (wb instanceof LogWriteBuilder lwb) {
            return lwb;
        }
        throw new IllegalStateException("期望 LogWriteBuilder，实际: " + wb.getClass());
    }

    void testBasicAppend() throws Exception {
        log.info("\n===== 1. 基础追加写入 =====");
        File log = new File(workDir, "app.log");
        logWrite(log)
                .write("系统启动成功")
                .write("用户登录: userId=1001")
                .write("数据库连接成功")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        log.info("  [写入] {} 行", lines.size());
        lines.forEach(line -> log.info("    {}", line));

        logWrite(log).write("服务关闭").finish();
        List<String> lines2 = FileSystem.create("log").read(log).lines();
        log.info("  [追加后] {} 行", lines2.size());
        check(lines2.size() > lines.size(), "追加失败");
    }

    void testTimestamp() throws Exception {
        log.info("\n===== 2. 时间戳前缀 =====");
        File log = new File(workDir, "timestamp.log");
        logWrite(log)
                .withTimestamp(true)
                .write("订单创建: orderId=2026001")
                .write("支付成功: orderId=2026001, amount=99.00")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        log.info("  [时间戳日志]");
        lines.forEach(line -> log.info("    {}", line));
        check(lines.stream().anyMatch(l -> l.matches(".*\\d{4}-\\d{2}-\\d{2}.*")),
                "缺少时间戳格式 yyyy-MM-dd");
    }

    void testPrefixSuffix() throws Exception {
        log.info("\n===== 3. 自定义前缀/后缀 =====");
        File log = new File(workDir, "prefix.log");
        logWrite(log)
                .withTimestamp(true)
                .withPrefix("[APP] ")
                .withSuffix(" [END]")
                .write("这是一条带前缀和后缀的日志")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        log.info("  [自定义格式]");
        lines.forEach(line -> log.info("    {}", line));
        check(lines.stream().anyMatch(l -> l.contains("[APP]")), "缺少前缀 [APP]");
    }

    void testMultipleWrites() throws Exception {
        log.info("\n===== 4. 多次写入 =====");
        File log = new File(workDir, "multi.log");
        logWrite(log).write("第1次: 启动").finish();
        logWrite(log).write("第2次: 处理中").finish();
        logWrite(log).write("第3次: 完成").finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        log.info("  [读取] {} 行", lines.size());
        lines.forEach(line -> log.info("    {}", line));
        check(lines.size() >= 3, "行数不足");
    }

    void testReadAndFilter() throws Exception {
        log.info("\n===== 5. 读取日志 =====");
        File log = new File(workDir, "filter.log");
        logWrite(log)
                .write("INFO: 服务启动")
                .write("ERROR: 连接超时")
                .write("INFO: 重试成功")
                .write("ERROR: 磁盘空间不足")
                .write("INFO: 任务完成")
                .finish();

        List<String> allLines = FileSystem.create("log").read(log).lines();
        log.info("  [全部] {} 行", allLines.size());

        String fullText = FileSystem.create("log").read(log).asString();
        log.info("  [asString] {} 字符", fullText.length());

        check(allLines.size() == 5, "行数不匹配");
    }

    void testOverwrite() throws Exception {
        log.info("\n===== 6. 覆盖写入 =====");
        File log = new File(workDir, "overwrite.log");

        logWrite(log).write("旧数据: 第1行").write("旧数据: 第2行").finish();

        log.delete();
        logWrite(log).write("新数据: 覆盖后的内容").finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        log.info("  [覆盖后] {} 行", lines.size());
        lines.forEach(line -> log.info("    {}", line));
        check(lines.size() == 1 && lines.get(0).contains("新数据"), "覆盖后内容不符");
    }

    void cleanUp() {
        deleteDir(workDir);
    }

    private void check(boolean condition, String msg) {
        if (!condition) {
            log.info("  [FAIL] {}", msg);
            allPassed = false;
        } else {
            log.info("  [PASS]");
        }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}

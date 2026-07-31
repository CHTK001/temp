package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;
import com.chua.log.support.file.impl.LogWriteBuilder;

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
public class LogFileSystemExample {

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        LogFileSystemExample example = new LogFileSystemExample();
        example.setUp();
        example.testBasicAppend();
        example.testTimestamp();
        example.testPrefixSuffix();
        example.testMultipleWrites();
        example.testReadAndFilter();
        example.testOverwrite();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[LogFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("log-example-").toFile();
        System.out.println("[Log] 工作目录: " + workDir);
    }

    /** 辅助获取 LogWriteBuilder */
    private LogWriteBuilder logWrite(File file) {
        WriteBuilder wb = FileSystem.create("log").write(file);
        if (wb instanceof LogWriteBuilder lwb) return lwb;
        throw new IllegalStateException("期望 LogWriteBuilder，实际: " + wb.getClass());
    }

    void testBasicAppend() throws Exception {
        System.out.println("\n===== 1. 基础追加写入 =====");
        File log = new File(workDir, "app.log");
        logWrite(log)
                .write("系统启动成功")
                .write("用户登录: userId=1001")
                .write("数据库连接成功")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        System.out.println("  [写入] " + lines.size() + " 行");
        lines.forEach(line -> System.out.println("    " + line));

        // 再次追加
        logWrite(log).write("服务关闭").finish();
        List<String> lines2 = FileSystem.create("log").read(log).lines();
        System.out.println("  [追加后] " + lines2.size() + " 行");
        check(lines2.size() > lines.size(), "追加失败");
    }

    void testTimestamp() throws Exception {
        System.out.println("\n===== 2. 时间戳前缀 =====");
        File log = new File(workDir, "timestamp.log");
        logWrite(log)
                .withTimestamp(true)
                .write("订单创建: orderId=2026001")
                .write("支付成功: orderId=2026001, amount=99.00")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        System.out.println("  [时间戳日志]");
        lines.forEach(line -> System.out.println("    " + line));
        check(lines.stream().anyMatch(l -> l.matches(".*\\d{4}-\\d{2}-\\d{2}.*")),
                "缺少时间戳格式 yyyy-MM-dd");
    }

    void testPrefixSuffix() throws Exception {
        System.out.println("\n===== 3. 自定义前缀/后缀 =====");
        File log = new File(workDir, "prefix.log");
        logWrite(log)
                .withTimestamp(true)
                .withPrefix("[APP] ")
                .withSuffix(" [END]")
                .write("这是一条带前缀和后缀的日志")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        System.out.println("  [自定义格式]");
        lines.forEach(line -> System.out.println("    " + line));
        check(lines.stream().anyMatch(l -> l.contains("[APP]")), "缺少前缀 [APP]");
    }

    void testMultipleWrites() throws Exception {
        System.out.println("\n===== 4. 多次写入 =====");
        File log = new File(workDir, "multi.log");
        logWrite(log).write("第1次: 启动").finish();
        logWrite(log).write("第2次: 处理中").finish();
        logWrite(log).write("第3次: 完成").finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        System.out.println("  [读取] " + lines.size() + " 行");
        lines.forEach(line -> System.out.println("    " + line));
        check(lines.size() >= 3, "行数不足");
    }

    void testReadAndFilter() throws Exception {
        System.out.println("\n===== 5. 读取日志 =====");
        File log = new File(workDir, "filter.log");
        logWrite(log)
                .write("INFO: 服务启动")
                .write("ERROR: 连接超时")
                .write("INFO: 重试成功")
                .write("ERROR: 磁盘空间不足")
                .write("INFO: 任务完成")
                .finish();

        List<String> allLines = FileSystem.create("log").read(log).lines();
        System.out.println("  [全部] " + allLines.size() + " 行");

        String fullText = FileSystem.create("log").read(log).asString();
        System.out.println("  [asString] " + fullText.length() + " 字符");

        check(allLines.size() == 5, "行数不匹配");
    }

    void testOverwrite() throws Exception {
        System.out.println("\n===== 6. 覆盖写入 =====");
        File log = new File(workDir, "overwrite.log");

        // 先写入旧数据
        logWrite(log).write("旧数据: 第1行").write("旧数据: 第2行").finish();
        long oldLen = log.length();

        // 覆盖写入（重新创建新文件实现覆盖）
        log.delete();
        logWrite(log).write("新数据: 覆盖后的内容").finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        System.out.println("  [覆盖后] " + lines.size() + " 行");
        lines.forEach(line -> System.out.println("    " + line));
        check(lines.size() == 1 && lines.get(0).contains("新数据"), "覆盖后内容不符");
    }

    void cleanUp() { deleteDir(workDir); }

    private void check(boolean condition, String msg) {
        if (!condition) { System.err.println("  [FAIL] " + msg); allPassed = false; }
        else { System.out.println("  [PASS]"); }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }
}

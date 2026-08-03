package com.chua.example.file;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Zip4jFileSystem 独立完整测试示例 — 演示密码加密写入与解密读取。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写入带密码加密的 ZIP（addBytes + addFile）</li>
 *   <li>用正确密码读取加密 ZIP（listEntries / extract / readEntry）</li>
 *   <li>验证未提供密码时读取失败</li>
 *   <li>验证提供错误密码时读取失败</li>
 *   <li>向已有加密 ZIP 追加条目</li>
 * </ul>
 *
 * <p><b>注意：</b>依赖 zip4j 库（net.lingala.zip4j），确保 classpath 中包含相关依赖。</p>
 *
 * <pre>{@code
 * java com.chua.example.file.Zip4jFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class Zip4jFileSystemExample {

    /**
     * 加密 ZIP 使用的密码
     */
    private static final String PASSWORD = "mySecret123";

    /**
     * 测试用错误密码
     */
    private static final String WRONG_PASSWORD = "wrongPassword";

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
        Zip4jFileSystemExample example = new Zip4jFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testEncryptedWriteAndRead();
        testReadWithoutPasswordFails();
        testReadWithWrongPasswordFails();
        testExtractEncrypted();
        testAddMultipleEntries();
        cleanUp();

        log.info("========================================");
        log.info("[Zip4jFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("zip4j-example-").toFile();
        log.info("[Zip4j] 工作目录: {}", workDir);
    }

    /**
     * 1. 加密写入 + 正确密码读取
     */
    void testEncryptedWriteAndRead() throws Exception {
        log.info("\n===== 1. 加密写入 + 正确密码读取 =====");
        File zip = new File(workDir, "encrypted.zip");
        Zip4jWriteBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").write(zip))
                .password(PASSWORD)
                .addBytes("hello.txt", "Hello, 加密世界!".getBytes(StandardCharsets.UTF_8))
                .addBytes("data/numbers.txt", "12345\n67890".getBytes(StandardCharsets.UTF_8))
                .finish();

        check(zip.length() > 0, "ZIP 为空");

        List<String> entries = Zip4jReadBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                .password(PASSWORD)
                .listEntries();
        log.info("  [条目] {}", entries);
        check(entries.size() == 2, "期望 2 个条目");

        String content1 = Zip4jReadBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                .password(PASSWORD)
                .readEntry("hello.txt");
        log.info("  [hello.txt] {}", content1);
        check("Hello, 加密世界!".equals(content1), "内容不匹配");

        log.info("  [PASS]");
    }

    /**
     * 2. 无密码读取应失败
     */
    void testReadWithoutPasswordFails() throws Exception {
        log.info("\n===== 2. 无密码读取应失败 =====");
        File zip = new File(workDir, "no-pw.zip");
        Zip4jWriteBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").write(zip))
                .password(PASSWORD)
                .addBytes("secret.txt", "机密数据".getBytes(StandardCharsets.UTF_8))
                .finish();

        try {
            Zip4jReadBuilderCaster.cast(
                    com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                    .listEntries();
            log.info("  [FAIL] 应抛出异常但未抛");
            allPassed = false;
        } catch (Exception e) {
            log.info("  [正确] 无密码时读取失败: {}", e.getClass().getSimpleName());
            log.info("  [PASS]");
        }
    }

    /**
     * 3. 错误密码读取应失败
     */
    void testReadWithWrongPasswordFails() throws Exception {
        log.info("\n===== 3. 错误密码读取应失败 =====");
        File zip = new File(workDir, "wrong-pw.zip");
        Zip4jWriteBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").write(zip))
                .password(PASSWORD)
                .addBytes("data.txt", "敏感数据".getBytes(StandardCharsets.UTF_8))
                .finish();

        try {
            Zip4jReadBuilderCaster.cast(
                    com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                    .password(WRONG_PASSWORD)
                    .listEntries();
            log.info("  [FAIL] 应抛出异常但未抛");
            allPassed = false;
        } catch (Exception e) {
            log.info("  [正确] 错误密码时读取失败: {}", e.getClass().getSimpleName());
            log.info("  [PASS]");
        }
    }

    /**
     * 4. 提取加密 ZIP
     */
    void testExtractEncrypted() throws Exception {
        log.info("\n===== 4. 提取加密 ZIP =====");
        File zip = new File(workDir, "extract.zip");
        File extractDir = new File(workDir, "extracted");
        extractDir.mkdirs();

        Zip4jWriteBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").write(zip))
                .password(PASSWORD)
                .addBytes("doc.txt", "文档内容".getBytes(StandardCharsets.UTF_8))
                .addBytes("sub/config.ini", "[app]\nname=test\n".getBytes(StandardCharsets.UTF_8))
                .finish();

        Zip4jReadBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                .password(PASSWORD)
                .extractAll(extractDir);

        File doc = new File(extractDir, "doc.txt");
        File config = new File(extractDir, "sub/config.ini");
        check(doc.exists(), "doc.txt 未提取");
        check(config.exists(), "sub/config.ini 未提取");
        log.info("  [PASS]");
    }

    /**
     * 5. 多条条目写入
     */
    void testAddMultipleEntries() throws Exception {
        log.info("\n===== 5. 多条条目写入 =====");
        File zip = new File(workDir, "multi.zip");
        File sourceFile = new File(workDir, "source.txt");
        Files.write(sourceFile.toPath(), "来自文件的内容".getBytes(StandardCharsets.UTF_8));

        Zip4jWriteBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").write(zip))
                .password(PASSWORD)
                .addBytes("直接写入.txt", "直接写入的字节数据".getBytes(StandardCharsets.UTF_8))
                .addFile("来自文件.txt", sourceFile)
                .finish();

        List<String> entries = Zip4jReadBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                .password(PASSWORD)
                .listEntries();
        log.info("  [条目] {}", entries);
        check(entries.size() == 2, "期望 2 个条目");
        check(entries.contains("直接写入.txt"), "缺少直接写入.txt");
        check(entries.contains("来自文件.txt"), "缺少来自文件.txt");

        String content = Zip4jReadBuilderCaster.cast(
                com.chua.common.support.file.FileSystem.create("zip4j").read(zip))
                .password(PASSWORD)
                .readEntry("来自文件.txt");
        check("来自文件的内容".equals(content), "文件内容不匹配");
        log.info("  [PASS]");
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

    private static final class Zip4jWriteBuilderCaster {
        static com.chua.filesystem.support.file.impl.Zip4jFileSystem.Zip4jWriteBuilder cast(
                com.chua.common.support.file.builder.WriteBuilder wb) {
            return (com.chua.filesystem.support.file.impl.Zip4jFileSystem.Zip4jWriteBuilder) wb;
        }
    }

    private static final class Zip4jReadBuilderCaster {
        static com.chua.filesystem.support.file.impl.Zip4jFileSystem.Zip4jReadBuilder cast(
                com.chua.common.support.file.builder.ReadBuilder rb) {
            return (com.chua.filesystem.support.file.impl.Zip4jFileSystem.Zip4jReadBuilder) rb;
        }
    }
}

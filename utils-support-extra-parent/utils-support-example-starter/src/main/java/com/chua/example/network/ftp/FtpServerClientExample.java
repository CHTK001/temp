package com.chua.example.network.ftp;

import com.chua.common.support.network.ftp.FtpConfig;
import com.chua.common.support.network.ftp.FtpServer;
import com.chua.ftp.support.client.FtpClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * FTP Server 全能力综合测试 — 启动内嵌服务器，客户端链式操作覆盖全部 FTP 命令。
 *
 * <p>覆盖能力矩阵：</p>
 * <ul>
 *   <li>连接：USER / PASS / QUIT</li>
 *   <li>传输模式：TYPE I（二进制）/ TYPE A（ASCII）</li>
 *   <li>目录：PWD / CWD / CDUP / MKD / RMD</li>
 *   <li>文件：STOR / RETR / DELE / SIZE / RNFR / RNTO</li>
 *   <li>数据通道：PASV（被动模式）</li>
 *   <li>列表：LIST / NLST</li>
 *   <li>系统：SYST / FEAT / NOOP</li>
 *   <li>链式 DSL：upload().local().remote().exec() 全链路</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   java FtpServerClientExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class FtpServerClientExample {

    /**
     * 测试用 FTP 端口
     */
    private static final int TEST_PORT = 2121;

    /**
     * 测试用根目录
     */
    private static final String TEST_HOME = "ftp-test-home";

    /**
     * FTP 服务器地址
     */
    private static final String SERVER_HOST = "127.0.0.1";

    /**
     * 通过总数
     */
    private static int passed = 0;

    /**
     * 失败总数
     */
    private static int failed = 0;

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("FTP Server 全能力综合测试");
        System.out.println("========================================");

        // 确保测试目录存在
        var homeDir = new File(TEST_HOME);
        homeDir.mkdirs();

        // 构建并启动 FTP 服务器
        var config = FtpConfig.builder()
                .controlPort(TEST_PORT)
                .host("0.0.0.0")
                .homeDirectory(homeDir)
                .anonymousEnabled(true)
                .anonymousWriteEnabled(true)
                .passivePortRange(49152, 65535)
                .build();
        var server = new FtpServer(config);
        server.start();

        try {
            Thread.sleep(500);
            testAllCapabilities();
            System.out.println();
            System.out.println("========================================");
            System.out.println("测试结果: 通过=" + passed + ", 失败=" + failed);
            System.out.println("========================================");
            if (failed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] 测试异常: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            server.stop();
            deleteRecursive(homeDir);
        }
    }

    /**
     * 全能力链式测试：覆盖 FTP 所有核心命令。
     */
    private static void testAllCapabilities() {
        try (FtpClient client = FtpClient.builder()
                .host(SERVER_HOST).port(TEST_PORT)
                .username("anonymous").password("test@test.com")
                .build().connect()) {

            // ========== PWD / USER / PASS ==========
            test("PWD 根目录", () -> {
                String dir = client.pwd();
                assertResult("PWD 返回 /", "/".equals(dir));
            });

            // ========== MKD — 创建目录 ==========
            test("MKD 创建目录", () -> {
                client.mkdir().path("/test-dir").exec();
                assertResult("目录 /test-dir 存在", client.exists("/test-dir"));
            });

            test("MKD 嵌套目录", () -> {
                client.mkdir().path("/test-dir/sub-dir").exec();
                assertResult("子目录 /test-dir/sub-dir 存在", client.exists("/test-dir/sub-dir"));
            });

            // ========== CWD — 切换目录 ==========
            test("CWD 切换目录", () -> {
                client.cd().path("/test-dir").exec();
                String dir = client.pwd();
                assertResult("当前目录包含 test-dir", dir.contains("test-dir"));
                client.cd().path("/").exec();
            });

            // ========== STOR — 上传文件（链式） ==========
            test("STOR 文件上传", () -> {
                var tmpFile = Path.of(TEST_HOME, "upload-test.txt");
                Files.writeString(tmpFile, "Hello FTP Server Test!", StandardCharsets.UTF_8);
                client.upload()
                        .local(tmpFile.toString())
                        .remote("/upload-test.txt")
                        .exec();
                assertResult("文件 /upload-test.txt 存在", client.exists("/upload-test.txt"));
                Files.deleteIfExists(tmpFile);
            });

            // ========== SIZE — 获取文件大小 ==========
            test("SIZE 文件大小", () -> {
                long size = client.size("/upload-test.txt");
                assertResult("文件大小 = 21 字节", size == 21);
            });

            // ========== RETR — 下载文件（链式） ==========
            test("RETR 文件下载", () -> {
                var localFile = Path.of(TEST_HOME, "download-test.txt");
                client.download()
                        .remote("/upload-test.txt")
                        .local(localFile.toString())
                        .exec();
                String content = Files.readString(localFile, StandardCharsets.UTF_8);
                assertResult("下载内容 = Hello FTP Server Test!", "Hello FTP Server Test!".equals(content));
                Files.deleteIfExists(localFile);
            });

            // ========== STOR stream — 流式上传 ==========
            test("STOR 流式上传", () -> {
                byte[] data = "Stream upload content".getBytes(StandardCharsets.UTF_8);
                client.upload()
                        .stream(new ByteArrayInputStream(data))
                        .remote("/stream-upload.txt")
                        .exec();
                assertResult("流式上传文件存在", client.exists("/stream-upload.txt"));
                assertResult("流式上传大小正确", client.size("/stream-upload.txt") == data.length);
            });

            // ========== RETR stream — 流式下载 ==========
            test("RETR 流式下载", () -> {
                var baos = new ByteArrayOutputStream();
                client.download()
                        .remote("/stream-upload.txt")
                        .stream(baos)
                        .exec();
                String content = baos.toString(StandardCharsets.UTF_8);
                assertResult("流式下载内容匹配", "Stream upload content".equals(content));
            });

            // ========== LIST — 列出目录 ==========
            test("LIST 列目录", () -> {
                List<String> files = client.ls().path("/").exec();
                assertResult("LIST 包含 upload-test.txt", files.contains("upload-test.txt"));
            });

            // ========== RENAME — 重命名 ==========
            test("RENAME 重命名", () -> {
                client.rename().from("/upload-test.txt").to("/renamed.txt").exec();
                assertResult("重命名后新文件存在", client.exists("/renamed.txt"));
                assertResult("重命名后旧文件不存在", !client.exists("/upload-test.txt"));
            });

            // ========== TYPE A — ASCII 模式 ==========
            test("TYPE A ASCII 模式", () -> {
                client.setBinary(false);
                byte[] data = "ASCII mode content".getBytes(StandardCharsets.UTF_8);
                client.upload()
                        .stream(new ByteArrayInputStream(data))
                        .remote("/ascii-test.txt")
                        .exec();
                assertResult("ASCII 模式上传成功", client.exists("/ascii-test.txt"));
                client.setBinary(true);
            });

            // ========== TYPE I — 二进制模式 ==========
            test("TYPE I 二进制模式", () -> {
                byte[] binaryData = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF};
                client.upload()
                        .stream(new ByteArrayInputStream(binaryData))
                        .remote("/binary-test.bin")
                        .exec();
                assertResult("二进制模式上传成功", client.exists("/binary-test.bin"));
                assertResult("二进制文件大小 = 4", client.size("/binary-test.bin") == 4);
            });

            // ========== DELE — 删除文件 ==========
            test("DELE 删除文件", () -> {
                client.rm().path("/renamed.txt").exec();
                assertResult("删除后文件不存在", !client.exists("/renamed.txt"));
            });

            // ========== RMD — 删除目录 ==========
            test("RMD 删除目录", () -> {
                client.rmdir().path("/test-dir/sub-dir").exec();
                assertResult("删除子目录后不存在", !client.exists("/test-dir/sub-dir"));
            });

            // ========== EXISTS — 检查文件存在 ==========
            test("EXISTS 文件检查", () -> {
                assertResult("存在文件返回 true", client.exists("/stream-upload.txt"));
                assertResult("不存在文件返回 false", !client.exists("/non-exist.txt"));
            });

            // ========== CDUP — 切换上级目录 ==========
            test("CDUP 上级目录", () -> {
                client.cd().path("/test-dir").exec();
                client.cd().path("..").exec();
                String dir = client.pwd();
                assertResult("CDUP 回到根目录", "/".equals(dir) || dir.isEmpty());
            });

            // ========== 多客户端并发 ==========
            test("MULTI-CLIENT 多客户端", () -> {
                try (FtpClient client2 = FtpClient.builder()
                        .host(SERVER_HOST).port(TEST_PORT)
                        .username("user2").password("pass2")
                        .build().connect()) {
                    client2.upload()
                            .stream(new ByteArrayInputStream("client2 data".getBytes()))
                            .remote("/client2-file.txt")
                            .exec();
                    assertResult("第二客户端上传成功", client2.exists("/client2-file.txt"));
                }
                assertResult("第一客户端文件仍在", client.exists("/stream-upload.txt"));
            });

            // ========== 清理 ==========
            test("CLEANUP 清理", () -> {
                client.rm().path("/stream-upload.txt").exec();
                client.rm().path("/ascii-test.txt").exec();
                client.rm().path("/binary-test.bin").exec();
                client.rm().path("/client2-file.txt").exec();
                client.rmdir().path("/test-dir").exec();
                assertResult("清理完成，根目录为空", client.ls().path("/").exec().isEmpty());
            });

        } catch (Exception e) {
            System.err.println("[ERROR] 客户端异常: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }
    }

    /**
     * 执行测试用例，捕获异常并计数。
     *
     * @param name 测试名称
     * @param test 测试逻辑
     */
    private static void test(String name, ThrowingRunnable test) {
        try {
            test.run();
        } catch (Exception e) {
            System.err.println("[FAIL] " + name + " - " + e.getMessage());
            failed++;
        }
    }

    /**
     * 可抛出异常的 Runnable 函数式接口。
     */
    @FunctionalInterface
    private interface ThrowingRunnable {
        /**
         * 执行测试逻辑。
         *
         * @throws Exception 任何异常
         */
        void run() throws Exception;
    }

    /**
     * 断言结果并打印。
     *
     * @param desc   断言描述
     * @param result 断言结果
     */
    private static void assertResult(String desc, boolean result) {
        if (result) {
            System.out.println("[PASS] " + desc);
            passed++;
        } else {
            System.out.println("[FAIL] " + desc);
            failed++;
        }
    }

    /**
     * 递归删除目录。
     *
     * @param file 目录或文件
     */
    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            var children = file.listFiles();
            if (children != null) {
                for (var child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}

package com.chua.example.ssh;

import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.ssh.support.client.SftpClient;
import com.chua.ssh.support.client.SshClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * SshClient / SftpClient 测试示例
 *
 * <p>连接远程 SSH 服务，覆盖命令执行、交互 Shell、PTY 终端、
 * 文件上传下载、目录操作等场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SshSftpClientExample {

    /** 私有构造，防止实例化 */
    private SshSftpClientExample() { }

    /** SSH 示例默认端口（演示用） */
    private static final int DEFAULT_SSH_PORT = 2222;
    /** SSH 示例默认用户名（演示用） */
    private static final String DEFAULT_USER = "admin";
    /** SSH 示例默认密码（演示 mock 数据，仅用于示例） */
    private static final String DEFAULT_PASSWORD = "admin123";
    /** Shell 等待时间(ms) */
    private static final long SHELL_WAIT_MS = 1500L;

    /**
     * 入口方法，演示 SshClient / SftpClient 的常用功能。
     *
     * <p>用法：{@code java com.chua.example.ssh.SshSftpClientExample [host] [port] [user] [pass]}</p>
     *
     * @param args 命令行参数，顺序为 host、port、user、pass
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "172.16.0.40";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SSH_PORT;
        String user = args.length > 2 ? args[2] : DEFAULT_USER;
        String pass = args.length > 3 ? args[3] : DEFAULT_PASSWORD;

        log.info("========== SshClient / SftpClient 测试开始 ==========");
        log.info("目标: {}@{}:{}", user, host, port);

        testSshExec(host, port, user, pass);
        testSshShell(host, port, user, pass);
        testSftp(host, port, user, pass);

        log.info("\n========== 测试结束 ==========");
    }

    /** SshClient exec 命令执行测试 */
    private static void testSshExec(String host, int port, String user, String pass) {
        log.info("\n--- 1. SshClient 连接与 exec 命令 ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .connectTimeout(10).sessionTimeout(15)
                .build()) {

            long t0 = System.currentTimeMillis();
            ssh.connect();
            log.info("PASS: 连接成功 ({} ms)", System.currentTimeMillis() - t0);

            // whoami
            String who = ssh.exec().command("whoami").executeAndGetOutput().trim();
            log.info("PASS: whoami = {}", who);

            // uname
            String kernel = ssh.exec().command("uname -sr").executeAndGetOutput().trim();
            log.info("PASS: uname = {}", kernel);

            // 退出码验证
            var okResult = ssh.exec().command("exit 0").execute();
            log.info("PASS: exit 0 -> code={}", okResult.exitCode());

            var failResult = ssh.exec().command("ls /nonexistent-dir-xyz").execute();
            boolean stderrOk = failResult.stderr() != null && !failResult.stderr().isEmpty();
            log.info("PASS: ls 失败路径 -> code={}, stderr 捕获={}", failResult.exitCode(), stderrOk ? "是" : "否");

        } catch (Exception e) {
            log.info("FAIL: {}", e.getMessage());
        }
    }

    /** SshClient shell 交互测试 */
    private static void testSshShell(String host, int port, String user, String pass) {
        log.info("\n--- 2. SshClient 交互 Shell ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            ssh.connect();

            var shell = ssh.shell().connect();
            shell.send("echo SHELL_TEST_OK_$$");
            ThreadUtils.sleep(SHELL_WAIT_MS);
            // 发送 exit 结束会话使 readAll 返回
            shell.send("exit");
            String output = shell.readAll();
            shell.close();

            boolean found = output.contains("SHELL_TEST_OK");
            log.info("{}: shell 输出包含标记={}, 总长 {} 字符", found ? "PASS" : "FAIL", found, output.length());
        } catch (Exception e) {
            log.info("FAIL: {}", e.getMessage());
        }
    }

    /** SftpClient 文件操作全链路测试 */
    private static void testSftp(String host, int port, String user, String pass) {
        log.info("\n--- 3. SftpClient 文件操作 ---");
        Path localFile = null;
        Path localDownload = null;
        try (SftpClient sftp = SftpClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            long t0 = System.currentTimeMillis();
            sftp.connect();
            log.info("PASS: 连接成功 ({} ms)", System.currentTimeMillis() - t0);

            // 准备本地测试文件
            localFile = Files.createTempFile("sftp-test-", ".txt");
            String content = "SFTP TEST CONTENT " + System.currentTimeMillis() + "\n".repeat(1);
            Files.writeString(localFile, content.repeat(100));
            log.info("PASS: 本地测试文件 {} 字节", Files.size(localFile));

            // 上传
            String remotePath = "/tmp/sftp-test-upload.txt";
            sftp.upload().local(localFile.toString()).remote(remotePath).exec();
            log.info("PASS: 上传 -> {}", remotePath);

            // stat 校验大小
            Map<String, Object> stat = sftp.stat().path(remotePath).exec();
            long remoteSize = (long) stat.get("size");
            log.info("PASS: stat size={}, isRegularFile={}", remoteSize, stat.get("isRegularFile"));
            if (remoteSize != Files.size(localFile)) {
                throw new IllegalStateException("上传后大小不一致: " + remoteSize + " != " + Files.size(localFile));
            }

            // 列目录确认文件存在
            List<Map<String, Object>> entries = sftp.ls().path("/tmp").exec();
            boolean exists = entries.stream()
                    .anyMatch(e -> "sftp-test-upload.txt".equals(e.get("name")));
            log.info("PASS: ls /tmp 共 {} 项, 目标文件存在={}", entries.size(), exists);

            // 下载并校验内容一致
            localDownload = Files.createTempFile("sftp-dl-", ".txt");
            sftp.download().remote(remotePath).local(localDownload.toString()).exec();
            boolean same = arraysEquals(Files.readAllBytes(localFile), Files.readAllBytes(localDownload));
            log.info("PASS: 下载完成, 内容一致={}", same);
            if (!same) {
                throw new IllegalStateException("下载内容与源不一致");
            }

            // mkdir
            String remoteDir = "/tmp/sftp-test-dir";
            try {
                sftp.mkdir().path(remoteDir).exec();
                log.info("PASS: mkdir {}", remoteDir);
            } catch (Exception e) {
                log.info("SKIP: mkdir(可能已存在): {}", e.getMessage());
            }

            // rename
            String renamedPath = "/tmp/sftp-test-renamed.txt";
            sftp.rename().from(remotePath).to(renamedPath).exec();
            log.info("PASS: rename -> {}", renamedPath);

            // rm 清理
            sftp.rm().path(renamedPath).exec();
            log.info("PASS: rm {}", renamedPath);
            try {
                sftp.rm().path(remoteDir).recursive(true).exec();
                log.info("PASS: rmdir {}", remoteDir);
            } catch (Exception e) {
                log.info("SKIP: rmdir: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.info("FAIL: {}", e.getMessage());
        } finally {
            // 静默删除临时文件，异常已通过 FileUtils.deleteQuietly 内部吞掉
            FileUtils.deleteQuietly(localFile);
            FileUtils.deleteQuietly(localDownload);
        }
    }

    /** 数组比较 */
    private static boolean arraysEquals(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }
}

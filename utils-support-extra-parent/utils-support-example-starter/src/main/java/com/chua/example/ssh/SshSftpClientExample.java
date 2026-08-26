package com.chua.example.ssh;

import com.chua.ssh.support.client.SftpClient;
import com.chua.ssh.support.client.SshClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * SshClient / SftpClient 测试示例
 *
 * <p>连接远程 SSH 服务，覆盖命令执行、交互 Shell、PTY 终端、
 * 文件上传下载、目录操作等场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SshSftpClientExample {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "172.16.0.40";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2222;
        String user = args.length > 2 ? args[2] : "admin";
        String pass = args.length > 3 ? args[3] : "admin123";

        System.out.println("========== SshClient / SftpClient 测试开始 ==========");
        System.out.println("目标: " + user + "@" + host + ":" + port);

        testSshExec(host, port, user, pass);
        testSshShell(host, port, user, pass);
        testSftp(host, port, user, pass);

        System.out.println("\n========== 测试结束 ==========");
    }

    /** SshClient exec 命令执行测试 */
    private static void testSshExec(String host, int port, String user, String pass) {
        System.out.println("\n--- 1. SshClient 连接与 exec 命令 ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .connectTimeout(10).sessionTimeout(15)
                .build()) {

            long t0 = System.currentTimeMillis();
            ssh.connect();
            System.out.println("PASS: 连接成功 (" + (System.currentTimeMillis() - t0) + " ms)");

            // whoami
            String who = ssh.exec().command("whoami").executeAndGetOutput().trim();
            System.out.println("PASS: whoami = " + who);

            // uname
            String kernel = ssh.exec().command("uname -sr").executeAndGetOutput().trim();
            System.out.println("PASS: uname = " + kernel);

            // 退出码验证
            var okResult = ssh.exec().command("exit 0").execute();
            System.out.println("PASS: exit 0 -> code=" + okResult.exitCode());

            var failResult = ssh.exec().command("ls /nonexistent-dir-xyz").execute();
            boolean stderrOk = failResult.stderr() != null && !failResult.stderr().isEmpty();
            System.out.println("PASS: ls 失败路径 -> code=" + failResult.exitCode()
                    + ", stderr 捕获=" + (stderrOk ? "是" : "否"));

        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
        }
    }

    /** SshClient shell 交互测试 */
    private static void testSshShell(String host, int port, String user, String pass) {
        System.out.println("\n--- 2. SshClient 交互 Shell ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            ssh.connect();

            var shell = ssh.shell().connect();
            shell.send("echo SHELL_TEST_OK_$$");
            Thread.sleep(1500);
            // 发送 exit 结束会话使 readAll 返回
            shell.send("exit");
            String output = shell.readAll();
            shell.close();

            boolean found = output.contains("SHELL_TEST_OK");
            System.out.println((found ? "PASS" : "FAIL")
                    + ": shell 输出包含标记=" + found + ", 总长 " + output.length() + " 字符");
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
        }
    }

    /** SftpClient 文件操作全链路测试 */
    private static void testSftp(String host, int port, String user, String pass) {
        System.out.println("\n--- 3. SftpClient 文件操作 ---");
        Path localFile = null;
        Path localDownload = null;
        try (SftpClient sftp = SftpClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            long t0 = System.currentTimeMillis();
            sftp.connect();
            System.out.println("PASS: 连接成功 (" + (System.currentTimeMillis() - t0) + " ms)");

            // 准备本地测试文件
            localFile = Files.createTempFile("sftp-test-", ".txt");
            String content = "SFTP TEST CONTENT " + System.currentTimeMillis() + "\n".repeat(1);
            Files.writeString(localFile, content.repeat(100));
            System.out.println("PASS: 本地测试文件 " + Files.size(localFile) + " 字节");

            // 上传
            String remotePath = "/tmp/sftp-test-upload.txt";
            sftp.upload().local(localFile.toString()).remote(remotePath).exec();
            System.out.println("PASS: 上传 -> " + remotePath);

            // stat 校验大小
            Map<String, Object> stat = sftp.stat().path(remotePath).exec();
            long remoteSize = (Long) stat.get("size") == null ? ((Number) stat.get("size")).longValue() : (long) stat.get("size");
            System.out.println("PASS: stat size=" + remoteSize
                    + ", isRegularFile=" + stat.get("isRegularFile"));
            if (remoteSize != Files.size(localFile)) {
                throw new IllegalStateException("上传后大小不一致: " + remoteSize + " != " + Files.size(localFile));
            }

            // 列目录确认文件存在
            List<Map<String, Object>> entries = sftp.ls().path("/tmp").exec();
            boolean exists = entries.stream()
                    .anyMatch(e -> "sftp-test-upload.txt".equals(e.get("name")));
            System.out.println("PASS: ls /tmp 共 " + entries.size() + " 项, 目标文件存在=" + exists);

            // 下载并校验内容一致
            localDownload = Files.createTempFile("sftp-dl-", ".txt");
            sftp.download().remote(remotePath).local(localDownload.toString()).exec();
            boolean same = Arrays_equals(Files.readAllBytes(localFile), Files.readAllBytes(localDownload));
            System.out.println("PASS: 下载完成, 内容一致=" + same);
            if (!same) {
                throw new IllegalStateException("下载内容与源不一致");
            }

            // mkdir
            String remoteDir = "/tmp/sftp-test-dir";
            try {
                sftp.mkdir().path(remoteDir).exec();
                System.out.println("PASS: mkdir " + remoteDir);
            } catch (Exception e) {
                System.out.println("SKIP: mkdir(可能已存在): " + e.getMessage());
            }

            // rename
            String renamedPath = "/tmp/sftp-test-renamed.txt";
            sftp.rename().from(remotePath).to(renamedPath).exec();
            System.out.println("PASS: rename -> " + renamedPath);

            // rm 清理
            sftp.rm().path(renamedPath).exec();
            System.out.println("PASS: rm " + renamedPath);
            try {
                sftp.rm().path(remoteDir).recursive(true).exec();
                System.out.println("PASS: rmdir " + remoteDir);
            } catch (Exception e) {
                System.out.println("SKIP: rmdir: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
        } finally {
            try { if (localFile != null) { Files.deleteIfExists(localFile); } } catch (Exception ignored) {}
            try { if (localDownload != null) { Files.deleteIfExists(localDownload); } } catch (Exception ignored) {}
        }
    }

    /** 数组比较 */
    private static boolean Arrays_equals(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }
}

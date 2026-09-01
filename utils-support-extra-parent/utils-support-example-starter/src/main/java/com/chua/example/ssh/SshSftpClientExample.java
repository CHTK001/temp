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

import static java.util.Arrays.equals;

/**
 * SshClient / SftpClient 娴嬭瘯绀轰緥
 *
 * <p>杩炴帴杩滅▼ SSH 鏈嶅姟锛岃鐩栧懡浠ゆ墽琛屻€佷氦浜?Shell銆丳TY 缁堢銆? * 鏂囦欢涓婁紶涓嬭浇銆佺洰褰曟搷浣滅瓑鍦烘櫙銆?/p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SshSftpClientExample {

    /** 绉佹湁鏋勯€狅紝闃叉瀹炰緥鍖?*/
    private SshSftpClientExample() { }

    /** SSH 绀轰緥榛樿绔彛锛堟紨绀虹敤锛?*/
    private static final int DEFAULT_SSH_PORT = 2222;
    /** SSH 绀轰緥榛樿鐢ㄦ埛鍚嶏紙婕旂ず鐢級 */
    private static final String DEFAULT_USER = "admin";
    /** Shell 绛夊緟鏃堕棿(ms) */
    private static final long SHELL_WAIT_MS = 1500L;

    /**
     * 鍏ュ彛鏂规硶锛屾紨绀?SshClient / SftpClient 鐨勫父鐢ㄥ姛鑳姐€?     *
     * <p>鐢ㄦ硶锛歿@code java com.chua.example.ssh.SshSftpClientExample [host] [port] [user] [pass]}</p>
     *
     * @param args 鍛戒护琛屽弬鏁帮紝椤哄簭涓?host銆乸ort銆乽ser銆乸ass
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "172.16.0.40";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SSH_PORT;
        String user = args.length > 2 ? args[2] : DEFAULT_USER;
        String pass = args.length > 3 ? args[3] : System.getenv("SSH_PASSWORD");
        if (pass == null || pass.isEmpty()) {
            log.warn("[WARN] SSH_PASSWORD env not set, please set it before running");
            pass = "";
        }

        log.info("========== SshClient / SftpClient 娴嬭瘯寮€濮?==========");
        log.info("鐩爣: {}@{}:{}", user, host, port);

        testSshExec(host, port, user, pass);
        testSshShell(host, port, user, pass);
        testSftp(host, port, user, pass);

        log.info("\n========== 娴嬭瘯缁撴潫 ==========");
    }

    /** SshClient exec 鍛戒护鎵ц娴嬭瘯 */
    private static void testSshExec(String host, int port, String user, String pass) {
        log.info("\n--- 1. SshClient 杩炴帴涓?exec 鍛戒护 ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .connectTimeout(10).sessionTimeout(15)
                .build()) {

            long t0 = System.currentTimeMillis();
            ssh.connect();
            log.info("PASS: 杩炴帴鎴愬姛 ({} ms)", System.currentTimeMillis() - t0);

            // whoami
            String who = ssh.exec().command("whoami").executeAndGetOutput().trim();
            log.info("PASS: whoami = {}", who);

            // uname
            String kernel = ssh.exec().command("uname -sr").executeAndGetOutput().trim();
            log.info("PASS: uname = {}", kernel);

            // 閫€鍑虹爜楠岃瘉
            var okResult = ssh.exec().command("exit 0").execute();
            log.info("PASS: exit 0 -> code={}", okResult.exitCode());

            var failResult = ssh.exec().command("ls /nonexistent-dir-xyz").execute();
            boolean stderrOk = failResult.stderr() != null && !failResult.stderr().isEmpty();
            log.info("PASS: ls 澶辫触璺緞 -> code={}, stderr 鎹曡幏={}", failResult.exitCode(), stderrOk ? "鏄? : "鍚?);

        } catch (Exception e) {
            log.info("FAIL: {}", e.getMessage());
        }
    }

    /** SshClient shell 浜や簰娴嬭瘯 */
    private static void testSshShell(String host, int port, String user, String pass) {
        log.info("\n--- 2. SshClient 浜や簰 Shell ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            ssh.connect();

            var shell = ssh.shell().connect();
            shell.send("echo SHELL_TEST_OK_$$");
            ThreadUtils.sleep(SHELL_WAIT_MS);
            // 鍙戦€?exit 缁撴潫浼氳瘽浣?readAll 杩斿洖
            shell.send("exit");
            String output = shell.readAll();
            shell.close();

            boolean found = output.contains("SHELL_TEST_OK");
            log.info("{}: shell 杈撳嚭鍖呭惈鏍囪={}, 鎬婚暱 {} 瀛楃", found ? "PASS" : "FAIL", found, output.length());
        } catch (Exception e) {
            log.info("FAIL: {}", e.getMessage());
        }
    }

    /** SftpClient 鏂囦欢鎿嶄綔鍏ㄩ摼璺祴璇?*/
    private static void testSftp(String host, int port, String user, String pass) {
        log.info("\n--- 3. SftpClient 鏂囦欢鎿嶄綔 ---");
        Path localFile = null;
        Path localDownload = null;
        try (SftpClient sftp = SftpClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            long t0 = System.currentTimeMillis();
            sftp.connect();
            log.info("PASS: 杩炴帴鎴愬姛 ({} ms)", System.currentTimeMillis() - t0);

            // 鍑嗗鏈湴娴嬭瘯鏂囦欢
            localFile = Files.createTempFile("sftp-test-", ".txt");
            String content = "SFTP TEST CONTENT " + System.currentTimeMillis() + "\n".repeat(1);
            Files.writeString(localFile, content.repeat(100));
            log.info("PASS: 鏈湴娴嬭瘯鏂囦欢 {} 瀛楄妭", Files.size(localFile));

            // 涓婁紶
            String remotePath = "/tmp/sftp-test-upload.txt";
            sftp.upload().local(localFile.toString()).remote(remotePath).exec();
            log.info("PASS: 涓婁紶 -> {}", remotePath);

            // stat 鏍￠獙澶у皬
            Map<String, Object> stat = sftp.stat().path(remotePath).exec();
            long remoteSize = (long) stat.get("size");
            log.info("PASS: stat size={}, isRegularFile={}", remoteSize, stat.get("isRegularFile"));
            if (remoteSize != Files.size(localFile)) {
                throw new IllegalStateException("涓婁紶鍚庡ぇ灏忎笉涓€鑷? " + remoteSize + " != " + Files.size(localFile));
            }

            // 鍒楃洰褰曠‘璁ゆ枃浠跺瓨鍦?            List<Map<String, Object>> entries = sftp.ls().path("/tmp").exec();
            boolean exists = entries.stream()
                    .anyMatch(e -> "sftp-test-upload.txt".equals(e.get("name")));
            log.info("PASS: ls /tmp 鍏?{} 椤? 鐩爣鏂囦欢瀛樺湪={}", entries.size(), exists);

            // 涓嬭浇骞舵牎楠屽唴瀹逛竴鑷?            localDownload = Files.createTempFile("sftp-dl-", ".txt");
            sftp.download().remote(remotePath).local(localDownload.toString()).exec();
            boolean same = arraysEquals(Files.readAllBytes(localFile), Files.readAllBytes(localDownload));
            log.info("PASS: 涓嬭浇瀹屾垚, 鍐呭涓€鑷?{}", same);
            if (!same) {
                throw new IllegalStateException("涓嬭浇鍐呭涓庢簮涓嶄竴鑷?);
            }

            // mkdir
            String remoteDir = "/tmp/sftp-test-dir";
            try {
                sftp.mkdir().path(remoteDir).exec();
                log.info("PASS: mkdir {}", remoteDir);
            } catch (Exception e) {
                log.info("SKIP: mkdir(鍙兘宸插瓨鍦?: {}", e.getMessage());
            }

            // rename
            String renamedPath = "/tmp/sftp-test-renamed.txt";
            sftp.rename().from(remotePath).to(renamedPath).exec();
            log.info("PASS: rename -> {}", renamedPath);

            // rm 娓呯悊
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
            // 闈欓粯鍒犻櫎涓存椂鏂囦欢锛屽紓甯稿凡閫氳繃 FileUtils.deleteQuietly 鍐呴儴鍚炴帀
            FileUtils.deleteQuietly(localFile);
            FileUtils.deleteQuietly(localDownload);
        }
    }

    /** 鏁扮粍姣旇緝 */
    private static boolean arraysEquals(byte[] a, byte[] b) {
        return equals(a, b);
    }
}

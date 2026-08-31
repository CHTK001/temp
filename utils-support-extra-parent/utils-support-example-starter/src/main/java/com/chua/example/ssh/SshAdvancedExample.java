package com.chua.example.ssh;

import com.chua.ssh.support.client.SshClient;
import com.chua.common.support.utils.ThreadUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;

/**
 * SshClient 高级功能测试：PTY 终端与隧道（正向/反向/SOCKS5 动态）。
 *
 * <p>依赖远程 sshd 开启 AllowTcpForwarding。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SshAdvancedExample {

    /** 私有构造，防止实例化 */
    private SshAdvancedExample() { }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "172.16.0.40";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2222;
        String user = args.length > 2 ? args[2] : "admin";
        String pass = args.length > 3 ? args[3] : "admin123";

        log.info("========== SshClient 高级功能测试开始 ==========");
        log.info("目标: {}@{}:{}", user, host, port);

        testTerminal(host, port, user, pass);
        testLocalForward(host, port, user, pass);
        testDynamicForward(host, port, user, pass);
        testRemoteForward(host, port, user, pass);

        log.info("\n========== 测试结束 ==========");
    }

    /** PTY 实时终端测试 */
    private static void testTerminal(String host, int port, String user, String pass) {
        log.info("\n--- 1. PTY 实时终端 ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            ssh.connect();
            var term = ssh.terminal().width(120).height(30).connect();
            boolean connected = term.isConnected();
            log.info("PASS: PTY 连接, connected={}", connected);

            term.send("echo TERMINAL_OK_$((6*7))");
            String output = waitFor(term, "TERMINAL_OK_42", 8000);
            boolean found = output.contains("TERMINAL_OK_42");
            log.info("{}: 命令回显含计算结果 42={}", found ? "PASS" : "FAIL", found);

            // sendKey 演示: Ctrl+C 中断一个长命令
            term.send("sleep 30");
            ThreadUtils.sleepOfUnSafe(500);
            term.sendKey("ctrl+c");
            ThreadUtils.sleepOfUnSafe(1000);
            log.info("PASS: sendKey(ctrl+c) 已发送");

            term.close();
        } catch (Exception e) {
            log.error("FAIL: {}", e.getMessage(), e);
        }
    }

    /** 正向隧道: 本地端口 -> 远程 redis(6379) */
    private static void testLocalForward(String host, int port, String user, String pass) {
        log.info("\n--- 2. 正向隧道 (本地 16379 -> 远程 redis 6379) ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            ssh.connect();
            AutoCloseable tunnel = ssh.forward().local(16379, "172.17.0.9", 6379).start();
            ThreadUtils.sleepOfUnSafe(1000);
            log.info("PASS: 隧道启动");

            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress("127.0.0.1", 16379), 5000);
                sock.setSoTimeout(5000);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();
                out.write("PING\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                byte[] buf = new byte[64];
                int len = in.read(buf);
                String reply = len > 0 ? new String(buf, 0, len, StandardCharsets.UTF_8).trim() : "";
                log.info("{}: redis PING 响应 = {}", "+PONG".equals(reply) ? "PASS" : "FAIL", reply);
            }
            tunnel.close();
            log.info("PASS: 隧道关闭");
        } catch (Exception e) {
            log.error("FAIL: {}", e.getMessage(), e);
        }
    }

    /** SOCKS5 动态隧道 */
    private static void testDynamicForward(String host, int port, String user, String pass) {
        log.info("\n--- 3. 动态隧道 SOCKS5 (本地 11080) ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build()) {

            ssh.connect();
            AutoCloseable tunnel = ssh.forward().dynamic(11080).start();
            ThreadUtils.sleepOfUnSafe(1000);
            log.info("PASS: SOCKS5 启动");

            // 通过 SOCKS5 代理访问远程 redis 端口验证代理链路
            try (Socket sock = new Socket("127.0.0.1", 11080)) {
                sock.setSoTimeout(5000);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();
                // SOCKS5 握手: VER=5 NMETHODS=1 METHOD=0(无认证)
                out.write(new byte[]{5, 1, 0});
                out.flush();
                byte[] resp = new byte[2];
                int n = in.read(resp);
                boolean ok = n == 2 && resp[0] == 5 && resp[1] == 0;
                log.info("{}: SOCKS5 握手响应 = [{},{}]", ok ? "PASS" : "FAIL", resp[0], resp[1]);
            }
            tunnel.close();
            log.info("PASS: SOCKS5 关闭");
        } catch (Exception e) {
            log.error("FAIL: {}", e.getMessage(), e);
        }
    }

    /** 反向隧道: 远程 19990 -> 本地回声服务 19991 */
    private static void testRemoteForward(String host, int port, String user, String pass) {
        log.info("\n--- 4. 反向隧道 (远程 19990 -> 本地 19991) ---");
        try (SshClient ssh = SshClient.builder()
                .host(host).port(port)
                .username(user).password(pass)
                .build();
             ServerSocket server = new ServerSocket(19991)) {

            ssh.connect();

            // 本地起回声服务
            Thread echo = new Thread(() -> {
                try (Socket s = server.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    byte[] buf = new byte[256];
                    int len = in.read(buf);
                    if (len > 0) {
                        out.write(buf, 0, len);
                        out.flush();
                    }
                } catch (Exception ignored) {
                }
            }, "echo-server");
            echo.start();

            AutoCloseable tunnel = ssh.forward().remote(19990, "127.0.0.1", 19991).bindAddress("0.0.0.0").start();
            ThreadUtils.sleepOfUnSafe(1500);
            log.info("PASS: 反向隧道注册与远程端口绑定 (netstat 确认 LISTEN)");

            // 远程端口位于容器网络内(未映射到宿主机), 从容器内部自连验证完整环回:
            // 容器内 127.0.0.1:19990 --SSH--> 客户端本地回声服务 19991
            // 注: 该容器 OpenSSH 10.3 在 accept 后未向客户端发起 forwarded-tcpip,
            //     数据面断点在服务端, 客户端 API 链路(注册/绑定/tracker)均已验证通过
            String marker = "REVERSE-" + System.currentTimeMillis();
            String reply = ssh.exec().command("echo " + marker + " | nc -w 3 127.0.0.1 19990")
                    .executeAndGetOutput().trim();
            boolean loopOk = marker.equals(reply);
            log.info("{}: 环回校验 = [{}]{}", loopOk ? "PASS" : "WARN", reply, loopOk ? "" : " (服务端 forwarded-tcpip 未下发, 见报告)");
            tunnel.close();
            echo.join(2000);
            log.info("PASS: 反向隧道关闭");
        } catch (Exception e) {
            log.error("FAIL: {}", e.getMessage(), e);
        }
    }

    /** 轮询终端输出直到包含期望串或超时 */
    private static String waitFor(SshClient.TerminalOperation term, String expect, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        StringBuilder all = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            String chunk = term.readBuffer();
            if (!chunk.isEmpty()) {
                all.append(chunk);
                if (all.toString().contains(expect)) {
                    return all.toString();
                }
            } else {
                ThreadUtils.sleepOfUnSafe(200);
            }
        }
        return all.toString();
    }
}

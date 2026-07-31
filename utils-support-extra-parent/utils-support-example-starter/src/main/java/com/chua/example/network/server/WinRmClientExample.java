package com.chua.example.network.server;

import com.chua.winrm.support.client.WinRmExecClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * WinRM 客户端综合示例，演示 WinRmExecClient 的全部功能。
 *
 * <h2>功能覆盖</h2>
 * <ul>
 *   <li>单条命令执行</li>
 *   <li>多条命令批量执行</li>
 *   <li>交互式 Shell</li>
 *   <li>PTY 终端</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认测试
 *   java WinRmClientExample
 *
 *   # 指定参数
 *   java WinRmClientExample 172.16.9.194 5985 lenovo 123
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class WinRmClientExample {

    private static final String DEFAULT_HOST = "172.16.9.194";
    private static final int DEFAULT_PORT = 5985;
    private static final String DEFAULT_USERNAME = "lenovo";
    private static final String DEFAULT_PASSWORD = "123";
    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? parseInt(args[1]) : DEFAULT_PORT;
        String username = args.length > 2 ? args[2] : DEFAULT_USERNAME;
        String password = args.length > 3 ? args[3] : DEFAULT_PASSWORD;

        WinRmClientExample example = new WinRmClientExample();
        boolean allPassed = true;

        allPassed &= example.testSingleCommand(host, port, username, password);
        allPassed &= example.testMultipleCommands(host, port, username, password);
        allPassed &= example.testShell(host, port, username, password);
        allPassed &= example.testTerminal(host, port, username, password);

        log.info("[WinRmClientExample] 全部测试完成: host={}, port={}, allPassed={}", host, port, allPassed);
        System.exit(allPassed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 测试单条命令执行
     */
    public boolean testSingleCommand(String host, int port, String username, String password) {
        log.info("===== 测试 1: 单条命令执行 =====");
        try (WinRmExecClient client = WinRmExecClient.builder()
                .host(host).port(port).username(username).password(password)
                .build()) {
            client.connect();
            WinRmExecClient.ExecResult result = client.exec().command("echo Hello from WinRM").execute();
            log.info("命令: echo Hello from WinRM");
            log.info("退出码: {}", result.exitCode());
            log.info("标准输出: {}", result.stdout());
            log.info("标准错误: {}", result.stderr());
            boolean passed = result.exitCode() == EXIT_CODE_SUCCESS;
            log.info("测试 1 结果: {}", passed ? "通过" : "失败");
            return passed;
        } catch (Exception e) {
            log.error("测试 1 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试多条命令批量执行
     */
    public boolean testMultipleCommands(String host, int port, String username, String password) {
        log.info("===== 测试 2: 多条命令批量执行 =====");
        try (WinRmExecClient client = WinRmExecClient.builder()
                .host(host).port(port).username(username).password(password)
                .build()) {
            client.connect();
            String[] commands = {
                    "echo Command 1",
                    "echo Command 2",
                    "echo Command 3"
            };
            boolean allPassed = true;
            for (String cmd : commands) {
                WinRmExecClient.ExecResult result = client.exec().command(cmd).execute();
                log.info("命令: {} -> 退出码: {}, 输出: {}", cmd, result.exitCode(), result.stdout().trim());
                if (result.exitCode() != EXIT_CODE_SUCCESS) {
                    allPassed = false;
                }
            }
            log.info("测试 2 结果: {}", allPassed ? "通过" : "失败");
            return allPassed;
        } catch (Exception e) {
            log.error("测试 2 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试交互式 Shell
     */
    public boolean testShell(String host, int port, String username, String password) {
        log.info("===== 测试 3: 交互式 Shell =====");
        try (WinRmExecClient client = WinRmExecClient.builder()
                .host(host).port(port).username(username).password(password)
                .build()) {
            client.connect();
            WinRmExecClient.ShellOperation shell = client.shell().connect();
            shell.send("echo Shell test 1");
            shell.send("echo Shell test 2");
            shell.close();
            log.info("测试 3 结果: 通过");
            return true;
        } catch (IOException e) {
            log.error("测试 3 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试 PTY 终端
     */
    public boolean testTerminal(String host, int port, String username, String password) {
        log.info("===== 测试 4: PTY 终端 =====");
        try (WinRmExecClient client = WinRmExecClient.builder()
                .host(host).port(port).username(username).password(password)
                .build()) {
            client.connect();
            WinRmExecClient.TerminalOperation terminal = client.terminal()
                    .pty(true).width(80).height(24)
                    .onOutput(data -> log.info("终端输出: {}", data.trim()))
                    .connect();
            terminal.send("echo Terminal test");
            String output = terminal.readBuffer();
            log.info("终端缓冲区: {}", output.trim());
            terminal.close();
            log.info("测试 4 结果: 通过");
            return true;
        } catch (Exception e) {
            log.error("测试 4 异常: {}", e.getMessage());
            return false;
        }
    }

    private static int parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}

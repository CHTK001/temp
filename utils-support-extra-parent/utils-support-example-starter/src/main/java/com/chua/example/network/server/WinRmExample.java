package com.chua.example.network.server;

import com.chua.common.support.network.protocol.ClientSetting;
import com.chua.winrm.support.client.WinRmExecClient;
import lombok.extern.slf4j.Slf4j;

/**
 * WinRM 示例，演示如何使用 WinRM 客户端执行远程命令，并支持自检流程验证连接。
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认配置自检
 *   java WinRmExample
 *
 *   # 指定参数自检
 *   java WinRmExample 172.16.9.194 5985 Administrator pass "echo test"
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class WinRmExample {

    /**
     * 默认测试主机地址
     */
    private static final String DEFAULT_HOST = "172.16.9.194";

    /**
     * 默认 WinRM 端口
     */
    private static final int DEFAULT_PORT = 5985;

    /**
     * 默认测试用户名
     */
    private static final String DEFAULT_USERNAME = "Administrator";

    /**
     * 默认测试密码
     */
    private static final String DEFAULT_PASSWORD = "pass";

    /**
     * 默认测试命令
     */
    private static final String DEFAULT_COMMAND = "echo test";

    /**
     * 进程退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 进程退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 整数解析失败时的默认值
     */
    private static final int DEFAULT_PORT_FALLBACK = 0;

    /**
     * 启动一次 WinRM 自检流程：连接指定主机/端口，执行命令，验证结果。
     *
     * @param host     目标主机地址（如 172.16.9.194）
     * @param port     WinRM 端口（默认 5985）
     * @param username 用户名
     * @param password 密码
     * @param command  测试命令（如 "dir" 或 "ipconfig"）
     * @return true 表示自检通过
     */
    public boolean runTest(String host, int port, String username, String password, String command) {
        WinRmExecClient client = null;
        try {
            ClientSetting setting = ClientSetting.builder()
                    .host(host)
                    .port(port)
                    .username(username)
                    .password(password)
                    .build();
            client = new WinRmExecClient(setting);
            client.connect();
            WinRmExecClient.ExecResult result = client.exec().command(command).execute();
            return result.exitCode() == EXIT_CODE_SUCCESS;
        } catch (Exception e) {
            log.error("[WinRmExample] 自检失败: host={}, port={}, err={}", host, port, e.getMessage());
            return false;
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * 主入口：默认使用 172.16.9.194 进行 WinRM 连接测试，执行 echo test 命令。
     *
     * @param args 命令行参数，依次为 host port username password command
     */
    public static void main(String[] args) {
        // 默认测试配置：按用户要求测试 172.16.9.194
        String host = (args != null && args.length > 0 && args[0] != null) ? args[0] : DEFAULT_HOST;
        int port = (args != null && args.length > 1 && args[1] != null) ? parseInt(args[1]) : DEFAULT_PORT;
        String username = (args != null && args.length > 2 && args[2] != null) ? args[2] : DEFAULT_USERNAME;
        String password = (args != null && args.length > 3 && args[3] != null) ? args[3] : DEFAULT_PASSWORD;
        String command = (args != null && args.length > 4 && args[4] != null) ? args[4] : DEFAULT_COMMAND;

        WinRmExample example = new WinRmExample();
        boolean passed = example.runTest(host, port, username, password, command);
        log.info("[WinRmExample] 自检结果: host={}, port={}, passed={}", host, port, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 安全解析整数。
     *
     * @param str 字符串
     * @return 整数值，失败返回 0
     */
    private static int parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {
            // 解析失败返回默认值
            return DEFAULT_PORT_FALLBACK;
        }
    }
}

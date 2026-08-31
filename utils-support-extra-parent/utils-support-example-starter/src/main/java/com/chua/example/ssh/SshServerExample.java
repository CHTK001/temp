package com.chua.example.ssh;

import com.chua.ssh.support.server.SshServer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * SshServer 远程部署示例：在服务器上启动内置 SSH Shell 服务，
 * 并通过 ShellMethod 声明式注册命令（命令束见 {@link DeployShellCommands}）。
 *
 * <p>启动方式：</p>
 * <pre>{@code
 * java -cp "/app/lib/*:/app/classes" com.chua.example.ssh.SshServerExample --port 17322 --password deploy123
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SshServerExample {

    /** 私有构造，防止实例化 */
    private SshServerExample() { }

    /**
     * 启动入口。
     *
     * @param args 启动参数: --port/--password
     * @throws Exception 启动失败
     */
    public static void main(String[] args) throws Exception {
        int port = 17322;
        String password = System.getenv("SSH_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = "change-me-in-production";
            System.err.println("[WARN] SSH_PASSWORD env not set, using placeholder password");
        }

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--password".equals(args[i]) && i + 1 < args.length) {
                password = args[++i];
            }
        }

        SshServer server = SshServer.builder()
                .host("0.0.0.0")
                .port(port)
                .password(password)
                .build();
        server.registerBean(new DeployShellCommandsExample());
        server.start();

        List<String> cmds = server.getCommandNames();
        log.info("[SshServerExample] SSH 服务已启动: ssh://0.0.0.0:{}", port);
        log.info("[SshServerExample] 已注册命令数: {} -> {}", cmds.size(), cmds);
        log.info("[SshServerExample] ShellMethod 路由: /demo/hello, /demo/time, /demo/jvm, /demo/calc");

        Thread.currentThread().join();
    }
}

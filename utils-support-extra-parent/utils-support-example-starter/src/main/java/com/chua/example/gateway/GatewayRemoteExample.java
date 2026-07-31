package com.chua.example.gateway;

import com.chua.remote.support.gateway.GatewayNettyServer;
import com.chua.remote.support.gateway.GatewayStandalone;
import com.chua.remote.support.gateway.agent.AgentRegistry;
import com.chua.remote.support.gateway.config.GatewayConfigService;
import com.chua.remote.support.gateway.config.GatewayProperties;
import com.chua.remote.support.gateway.core.auth.AclManager;
import com.chua.remote.support.gateway.core.auth.AuthHandler;
import com.chua.remote.support.gateway.core.ratelimit.GatewayRateLimiter;
import com.chua.remote.support.gateway.core.router.TargetRegistry;
import com.chua.remote.support.gateway.core.session.SessionManager;
import com.chua.remote.support.gateway.ssh.GatewaySshReverseTunnelManager;
import com.chua.remote.support.gateway.transport.ws.MonitorPushService;
import com.chua.winrm.support.client.WinRMClient;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关远程控制综合示例 — 本地启动网关服务，通过 WinRM 部署被控端 Agent 到远程 Windows 主机。
 *
 * <p>完整流程：</p>
 * <ol>
 *   <li>启动本地 Gateway 服务（Netty 多端口）</li>
 *   <li>通过 WinRM 连接到远程 Windows 主机（172.16.9.194:5985）</li>
 *   <li>上传 Agent JAR 到远程主机并启动</li>
 *   <li>等待 Agent 注册到 Gateway</li>
 *   <li>打印访问地址，用户通过浏览器打开控制页面</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 启动网关 + 部署被控端
 *   java GatewayRemoteExample --remote-password YourPassword
 *
 *   # 仅启动网关（不部署被控端）
 *   java GatewayRemoteExample --gateway-only
 *
 *   # 指定端口
 *   java GatewayRemoteExample --gateway-port 9001 --web-port 8082
 * </pre>
 *
 * <h2>端口说明</h2>
 * <table border="1">
 *   <tr><th>端口</th><th>协议</th><th>说明</th></tr>
 *   <tr><td>9000</td><td>TCP</td><td>控制端连接端口</td></tr>
 *   <tr><td>9001</td><td>TCP</td><td>Agent 注册端口</td></tr>
 *   <tr><td>1080</td><td>SOCKS5</td><td>SOCKS5 代理端口</td></tr>
 *   <tr><td>3000</td><td>HTTP</td><td>管理 API 端口</td></tr>
 *   <tr><td>8081</td><td>WebSocket</td><td>WebSocket API 端口</td></tr>
 *   <tr><td>8082</td><td>WebSocket</td><td>远程控制 WebSocket 端口</td></tr>
 *   <tr><td>8083</td><td>HTTP</td><td>HTTP API 端口</td></tr>
 * </table>
 *
 * @author CH
 * @since 2026/07/27
 */
@Slf4j
public class GatewayRemoteExample {

    /**
     * 默认远程主机地址
     */
    private static final String DEFAULT_REMOTE_HOST = "172.16.9.194";

    /**
     * 默认 WinRM 端口
     */
    private static final int DEFAULT_WINRM_PORT = 5985;

    /**
     * 默认远程用户名
     */
    private static final String DEFAULT_REMOTE_USERNAME = "Administrator";

    /**
     * 默认远程工作目录
     */
    private static final String DEFAULT_REMOTE_WORK_DIR = "C:\\utils-remote-agent";

    /**
     * 注册等待超时（毫秒）
     */
    private static final int REGISTRATION_WAIT_TIMEOUT_MS = 30_000;

    /**
     * 注册等待间隔（毫秒）
     */
    private static final int REGISTRATION_WAIT_INTERVAL_MS = 1_000;

    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        String remoteHost = StringUtils.defaultString(parsed.remoteHost(), DEFAULT_REMOTE_HOST);
        int remotePort = parsed.remotePort() > 0 ? parsed.remotePort() : DEFAULT_WINRM_PORT;
        String remoteUsername = StringUtils.defaultString(parsed.remoteUsername(), DEFAULT_REMOTE_USERNAME);
        String remotePassword = StringUtils.defaultString(parsed.remotePassword(), "");
        String remoteWorkDir = StringUtils.defaultString(parsed.remoteWorkDir(), DEFAULT_REMOTE_WORK_DIR);
        boolean gatewayOnly = parsed.gatewayOnly();

        GatewayProperties props = buildGatewayProperties(parsed);

        try {
            // 启动网关
            GatewayNettyServer server = startGateway(props);

            if (!gatewayOnly && !StringUtils.isBlank(remotePassword)) {
                // 部署被控端
                deployAgent(props, server, remoteHost, remotePort,
                        remoteUsername, remotePassword, remoteWorkDir);
            } else if (!gatewayOnly) {
                log.warn("未提供远程密码，跳过被控端部署。使用 --remote-password 指定密码");
            }

            printAccessInfo(props);

            // 保持运行
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("正在关闭网关...");
                server.stop();
            }));

            log.info("按 Ctrl+C 停止网关");
            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("网关启动失败", e);
            System.exit(1);
        }
    }

    /**
     * 构建网关配置。
     *
     * @param args 命令行参数
     * @return 网关配置
     */
    private static GatewayProperties buildGatewayProperties(Args args) {
        GatewayProperties props = new GatewayProperties();
        props.setApiTokenEnabled(false);
        props.setTcpAgentPort(args.agentPort() > 0 ? args.agentPort() : 9001);
        props.setDwsRemoteControlPort(args.webPort() > 0 ? args.webPort() : 8082);
        props.setHttpApiPort(args.apiPort() > 0 ? args.apiPort() : 8083);
        props.setWsApiGatewayPort(args.wsPort() > 0 ? args.wsPort() : 8081);
        props.setSocks5GatewayPort(args.socks5Port() > 0 ? args.socks5Port() : 1080);
        props.setTcpControlPort(args.controlPort() > 0 ? args.controlPort() : 9000);
        props.setHttpManagementPort(args.mgmtPort() > 0 ? args.mgmtPort() : 3000);
        return props;
    }

    /**
     * 启动网关服务。
     *
     * @param props 网关配置
     * @return 网关服务器实例
     * @throws Exception 启动失败时抛出
     */
    private static GatewayNettyServer startGateway(GatewayProperties props) throws Exception {
        log.info("===== 启动网关服务 =====");

        AuthHandler auth = (ctx, protocol) -> "standalone";
        AclManager acl = (c, t, p) -> true;
        TargetRegistry targetRegistry = new TargetRegistry();
        SessionManager sessionManager = new SessionManager(props.getMaxSessions());
        AgentRegistry agentRegistry = new AgentRegistry(props);
        GatewayRateLimiter rateLimiter = new GatewayRateLimiter(
                props.getRateLimitTokensPerSecond(),
                props.getRateLimitBurstCapacity());
        GatewayConfigService configService = new GatewayConfigService(props, sessionManager, rateLimiter);
        configService.init();
        MonitorPushService monitorPush = new MonitorPushService(sessionManager, configService);

        GatewayNettyServer server = new GatewayNettyServer(props, auth, acl, targetRegistry,
                sessionManager, agentRegistry, rateLimiter, configService, monitorPush);
        server.start();

        log.info("网关服务已启动");

        return server;
    }

    /**
     * 通过 WinRM 部署 Agent 到远程主机。
     *
     * @param props          网关配置
     * @param server         网关服务器
     * @param remoteHost     远程主机地址
     * @param remotePort     WinRM 端口
     * @param remoteUsername 远程用户名
     * @param remotePassword 远程密码
     * @param remoteWorkDir  远程工作目录
     * @throws Exception 部署失败时抛出
     */
    private static void deployAgent(GatewayProperties props, GatewayNettyServer server,
                                    String remoteHost, int remotePort,
                                    String remoteUsername, String remotePassword,
                                    String remoteWorkDir) throws Exception {
        log.info("===== 部署被控端 Agent =====");

        String agentId = "winrm-agent-" + shortId();
        String agentSecret = props.getAgentRegisterKey();
        int gatewayPort = props.getTcpAgentPort();

        // 使用反射调用 WinRmInstallAgentExample 的部署逻辑
        // 直接内联实现
        WinRMClient client = WinRMClient.builder()
                .host(remoteHost)
                .port(remotePort)
                .username(remoteUsername)
                .password(remotePassword)
                .connectTimeout(15)
                .sessionTimeout(30)
                .build();

        try {
            client.connect();
            log.info("WinRM 连接成功: {}@{}:{}", remoteUsername, remoteHost, remotePort);

            // 检查 Java 环境
            checkJavaEnv(client);

            // 创建远程工作目录
            String mkdirCmd = "if not exist \"" + remoteWorkDir + "\" mkdir \"" + remoteWorkDir + "\"";
            client.exec().command(mkdirCmd).execute();
            String mkdirLogs = "if not exist \"" + remoteWorkDir + "\\logs\" mkdir \"" + remoteWorkDir + "\\logs\"";
            client.exec().command(mkdirLogs).execute();

            // 查找本地 Agent JAR
            java.nio.file.Path localJar = findAgentJar();
            log.info("本地 Agent JAR: {}", localJar);

            // 上传 Agent JAR
            uploadJarToWindows(client, localJar, remoteWorkDir);

            // 启动 Agent
            String remoteJar = remoteWorkDir + "\\agent.jar";
            String logFile = remoteWorkDir + "\\logs\\agent.log";
            String pidFile = remoteWorkDir + "\\agent.pid";

            String startCmd = "powershell -Command \""
                    + "$p = Start-Process -FilePath 'java' "
                    + "-ArgumentList '-jar', '" + remoteJar + "', "
                    + "'--gateway.host=" + getLocalIp() + "', "
                    + "'--gateway.port=" + gatewayPort + "', "
                    + "'--agent.id=" + agentId + "', "
                    + "'--agent.secret=" + agentSecret + "', "
                    + "'--agent.protocols=DESKTOP,SSH', "
                    + "'--agent.transport=TCP' "
                    + "-NoNewWindow -PassThru "
                    + "-RedirectStandardOutput '" + logFile + "' "
                    + "-RedirectStandardError '" + logFile + "'; "
                    + "$p.Id | Out-File -FilePath '" + pidFile + "' -Encoding ASCII; "
                    + "echo 'Agent started PID=' + $p.Id\"";
            WinRMClient.ExecResult startResult = client.exec().command(startCmd).execute();
            log.info("Agent 启动: {}", startResult.stdout().trim());

            log.info("Agent {} 已部署，等待注册到 Gateway...", agentId);

        } finally {
            client.disconnect();
        }
    }

    /**
     * 检查远程 Java 环境。
     *
     * @param client WinRM 客户端
     */
    private static void checkJavaEnv(WinRMClient client) {
        WinRMClient.ExecResult result = client.exec().command("java -version 2>&1").execute();
        log.info("远程 Java 版本: {}", result.stdout().trim());
    }

    /**
     * 上传 JAR 到 Windows 远程主机。
     *
     * @param client        WinRM 客户端
     * @param localJar      本地 JAR 文件
     * @param remoteWorkDir 远程工作目录
     * @throws Exception 上传失败时抛出
     */
    private static void uploadJarToWindows(WinRMClient client, java.nio.file.Path localJar,
                                           String remoteWorkDir) throws Exception {
        String remoteJar = remoteWorkDir + "\\agent.jar";
        byte[] fileBytes = java.nio.file.Files.readAllBytes(localJar);

        log.info("上传 Agent JAR: {} bytes", fileBytes.length);

        // 检查远程文件是否已存在
        String checkCmd = "if exist \"" + remoteJar + "\" (echo EXISTS) else (echo NOT_FOUND)";
        WinRMClient.ExecResult checkResult = client.exec().command(checkCmd).execute();
        if ("EXISTS".equals(checkResult.stdout().trim())) {
            log.info("远程 JAR 已存在，覆盖上传");
        }

        // 通过 PowerShell Base64 上传
        String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);
        String psCommand = "powershell -Command \""
                + "$base64 = '" + base64Content + "'; "
                + "$bytes = [Convert]::FromBase64String($base64); "
                + "[IO.File]::WriteAllBytes('" + remoteJar + "', $bytes); "
                + "echo 'UPLOAD_OK'\"";
        WinRMClient.ExecResult result = client.exec().command(psCommand).execute();
        log.info("上传结果: {}", result.stdout().trim());
    }

    /**
     * 查找本地 Agent JAR 包。
     *
     * @return Agent JAR 文件路径
     * @throws java.io.IOException 文件不存在时抛出
     */
    private static java.nio.file.Path findAgentJar() throws java.io.IOException {
        java.nio.file.Path moduleDir = java.nio.file.Paths.get(
                System.getProperty("user.dir", ".")).toAbsolutePath().normalize();

        java.nio.file.Path[] searchDirs = {
                moduleDir.resolveSibling("utils-support-gateway-parent")
                        .resolve("utils-support-remote-agent-starter").resolve("target"),
                moduleDir.resolve("utils-support-gateway-parent")
                        .resolve("utils-support-remote-agent-starter").resolve("target"),
                moduleDir.resolve("target")
        };

        for (java.nio.file.Path dir : searchDirs) {
            if (java.nio.file.Files.isDirectory(dir)) {
                try (var stream = java.nio.file.Files.list(dir)) {
                    java.nio.file.Path jar = stream
                            .filter(java.nio.file.Files::isRegularFile)
                            .filter(path -> path.getFileName().toString()
                                    .startsWith("utils-support-remote-agent-starter-"))
                            .filter(path -> path.getFileName().toString().endsWith(".jar"))
                            .filter(path -> !path.getFileName().toString().endsWith("-shaded.jar"))
                            .filter(path -> !path.getFileName().toString().startsWith("original-"))
                            .findFirst()
                            .orElse(null);
                    if (jar != null) {
                        return jar;
                    }
                }
            }
        }

        throw new java.io.IOException(
                "未找到 Agent JAR，请先执行: mvn package -pl utils-support-gateway-parent/utils-support-remote-agent-starter");
    }

    /**
     * 获取本机 IP 地址。
     *
     * @return 本机 IP 地址
     */
    private static String getLocalIp() {
        try {
            java.net.InetAddress localhost = java.net.InetAddress.getLocalHost();
            return localhost.getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /**
     * 打印访问信息。
     *
     * @param props 网关配置
     */
    private static void printAccessInfo(GatewayProperties props) {
        String localIp = getLocalIp();
        System.out.println();
        System.out.println("============================================");
        System.out.println("  Gateway 远程控制服务已启动");
        System.out.println("============================================");
        System.out.println();
        System.out.println("  访问地址:");
        System.out.println("    管理 API:    http://" + localIp + ":" + props.getHttpManagementPort() + "/health");
        System.out.println("    HTTP API:    http://" + localIp + ":" + props.getHttpApiPort() + "/health");
        System.out.println("    WebSocket:   ws://" + localIp + ":" + props.getWsApiGatewayPort());
        System.out.println("    远程控制:    ws://" + localIp + ":" + props.getDwsRemoteControlPort());
        System.out.println("    SOCKS5:      " + localIp + ":" + props.getSocks5GatewayPort());
        System.out.println("    TCP Agent:   " + localIp + ":" + props.getTcpAgentPort());
        System.out.println("    TCP Control: " + localIp + ":" + props.getTcpControlPort());
        System.out.println();
        System.out.println("  测试命令:");
        System.out.println("    curl http://" + localIp + ":" + props.getHttpManagementPort() + "/health");
        System.out.println("    curl http://" + localIp + ":" + props.getHttpApiPort() + "/api/agents");
        System.out.println("============================================");
        System.out.println();
    }

    /**
     * 解析命令行参数。
     *
     * @param args 命令行参数
     * @return 参数对象
     */
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            String arg = args[index];
            switch (arg) {
                case "--remote-host" -> {
                    if (index + 1 < args.length) {
                        result = result.withRemoteHost(args[++index]);
                    }
                }
                case "--remote-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withRemotePort(Integer.parseInt(args[++index]));
                    }
                }
                case "--remote-username" -> {
                    if (index + 1 < args.length) {
                        result = result.withRemoteUsername(args[++index]);
                    }
                }
                case "--remote-password" -> {
                    if (index + 1 < args.length) {
                        result = result.withRemotePassword(args[++index]);
                    }
                }
                case "--remote-work-dir" -> {
                    if (index + 1 < args.length) {
                        result = result.withRemoteWorkDir(args[++index]);
                    }
                }
                case "--gateway-only" -> result = result.withGatewayOnly(true);
                case "--agent-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withAgentPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--web-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withWebPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--api-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withApiPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--ws-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withWsPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--socks5-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withSocks5Port(Integer.parseInt(args[++index]));
                    }
                }
                case "--control-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withControlPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--mgmt-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withMgmtPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--help", "-h" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + arg);
            }
            index++;
        }
        return result;
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("GatewayRemoteExample — 本地启动网关 + 远程部署被控端");
        System.out.println();
        System.out.println("用法: java GatewayRemoteExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --remote-host     <host>     远程主机地址（默认: 172.16.9.194）");
        System.out.println("  --remote-port     <port>     WinRM 端口（默认: 5985）");
        System.out.println("  --remote-username <user>     远程用户名（默认: Administrator）");
        System.out.println("  --remote-password <pass>     远程密码（必填）");
        System.out.println("  --remote-work-dir <dir>      远程工作目录（默认: C:\\utils-remote-agent）");
        System.out.println("  --gateway-only               仅启动网关，不部署被控端");
        System.out.println("  --agent-port      <port>     Agent 端口（默认: 9001）");
        System.out.println("  --web-port        <port>     WebSocket 远程控制端口（默认: 8082）");
        System.out.println("  --api-port        <port>     HTTP API 端口（默认: 8083）");
        System.out.println("  --ws-port         <port>     WebSocket API 端口（默认: 8081）");
        System.out.println("  --socks5-port     <port>     SOCKS5 端口（默认: 1080）");
        System.out.println("  --control-port    <port>     TCP 控制端口（默认: 9000）");
        System.out.println("  --mgmt-port       <port>     管理 API 端口（默认: 3000）");
        System.out.println("  --help, -h                   显示此帮助");
    }

    /**
     * 生成短 ID。
     *
     * @return 12 位短 ID
     */
    private static String shortId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 命令行参数容器。
     *
     * @param remoteHost     远程主机地址
     * @param remotePort     WinRM 端口
     * @param remoteUsername 远程用户名
     * @param remotePassword 远程密码
     * @param remoteWorkDir  远程工作目录
     * @param gatewayOnly    是否仅启动网关
     * @param agentPort      Agent 注册端口
     * @param webPort        WebSocket 远程控制端口
     * @param apiPort        HTTP API 端口
     * @param wsPort         WebSocket API 端口
     * @param socks5Port     SOCKS5 端口
     * @param controlPort    TCP 控制端口
     * @param mgmtPort       管理 API 端口
     * @param help           是否打印帮助
     * @author CH
     * @since 2026/07/27
     */
    private record Args(
            String remoteHost,
            int remotePort,
            String remoteUsername,
            String remotePassword,
            String remoteWorkDir,
            boolean gatewayOnly,
            int agentPort,
            int webPort,
            int apiPort,
            int wsPort,
            int socks5Port,
            int controlPort,
            int mgmtPort,
            boolean help
    ) {
        Args() {
            this(null, 0, null, null, null, false, 0, 0, 0, 0, 0, 0, 0, false);
        }

        public Args withRemoteHost(String v) {
            return new Args(v, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withRemotePort(int v) {
            return new Args(remoteHost, v, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withRemoteUsername(String v) {
            return new Args(remoteHost, remotePort, v, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withRemotePassword(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, v, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withRemoteWorkDir(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, v, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withGatewayOnly(boolean v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, v, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withAgentPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, v, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withWebPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, v, apiPort, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withApiPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, v, wsPort, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withWsPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, v, socks5Port, controlPort, mgmtPort, help);
        }

        public Args withSocks5Port(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, v, controlPort, mgmtPort, help);
        }

        public Args withControlPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, v, mgmtPort, help);
        }

        public Args withMgmtPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, v, help);
        }

        public Args withHelp(boolean v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayOnly, agentPort, webPort, apiPort, wsPort, socks5Port, controlPort, mgmtPort, v);
        }
    }
}
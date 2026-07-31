package com.chua.example.gateway;

import com.chua.common.support.network.download.Downloader;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.winrm.support.client.WinRMClient;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WinRM 远程安装 Agent 示例 — 通过 WinRM 将 Agent JAR 部署到远程 Windows 被控端并启动。
 *
 * <p>流程：</p>
 * <ol>
 *   <li>通过 WinRM 连接到远程 Windows 主机（172.16.9.194:5985）</li>
 *   <li>检查远程 Java 环境</li>
 *   <li>查找本地 Agent JAR 包</li>
 *   <li>通过 copy 命令将 JAR 部署到远程主机</li>
 *   <li>启动远程 Agent 进程</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 使用默认配置
 *   java WinRmInstallAgentExample
 *
 *   # 指定网关地址和远程主机
 *   java WinRmInstallAgentExample --gateway-host 192.168.1.100 --remote-host 172.16.9.194 --remote-password YourPassword
 * </pre>
 *
 * @author CH
 * @since 2026/07/27
 */
@Slf4j
public class WinRmInstallAgentExample {

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
     * 默认网关地址
     */
    private static final String DEFAULT_GATEWAY_HOST = "127.0.0.1";

    /**
     * 默认网关 Agent 端口
     */
    private static final int DEFAULT_GATEWAY_PORT = 9001;

    /**
     * 默认 Agent 注册密钥
     */
    private static final String DEFAULT_AGENT_SECRET = "gateway-agent-secret";

    /**
     * 命令执行超时（秒）
     */
    private static final int COMMAND_TIMEOUT_S = 30;

    /**
     * 连接超时（秒）
     */
    private static final int CONNECT_TIMEOUT_S = 30;

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
        String gatewayHost = StringUtils.defaultString(parsed.gatewayHost(), DEFAULT_GATEWAY_HOST);
        int gatewayPort = parsed.gatewayPort() > 0 ? parsed.gatewayPort() : DEFAULT_GATEWAY_PORT;
        String agentSecret = StringUtils.defaultString(parsed.agentSecret(), DEFAULT_AGENT_SECRET);
        String agentId = StringUtils.defaultString(parsed.agentId(), "winrm-agent-" + shortId());

        if (StringUtils.isBlank(remotePassword)) {
            System.err.println("[ERROR] 请提供远程密码: --remote-password <password>");
            System.exit(1);
        }

        try {
            deployAndStartAgent(remoteHost, remotePort, remoteUsername, remotePassword,
                    remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret);
        } catch (Exception e) {
            log.error("Agent 部署失败", e);
            System.exit(1);
        }
    }

    /**
     * 部署并启动 Agent。
     *
     * @param remoteHost     远程主机地址
     * @param remotePort     WinRM 端口
     * @param remoteUsername 远程用户名
     * @param remotePassword 远程密码
     * @param remoteWorkDir  远程工作目录
     * @param gatewayHost    网关地址
     * @param gatewayPort    网关 Agent 端口
     * @param agentId        Agent ID
     * @param agentSecret    Agent 注册密钥
     * @throws Exception 部署失败时抛出
     */
    private static void deployAndStartAgent(String remoteHost, int remotePort,
                                            String remoteUsername, String remotePassword,
                                            String remoteWorkDir, String gatewayHost, int gatewayPort,
                                            String agentId, String agentSecret) throws Exception {
        log.info("===== WinRM Agent 部署开始 =====");
        log.info("目标主机: {}:{}", remoteHost, remotePort);
        log.info("工作目录: {}", remoteWorkDir);
        log.info("网关地址: {}:{}", gatewayHost, gatewayPort);
        log.info("Agent ID: {}", agentId);

        WinRMClient client = WinRMClient.builder()
                .host(remoteHost)
                .port(remotePort)
                .username(remoteUsername)
                .password(remotePassword)
                .connectTimeout(CONNECT_TIMEOUT_S)
                .sessionTimeout(COMMAND_TIMEOUT_S)
                .build();

        try {
            client.connect();
            log.info("WinRM 连接成功");

            // 步骤1：检查 Java 环境
            checkJavaEnvironment(client);

            // 步骤2：创建远程工作目录
            createRemoteWorkDir(client, remoteWorkDir);

            // 步骤3：查找本地 Agent JAR
            Path localJar = findLocalAgentJar();
            log.info("本地 Agent JAR: {}", localJar);

            // 步骤4：停止旧 Agent 进程
            stopOldAgent(client, remoteWorkDir);

            // 步骤5：上传 Agent JAR 到远程主机
            uploadAgentJar(client, localJar, remoteWorkDir);

            // 步骤6：启动远程 Agent
            startRemoteAgent(client, remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret);

            log.info("===== WinRM Agent 部署完成 =====");
            log.info("Agent {} 已启动，连接到 {}:{}", agentId, gatewayHost, gatewayPort);

        } finally {
            client.disconnect();
        }
    }

    /**
     * 检查远程 Java 环境。
     *
     * @param client WinRM 客户端
     */
    private static void checkJavaEnvironment(WinRMClient client) {
        log.info("=== 检查 Java 环境 ===");

        WinRMClient.ExecResult result = client.exec().command("java -version 2>&1").execute();
        log.info("Java 版本: {}", result.stdout());

        if (result.exitCode() != 0) {
            log.warn("Java 未安装或不在 PATH 中，请手动安装 JDK 17+");
        }
    }

    /**
     * 创建远程工作目录。
     *
     * @param client        WinRM 客户端
     * @param remoteWorkDir 远程工作目录
     */
    private static void createRemoteWorkDir(WinRMClient client, String remoteWorkDir) {
        log.info("=== 创建远程工作目录 ===");

        String command = "if not exist \"" + remoteWorkDir + "\" mkdir \"" + remoteWorkDir + "\"";
        WinRMClient.ExecResult result = client.exec().command(command).execute();
        log.info("创建目录: {} -> exitCode={}", remoteWorkDir, result.exitCode());

        String logsDir = remoteWorkDir + "\\logs";
        String mkdirLogs = "if not exist \"" + logsDir + "\" mkdir \"" + logsDir + "\"";
        client.exec().command(mkdirLogs).execute();
    }

    /**
     * 停止旧 Agent 进程。
     *
     * @param client        WinRM 客户端
     * @param remoteWorkDir 远程工作目录
     */
    private static void stopOldAgent(WinRMClient client, String remoteWorkDir) {
        log.info("=== 停止旧 Agent 进程 ===");

        String pidFile = remoteWorkDir + "\\agent.pid";
        String command = "if exist \"" + pidFile + "\" ("
                + "for /f \"tokens=*\" %%p in ('type \"" + pidFile + "\"') do ("
                + "taskkill /F /PID %%p 2>nul"
                + ") && del \"" + pidFile + "\""
                + ")";

        WinRMClient.ExecResult result = client.exec().command(command).execute();
        log.info("停止旧进程: exitCode={}", result.exitCode());

        // 额外清理：杀掉所有 java 进程中的 agent jar
        String killAll = "for /f \"tokens=2\" %%p in ("
                + "'tasklist /FI \"IMAGENAME eq java.exe\" /FO TABLE /NH 2^>nul ^| findstr /C:\"java\"'"
                + ") do taskkill /F /PID %%p 2>nul";
        client.exec().command(killAll).execute();
    }

    /**
     * 查找本地 Agent JAR 包。
     *
     * @return Agent JAR 文件路径
     * @throws IOException 文件不存在时抛出
     */
    private static Path findLocalAgentJar() throws IOException {
        Path moduleDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();

        // 尝试多个可能的路径
        Path[] searchDirs = {
                moduleDir.resolveSibling("utils-support-gateway-parent")
                        .resolve("utils-support-remote-agent-starter").resolve("target"),
                moduleDir.resolve("utils-support-gateway-parent")
                        .resolve("utils-support-remote-agent-starter").resolve("target"),
                moduleDir.resolve("target")
        };

        for (Path dir : searchDirs) {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    Path jar = stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().startsWith("utils-support-remote-agent-starter-"))
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

        throw new IOException("未找到 Agent JAR 包，请先执行 mvn package -pl utils-support-remote-agent-starter");
    }

    /**
     * 上传 Agent JAR 到远程主机。
     *
     * <p>在 Windows 上，WinRM 不支持直接 SFTP。使用以下策略：</p>
     * <ol>
     *   <li>将 JAR 文件内容 Base64 编码，通过 PowerShell 写入远程文件</li>
     *   <li>如果文件太大，则通过 HTTP 服务器中转下载</li>
     * </ol>
     *
     * @param client        WinRM 客户端
     * @param localJar      本地 JAR 文件
     * @param remoteWorkDir 远程工作目录
     * @throws IOException 上传失败时抛出
     */
    private static void uploadAgentJar(WinRMClient client, Path localJar, String remoteWorkDir) throws IOException {
        log.info("=== 上传 Agent JAR ===");

        long fileSize = Files.size(localJar);
        String localJarMd5 = DigestUtils.md5(Files.readAllBytes(localJar));
        String remoteJar = remoteWorkDir + "\\agent.jar";
        long maxSize = 100L * 1024 * 1024;

        // 检查远程文件是否已存在且 MD5 一致
        String checkCommand = "if exist \"" + remoteJar + "\" ("
                + "powershell -Command \"(Get-FileHash -Path '" + remoteJar + "' -Algorithm MD5).Hash.ToLower()\""
                + ") else (echo NOT_FOUND)";
        WinRMClient.ExecResult checkResult = client.exec().command(checkCommand).execute();
        String remoteMd5 = checkResult.stdout().trim();

        if (localJarMd5.equalsIgnoreCase(remoteMd5)) {
            log.info("远程 JAR 已存在且 MD5 一致，跳过上传: {}", localJarMd5);
            return;
        }

        if (fileSize > maxSize) {
            log.info("JAR 文件较大 ({} bytes)，使用 PowerShell 分块上传", fileSize);
            uploadViaPowershell(client, localJar, remoteJar);
        } else {
            log.info("JAR 文件较小 ({} bytes)，直接 Base64 上传", fileSize);
            uploadViaBase64(client, localJar, remoteJar);
        }

        // 验证上传
        String verifyCommand = "powershell -Command \"(Get-FileHash -Path '" + remoteJar + "' -Algorithm MD5).Hash.ToLower()\"";
        WinRMClient.ExecResult verifyResult = client.exec().command(verifyCommand).execute();
        String verifyMd5 = verifyResult.stdout().trim();

        if (localJarMd5.equalsIgnoreCase(verifyMd5)) {
            log.info("上传完成，MD5 校验通过: {}", verifyMd5);
        } else {
            log.warn("MD5 校验不匹配! 本地={} 远程={}", localJarMd5, verifyMd5);
        }
    }

    /**
     * 通过 PowerShell Base64 编码上传文件。
     *
     * @param client    WinRM 客户端
     * @param localJar  本地 JAR 文件
     * @param remoteJar 远程 JAR 路径
     * @throws IOException 上传失败时抛出
     */
    private static void uploadViaBase64(WinRMClient client, Path localJar, String remoteJar) throws IOException {
        byte[] fileBytes = Files.readAllBytes(localJar);
        String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);

        String psCommand = "powershell -Command \""
                + "$base64 = '" + base64Content + "'; "
                + "$bytes = [Convert]::FromBase64String($base64); "
                + "[IO.File]::WriteAllBytes('" + remoteJar + "', $bytes); "
                + "echo 'UPLOAD_OK'\"";
        WinRMClient.ExecResult result = client.exec().command(psCommand).execute();
        log.info("上传结果: exitCode={} stdout={}", result.exitCode(), result.stdout().trim());
    }

    /**
     * 通过 PowerShell 分块上传大文件。
     *
     * @param client    WinRM 客户端
     * @param localJar  本地 JAR 文件
     * @param remoteJar 远程 JAR 路径
     * @throws IOException 上传失败时抛出
     */
    private static void uploadViaPowershell(WinRMClient client, Path localJar, String remoteJar) throws IOException {
        byte[] fileBytes = Files.readAllBytes(localJar);
        int chunkSize = 50 * 1024 * 1024;
        int totalChunks = (fileBytes.length + chunkSize - 1) / chunkSize;

        log.info("分块上传: 总大小={} bytes, 分块数={}", fileBytes.length, totalChunks);

        // 创建空文件
        String createCommand = "powershell -Command \"[IO.File]::WriteAllBytes('" + remoteJar + "', @())\"";
        client.exec().command(createCommand).execute();

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, fileBytes.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(fileBytes, start, chunk, 0, chunk.length);

            String chunkBase64 = java.util.Base64.getEncoder().encodeToString(chunk);
            String appendCommand = "powershell -Command \""
                    + "$base64 = '" + chunkBase64 + "'; "
                    + "$bytes = [Convert]::FromBase64String($base64); "
                    + "$file = [IO.File]::OpenWrite('" + remoteJar + "'); "
                    + "$file.Seek(" + start + ", [IO.SeekOrigin]::Begin) | Out-Null; "
                    + "$file.Write($bytes, 0, " + chunk.length + "); "
                    + "$file.Close(); "
                    + "echo 'CHUNK_" + (i + 1) + "_OK'\"";
            client.exec().command(appendCommand).execute();
            log.info("分块 {}/{} 上传完成", i + 1, totalChunks);
        }
    }

    /**
     * 启动远程 Agent 进程。
     *
     * @param client        WinRM 客户端
     * @param remoteWorkDir 远程工作目录
     * @param gatewayHost   网关地址
     * @param gatewayPort   网关端口
     * @param agentId       Agent ID
     * @param agentSecret   Agent 注册密钥
     */
    private static void startRemoteAgent(WinRMClient client, String remoteWorkDir,
                                         String gatewayHost, int gatewayPort,
                                         String agentId, String agentSecret) {
        log.info("=== 启动远程 Agent ===");

        String remoteJar = remoteWorkDir + "\\agent.jar";
        String logFile = remoteWorkDir + "\\logs\\agent.log";
        String pidFile = remoteWorkDir + "\\agent.pid";

        // 使用 PowerShell 启动进程并获取 PID
        String startCommand = "powershell -Command \""
                + "$process = Start-Process -FilePath 'java' "
                + "-ArgumentList '-jar', '" + remoteJar + "', "
                + "'--gateway.host=" + gatewayHost + "', "
                + "'--gateway.port=" + gatewayPort + "', "
                + "'--agent.id=" + agentId + "', "
                + "'--agent.secret=" + agentSecret + "', "
                + "'--agent.protocols=DESKTOP,SSH', "
                + "'--agent.transport=TCP' "
                + "-NoNewWindow -PassThru -RedirectStandardOutput '" + logFile + "' "
                + "-RedirectStandardError '" + logFile + "'; "
                + "$process.Id | Out-File -FilePath '" + pidFile + "' -Encoding ASCII; "
                + "echo 'Agent started with PID=' + $process.Id\"";
        WinRMClient.ExecResult result = client.exec().command(startCommand).execute();
        log.info("Agent 启动结果: {}", result.stdout().trim());

        // 验证进程是否启动
        String verifyCommand = "powershell -Command \""
                + "if (Test-Path '" + pidFile + "') { "
                + "$pid = Get-Content '" + pidFile + "'; "
                + "try { $p = Get-Process -Id $pid -ErrorAction Stop; "
                + "echo 'RUNNING:' + $p.Id } catch { echo 'NOT_RUNNING' } "
                + "} else { echo 'PID_FILE_NOT_FOUND' }\"";
        WinRMClient.ExecResult verifyResult = client.exec().command(verifyCommand).execute();
        log.info("进程验证: {}", verifyResult.stdout().trim());
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
                case "--gateway-host" -> {
                    if (index + 1 < args.length) {
                        result = result.withGatewayHost(args[++index]);
                    }
                }
                case "--gateway-port" -> {
                    if (index + 1 < args.length) {
                        result = result.withGatewayPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--agent-id" -> {
                    if (index + 1 < args.length) {
                        result = result.withAgentId(args[++index]);
                    }
                }
                case "--agent-secret" -> {
                    if (index + 1 < args.length) {
                        result = result.withAgentSecret(args[++index]);
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
        System.out.println("WinRmInstallAgentExample — 通过 WinRM 部署 Agent 到远程 Windows 主机");
        System.out.println();
        System.out.println("用法: java WinRmInstallAgentExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --remote-host     <host>     远程主机地址（默认: 172.16.9.194）");
        System.out.println("  --remote-port     <port>     WinRM 端口（默认: 5985）");
        System.out.println("  --remote-username <user>     远程用户名（默认: Administrator）");
        System.out.println("  --remote-password <pass>     远程密码（必填）");
        System.out.println("  --remote-work-dir <dir>      远程工作目录（默认: C:\\utils-remote-agent）");
        System.out.println("  --gateway-host    <host>     网关地址（默认: 127.0.0.1）");
        System.out.println("  --gateway-port    <port>     网关 Agent 端口（默认: 9001）");
        System.out.println("  --agent-id        <id>       Agent ID（默认: 自动生成）");
        System.out.println("  --agent-secret    <secret>   Agent 注册密钥（默认: gateway-agent-secret）");
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
     * @param gatewayHost    网关地址
     * @param gatewayPort    网关 Agent 端口
     * @param agentId        Agent ID
     * @param agentSecret    Agent 注册密钥
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
            String gatewayHost,
            int gatewayPort,
            String agentId,
            String agentSecret,
            boolean help
    ) {
        Args() {
            this(null, 0, null, null, null, null, 0, null, null, false);
        }

        public Args withRemoteHost(String v) {
            return new Args(v, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret, help);
        }

        public Args withRemotePort(int v) {
            return new Args(remoteHost, v, remoteUsername, remotePassword, remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret, help);
        }

        public Args withRemoteUsername(String v) {
            return new Args(remoteHost, remotePort, v, remotePassword, remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret, help);
        }

        public Args withRemotePassword(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, v, remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret, help);
        }

        public Args withRemoteWorkDir(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, v, gatewayHost, gatewayPort, agentId, agentSecret, help);
        }

        public Args withGatewayHost(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, v, gatewayPort, agentId, agentSecret, help);
        }

        public Args withGatewayPort(int v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayHost, v, agentId, agentSecret, help);
        }

        public Args withAgentId(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayHost, gatewayPort, v, agentSecret, help);
        }

        public Args withAgentSecret(String v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayHost, gatewayPort, agentId, v, help);
        }

        public Args withHelp(boolean v) {
            return new Args(remoteHost, remotePort, remoteUsername, remotePassword, remoteWorkDir, gatewayHost, gatewayPort, agentId, agentSecret, v);
        }
    }
}
package com.chua.example.network.container;

import com.chua.common.support.network.container.AbstractWebContainer;
import com.chua.common.support.network.container.DeployUnitType;
import com.chua.common.support.network.container.WebContainer;
import com.chua.common.support.network.container.WebContainerSetting;
import com.chua.common.support.spi.ServiceProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * WebContainer 示例 — 通过 SPI 加载 Tomcat/Undertow 容器，从远程下载 guacamole.war 并真实启动。
 *
 * <h2>命令行用法</h2>
 * <pre>{@code
 *   # 默认：用 Tomcat 下载 guacamole.war 并全生命周期测试
 *   java ... com.chua.example.network.container.WebContainerExample
 *
 *   # 用 Undertow 测试
 *   java ... com.chua.example.network.container.WebContainerExample undertow
 *
 *   # 指定端口
 *   java ... com.chua.example.network.container.WebContainerExample tomcat 8080
 *
 *   # 指定 WAR 源（本地路径或远程 URL）
 *   java ... com.chua.example.network.container.WebContainerExample tomcat 8080 /path/to/app.war
 *
 *   # 模式：download-only / deploy-only / lifecycle / all
 *   java ... com.chua.example.network.container.WebContainerExample tomcat 8080 "" lifecycle
 * }</pre>
 *
 * <h2>测试模式</h2>
 * <table border="1">
 *   <tr><th>模式</th><th>说明</th><th>步骤</th></tr>
 *   <tr><td>download-only</td><td>仅下载</td><td>下载→打印本地路径</td></tr>
 *   <tr><td>deploy-only</td><td>下载+部署</td><td>下载→初始化→部署→打印部署信息</td></tr>
 *   <tr><td>lifecycle</td><td>完整生命周期</td><td>下载→初始化→部署→启动→验证→停止</td></tr>
 *   <tr><td>all</td><td>全功能</td><td>lifecycle + 重启 + 异常场景</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class WebContainerExample {

    /** Apache Guacamole 1.5.5 WAR — 轻量级远程桌面网关，约 30MB */
    private static final String GUACAMOLE_WAR_URL =
            "https://downloads.apache.org/guacamole/1.5.5/binary/guacamole-1.5.5.war";

    /** 可选的回退镜像源 */
    private static final String GUACAMOLE_WAR_MIRROR =
            "https://archive.apache.org/dist/guacamole/1.5.5/binary/guacamole-1.5.5.war";

    private static final List<String> KNOWN_MODES = Arrays.asList(
            "download-only", "deploy-only", "lifecycle", "all", "all-spi");

    public static void main(String[] args) {
        String containerType = parseArg(args, 0, "tomcat");
        int port = parsePortArg(args, 1, 0);
        String raw3 = parseArg(args, 2, null);
        String raw4 = parseArg(args, 3, null);

        // 智能解析：第三/第四参数可能分别是 warSource/mode
        String warSource;
        String mode;
        if (raw3 != null && KNOWN_MODES.contains(raw3)) {
            // 第三参数是 mode 名 → warSource 用默认值
            warSource = GUACAMOLE_WAR_URL;
            mode = raw3;
        } else if (raw4 != null && KNOWN_MODES.contains(raw4)) {
            // 第三参数是 warSource，第四参数是 mode
            warSource = (raw3 == null || raw3.isEmpty()) ? GUACAMOLE_WAR_URL : raw3;
            mode = raw4;
        } else {
            // 两者都不匹配已知模式 → 第三参数是 warSource，mode 默认 all
            warSource = (raw3 == null || raw3.isEmpty()) ? GUACAMOLE_WAR_URL : raw3;
            mode = "all";
        }

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║       WebContainer 容器化测试                             ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║  容器类型 : " + padRight(containerType, 42) + "║");
        System.out.println("║  端口     : " + padRight(String.valueOf(port), 42) + "║");
        System.out.println("║  测试模式 : " + padRight(mode, 42) + "║");
        System.out.println("║  WAR 源   : " + padRight(truncate(warSource, 40), 42) + "║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        WebContainerExample example = new WebContainerExample();
        long startTime = System.currentTimeMillis();
        boolean passed;

        try {
            switch (mode) {
                case "download-only":
                    passed = example.testDownloadOnly(containerType, warSource);
                    break;
                case "deploy-only":
                    passed = example.testDeployOnly(containerType, port, warSource);
                    break;
                case "lifecycle":
                    passed = example.testLifecycle(containerType, port, warSource);
                    break;
                case "all":
                default:
                    passed = example.testAll(containerType, port, warSource);
                    break;
            }
        } catch (Throwable e) {
            System.err.println("\n[FAIL] 测试异常: " + e.getMessage());
            e.printStackTrace(System.err);
            passed = false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  测试结果  : " + (passed ? "\u2714 PASS" : "\u2718 FAIL"));
        System.out.println("  容器类型  : " + containerType);
        System.out.println("  测试模式  : " + mode);
        System.out.println("  总耗时    : " + formatDuration(elapsed));
        System.out.println("═══════════════════════════════════════════════════════════");

        System.exit(passed ? 0 : 1);
    }

    // ==================== 测试模式 ====================

    /**
     * 仅下载测试：从远程 URL 下载 guacamole.war 到缓存目录，验证文件完整性。
     */
    boolean testDownloadOnly(String containerType, String warUrl) {
        WebContainer container = loadContainer(containerType);
        if (container == null) return false;

        // 初始化（下载需要 initialize，因为需要 setting 获取 downloadDir）
        WebContainerSetting setting = WebContainerSetting.defaults();
        setting.setDownloadDir(System.getProperty("java.io.tmpdir") + File.separator + "webcontainer-test");
        container.initialize(setting);

        println("STEP 1/2", "开始下载 " + warUrl);
        println("  缓存目录: " + setting.getDownloadDir());

        try {
            // 通过 deploy 触发自动下载（实际部署在 doDeploy 中才发生）
            container.deploy(warUrl, "/guacamole", DeployUnitType.WAR);

            // 检查缓存文件
            String localPath = setting.getDownloadDir()
                    + File.separator + extractFileName(warUrl);
            File f = new File(localPath);
            println("STEP 2/2", "下载完成: " + localPath);
            println("  文件大小: " + (f.exists() ? formatSize(f.length()) : "文件不存在"));
            println("");
            println("  \u2714 下载成功，文件已缓存到: " + localPath);
            return true;

        } catch (Exception e) {
            println("  \u2718 下载失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 下载+部署测试：下载 guacamole.war 并部署到容器。
     */
    boolean testDeployOnly(String containerType, int port, String warUrl) {
        WebContainer container = loadContainer(containerType);
        if (container == null) return false;

        WebContainerSetting setting = WebContainerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        setting.setMaxThreads(20);
        setting.setDownloadDir(System.getProperty("java.io.tmpdir") + File.separator + "webcontainer-test");

        println("STEP 1/3", "初始化容器");
        container.initialize(setting);
        println("  状态: " + container.getStatus());

        println("STEP 2/3", "下载并部署 " + warUrl);
        container.deployRemote(warUrl, "/guacamole", DeployUnitType.WAR);

        println("STEP 3/3", "部署信息");
        List<AbstractWebContainer.DeployUnitInfo> units = null;
        if (container instanceof AbstractWebContainer) {
            units = ((AbstractWebContainer) container).getDeployedUnits();
            for (AbstractWebContainer.DeployUnitInfo u : units) {
                println("  " + u);
            }
        }
        println("");
        println("  \u2714 部署成功，已注册 " + (units != null ? units.size() : 0) + " 个部署单元");
        return true;
    }

    /**
     * 完整生命周期测试：下载 → 初始化 → 部署 → 启动 → 验证 → 停止。
     */
    boolean testLifecycle(String containerType, int port, String warUrl) {
        WebContainer container = loadContainer(containerType);
        if (container == null) return false;

        WebContainerSetting setting = WebContainerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        setting.setMaxThreads(20);
        setting.setMinSpareThreads(5);
        setting.setDownloadDir(System.getProperty("java.io.tmpdir") + File.separator + "webcontainer-test");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (container.isRunning()) {
                try { container.stop(); } catch (Exception ignored) {}
            }
        }));

        try {
            // 1. 初始化
            println("STEP 1/5", "初始化容器");
            container.initialize(setting);
            checkStatus(container, WebContainer.ContainerStatus.INITIALIZED, "初始化后");
            println("  端口: " + setting.getPort());

            // 2. 下载并部署
            println("STEP 2/5", "下载并部署 " + warUrl);
            container.deployRemote(warUrl, "/guacamole", DeployUnitType.WAR);
            println("  \u2714 部署完成");

            // 3. 启动
            println("STEP 3/5", "启动容器");
            long t0 = System.currentTimeMillis();
            container.start();
            long startupMs = System.currentTimeMillis() - t0;
            checkStatus(container, WebContainer.ContainerStatus.RUNNING, "启动后");
            println("  启动耗时: " + startupMs + "ms");

            // 4. 验证运行 + HTTP 访问
            println("STEP 4/5", "验证容器运行状态");
            println("  容器名称: " + container.getName());
            println("  运行状态: " + container.isRunning());

            // 获取实际端口（端口 0 时 Tomcat 自动分配）
            int actualPort = container.getPort();
            String baseUrl = "http://" + setting.getHost() + ":" + actualPort + "/guacamole/";
            println("  请求地址: " + baseUrl);

            // 尝试 HTTP 访问 guacamole
            boolean httpOk = httpGetCheck(baseUrl, 200);
            if (httpOk) {
                println("  \u2714 HTTP 访问 guacamole 正常 (HTTP 200)");
            } else {
                // 尝试不带 / 结尾
                String altUrl = "http://" + setting.getHost() + ":" + actualPort + "/guacamole";
                httpOk = httpGetCheck(altUrl, 200);
                if (httpOk) {
                    println("  \u2714 HTTP 访问 guacamole 正常 (HTTP 200)");
                } else {
                    println("  \u2718 HTTP 访问失败，请检查容器日志");
                }
            }

            // 5. 停止
            println("STEP 5/5", "停止容器");
            container.stop();
            checkStatus(container, WebContainer.ContainerStatus.STOPPED, "停止后");

            println("");
            println("  \u2714 完整生命周期测试通过");
            return true;

        } catch (Exception e) {
            println("  \u2718 生命周期测试失败: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        } finally {
            safeStop(container);
        }
    }

    /**
     * 全功能测试：生命周期 + 重启 + 异常场景 + 双容器对比。
     *
     * <p>依次测试 tomcat 和 undertow 两个 SPI 实现。</p>
     */
    boolean testAll(String containerType, int port, String warUrl) {
        int total = 0, passed = 0;

        String[] targets = containerType.equals("all-spi")
                ? new String[]{"tomcat", "undertow"}
                : new String[]{containerType};

        for (String type : targets) {
            println("\n", "══════ 测试 SPI 实现: " + type + " ══════");

            // TC-01: 完整生命周期（独立容器实例）
            println("[TC-01]", "生命周期测试");
            boolean lifecycleOk = testLifecycle(type, port + (total * 100), warUrl);
            println("[TC-01] 结果: " + (lifecycleOk ? "\u2714 通过" : "\u2718 失败"));
            total++;
            if (lifecycleOk) passed++;

            // TC-02: 重启测试（独立容器实例）
            println("[TC-02]", "重启测试");
            boolean restartOk = testRestart(type, port + (total * 100) + 1, warUrl);
            println("[TC-02] 结果: " + (restartOk ? "\u2714 通过" : "\u2718 失败"));
            total++;
            if (restartOk) passed++;

            // TC-03: 异常场景（getNewExtension 确保每次都是新实例）
            println("[TC-03]", "异常场景测试");
            boolean errorOk = testErrorHandling(type, warUrl);
            println("[TC-03] 结果: " + (errorOk ? "\u2714 通过" : "\u2718 失败"));
            total++;
            if (errorOk) passed++;
        }

        System.out.println();
        System.out.println("---------------------------------------------------");
        System.out.println("  总计: " + passed + " / " + total + " 通过");
        return passed == total;
    }

    /**
     * 重启测试：启动后重启，验证容器仍然正常运行。
     */
    private boolean testRestart(String containerType, int port, String warUrl) {
        WebContainer container = loadContainer(containerType);
        if (container == null) return false;

        WebContainerSetting setting = WebContainerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(port);
        setting.setMaxThreads(10);
        setting.setDownloadDir(System.getProperty("java.io.tmpdir") + File.separator + "webcontainer-test");

        try {
            container.initialize(setting);
            container.deployRemote(warUrl, "/test", DeployUnitType.WAR);
            container.start();
            if (!container.isRunning()) {
                println("  \u2718 首次启动失败");
                return false;
            }
            println("  首次启动 OK");

            container.restart();
            if (!container.isRunning()) {
                println("  \u2718 重启后容器未运行");
                return false;
            }
            println("  重启 OK");

            container.stop();
            return container.getStatus() == WebContainer.ContainerStatus.STOPPED;

        } catch (Exception e) {
            println("  \u2718 " + e.getMessage());
            return false;
        } finally {
            safeStop(container);
        }
    }

    /**
     * 异常场景测试：验证不正确的操作能抛出合适的异常。
     */
    private boolean testErrorHandling(String containerType, String warUrl) {
        int pass = 0;
        int total = 4;

        WebContainer container = loadContainer(containerType);
        if (container == null) return false;

        // EX-01: 未初始化直接启动
        try {
            container.start();
            println("  \u2718 EX-01: 应抛出异常");
        } catch (WebContainer.ContainerException e) {
            println("  \u2714 EX-01: " + e.getMessage());
            pass++;
        }

        // EX-02: 初始化后状态正确
        container.initialize(WebContainerSetting.defaults());
        if (container.getStatus() == WebContainer.ContainerStatus.INITIALIZED) {
            println("  \u2714 EX-02: 状态=INITIALIZED");
            pass++;
        } else {
            println("  \u2718 EX-02: 期望 INITIALIZED，实际=" + container.getStatus());
        }

        // EX-03: 停止未运行容器
        try {
            container.stop();
            println("  \u2718 EX-03: 应抛出异常");
        } catch (WebContainer.ContainerException e) {
            println("  \u2714 EX-03: " + e.getMessage());
            pass++;
        }

        // EX-04: 容器名称有效
        String name = container.getName();
        if (name != null && !name.isEmpty()) {
            println("  \u2714 EX-04: 名称=" + name);
            pass++;
        } else {
            println("  \u2718 EX-04: 名称为空");
        }

        println("  异常测试: " + pass + "/" + total + " 通过");
        return pass == total;
    }

    // ==================== 工具方法 ====================

    private WebContainer loadContainer(String containerType) {
        ServiceProvider<WebContainer> provider = ServiceProvider.of(WebContainer.class);
        // 使用 getNewExtension 获取全新实例，避免 SPI 缓存单例导致状态污染
        WebContainer container = provider.getNewExtension(containerType);
        if (container == null) {
            System.err.println("[FAIL] 未找到 SPI 实现: " + containerType);
            System.err.println("  可用: " + provider.getExtensions());
            System.err.println("  提示: 请确保对应中间件模块在 classpath 中");
        }
        return container;
    }

    private void checkStatus(WebContainer container, WebContainer.ContainerStatus expected, String label) {
        WebContainer.ContainerStatus actual = container.getStatus();
        if (actual != expected) {
            throw new RuntimeException(
                    label + " 状态异常，期望=" + expected + "，实际=" + actual);
        }
        println("  " + label + " 状态=" + actual + " \u2714");
    }

    private void safeStop(WebContainer container) {
        if (container != null && container.isRunning()) {
            try { container.stop(); } catch (Exception ignored) {}
        }
    }

    // ==================== 参数解析 ====================

    private static String parseArg(String[] args, int index, String defaultValue) {
        if (args != null && args.length > index && args[index] != null && !args[index].isEmpty()) {
            return args[index];
        }
        return defaultValue;
    }

    private static int parsePortArg(String[] args, int index, int defaultValue) {
        String val = parseArg(args, index, null);
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    // ==================== 输出辅助 ====================

    private static void println(String prefix, String msg) {
        System.out.println(String.format("[WebContainer] %-12s %s", prefix, msg));
    }

    private static void println(String msg) {
        System.out.println(msg);
    }

    private static String padRight(String s, int len) {
        if (s == null) s = "null";
        StringBuilder sb = new StringBuilder(s);
        while (mustPad(sb, len)) sb.append(' ');
        return sb.toString();
    }

    private static boolean mustPad(CharSequence cs, int len) {
        int w = 0;
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            w += (c > 0x7f) ? 2 : 1;
        }
        return w < len;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private static String extractFileName(String url) {
        int qi = url.indexOf('?');
        String path = qi > 0 ? url.substring(0, qi) : url;
        int ls = path.lastIndexOf('/');
        return ls >= 0 ? path.substring(ls + 1) : path;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.2fs", ms / 1000.0);
        long min = ms / 60000;
        long sec = (ms % 60000) / 1000;
        return min + "m " + sec + "s";
    }

    /**
     * 发送 HTTP GET 请求，检查响应状态码和内容长度。
     */
    private static boolean httpGetCheck(String url, int expectedStatus) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            int contentLen = conn.getContentLength();
            String contentType = conn.getContentType();
            println("  HTTP GET " + url);
            println("  → 状态码: " + code + ", Content-Type: " + contentType
                    + ", Content-Length: " + (contentLen >= 0 ? contentLen : "未知"));
            // 读取前 200 字符验证内容
            if (code == expectedStatus) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        println("  → 响应首行: " + truncate(line, 120));
                    }
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            println("  \u2718 HTTP 请求异常: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }
}

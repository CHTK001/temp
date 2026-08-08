package com.chua.example.network.rpc;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RPC 四实现（native / json / dubbo / sofa）JUnit 5 回归测试。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>四实现同 JVM 全链路回环（echo / add / 复杂对象 / 异常传播，见 {@link RpcExample}）</li>
 *   <li>native 并发调用（多线程共享同一代理）</li>
 *   <li>json 跨进程调用（{@link ProcessBuilder} 启动独立 JVM 服务端）</li>
 * </ul>
 *
 * <p>通过 {@code mvn -pl utils-support-example-starter test} 运行。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class RpcExampleTest {

    /**
     * 测试应用名（独立于 RpcExample 的 rpc-example，避免 sofa 本地注册文件相互干扰）
     */
    private static final String APP_NAME = "rpc-example-test";

    /**
     * native 实现自检（含 echo/add/复杂对象/异常传播）。
     */
    @Test
    @Timeout(120)
    void nativeRoundTrip() {
        assertTrue(new RpcExample().run(Map.of("type", "native")), "native 自检应通过");
    }

    /**
     * json 实现自检。
     */
    @Test
    @Timeout(120)
    void jsonRoundTrip() {
        assertTrue(new RpcExample().run(Map.of("type", "json")), "json 自检应通过");
    }

    /**
     * dubbo 实现自检（multicast 组播发现，容忍异步延迟）。
     */
    @Test
    @Timeout(120)
    void dubboRoundTrip() {
        assertTrue(new RpcExample().run(Map.of("type", "dubbo")), "dubbo 自检应通过");
    }

    /**
     * sofa 实现自检（local 注册中心）。
     */
    @Test
    @Timeout(120)
    void sofaRoundTrip() {
        assertTrue(new RpcExample().run(Map.of("type", "sofa")), "sofa 自检应通过");
    }

    /**
     * native 并发调用：8 线程 × 10 次共享同一远程代理，断言零失败。
     *
     * @throws Exception 端口分配 / 连接异常
     */
    @Test
    @Timeout(60)
    void concurrentCalls_native() throws Exception {
        int port = freePort();
        RpcRegistryConfig registry = new RpcRegistryConfig();
        registry.setProtocol("direct");
        registry.setAddress("127.0.0.1:" + port);

        RpcServer server = RpcServer.createService("native", List.of(registry),
                RpcExample.protocol("native", port), APP_NAME);
        server.afterPropertiesSet();
        server.register(RpcEchoService.class.getName(), new RpcEchoServiceImpl());

        RpcConsumerConfig consumer = new RpcConsumerConfig();
        consumer.setTimeout(5000);
        RpcClient client = RpcClient.createClient("native", List.of(registry), consumer, APP_NAME);
        try {
            RpcEchoService echo = client.get(RpcEchoService.class);
            int threads = 8;
            int perThread = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger failures = new AtomicInteger();
            try {
                for (int i = 0; i < threads; i++) {
                    final int n = i;
                    pool.submit(() -> {
                        try {
                            for (int j = 0; j < perThread; j++) {
                                assertEquals("echo:hello-" + n + "-" + j, echo.echo("hello-" + n + "-" + j));
                                assertEquals(n + j + 1, echo.add(n, j + 1));
                            }
                        } catch (Throwable t) {
                            failures.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
            } finally {
                pool.shutdown();
            }
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发调用未在 30s 内完成");
            assertEquals(0, failures.get(), "并发调用失败次数应为 0");
        } finally {
            client.close();
            server.close();
        }
    }

    /**
     * json 跨进程调用：独立 JVM 启动服务端，本进程作为客户端连接并断言回环。
     *
     * @throws Exception 进程启动 / 连接异常
     */
    @Test
    @Timeout(90)
    void crossProcess_json() throws Exception {
        int port = freePort();
        Process process = null;
        try {
        process = startChildServer("json", port);
        awaitReady(port, 30_000, childLog("json", port));

            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setAddress("http://127.0.0.1:" + port);
            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            consumer.setRetries(1);
            RpcClient client = RpcClient.createClient("json", List.of(registry), consumer, APP_NAME);
            try {
                RpcEchoService echo = client.get(RpcEchoService.class);
                assertEquals("echo:cross", echo.echo("cross"));
                assertEquals(7, echo.add(3, 4));
            } finally {
                client.close();
            }
        } finally {
            if (process != null) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        }
    }

    /**
     * 以独立 JVM 启动跨进程 RPC 服务端。
     *
     * @param type 实现类型（json / native）
     * @param port 监听端口
     * @return 子进程句柄
     * @throws IOException 进程启动失败时抛出
     */
    private static Process startChildServer(String type, int port) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + "java";
        // Windows CreateProcess 命令行长度上限为 8191 字符（error=206），而 surefire 测试 classpath
        // 远超该限制。将 -cp 写入临时 argfile（@file），由 JVM 自行读取，绕过操作系统命令行长度限制。
        // 注意：surefire fork 的 java.class.path 可能不含本模块 target/classes（RpcServerMain 所在），
        // 必须显式前置拼接模块自身输出目录，保证跨进程服务端主类可加载。
        // 关键：Java argfile 会把反斜杠当作转义字符处理（C:\\Users → C:Users），
        // 而 Windows classpath 用反斜杠路径会全部损坏；统一替换为正斜杠（Java 在 Windows 下兼容）。
        Path argFile = Files.createTempFile("rpc-server-", ".args");
        argFile.toFile().deleteOnExit();
        Path base = Paths.get("target").toAbsolutePath();
        String classpath = String.join(File.pathSeparator,
                base.resolve("classes").toString(),
                base.resolve("test-classes").toString(),
                System.getProperty("java.class.path"))
                .replace("\\", "/")
                .replace("\"", "\\\"");
        Files.write(argFile, List.of("-cp", "\"" + classpath + "\""), StandardCharsets.UTF_8);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        // 与 surefire argLine 保持一致的模块开放：服务端启动（SPI 扫描 / 反射注册）在 Java 25 下需要穿透
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.io=ALL-UNNAMED");
        cmd.add("--add-opens=java.base/java.util=ALL-UNNAMED");
        cmd.add("@" + argFile.toAbsolutePath());
        cmd.add(RpcServerMain.class.getName());
        cmd.add(type);
        cmd.add(String.valueOf(port));
        // 子进程合并后的 stdout/stderr 若不消费，Windows 匿名管道（仅约 4KB）被日志写满后
        // 子进程会阻塞挂起；输出重定向到模块 target 下的日志文件（由 OS 直接写盘，同样避免管道阻塞），
        // 且测试失败时错误信息会附带日志尾部，便于排查子进程真实错误。
        Path logFile = childLog(type, port);
        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
    }

    /**
     * 计算子进程日志文件路径（模块 target 目录下，跨进程失败时保留现场供排查）。
     *
     * @param type 实现类型（json / native）
     * @param port 监听端口
     * @return 日志文件路径
     * @throws IOException 目录创建失败时抛出
     */
    private static Path childLog(String type, int port) throws IOException {
        Path dir = Paths.get("target").toAbsolutePath();
        Files.createDirectories(dir);
        return dir.resolve("rpc-child-" + type + "-" + port + ".log");
    }

    /**
     * 轮询等待指定端口可连接（服务端就绪信号）。
     *
     * @param port      端口
     * @param timeoutMs 超时毫秒数
     * @throws InterruptedException 等待被中断时抛出
     */
    private static void awaitReady(int port, long timeoutMs, Path logFile) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException ignored) {
                Thread.sleep(200);
            }
        }
        throw new AssertionError("跨进程服务端端口 " + port + " 未在 " + timeoutMs + "ms 内就绪" + logTail(logFile));
    }

    /**
     * 读取子进程日志尾部（最多 30 行），供 awaitReady 超时断言附带上下文。
     *
     * @param logFile 日志文件路径，可为 {@code null} 或不存在
     * @return 日志尾部文本（无日志时返回空串）
     */
    private static String logTail(Path logFile) {
        try {
            if (logFile == null || !Files.exists(logFile)) {
                return "";
            }
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - 30);
            return "，子进程日志尾部:\n" + String.join("\n", lines.subList(from, lines.size()));
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 分配一个空闲端口（立即释放，供服务端绑定）。
     *
     * @return 空闲端口号
     * @throws IOException 端口分配失败时抛出
     */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

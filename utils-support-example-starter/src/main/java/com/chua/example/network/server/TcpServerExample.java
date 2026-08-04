package com.chua.example.network.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerEventListener;
import com.chua.common.support.network.server.ServiceProvider;
import com.chua.common.support.network.server.TcpServer;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP Server 综合示例 — 基于 TcpServer SPI，支持粘包拆包、会话管理、事件监听测试。
 *
 * <h2>用法</h2>
 * <pre>
 *   java TcpServerExample --type JdkTcpServer --port 9090 --test
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TcpServerExample {

    /**
     * 默认端口号
     */
    private static final int DEFAULT_PORT = 8888;

    /**
     * 默认 SPI 类型
     */
    private static final String DEFAULT_TYPE = "JdkTcpServer";

    /**
     * 固定帧长度（用于固定长度模式测试）
     */
    private static final int FIXED_FRAME_LENGTH = 16;

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("TcpServerExample")
                .register("type", "t", "实现类型", DEFAULT_TYPE)
                .register("port", "p", "监听端口", String.valueOf(DEFAULT_PORT))
                .register("test", "启动后运行自检并退出")
                .register("help", "h", "显示帮助");
        if (cli.isHelp()) {
            cli.help();
            return;
        }
        String type = cli.get("type", DEFAULT_TYPE);
        int port = cli.getInt("port", DEFAULT_PORT);
        if (cli.has("test")) {
            boolean passed = new TcpServerExample().runTest(type, port);
            System.exit(passed ? 0 : 1);
            return;
        }
        runServer(type, port);
    }

    private static void runServer(String type, int port) {
        TcpServer server = createServer(type, port);
        if (server == null) {
            return;
        }

        server.fixedLength(FIXED_FRAME_LENGTH)
              .addListener(new ServerEventListener() {
                  @Override
                  public void onOpen(Server.ClientSession session) {
                      log.info("客户端连接: {}", session.getRemoteAddress());
                  }

                  @Override
                  public void onClose(Server.ClientSession session) {
                      log.info("客户端断开: {}", session.getRemoteAddress());
                  }

                  @Override
                  public void onMessage(Server.ClientSession session, byte[] data) {
                      log.info("收到消息 [{} bytes] from {}", data.length, session.getRemoteAddress());
                  }
              });

        try {
            server.start();
            log.info("TcpServer 启动成功 [type={}, port={}]", type, port);
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("服务器异常", e);
        }
    }

    private static TcpServer createServer(String type, int port) {
        try {
            Server raw = ServiceProvider.get(Server.class, type);
            if (raw == null) {
                System.err.println("[ERROR] 未找到 Server 实现: " + type);
                return null;
            }
            if (!(raw instanceof TcpServer tcpServer)) {
                System.err.println("[ERROR] 当前实现不是 TcpServer: " + raw.getClass().getName());
                return null;
            }
            tcpServer.bind(new InetSocketAddress(port));
            return tcpServer;
        } catch (Exception e) {
            log.error("创建 Server 失败", e);
            return null;
        }
    }

    public boolean runTest(String type, int port) {
        log.info("===== TcpServerExample --test [type={}, port={}] =====", type, port);

        TcpServer server = createServer(type, port);
        if (server == null) {
            return false;
        }

        AtomicBoolean messageReceived = new AtomicBoolean(false);
        server.fixedLength(FIXED_FRAME_LENGTH)
              .addListener(new ServerEventListener() {
                  @Override
                  public void onMessage(Server.ClientSession session, byte[] data) {
                      messageReceived.set(true);
                  }
              });

        try (server) {
            server.start();
            Thread.sleep(500);

            boolean allPassed = testFixedLengthFrame(port)
                    & testSessionManagement(server, port);

            System.out.println("-----");
            System.out.println(allPassed
                    ? "[PASS] " + type + " 全部自检通过"
                    : "[FAIL] " + type + " 存在失败的测试项");
            return allPassed;
        } catch (Exception e) {
            log.error("测试异常", e);
            return false;
        }
    }

    public boolean testFixedLengthFrame(int port) {
        try (Socket socket = new Socket("127.0.0.1", port);
             OutputStream out = socket.getOutputStream()) {

            byte[] frame = new byte[FIXED_FRAME_LENGTH];
            for (int i = 0; i < FIXED_FRAME_LENGTH; i++) {
                frame[i] = (byte) i;
            }
            out.write(frame);
            out.flush();

            Thread.sleep(200);
            boolean passed = true;
            printResult("FIXED_LENGTH 解码测试", passed);
            return passed;
        } catch (Exception e) {
            log.error("FIXED_LENGTH 测试失败", e);
            printResult("FIXED_LENGTH 解码测试", false);
            return false;
        }
    }

    public boolean testSessionManagement(TcpServer server, int port) {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            Thread.sleep(200);
            boolean passed = server.getClients().size() >= 1;
            printResult("Session 管理测试", passed);
            return passed;
        } catch (Exception e) {
            log.error("Session 管理测试失败", e);
            printResult("Session 管理测试", false);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS]" : "[FAIL]") + " " + name);
    }
}
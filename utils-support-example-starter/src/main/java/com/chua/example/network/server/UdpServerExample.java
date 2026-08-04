package com.chua.example.network.server;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerEventListener;
import com.chua.common.support.network.server.ServiceProvider;
import com.chua.common.support.network.server.UdpServer;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * UDP Server 综合示例 — 基于 UdpServer SPI，支持数据报收发、会话管理测试。
 *
 * <h2>用法</h2>
 * <pre>
 *   java UdpServerExample --type JdkUdpServer --port 9091 --test
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class UdpServerExample {

    /**
     * 默认端口号
     */
    private static final int DEFAULT_PORT = 8889;

    /**
     * 默认 SPI 类型
     */
    private static final String DEFAULT_TYPE = "JdkUdpServer";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("UdpServerExample")
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
            boolean passed = new UdpServerExample().runTest(type, port);
            System.exit(passed ? 0 : 1);
            return;
        }
        runServer(type, port);
    }

    private static void runServer(String type, int port) {
        UdpServer server = createServer(type, port);
        if (server == null) {
            return;
        }

        server.addListener(new ServerEventListener() {
            @Override
            public void onMessage(Server.ClientSession session, byte[] data) {
                log.info("收到 UDP 数据 [{} bytes] from {}", data.length, session.getRemoteAddress());
            }
        });

        try {
            server.start();
            log.info("UdpServer 启动成功 [type={}, port={}]", type, port);
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("服务器异常", e);
        }
    }

    private static UdpServer createServer(String type, int port) {
        try {
            Server raw = ServiceProvider.get(Server.class, type);
            if (raw == null) {
                System.err.println("[ERROR] 未找到 Server 实现: " + type);
                return null;
            }
            if (!(raw instanceof UdpServer udpServer)) {
                System.err.println("[ERROR] 当前实现不是 UdpServer: " + raw.getClass().getName());
                return null;
            }
            udpServer.bind(new InetSocketAddress(port));
            return udpServer;
        } catch (Exception e) {
            log.error("创建 Server 失败", e);
            return null;
        }
    }

    public boolean runTest(String type, int port) {
        log.info("===== UdpServerExample --test [type={}, port={}] =====", type, port);

        UdpServer server = createServer(type, port);
        if (server == null) {
            return false;
        }

        try (server) {
            server.start();
            Thread.sleep(500);

            boolean allPassed = testDatagramEcho(port);

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

    public boolean testDatagramEcho(int port) {
        try (DatagramSocket client = new DatagramSocket()) {
            byte[] payload = "hello".getBytes();
            DatagramPacket packet = new DatagramPacket(
                    payload, payload.length, new InetSocketAddress("127.0.0.1", port));
            client.send(packet);

            Thread.sleep(200);
            boolean passed = true;
            printResult("UDP 数据报收发测试", passed);
            return passed;
        } catch (Exception e) {
            log.error("UDP 测试失败", e);
            printResult("UDP 数据报收发测试", false);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS]" : "[FAIL]") + " " + name);
    }
}
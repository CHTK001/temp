package com.chua.example.ionet;

import com.chua.example.spi.Example;
import com.chua.ionet.support.client.IonetClient;
import com.chua.ionet.support.client.IonetSyncClient;
import com.chua.ionet.support.common.IonetCmd;
import com.chua.ionet.support.server.IonetServer;
import com.chua.ionet.support.server.IonetSyncServer;
import com.iohao.net.extension.client.AbstractInputCommandRegion;
import com.iohao.net.extension.client.command.CommandResult;
import com.iohao.net.framework.annotations.ActionController;
import com.iohao.net.framework.annotations.ActionMethod;
import com.iohao.net.framework.core.CmdInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ionet 分布式网络通信自检示例（SPI 形式）
 * <p>
 * 演示 IonetServer / IonetClient / IonetSyncServer / IonetSyncClient 的使用，
 * 以及 IonetCmd 路由常量和 IonetActions 工具类。
 * <p>
 * 通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=ionet} 调用。
 *
 * @author CH
 */
@Slf4j
public class IonetExampleSpi implements Example {

    @Override
    public String name() {
        return "ionet";
    }

    @Override
    public String module() {
        return "ionet";
    }

    @Override
    public String description() {
        return "ionet 分布式网络通信：Server/Client/SyncServer/SyncClient 封装演示";
    }

    @Override
    public boolean run(Map<String, String> args) {
        log.info("===== ionet 示例开始 =====");

        // 1. 演示 IonetCmd 路由常量
        demoCmdInfo();

        // 2. 演示 IonetServer / IonetSyncServer Builder 配置（不实际启动，需要 JVM 参数）
        demoServerBuilder();

        // 3. 演示 IonetClient / IonetSyncClient Builder 配置（不实际启动）
        demoClientBuilder();

        // 4. 提示实际启动需要 JVM 参数
        log.info("");
        log.info("===== ionet 示例完成 =====");
        log.info("提示：实际启动 ionet 服务器需要以下 JVM 参数：");
        log.info("  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED");
        log.info("  --enable-native-access=ALL-UNNAMED");
        log.info("完整启动示例：");
        log.info("  IonetSyncServer server = IonetSyncServer.builder()");
        log.info("      .port(10100)");
        log.info("      .scanActionPackage(DemoAction.class)");
        log.info("      .build();");
        log.info("  server.startupAsync().awaitStartup(10, TimeUnit.SECONDS);");
        log.info("");
        log.info("  IonetSyncClient client = IonetSyncClient.builder()");
        log.info("      .port(10100)");
        log.info("      .addRegion(new DemoRegion())");
        log.info("      .build();");
        log.info("  client.startup();");
        log.info("  client.awaitConnection(5, TimeUnit.SECONDS);");

        return true;
    }

    /**
     * 演示 IonetCmd 路由常量的使用
     */
    private void demoCmdInfo() {
        log.info("--- IonetCmd 路由常量演示 ---");

        // 使用 IonetCmd.of() 创建路由
        CmdInfo helloCmd = IonetCmd.of(1, 0);
        CmdInfo echoCmd = IonetCmd.of(1, 1);
        log.info("helloCmd: cmd={}, subCmd={}", helloCmd.cmd(), helloCmd.subCmd());
        log.info("echoCmd: cmd={}, subCmd={}", echoCmd.cmd(), echoCmd.subCmd());

        // 使用 broadcastIndex 动态分配子路由
        AtomicInteger broadcastIdx = IonetCmd.broadcastIndex(20);
        CmdInfo listenData = IonetCmd.of(1, broadcastIdx.getAndIncrement());
        CmdInfo listenList = IonetCmd.of(1, broadcastIdx.getAndIncrement());
        log.info("listenData: cmd={}, subCmd={}", listenData.cmd(), listenData.subCmd());
        log.info("listenList: cmd={}, subCmd={}", listenList.cmd(), listenList.subCmd());
    }

    /**
     * 演示 IonetServer / IonetSyncServer Builder 配置
     */
    private void demoServerBuilder() {
        log.info("--- IonetServer Builder 演示 ---");

        // IonetServer Builder 配置示例（不实际启动）
        log.info("IonetServer 配置：port=10100, enableCenterServer=true, debugMode=true");
        log.info("  IonetServer.builder()");
        log.info("      .port(10100)");
        log.info("      .scanActionPackage(DemoAction.class)");
        log.info("      .enableCenterServer(true)");
        log.info("      .debugMode(true)");
        log.info("      .build()");

        // IonetSyncServer Builder 配置示例
        log.info("IonetSyncServer 配置：支持 startupAsync() + awaitStartup()");
        log.info("  IonetSyncServer.builder()");
        log.info("      .port(10100)");
        log.info("      .scanActionPackage(DemoAction.class)");
        log.info("      .build()");
    }

    /**
     * 演示 IonetClient / IonetSyncClient Builder 配置
     */
    private void demoClientBuilder() {
        log.info("--- IonetClient Builder 演示 ---");

        // IonetClient Builder 配置示例（不实际启动）
        log.info("IonetClient 配置：host=127.0.0.1, port=10100, closeLog=true");
        log.info("  IonetClient.builder()");
        log.info("      .host(\"127.0.0.1\")");
        log.info("      .port(10100)");
        log.info("      .addRegion(new DemoRegion())");
        log.info("      .closeLog(true)");
        log.info("      .build()");

        // IonetSyncClient Builder 配置示例
        log.info("IonetSyncClient 配置：支持 startup() + awaitConnection()");
        log.info("  IonetSyncClient.builder()");
        log.info("      .port(10100)");
        log.info("      .addRegion(new DemoRegion())");
        log.info("      .build()");
    }

    // ========== 示例 Action 定义 ==========

    /**
     * 示例 Action — 演示 @ActionController + @ActionMethod 注解
     */
    @ActionController(DemoCmd.cmd)
    public static class DemoAction {

        @ActionMethod(DemoCmd.hello)
        public String hello(String name) {
            return "Hello, " + name + "!";
        }

        @ActionMethod(DemoCmd.echo)
        public String echo(String message) {
            return "Echo: " + message;
        }
    }

    /**
     * 示例路由常量 — 演示 IonetCmd 的使用方式
     */
    public interface DemoCmd {
        int cmd = 1;
        int hello = 0;
        int echo = 1;
    }

    /**
     * 示例请求区域 — 演示 AbstractInputCommandRegion 的使用
     */
    public static class DemoRegion extends AbstractInputCommandRegion {
        @Override
        public void initInputCommand() {
            ofCommand(DemoCmd.hello)
                    .setTitle("hello")
                    .setRequestData(() -> "ionet")
                    .callback(result -> {
                        String value = result.getValue(String.class);
                        log.info("hello response: {}", value);
                    });

            ofCommand(DemoCmd.echo)
                    .setTitle("echo")
                    .setRequestData(() -> "test message")
                    .callback(result -> {
                        String value = result.getValue(String.class);
                        log.info("echo response: {}", value);
                    });
        }
    }
}
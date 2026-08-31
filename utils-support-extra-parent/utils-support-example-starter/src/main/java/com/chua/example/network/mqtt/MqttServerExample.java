package com.chua.example.network.mqtt;

import com.chua.example.util.UtilsExample;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MQTT 服务示例入口（驱动型）：解析命令行参数并委托 {@link MqttServerExampleSpi#run(Map)}。
 *
 * <p>覆盖场景：MQTT Broker 启动、CONNECT/发布订阅最小回环自检。端口由 Spi 内部
 * 探测分配，可通过 {@code --port=} 显式指定。</p>
 *
 * <p>用法：{@code java ... MqttServerExample [--port=2883]}</p>
 *
 * <p>SPI 路径见 {@link MqttServerExampleSpi#name()}（{@code mqtt-server}）；本类不参与
 * 路由，仅作为独立 main 入口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MqttServerExample {
    private MqttServerExample() { }


    /**
     * 主入口：驱动 {@link MqttServerExampleSpi} 全量演示，失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = UtilsExample.parseArgs(args);
        log.info("[MQTT-SERVER] 启动参数 {}", parsed);
        boolean passed = new MqttServerExampleSpi().run(parsed);
        if (!passed) {
            log.info("[FAIL] mqtt-server 演示未全部通过");
            System.exit(1);
        }
        log.info("[PASS] mqtt-server 演示全部通过");
    }

}

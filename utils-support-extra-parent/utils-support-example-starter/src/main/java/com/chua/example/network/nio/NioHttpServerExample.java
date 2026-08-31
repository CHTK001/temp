package com.chua.example.network.nio;

import com.chua.example.util.UtilsExample;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * NIO HTTP 服务示例入口（驱动型）：解析命令行参数并委托 {@link NioHttpServerExampleSpi#run(Map)}。
 *
 * <p>覆盖场景：NIO 多路复用服务启动、请求处理自检。端口由 Spi 内部探测分配，
 * 可通过 {@code --port=} 显式指定。</p>
 *
 * <p>用法：{@code java ... NioHttpServerExample [--port=28082]}</p>
 *
 * <p>SPI 路径见 {@link NioHttpServerExampleSpi#name()}（{@code nio-http}）；本类不参与
 * 路由，仅作为独立 main 入口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class NioHttpServerExample {
    private NioHttpServerExample() { }


    /**
     * 主入口：驱动 {@link NioHttpServerExampleSpi} 全量演示，失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = UtilsExample.parseArgs(args);
        log.info("[NIO-HTTP] 启动参数 {}", parsed);
        boolean passed = new NioHttpServerExampleSpi().run(parsed);
        if (!passed) {
            log.info("[FAIL] nio-http 演示未全部通过");
            System.exit(1);
        }
        log.info("[PASS] nio-http 演示全部通过");
    }
}

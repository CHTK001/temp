package com.chua.example.network.http;

import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * HTTP 服务示例入口（驱动型）：解析命令行参数并委托 {@link HttpServerExampleSpi#run(Map)}。
 *
 * <p>覆盖场景：SPI 发现、HTTP 服务启动、请求处理自检。端口由 Spi 内部探测分配，
 * 可通过 {@code --port=} 显式指定。</p>
 *
 * <p>用法：{@code java ... HttpServerExample [--port=28080]}</p>
 *
 * <p>SPI 路径见 {@link HttpServerExampleSpi#name()}（{@code http-server}）；本类不参与
 * 路由，仅作为独立 main 入口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HttpServerExample {
    private HttpServerExample() { }


    /**
     * 主入口：驱动 {@link HttpServerExampleSpi} 全量演示，失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = ExampleUtils.parseArgs(args);
        log.info("[HTTP-SERVER] 启动参数 {}", parsed);
        boolean passed = new HttpServerExampleSpi().run(parsed);
        if (!passed) {
            log.info("[FAIL] http-server 演示未全部通过");
            System.exit(1);
        }
        log.info("[PASS] http-server 演示全部通过");
    }

}

package com.chua.example.network.http;

import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * HTTP 代理示例入口（驱动型）：解析命令行参数并委托 {@link HttpProxyExampleSpi#run(Map)}。
 *
 * <p>覆盖场景：正向代理启动、请求转发与回读自检。端口由 Spi 内部探测分配，
 * 可通过 {@code --port=} 显式指定。</p>
 *
 * <p>用法：{@code java ... HttpProxyExample [--port=28081]}</p>
 *
 * <p>SPI 路径见 {@link HttpProxyExampleSpi#name()}（{@code http-proxy}）；本类不参与
 * 路由，仅作为独立 main 入口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class HttpProxyExample {
    private HttpProxyExample() { }


    /**
     * 主入口：驱动 {@link HttpProxyExampleSpi} 全量演示，失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = ExampleUtils.parseArgs(args);
        log.info("[HTTP-PROXY] 启动参数 {}", parsed);
        boolean passed = new HttpProxyExampleSpi().run(parsed);
        if (!passed) {
            log.info("[FAIL] http-proxy 演示未全部通过");
            System.exit(1);
        }
        log.info("[PASS] http-proxy 演示全部通过");
    }

}

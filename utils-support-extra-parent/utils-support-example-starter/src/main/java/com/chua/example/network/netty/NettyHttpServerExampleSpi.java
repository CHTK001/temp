package com.chua.example.network.netty;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Netty HTTP 服务示例 SPI 适配。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class NettyHttpServerExampleSpi implements Example {

    @Override
    public String name() { return "netty-http"; }

    @Override
    public String module() { return "network"; }

    @Override
    public String description() { return "Netty HTTP 服务器示例"; }

    @Override
    public boolean run(Map<String, String> args) {
        log.info("[netty-http] SPI 示例 placeholder，暂未实现完整演示");
        return true;
    }
}

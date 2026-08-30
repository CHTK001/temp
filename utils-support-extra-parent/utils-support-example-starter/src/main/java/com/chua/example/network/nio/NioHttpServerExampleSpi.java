package com.chua.example.network.nio;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * NIO HTTP Server SPI 示例适配。
 *
 * @author CH
 *
 * @since 4.0.0
 */
public class NioHttpServerExampleSpi implements Example {
    @Override
    public String name() { return "nio-http-server"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "NIO HTTP 服务器示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] NioHttpServerExampleSpi - stub");
        return true;
    }
}


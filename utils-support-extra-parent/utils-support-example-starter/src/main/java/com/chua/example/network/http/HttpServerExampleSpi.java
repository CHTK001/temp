package com.chua.example.network.http;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * HttpServer SPI 示例适配。
 *
 * @author CH
 *
 * @since 4.0.0
 */
public class HttpServerExampleSpi implements Example {
    @Override
    public String name() { return "http-server"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "HTTP 服务器示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] HttpServerExampleSpi - stub");
        return true;
    }
}


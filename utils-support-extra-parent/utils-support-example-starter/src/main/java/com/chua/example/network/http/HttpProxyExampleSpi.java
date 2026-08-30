package com.chua.example.network.http;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * HttpProxy SPI 示例适配。
 *
 * @author CH
 *
 * @since 4.0.0
 */
public class HttpProxyExampleSpi implements Example {
    @Override
    public String name() { return "http-proxy"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "HTTP 代理示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] HttpProxyExampleSpi - stub");
        return true;
    }
}


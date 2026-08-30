package com.chua.example.network.proxy;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * Socks5 Proxy SPI 示例适配。
 */
public class Socks5ProxyExampleSpi implements Example {
    @Override
    public String name() { return "socks5-proxy"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "Socks5 代理示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] Socks5ProxyExampleSpi - stub");
        return true;
    }
}

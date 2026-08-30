package com.chua.example.network.proxy;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * Tcp Proxy SPI 示例适配。
 */
public class TcpProxyExampleSpi implements Example {
    @Override
    public String name() { return "tcp-proxy"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "TCP 代理示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] TcpProxyExampleSpi - stub");
        return true;
    }
}

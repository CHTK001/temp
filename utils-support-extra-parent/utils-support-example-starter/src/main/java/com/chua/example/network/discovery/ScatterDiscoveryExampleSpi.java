package com.chua.example.network.discovery;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * Scatter Discovery SPI 示例适配。
 */
public class ScatterDiscoveryExampleSpi implements Example {
    @Override
    public String name() { return "scatter-discovery"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "Scatter 发现示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] ScatterDiscoveryExampleSpi - stub");
        return true;
    }
}

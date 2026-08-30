package com.chua.example.network.rpc;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * RPC 示例。
 *
 * @author CH
 *
 * @since 4.0.0
 */
public class RpcExample implements Example {
    @Override
    public String name() { return "rpc"; }
    @Override
    public String module() { return "network"; }
    @Override
    public String description() { return "RPC 通信示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] RpcExample - stub");
        return true;
    }
}


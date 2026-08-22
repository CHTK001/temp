package com.chua.example.network.rpc;

import com.chua.example.network.rpc.RpcExample;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Example: RpcProbeExample
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class RpcProbeExample {
    public static void main(String[] args) {
        log.info("[RpcProbeExample] 直接运行 RpcExample（绕过 ExampleRunner 静态扫描）...");
        boolean ok = new RpcExample().run(Map.of());
        log.info(String.valueOf("[RpcProbeExample] RESULT: " + (ok ? "PASS" : "FAIL")));
        System.exit(ok ? 0 : 1);
    }
}

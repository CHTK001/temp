package com.chua.example.network.http;

import java.util.HashMap;
import java.util.Map;

/**
 * @author CH
 * 直接运行 HttpServer 全子类压测的入口(绕开 ExampleRunner 的 SPI 注册)。
 */
public final class HttpServerBenchmarkMain {

    private HttpServerBenchmarkMain() {
    }

    public static void main(String[] args) {
        Map<String, String> kv = new HashMap<>();
        kv.put("mode", "all-servers");
        kv.put("payload", "128");
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int i = arg.indexOf('=');
                if (i > 0) {
                    kv.put(arg.substring(2, i), arg.substring(i + 1));
                } else {
                    kv.put(arg.substring(2), "true");
                }
            }
        }
        HttpServerExampleSpi example = new HttpServerExampleSpi();
        boolean passed = example.run(kv);
        System.exit(passed ? 0 : 1);
    }
}


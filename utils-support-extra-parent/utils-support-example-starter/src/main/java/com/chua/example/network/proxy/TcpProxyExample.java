package com.chua.example.network.proxy;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link TcpProxyExampleSpi} 的同名独立主示例（迁移型）。
 *
 * <p>承载原 {@link TcpProxyExampleSpi#main(String[])} 的完整流程：
 * 解析 {@code --key=value} 参数后执行 TcpProxyServer 自检 / 性能基准，行为不变。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ... TcpProxyExample
 *   java ... TcpProxyExample --mode=perf
 *   java ... TcpProxyExample --mode=perf --concurrency=128 --connections=64 --requests=500
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class TcpProxyExample {

    private TcpProxyExample() {
    }

    /**
     * 独立入口：解析命令行参数并运行 tcp-proxy 流程。
     *
     * @param args 命令行参数（--key=value）
     */
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String kv = arg.substring(2);
                int eq = kv.indexOf('=');
                map.put(eq > 0 ? kv.substring(0, eq) : kv, eq > 0 ? kv.substring(eq + 1) : "");
            }
        }
        new TcpProxyExampleSpi().run(map);
    }
}

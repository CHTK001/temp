package com.chua.example.network.rpc;

import java.util.Map;

/**
 * {@link TcpRpcExampleSpi} 的同名独立主示例（迁移型）。
 *
 * <p>承载原 {@link TcpRpcExampleSpi#main(String[])} 的完整流程：
 * 实例化 SPI 并运行「TcpServer 注册 → ServiceDiscovery 发现 → SyncClient RPC 调用」链路，行为不变。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class TcpRpcExample {

    private TcpRpcExample() {
    }

    /**
     * 独立入口：运行 tcp-rpc 完整流程（当前流程未使用命令行参数，保持原行为）。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        new TcpRpcExampleSpi().run(Map.of());
    }
}

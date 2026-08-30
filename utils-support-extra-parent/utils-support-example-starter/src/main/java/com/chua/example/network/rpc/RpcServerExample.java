package com.chua.example.network.rpc;

import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 跨进程 RPC 服务端独立启动入口 — 供 {@link RpcExample} 的跨进程用例通过
 * {@link ProcessBuilder} 以独立 JVM 启动，验证「服务端与客户端分离进程」场景。
 *
 * <pre>
 *   java com.chua.example.network.rpc.RpcServerMain json 28080
 *   java com.chua.example.network.rpc.RpcServerMain native 28866
 * </pre>
 *
 * <p>启动后注册 {@link RpcEchoServiceExample} 并阻塞保持存活，由外部进程（测试）连接调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class RpcServerExample {

    /**
     * 私有构造函数，禁止实例化
     */
    private RpcServerExample() {
    }

    /**
     * 独立进程入口：按类型启动 RPC 服务端并阻塞保持存活。
     *
     * @param args {@code args[0]}=实现类型（json / native），{@code args[1]}=监听端口
     */
    public static void main(String[] args) {
        String type = args.length > 0 ? args[0] : "json";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        start(type, port);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 启动指定类型的 RPC 服务端并注册回显服务。
     *
     * @param type 实现类型（json / native）
     * @param port 监听端口
     * @return 已注册并启动的服务端实例
     */
    static RpcServer start(String type, int port) {
        RpcServer server;
        if ("native".equalsIgnoreCase(type)) {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setProtocol("direct");
            registry.setAddress("127.0.0.1:" + port);
            server = RpcServer.createService("native", List.of(registry),
                    RpcProtocolConfig.auto("native", port), "rpc-example-server");
        } else {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setAddress("http://127.0.0.1:" + port);
            server = RpcServer.createService("json", List.of(registry),
                    RpcProtocolConfig.auto("json", port), "rpc-example-server");
        }
        server.afterPropertiesSet();
        server.register(RpcEchoServiceExample.class.getName(), new RpcEchoServiceImplExample());
        log.info("RpcServerMain started: type={}, port={}", type, port);
        return server;
    }
}

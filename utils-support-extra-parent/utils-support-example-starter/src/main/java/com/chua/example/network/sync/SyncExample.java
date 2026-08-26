package com.chua.example.network.sync;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Sync 全子类对接自检主示例（迁移型）：承载原 {@link SyncExampleSpi#main(String[])} 完整流程。
 *
 * <p>流程：解析 {@code --key=value} / {@code --key value} 参数 → 驱动
 * {@link SyncExampleSpi#run(Map)} 执行 tcp/udp/kcp/http/websocket 五协议往返自检
 * （或 {@code --mode=throughput} / {@code --mode=rpc} 压测）→ 断言结果。
 * 服务端口由内部空闲端口探测分配，无需指定。</p>
 *
 * <p>用法：{@code java ... SyncExample [--mode=roundtrip|throughput|rpc] [--protocol=tcp]
 * [--messages=2000] [--threads=4] [--clients=1]}</p>
 *
 * <p>{@link SyncExampleSpi} 的 main 已一行委托至本类；SPI 路由键 {@code sync} 保持不变。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SyncExample {
    private SyncExample() { }


    /**
     * 主入口：解析参数并驱动 {@link SyncExampleSpi#run(Map)}，失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        log.info("[SYNC] 自检启动 params={}", parsed);
        boolean passed = new SyncExampleSpi().run(parsed);
        if (!passed) {
            System.out.println("[FAIL] sync 全子类自检");
            System.exit(1);
        }
        System.out.println("[PASS] sync 全子类自检");
    }

    /**
     * 解析 {@code --key=value} 与 {@code --key value} 两种形式；无值开关记为空串。
     *
     * @param args 原生命令行参数
     * @return 键值参数表
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String kv = arg.substring(2);
            int eq = kv.indexOf('=');
            if (eq > 0) {
                map.put(kv.substring(0, eq), kv.substring(eq + 1));
                continue;
            }
            String next = i + 1 < args.length ? args[i + 1] : null;
            if (next != null && !next.startsWith("--")) {
                map.put(kv, next);
                i++;
                continue;
            }
            map.put(kv, "");
        }
        return map;
    }
}

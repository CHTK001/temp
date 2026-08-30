package com.chua.example.network.scatter;

import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Scatter 集群主示例（驱动型）：解析命令行参数后驱动 {@link ScatterClusterExampleSpi#run(Map)}，
 * 复现「seed 引导互发现」最小场景。
 *
 * <p>默认 {@code --mode=seed}（TCP 双节点 seed 引导互发现 + hash 同步的最小场景）；
 * 可传 {@code --mode=heartbeat} / {@code --mode=all} 覆盖为心跳剔除或全场景。
 * 通信端口由 Spi 内部空闲端口探测分配，无需显式指定。</p>
 *
 * <p>用法：{@code java ... ScatterClusterExample [--mode=seed|heartbeat|all]}</p>
 *
 * <p>SPI 路由键仍由 {@link ScatterClusterExampleSpi#name()}（{@code scatter-cluster}）提供，
 * 本类不参与路由，仅作为独立 main 入口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ScatterClusterExample {
    private ScatterClusterExample() { }


    /** 默认执行模式：仅 seed 引导互发现最小场景。 */
    private static final String DEFAULT_MODE = "seed";

    /**
     * 主入口：解析参数并驱动 {@link ScatterClusterExampleSpi}，失败以退出码 1 结束。
     *
     * @param args 命令行参数，支持 {@code --key=value} 与 {@code --key value}
     */
    public static void main(String[] args) {
        Map<String, String> parsed = ExampleUtils.parseArgs(args);
        parsed.putIfAbsent("mode", DEFAULT_MODE);
        log.info("[SCATTER-CLUSTER] 启动 mode={}", parsed.get("mode"));
        boolean passed = new ScatterClusterExampleSpi().run(parsed);
        if (!passed) {
            log.info("[FAIL] scatter-cluster seed 引导互发现场景");
            System.exit(1);
        }
        log.info("[PASS] scatter-cluster seed 引导互发现场景");
    }

}

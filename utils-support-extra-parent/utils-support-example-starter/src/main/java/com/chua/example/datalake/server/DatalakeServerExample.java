package com.chua.example.datalake.server;

import com.chua.common.support.utils.CommandLine;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.server.DatalakeServerBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * DatalakeServer 综合示例 — 启动 DatalakeServer 并验证生命周期。
 *
 * <h2>用法</h2>
 * <pre>
 *   java DatalakeServerExample
 *   java DatalakeServerExample --type basic|all
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class DatalakeServerExample {

    /**
     * 默认能力点
     */
    private static final String DEFAULT_TYPE = "all";

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("DatalakeServerExample")
                .register("type", "t", "能力点（basic|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");
        if (cli.isHelp()) {
            cli.help();
            return;
        }
        String type = cli.get("type", DEFAULT_TYPE);
        boolean passed = switch (type.toLowerCase()) {
            case "basic" -> testBasicLifecycle();
            case "all" -> testBasicLifecycle();
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                yield false;
            }
        };
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 基础生命周期：builder.build() → start() → stop()。
     *
     * @return 自检通过返回 true，失败返回 false
     */
    public static boolean testBasicLifecycle() {
        log.info("===== basic =====");
        try {
            DatalakeServer server = DatalakeServerBuilder.builder().build();
            server.start();
            server.stop();
            printResult("start/stop completes", true);
            return true;
        } catch (Exception e) {
            log.error("basic test failed", e);
            return false;
        }
    }

    /**
     * 打印单条自检结果。
     *
     * @param name   自检项名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info("{}{}", (passed ? "[PASS]" : "[FAIL]"), name);
    }
}
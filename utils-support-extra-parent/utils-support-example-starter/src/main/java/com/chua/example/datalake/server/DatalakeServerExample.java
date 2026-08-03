package com.chua.example.datalake.server;

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
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.help()) {
            printHelp();
            return;
        }
        String type = parsed.type() != null ? parsed.type() : "all";
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

    private static void printResult(String name, boolean passed) {
        log.info("{}{}", (passed ? "[PASS]" : "[FAIL]"), name);
    }

    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--type", "-t" -> {
                    if (index + 1 < args.length) {
                        result = result.withType(args[++index]);
                    }
                }
                case "--help", "-h" -> result = result.withHelp(true);
                default -> log.warn("[WARN] 未知参数: {}", args[index]);
            }
            index++;
        }
        return result;
    }

    private static void printHelp() {
        log.info("DatalakeServer 综合示例");
        log.info("");
        log.info("用法: java DatalakeServerExample [选项]");
        log.info("");
        log.info("选项:");
        log.info("  --type, -t <key>    能力点（basic|all）");
        log.info("  --help,  -h          打印帮助");
    }

    /**
     * 命令行参数容器。
     */
    private record Args(String type, boolean help) {
        Args() {
            this(null, false);
        }

        public Args withType(String type) {
            return new Args(type, help);
        }

        public Args withHelp(boolean help) {
            return new Args(type, help);
        }
    }
}
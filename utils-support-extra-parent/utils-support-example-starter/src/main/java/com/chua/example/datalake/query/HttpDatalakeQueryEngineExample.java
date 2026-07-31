package com.chua.example.datalake.query;

import com.chua.datalake.support.engine.HttpDatalakeQueryEngine;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpDatalakeQueryEngine 综合示例 — 演示查询 Engine 的实例化与基本调用。
 *
 * <h2>用法</h2>
 * <pre>
 *   java HttpDatalakeQueryEngineExample
 *   java HttpDatalakeQueryEngineExample --type construct|all
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class HttpDatalakeQueryEngineExample {

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
            case "construct" -> testConstruct();
            case "all" -> testConstruct();
            default -> {
                System.err.println("[FAIL] 未知 type: " + type);
                yield false;
            }
        };
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 构造测试：HttpDatalakeQueryEngine 接收 baseUrl，能拿到 engine 引用。
     */
    public static boolean testConstruct() {
        log.info("===== construct =====");
        try {
            HttpDatalakeQueryEngine engine = new HttpDatalakeQueryEngine("http://localhost:8700");
            boolean ok = engine != null && "datalake".equals(engine.getDefaultDataSourceName());
            printResult("engine constructs and exposes default name", ok);
            engine.close();
            return ok;
        } catch (Exception e) {
            log.error("construct failed", e);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS]" : "[FAIL]") + " " + name);
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
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    private static void printHelp() {
        System.out.println("HttpDatalakeQueryEngine 综合示例");
        System.out.println();
        System.out.println("用法: java HttpDatalakeQueryEngineExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --type, -t <key>    能力点（construct|all）");
        System.out.println("  --help,  -h          打印帮助");
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
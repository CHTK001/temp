package com.chua.example.datalake.query;

import com.chua.common.support.utils.CommandLine;
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

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("HttpDatalakeQueryEngineExample")
                .register("type", "t", "能力点（construct|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");
        if (cli.isHelp()) {
            cli.help();
            return;
        }
        String type = cli.get("type", DEFAULT_TYPE);
        boolean passed = switch (type.toLowerCase()) {
            case "construct" -> testConstruct();
            case "all" -> testConstruct();
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                yield false;
            }
        };
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 构造测试：HttpDatalakeQueryEngine 接收 baseUrl，能拿到 engine 引用。
     *
     * @return 自检通过返回 true，失败返回 false
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

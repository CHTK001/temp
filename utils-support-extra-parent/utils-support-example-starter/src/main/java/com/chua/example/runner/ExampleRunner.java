package com.chua.example.runner;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一示例调度入口。
 *
 * <p>通过 SPI 发现所有 {@link Example} 实现，根据 {@code --example=xxx} 参数路由到对应实现执行自检。
 * 未指定参数时列出所有可用示例。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.runner.ExampleRunner
 *   java com.chua.example.runner.ExampleRunner --example=http-server
 *   java com.chua.example.runner.ExampleRunner --example=http-server --type=nio --mode=perf
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ExampleRunner {

    private ExampleRunner() {}

    /**
     * 主入口。
     *
     * @param args 命令行参数，支持 --example=xxx [附加参数...]
     */
    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        String exampleName = parsed.remove("example");

        ServiceProvider<Example> provider = ServiceProvider.of(Example.class);
        List<Example> all = provider.collect(true);

        if (all.isEmpty()) {
            log.error("[FAIL] 未发现任何 Example SPI 实现");
            System.exit(1);
        }

        if (exampleName == null || exampleName.isEmpty()) {
            printHelp(all);
            return;
        }

        Example target = all.stream()
                .filter(e -> e.name().equals(exampleName))
                .findFirst()
                .orElse(null);

        if (target == null) {
            log.error("[FAIL] 未找到名为 '{}' 的示例，可用列表：", exampleName);
            all.forEach(e -> log.error("  --example={}  ({})", e.name(), e.description()));
            System.exit(1);
        }

        log.info("[START] 运行示例: {} ({})", target.name(), target.description());
        boolean passed = target.run(parsed);

        if (passed) {
            log.info("[PASS] 示例 '{}' 全部场景通过", target.name());
            System.exit(0);
        } else {
            log.error("[FAIL] 示例 '{}' 存在失败的场景", target.name());
            System.exit(1);
        }
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp(List<Example> examples) {
            log.info("使用方法: java {} --example=<名称> [参数...]\n", ExampleRunner.class.getName());
            log.info("可用示例列表：");
            log.info("  %-28s %-20s %s", "命令", "模块", "描述");
            log.info("  " + "-".repeat(90));
            for (Example e : examples) {
                log.info("  --example=%-18s %-20s %s", e.name(), e.module(), e.description());
            }
            log.info("\n附加参数由具体示例自行解析，例如 --type=nio --mode=perf");
    }

    /**
     * 解析命令行参数为 Map。
     * 支持 --key=value 或 --key value 格式。
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                int eq = key.indexOf('=');
                if (eq > 0) {
                    result.put(key.substring(0, eq), key.substring(eq + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    result.put(key, args[++i]);
                } else {
                    result.put(key, "true");
                }
            }
        }
        return result;
    }
}

package com.chua.example.runner;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统一 Example 入口 — 基于 {@link Example} SPI 自动发现所有示例实现。
 *
 * <p>所有 Example 不再各自拥有 main 方法，全部注册到
 * {@code META-INF/extensions/com.chua.example.spi.Example}，本 Runner 通过
 * {@link ServiceLoader} 加载并按 {@code --example=xxx} 参数路由。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 列出全部已注册示例
 *   java ExampleRunner --list
 *
 *   # 运行指定示例
 *   java ExampleRunner --example=vector-storage --type=memory --test
 *
 *   # 跨模块示例演示（Milvus + Zilliz Cloud）
 *   java ExampleRunner --example=vector-storage --type=milvus \
 *       --host=in03-xxx.serverless.gcp-us-west1.cloud.zilliz.com \
 *       --port=443 --collection=vector_store_v4 --token=xxx
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ExampleRunner {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 已注册 Example 名称 → 实例映射（按名称排序，便于展示）。
     */
    private static final Map<String, Example> REGISTRY = new TreeMap<>();

    static {
        // 1) 加载 SPI 注册的 Example 实现
        for (Example example : ServiceProvider.of(Example.class).collect()) {
            REGISTRY.put(example.name(), example);
        }
        // 2) 自动扫描 com.chua.example.**.*Example 类作为 fallback（不要求改写为 SPI）
        scanAndRegisterByReflection();
    }

    /**
     * 通过反射扫描 com.chua.example 包下所有以 "Example" 结尾的类，
     * 注册为 fallback Example，由 Runner 通过反射调用其 main 方法。
     *
     * <p><b>注意</b>：JDK 25 已移除 {@code System.setSecurityManager}，
     * 被调用的 main 内部 {@code System.exit(...)} 无法被 Runner 拦截，
     * 会导致整个 JVM 退出。当前实现仅作为迁移期参考，
     * 真正的 SPI 模式应使用 {@link com.chua.example.spi.Example} 接口实现。</p>
     */
    private static void scanAndRegisterByReflection() {
        String[] candidates = {
            "com.chua.example.concurrent.offset.OffsetFlowExample",
            "com.chua.example.engine.EngineExample",
            "com.chua.example.datasync.DataSyncExample",
            "com.chua.example.datalake.server.DatalakeServerExample",
            "com.chua.example.datalake.pipeline.PipelineEngineExample",
            "com.chua.example.datalake.query.HttpDatalakeQueryEngineExample",
            "com.chua.example.datalake.sink.DataSinkExample",
            "com.chua.example.datalake.subscribe.SubscriberExample",
            "com.chua.example.datalake.integrated.DatalakeIntegratedExample",
            "com.chua.example.ai.chat.AiProxyDetectorExample",
            "com.chua.example.lang.document.DocumentExample",
            "com.chua.example.tui.TuiDashboardExample",
            "com.chua.example.media.VideoCodecExample",
            "com.chua.example.media.ScreenCaptureExample"
        };
        for (String className : candidates) {
            try {
                Class<?> clazz = Class.forName(className);
                String simpleName = clazz.getSimpleName();
                if (simpleName.endsWith("ExampleSpi")) {
                    continue;
                }
                String name = camelToKebab(simpleName.replace("Example", ""));
                if (REGISTRY.containsKey(name)) {
                    continue;
                }
                // JDK 25 不支持 SecurityManager，反射 fallback 暂禁用
                // 仅注册以提供 --list 提示用户使用真正的 SPI
                REGISTRY.put(name, new ReflectionExample(name, simpleName, className));
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    private static String camelToKebab(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * 统一入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help() || parsed.list()) {
            printHelp();
            return;
        }

        if (parsed.exampleName() == null) {
            System.err.println("[ERROR] 必须指定 --example=<name> 或 --list");
            printHelp();
            System.exit(EXIT_CODE_FAILURE);
            return;
        }

        Example example = REGISTRY.get(parsed.exampleName());
        if (example == null) {
            System.err.println("[ERROR] 未注册的示例: " + parsed.exampleName());
            System.err.println("[HINT]  --list 查看全部可用示例");
            System.exit(EXIT_CODE_FAILURE);
            return;
        }

        log.info("===== ExampleRunner --example={} (module={}) =====",
                example.name(), example.module());
        boolean passed;
        try {
            passed = example.run(parsed.kv());
        } catch (Throwable t) {
            log.error("示例运行异常: {}", t.getMessage(), t);
            passed = false;
        }
        log.info("===== {} {} =====", example.name(),
                passed ? "✓ PASSED" : "✗ FAILED");
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 列出全部已注册示例。
     *
     * @return 名称列表
     */
    public static List<String> listNames() {
        return Arrays.asList(REGISTRY.keySet().toArray(new String[0]));
    }

    /**
     * 解析命令行参数。
     */
    private static Args parseArgs(String[] args) {
        Map<String, String> kv = new HashMap<>();
        boolean help = false;
        boolean list = false;
        String exampleName = null;

        int index = 0;
        while (index < args.length) {
            String token = args[index];
            // 通用 --key=value 处理：仅 --example= 作为特殊路由，其余全部进 kv
            if (token.startsWith("--example=")) {
                exampleName = token.substring(token.indexOf('=') + 1);
                index++;
                continue;
            }
            switch (token) {
                case "--help", "-h" -> help = true;
                case "--list", "-l" -> list = true;
                case "--example", "-e" -> {
                    if (index + 1 < args.length) {
                        exampleName = args[++index];
                    }
                }
                default -> {
                    // 支持 --key=value 形式
                    if (token.startsWith("--") && token.contains("=")) {
                        int eq = token.indexOf('=');
                        kv.put(token.substring(2, eq), token.substring(eq + 1));
                    } else if (token.startsWith("--") && index + 1 < args.length
                            && !args[index + 1].startsWith("--")) {
                        kv.put(token.substring(2), args[++index]);
                    } else if (token.startsWith("--")) {
                        kv.put(token.substring(2), "true");
                    } else {
                        System.err.println("[WARN] 未知位置参数: " + token);
                    }
                }
            }
            index++;
        }

        return new Args(exampleName, help, list, kv);
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("ExampleRunner — 统一 SPI 示例入口");
        System.out.println();
        System.out.println("用法: java ExampleRunner [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --example, -e <name>  指定要运行的示例名称");
        System.out.println("  --list,    -l         列出全部已注册示例");
        System.out.println("  --help,    -h         显示此帮助");
        System.out.println("  --<key> <value>       透传给示例实现的额外参数");
        System.out.println();
        System.out.println("已注册示例 (" + REGISTRY.size() + "):");
        if (REGISTRY.isEmpty()) {
            System.out.println("  <none>");
        } else {
            REGISTRY.forEach((name, example) -> System.out.printf(
                    "  %-30s  %-20s  %s%n", name, example.module(), example.description()));
        }
    }

    /**
     * 参数容器。
     *
     * @param exampleName 示例名称
     * @param help        是否打印帮助
     * @param list        是否列出全部示例
     * @param kv          透传给示例的 KV 参数
     */
    private record Args(String exampleName, boolean help, boolean list, Map<String, String> kv) {
    }

    /**
     * 反射 fallback Example — 通过 {@code Class.forName(...).main(args)} 调用原始 main。
     *
     * <p>用于在迁移期保留旧 Example 的运行能力，同时统一通过 Runner 调度。
     * 真正的 SPI 实现应优先实现 {@link Example} 接口（享受类型安全 + 非反射调用）。</p>
     */
    private static final class ReflectionExample implements Example {

        private final String name;
        private final String displayName;
        private final String className;

        ReflectionExample(String name, String displayName, String className) {
            this.name = name;
            this.displayName = displayName;
            this.className = className;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String module() {
            return "reflection-fallback";
        }

        @Override
        public String description() {
            return displayName + " (待迁移为 SPI)";
        }

        @Override
        public boolean run(Map<String, String> args) {
            log.warn("[ReflectionExample] {} 尚未迁移为真正的 SPI 实现，请参考 VectorStorageExampleSpi 改造",
                    className);
            return false;
        }
    }
}

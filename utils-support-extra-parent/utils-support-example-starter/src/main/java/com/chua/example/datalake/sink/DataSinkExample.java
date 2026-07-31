package com.chua.example.datalake.sink;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.spi.sink.AccessSink;
import com.chua.datalake.support.spi.sink.DataSink;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DataSink 综合示例 — 演示 {@link DataSink} 与 {@link AccessSink} SPI 的加载与写入。
 *
 * <h2>用法</h2>
 * <pre>
 *   java DataSinkExample
 *   java DataSinkExample --type access|store|all
 * </pre>
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>access 加载</td><td>{@link #testAccessSinks()}</td><td>遍历 AccessSink 实现</td></tr>
 *   <tr><td>store 加载</td><td>{@link #testStoreSinks()}</td><td>遍历 DataSink 实现</td></tr>
 *   <tr><td>写入自检</td><td>{@link #testWrite()}</td><td>调 write 并验证返回 true</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class DataSinkExample {

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
            case "access" -> testAccessSinks();
            case "store" -> testStoreSinks();
            case "write" -> testWrite();
            case "all" -> testAccessSinks() && testStoreSinks() && testWrite();
            default -> {
                System.err.println("[FAIL] 未知 type: " + type);
                yield false;
            }
        };

        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 加载所有 AccessSink 实现。
     */
    public static boolean testAccessSinks() {
        log.info("===== access =====");
        List<DataSink> sinks = collectSinks();
        long count = sinks.stream().filter(s -> s instanceof AccessSink).count();
        boolean ok = count >= 3L;
        printResult("found >= 3 AccessSinks (log/statistic/realtime)", ok);
        return ok;
    }

    /**
     * 加载所有 DataSink 实现（含存储型）。
     */
    public static boolean testStoreSinks() {
        log.info("===== store =====");
        List<DataSink> sinks = collectSinks();
        boolean ok = sinks.stream().anyMatch(s -> "jdbc".equals(s.type()));
        printResult("found jdbc DataSink", ok);
        return ok;
    }

    /**
     * 给所有 sink 发一条数据并验证返回 true。
     */
    public static boolean testWrite() {
        log.info("===== write =====");
        List<DataSink> sinks = collectSinks();
        DataEnvelope envelope = new DataEnvelope(sampleData());
        envelope.setPipelineId("example-pipeline");
        envelope.setTraceId("trace-001");
        boolean allOk = true;
        for (DataSink sink : sinks) {
            boolean ok = sink.write(envelope, new HashMap<>());
            if (!ok) {
                log.warn("sink={} 写入返回 false", sink.type());
            }
            allOk &= ok;
        }
        printResult("all sinks accept envelope", allOk);
        return allOk;
    }

    /**
     * 收集所有已注册的 DataSink 实现。
     */
    private static List<DataSink> collectSinks() {
        Set<String> names = ServiceProvider.of(DataSink.class).getExtensions();
        List<DataSink> result = new ArrayList<>();
        for (String name : names) {
            DataSink sink = ServiceProvider.of(DataSink.class).getExtension(name);
            if (sink != null) {
                result.add(sink);
            }
        }
        return result;
    }

    /**
     * 示例业务数据。
     */
    public static Map<String, Object> sampleData() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", 1);
        map.put("name", "datalake");
        map.put("ts", System.currentTimeMillis());
        return map;
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
        System.out.println("DataSink 综合示例 — 基于 DataSink/AccessSink SPI");
        System.out.println();
        System.out.println("用法: java DataSinkExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --type, -t <key>    能力点（access|store|write|all）");
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
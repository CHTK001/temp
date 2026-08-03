package com.chua.example.datasync;

import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.DataSyncAgentSource;
import com.chua.datasync.agent.support.executor.ReactorDataSyncExecutor;
import com.chua.starter.datasync.DataSyncServer;
import com.chua.starter.datasync.DefaultDataSyncServer;
import com.chua.starter.datasync.agent.AgentServerManager;
import com.chua.starter.datasync.agent.DataSyncAgentServer;
import com.chua.starter.datasync.config.DataSyncConfigDefinition;
import com.chua.starter.datasync.mapping.DataSyncFieldMapping;
import com.chua.starter.datasync.mapping.DefaultDataSyncMapping;
import com.chua.starter.datasync.model.DataSyncMapping;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSync 数据同步综合示例 — 演示 {@link DataSyncServer} 的端到端流程。
 *
 * <h2>用法</h2>
 * <pre>
 *   java DataSyncExample
 *   java DataSyncExample --type basic|repeat|direct|all
 * </pre>
 *
 * <h2>能力点</h2>
 * <ul>
 *   <li>basic：注册 source / sink / mapping 后启动，验证 executor.publish 是否抛异常</li>
 *   <li>repeat：连续触发多次映射，观察调度稳定性</li>
 *   <li>direct：直接调用 {@link ReactorDataSyncExecutor} publish 链路</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DataSyncExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 等待时间（毫秒），让调度器轮询并执行
     */
    private static final long WAIT_MILLIS = 4000L;

    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.help()) {
            printHelp();
            return;
        }
        String type = parsed.type() != null ? parsed.type() : "basic";
        DataSyncExample example = new DataSyncExample();
        boolean passed = example.runTest(type);
        printResult("总结果", passed);
        System.out.flush();
        System.err.flush();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：根据能力点类型分发到对应测试方法。
     *
     * @param type 能力点类型（basic / repeat / direct / all）
     * @return true 表示所选能力点全部通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = "basic";
        }
        switch (type.toLowerCase()) {
            case "basic":
                return testBasic();
            case "repeat":
                return testRepeat();
            case "direct":
                return testDirectExecutor();
            case "all":
                return testBasic() && testRepeat() && testDirectExecutor();
            default:
                log.error("[DataSyncExample] 未知 type: {}", type);
                return false;
        }
    }

    /**
     * basic：构造最小 source/sink/mapping，启动 server 后等待调度器执行。
     */
    public static boolean testBasic() {
        log.info("===== basic =====");
        AgentServerManager agentManager = new DataSyncAgentServer(null, null, "agent");
        DataSyncServer server = new DefaultDataSyncServer(agentManager);

        AtomicInteger received = new AtomicInteger();
        DataSyncAgentSource source = new InMemorySource("src-1", "in-1");
        DataSyncAgentSink sink = new CountingSink("sink-1", received);
        server.registerSource(source);
        server.registerSink(sink);

        List<DataSyncFieldMapping> fields = List.of(
                new SimpleField("id", "id", "toString"),
                new SimpleField("name", "name", "toString")
        );

        DataSyncConfigDefinition cfg = new SimpleConfig("in-1", "src-1", "out-1", "sink-1",
                fields, 10, "simple", "* * * * * ?", Map.of());

        DataSyncMapping mapping = new DefaultDataSyncMapping(
                "map-1",
                cfg.inputId(),
                cfg.sourceId(),
                cfg.outputId(),
                cfg.sinkId(),
                cfg,
                fields,
                cfg.batch(),
                cfg.cronType(),
                cfg.cron(),
                cfg.params(),
                null
        );
        server.addMapping(mapping);

        try {
            server.start();
            log.info("Server 已启动，等待 {} ms 让调度器执行...", WAIT_MILLIS);
            Thread.sleep(WAIT_MILLIS);
        } catch (Exception e) {
            log.error("basic 测试异常", e);
            return false;
        } finally {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }

        int count = received.get();
        log.info("basic 接收记录数: {}", count);
        printResult("basic", count > 0);
        return count > 0;
    }

    /**
     * repeat：连续启停 3 次，观察 publish 是否稳定。
     */
    public static boolean testRepeat() {
        log.info("===== repeat =====");
        for (int i = 0; i < 3; i++) {
            log.info("--- 第 {} 轮 ---", i + 1);
            AgentServerManager agentManager = new DataSyncAgentServer(null, null, "agent");
            DataSyncServer server = new DefaultDataSyncServer(agentManager);
            AtomicInteger received = new AtomicInteger();
            server.registerSource(new InMemorySource("src-r", "in-r"));
            server.registerSink(new CountingSink("sink-r", received));
            List<DataSyncFieldMapping> fields = List.of(
                    new SimpleField("id", "id", "toString")
            );
            DataSyncConfigDefinition cfg = new SimpleConfig("in-r", "src-r", "out-r", "sink-r",
                    fields, 5, "simple", "* * * * * ?", Map.of());
            DataSyncMapping mapping = new DefaultDataSyncMapping(
                    "map-r",
                    cfg.inputId(),
                    cfg.sourceId(),
                    cfg.outputId(),
                    cfg.sinkId(),
                    cfg,
                    fields,
                    cfg.batch(),
                    cfg.cronType(),
                    cfg.cron(),
                    cfg.params(),
                    null
            );
            server.addMapping(mapping);
            try {
                server.start();
                Thread.sleep(2000L);
            } catch (Exception e) {
                log.error("repeat 第 {} 轮异常", i + 1, e);
                return false;
            } finally {
                try {
                    server.stop();
                } catch (Exception ignored) {
                }
            }
            log.info("第 {} 轮 接收: {}", (i + 1), received.get());
        }
        return true;
    }

    /**
     * direct：直接调用 {@link ReactorDataSyncExecutor} 验证 publish 链路。
     */
    public static boolean testDirectExecutor() {
        log.info("===== direct executor =====");
        ReactorDataSyncExecutor executor = new ReactorDataSyncExecutor("agent-1", true);
        try {
            executor.start();
            executor.subscribe("topic-1", batch -> log.info("subscribe 收到: {}", batch));
            executor.publish("topic-1", List.of(Map.of("k", "v")));
            Thread.sleep(500L);
        } catch (Exception e) {
            log.error("direct executor 异常", e);
            return false;
        } finally {
            try {
                executor.stop();
            } catch (Exception ignored) {
            }
        }
        return true;
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
        log.info("DataSync 综合示例");
        log.info("");
        log.info("用法: java DataSyncExample [选项]");
        log.info("");
        log.info("选项:");
        log.info("  --type, -t <key>    能力点（basic|repeat|direct|all）");
        log.info("  --help,  -h          打印帮助");
    }

    /**
     * 命令行参数容器。
     *
     * @param type 能力点类型
     * @param help 是否打印帮助
     * @author CH
     * @since 4.0.0.42
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

    /**
     * 简单字段映射。
     *
     * @param sourceField 源字段
     * @param targetField 目标字段
     * @param converter   转换器
     * @author CH
     * @since 4.0.0.42
     */
    private record SimpleField(String sourceField, String targetField, String converter)
            implements DataSyncFieldMapping {
    }

    /**
     * 简单配置定义（record 实现 DataSyncConfigDefinition）。
     *
     * @param inputId   输入 ID
     * @param sourceId  源 ID
     * @param outputId  输出 ID
     * @param sinkId    目标 ID
     * @param mappings  字段映射列表
     * @param batch     批大小
     * @param cronType  调度类型
     * @param cron      cron 表达式
     * @param params    额外参数
     * @author CH
     * @since 4.0.0.42
     */
    private record SimpleConfig(
            String inputId,
            String sourceId,
            String outputId,
            String sinkId,
            List<DataSyncFieldMapping> mappings,
            int batch,
            String cronType,
            String cron,
            Map<String, Object> params
    ) implements DataSyncConfigDefinition {
    }

    /**
     * 内存数据源，构造简单 3 行测试数据。
     */
    private static final class InMemorySource implements DataSyncAgentSource {

        /**
         * Source 实例 ID
         */
        private final String id;

        /**
         * 输入 ID
         */
        private final String inputId;

        InMemorySource(String id, String inputId) {
            this.id = id;
            this.inputId = inputId;
        }

        /**
         * 获取 Source ID。
         *
         * @return Source ID
         */
        @Override
        public String sourceId() {
            return id;
        }

        /**
         * 获取输入 ID。
         *
         * @return 输入 ID
         */
        @Override
        public String inputId() {
            return inputId;
        }

        /**
         * 读取所有测试数据。
         *
         * @param params 额外参数（本示例忽略）
         * @return 3 行测试数据的 Flux
         */
        @Override
        public Flux<Map<String, Object>> read(Map<String, Object> params) {
            return Flux.just(
                    Map.<String, Object>of("id", "1", "name", "alice"),
                    Map.<String, Object>of("id", "2", "name", "bob"),
                    Map.<String, Object>of("id", "3", "name", "carol")
            );
        }

        @Override
        public void close() {
        }
    }

    /**
     * 计数 Sink，每收到一条数据自增。
     */
    private static final class CountingSink implements DataSyncAgentSink {

        /**
         * Sink 实例 ID
         */
        private final String id;

        /**
         * 计数器
         */
        private final AtomicInteger counter;

        CountingSink(String id, AtomicInteger counter) {
            this.id = id;
            this.counter = counter;
        }

        @Override
        public String sinkId() {
            return id;
        }

        @Override
        public void write(Flux<Map<String, Object>> data) {
            data.doOnNext(row -> {
                counter.incrementAndGet();
                log.info("[SINK.row] sink={} row={}", id, row);
            }).subscribe();
        }

        @Override
        public void close() {
        }
    }
}

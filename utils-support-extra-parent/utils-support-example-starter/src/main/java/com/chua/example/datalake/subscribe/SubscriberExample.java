package com.chua.example.datalake.subscribe;

import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.subscriber.RealTimeDatalakeSubscriber;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订阅器综合示例 — 演示 {@link RealTimeDatalakeSubscriber} 与 OffsetFlow 的集成。
 *
 * <h2>用法</h2>
 * <pre>
 *   java SubscriberExample
 *   java SubscriberExample --type push|reset|all
 * </pre>
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>push 推送</td><td>{@link #testPush()}</td><td>推送一条数据并验证 consumer 接收</td></tr>
 *   <tr><td>reset</td><td>{@link #testReset()}</td><td>推送后 reset offset，重启后仍能继续</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class SubscriberExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 临时目录
     */
    private static final String TEST_DIR = System.getProperty("java.io.tmpdir") + "/datalake-sub-example-" + System.currentTimeMillis();

    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.help()) {
            printHelp();
            return;
        }
        String type = parsed.type() != null ? parsed.type() : "all";
        boolean passed = switch (type.toLowerCase()) {
            case "push" -> testPush();
            case "reset" -> testReset();
            case "all" -> testPush() && testReset();
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                yield false;
            }
        };
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * push 推送：构造订阅器并通过 push 触发 consumer。
     */
    public static boolean testPush() {
        log.info("===== push =====");
        Path dir = Path.of(TEST_DIR + "-push");
        AtomicInteger counter = new AtomicInteger();
        try (OffsetFlow flow = OffsetFlow.create().basePath(dir.toString()).start()) {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-push",
                    flow,
                    env -> counter.incrementAndGet());
            sub.subscribe();
            DataEnvelope envelope = sampleEnvelope();
            sub.push(envelope).block();
            boolean ok = counter.get() == 1;
            printResult("consumer invoked once", ok);
            return ok;
        }
    }

    /**
     * reset：reset 后 push 仍能正常工作。
     */
    public static boolean testReset() {
        log.info("===== reset =====");
        Path dir = Path.of(TEST_DIR + "-reset");
        AtomicInteger counter = new AtomicInteger();
        try (OffsetFlow flow = OffsetFlow.create().basePath(dir.toString()).start()) {
            RealTimeDatalakeSubscriber sub = new RealTimeDatalakeSubscriber(
                    "sub-reset",
                    flow,
                    env -> counter.incrementAndGet());
            sub.subscribe();
            sub.push(sampleEnvelope()).block();
            sub.reset(0L);
            sub.push(sampleEnvelope()).block();
            boolean ok = counter.get() == 2;
            printResult("after reset, push still works", ok);
            return ok;
        }
    }

    private static DataEnvelope sampleEnvelope() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("name", "subscribe");
        DataEnvelope envelope = new DataEnvelope(data);
        envelope.setPipelineId("example");
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    @SuppressWarnings("unused")
    private static Mono<Void> ignoredMono() {
        return Mono.empty();
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
        log.info("Subscriber 综合示例");
        log.info("");
        log.info("用法: java SubscriberExample [选项]");
        log.info("");
        log.info("选项:");
        log.info("  --type, -t <key>    能力点（push|reset|all）");
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
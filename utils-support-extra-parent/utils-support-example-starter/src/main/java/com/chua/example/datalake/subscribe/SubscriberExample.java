package com.chua.example.datalake.subscribe;

import com.chua.common.support.concurrent.offset.OffsetFlow;
import com.chua.common.support.utils.CommandLine;
import com.chua.datalake.support.model.DataEnvelope;
import com.chua.datalake.support.subscriber.RealTimeDatalakeSubscriber;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;
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
    private SubscriberExample() { }


    /**
     * 默认能力点
     */
    private static final String DEFAULT_TYPE = "all";

    /**
     * 临时目录
     */
    private static final String TEST_DIR = System.getProperty("java.io.tmpdir") + "/datalake-sub-example-" + System.currentTimeMillis();

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("SubscriberExample")
                .register("type", "t", "能力点（push|reset|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);
        boolean passed = switch (type.toLowerCase()) {
            case "push" -> testPush();
            case "reset" -> testReset();
            case "all" -> testPush() && testReset();
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                yield false;
            }
        };
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
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

    /** SampleEnvelope */
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
    /** IgnoredMono */
    private static Mono<Void> ignoredMono() {
        return Mono.empty();
    }

    /** PrintResult */
    private static void printResult(String name, boolean passed) {
        log.info("{}{}", (passed ? "[PASS]" : "[FAIL]"), name);
    }
}

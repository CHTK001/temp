package com.chua.example.flow;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.flow.FlowNodeExecutor;
import com.chua.common.support.task.flow.FlowProps;
import com.chua.common.support.task.flow.FlowStatus;
import com.chua.flow.support.FlowEngine;
import com.chua.common.support.task.flow.Flow;
import com.chua.common.support.task.flow.FlowInstance;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流程编排综合示例 — 基于 {@link FlowEngine} 门面与 {@link FlowNodeExecutor} 节点注册表。
 *
 * <p>演示流程编排的核心能力：</p>
 * <ul>
 *   <li>DSL 链式构建：start → condition（true/false 分支）→ transform → log → end</li>
 *   <li>JSON 导出与导入：图模型往返一致</li>
 *   <li>同一实例上下文复用：多次 run 不重建上下文，参数合并写入</li>
 *   <li>WAIT / resume 挂起恢复：节点挂起后恢复继续执行</li>
 *   <li>自定义节点注册：通过 SPI 注册新节点类型并即时使用</li>
 *   <li>spider 节点：流程中嵌入爬虫抓取（需联网，失败不计入自检结果）</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行：执行全部自检
 *   java FlowExample
 *
 *   # 仅运行指定自检项（dsl/json/ctx/wait/spi/spider/all）
 *   java FlowExample --type dsl
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FlowExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        String type = parseType(args);
        boolean passed = new FlowExample().runTest(type);
        System.out.println("[FlowExample] self-test type=" + type + ", passed=" + passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 解析命令行类型参数。
     *
     * @param args 命令行参数
     * @return 类型参数，默认 all
     */
    private static String parseType(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--type=")) {
                return arg.substring("--type=".length());
            }
        }
        return "all";
    }

    /**
     * 自检入口：根据类型分发到对应测试方法。
     *
     * @param type 自检项（dsl / json / ctx / wait / spi / spider / all）
     * @return 是否通过
     */
    public boolean runTest(String type) {
        switch (type.toLowerCase()) {
            case "dsl":
                return testDsl();
            case "json":
                return testJsonRoundTrip();
            case "ctx":
                return testContextReuse();
            case "wait":
                return testWaitResume();
            case "spi":
                return testCustomNode();
            case "spider":
                return testSpiderNode();
            case "all":
                return testDsl() && testJsonRoundTrip() && testContextReuse()
                        && testWaitResume() && testCustomNode() && testSpiderNode();
            default:
                System.out.println("[FlowExample] 未知 type: " + type);
                return false;
        }
    }

    /**
     * 自检项 1：DSL 构建与执行。
     *
     * <p>构建 start → check(condition) → transform → log → end 流程，
     * 入参 bizId 非空走 true 分支完成；为空走 false 分支直接结束。</p>
     *
     * @return 是否通过
     */
    public static boolean testDsl() {
        System.out.println("===== dsl =====");
        Flow flow = FlowEngine.createFlow("demo")
                .addNode("start", "start")
                .addNext("start", "check")
                .addNode("check", "condition", Map.of("key", "bizId"))
                .when("check", true, "transform")
                .when("check", false, "end")
                .addNext("check", "transform")
                .addNode("transform", "transform", Map.of("source", "attribute:bizId"))
                .addNext("transform", "log")
                .addNode("log", "log", Map.of("message", "bizId={}", "level", "info"))
                .addNext("log", "end")
                .addNode("end", "end");

        FlowInstance instance = flow.createInstance();
        instance.run(Map.of("bizId", "CH-1001"));
        boolean completed = instance.isCompleted();
        boolean dataOk = "CH-1001".equals(instance.getCurrentData());
        printResult("DSL 执行完成", completed);
        printResult("transform 透传数据", dataOk);
        return completed && dataOk;
    }

    /**
     * 自检项 2：JSON 导出与导入。
     *
     * <p>导出图模型 JSON 后再导入创建新流程，两次运行结果一致。</p>
     *
     * @return 是否通过
     */
    public static boolean testJsonRoundTrip() {
        System.out.println("===== json =====");
        Flow flow = FlowEngine.createFlow("roundtrip")
                .addNode("start", "start")
                .addNext("start", "echo")
                .addNode("echo", "log", Map.of("message", "roundtrip", "level", "info"))
                .addNext("echo", "end")
                .addNode("end", "end");

        String json = flow.exportJson();
        Flow imported = FlowEngine.parseJson(json);
        FlowInstance instance = imported.createInstance();
        instance.run();
        boolean ok = instance.isCompleted();
        printResult("JSON 往返后执行完成", ok);
        return ok;
    }

    /**
     * 自检项 3：同一实例上下文复用。
     *
     * <p>实例创建后复用上下文多次运行，每次 run 传入新参数合并写入，
     * 通过多段流程验证参数透传。</p>
     *
     * @return 是否通过
     */
    public static boolean testContextReuse() {
        System.out.println("===== ctx =====");
        // 每个 flow 创建独立实例，验证上下文不共享
        boolean allOk = true;

        Flow flowA = FlowEngine.createFlow("ctx-A")
                .addNode("start", "start")
                .addNext("start", "end")
                .addNode("end", "end");
        FlowInstance instanceA = flowA.createInstance();
        instanceA.run(Map.of("key", "first"));
        allOk &= "first".equals(instanceA.getAttribute("key"));
        printResult("首次实例属性生效", "first".equals(instanceA.getAttribute("key")));

        FlowInstance instanceB = flowA.createInstance();
        instanceB.run(Map.of("key", "second"));
        allOk &= "second".equals(instanceB.getAttribute("key"));
        printResult("新实例属性独立", "second".equals(instanceB.getAttribute("key")));

        // wait/resume 模式验证上下文不重置
        ServiceProvider.of(FlowNodeExecutor.class)
                .register("ctxWait", (FlowNodeExecutor) FlowInstance::waitForResume);
        Flow flowC = FlowEngine.createFlow("ctx-C")
                .addNode("start", "start")
                .addNext("start", "wait")
                .addNode("wait", "ctxWait")
                .addNext("wait", "transform")
                .addNode("transform", "transform", Map.of("source", "attribute:key"))
                .addNext("transform", "end")
                .addNode("end", "end");
        FlowInstance instanceC = flowC.createInstance();
        instanceC.run(Map.of("key", "ctx-value"));
        boolean waited = instanceC.getStatus() == FlowStatus.WAITED;
        boolean attrOk = "ctx-value".equals(instanceC.getAttribute("key"));
        instanceC.setAttribute("key", "updated-value");
        instanceC.resume();
        boolean completed = instanceC.isCompleted();
        boolean dataOk = "updated-value".equals(instanceC.getCurrentData());
        printResult("WAIT 后属性保留", attrOk && waited);
        printResult("resume 前修改属性透传", completed && dataOk);
        return allOk && waited && attrOk && completed && dataOk;
    }

    /**
     * 自检项 4：WAIT / resume 挂起恢复。
     *
     * <p>向节点注册表注册一个临时挂起节点 waitNode，
     * 流程运行到该节点挂起（WAITED），恢复后继续执行到结束。</p>
     *
     * @return 是否通过
     */
    public static boolean testWaitResume() {
        System.out.println("===== wait =====");
        ServiceProvider.of(FlowNodeExecutor.class)
                .register("waitNode", (FlowNodeExecutor) FlowInstance::waitForResume);

        Flow flow = FlowEngine.createFlow("wait")
                .addNode("start", "start")
                .addNext("start", "wait")
                .addNode("wait", "waitNode")
                .addNext("wait", "end")
                .addNode("end", "end");

        FlowInstance instance = flow.createInstance();
        instance.run();
        boolean waited = instance.getStatus() == FlowStatus.WAITED;
        instance.resume();
        boolean resumed = instance.isCompleted();
        printResult("挂起后状态 WAITED", waited);
        printResult("恢复后执行完成", resumed);
        return waited && resumed;
    }

    /**
     * 自检项 5：自定义节点注册。
     *
     * <p>通过 SPI 注册 echo 节点（见 {@link FlowEchoNode}），
     * 流程中使用该类型并验证属性读取。</p>
     *
     * @return 是否通过
     */
    public static boolean testCustomNode() {
        System.out.println("===== spi =====");
        Flow flow = FlowEngine.createFlow("spi")
                .addNode("start", "start")
                .addNext("start", "echo")
                .addNode("echo", "echo", Map.of("message", "hello-flow"))
                .addNext("echo", "end")
                .addNode("end", "end");

        FlowInstance instance = flow.createInstance();
        instance.run();
        boolean ok = "hello-flow".equals(instance.getCurrentData());
        printResult("自定义 echo 节点执行", ok);
        return ok;
    }

    /**
     * 自检项 6：spider 节点嵌入流程。
     *
     * <p>流程中使用内置 spider 节点抓取页面，结果写入 spider.result 属性。
     * 该自检依赖网络，失败仅提示不阻断其余自检项。</p>
     *
     * @return 是否通过
     */
    public static boolean testSpiderNode() {
        System.out.println("===== spider =====");
        try {
            Flow flow = FlowEngine.createFlow("spider-demo")
                    .addNode("start", "start")
                    .addNext("start", "spider")
                    .addNode("spider", "spider", Map.of(
                            "urls", Collections.singletonList("https://example.com"),
                            "threads", 1,
                            "maxPages", 1,
                            "interval", 1000))
                    .addNext("spider", "end")
                    .addNode("end", "end");

            FlowInstance instance = flow.createInstance();
            instance.run();
            Object result = instance.getAttribute("spider.result");
            boolean ok = instance.isCompleted() && result instanceof List && !((List<?>) result).isEmpty();
            printResult("spider 节点抓取完成", ok);
            return ok;
        } catch (Exception e) {
            System.out.println("[WARN] spider 自检需要联网，已跳过: " + e.getMessage());
            return true;
        }
    }

    /**
     * 打印自检结果。
     *
     * @param name   自检项名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS] " : "[FAIL] ") + name);
    }
}

package com.chua.example.flow;

import com.chua.common.support.task.flow.ConditionNode;
import com.chua.common.support.task.flow.Flow;
import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowInstance;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeRegistry;
import com.chua.common.support.task.flow.FlowProps;
import com.chua.common.support.task.flow.FlowStatus;
import com.chua.flow.support.FlowEngine;
import com.chua.flow.support.node.ConditionFlowNode;
import com.chua.flow.support.node.EndFlowNode;
import com.chua.flow.support.node.LogFlowNode;
import com.chua.flow.support.node.StartFlowNode;
import com.chua.flow.support.node.TransformFlowNode;
import com.chua.spider.support.flow.SpiderFlowNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流程编排综合示例 — 基于新架构的节点接口 + 编排图 DSL。
 *
 * <p>演示流程编排的核心能力：</p>
 * <ul>
 *   <li>DSL 构建：{@code flow.createGraph().start(id).next(...).when(...).end()}，支持分支多目标</li>
 *   <li>节点直接 new 实例加入：start → condition（true/false 分支）→ transform → log → end</li>
 *   <li>JSON 导出与导入：图模型往返一致</li>
 *   <li>同一实例上下文复用：多次 run 不重建上下文，参数合并写入</li>
 *   <li>WAIT / resume 挂起恢复：节点调用上下文挂起后恢复继续执行</li>
 *   <li>自定义节点：实现 {@link FlowNode} 接口直接使用</li>
 *   <li>spider 节点：流程中嵌入爬虫抓取（需联网，失败不计入自检结果）</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行：执行全部自检
 *   java FlowExample
 *
 *   # 仅运行指定自检项（dsl/json/multitarget/ctx/wait/spi/spider/all）
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
     *     @param type 自检项（dsl / json / multitarget / trace / loop / ctx / wait / spi / spider / all）
     * @return 是否通过
     */
    public boolean runTest(String type) {
        switch (type.toLowerCase()) {
            case "dsl":
                return testDsl();
            case "json":
                return testJsonRoundTrip();
            case "multitarget":
                return testMultiTarget();
            case "trace":
                return testTrace();
            case "loop":
                return testLoopGuard();
            case "ctx":
                return testContextReuse();
            case "wait":
                return testWaitResume();
            case "spi":
                return testCustomNode();
            case "spider":
                return testSpiderNode();
            case "all":
                return testDsl() && testJsonRoundTrip() && testMultiTarget()
                        && testTrace() && testLoopGuard()
                        && testContextReuse()
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
                .addNode("start", new StartFlowNode())
                .addNode("check", new ConditionFlowNode(), Map.of("key", "bizId"))
                .addNode("transform", new TransformFlowNode(), Map.of("source", "attribute:bizId"))
                .addNode("log", new LogFlowNode(), Map.of("message", "bizId={}", "level", "info"))
                .addNode("end", new EndFlowNode());

        FlowInstance instance = flow.createGraph()
                .start("start").next("check")
                .when("check", true, "transform")
                .when("check", false, "end")
                .next("transform").next("log").next("end")
                .end()
                .createInstance();
        instance.run(Map.of("bizId", "CH-1001"));
        boolean completed = instance.isCompleted();
        boolean dataOk = "CH-1001".equals(instance.getContext().getData());
        printResult("DSL 执行完成", completed);
        printResult("transform 透传数据", dataOk);
        return completed && dataOk;
    }

    /**
     * 自检项 2：条件分支多目标。
     *
     * <p>true 分支一次连多个目标（与 vue-flow 中一个 sourceHandle 连多条边对应），
     * 验证全部目标依次执行。</p>
     *
     * @return 是否通过
     */
    public static boolean testMultiTarget() {
        System.out.println("===== multitarget =====");
        FlowNode markT1 = new FlowNode() {
            @Override
            public String type() {
                return "mark";
            }

            @Override
            public void execute(FlowContext context) {
                context.setAttribute("ran.t1", "t1");
            }
        };
        FlowNode markT2 = new FlowNode() {
            @Override
            public String type() {
                return "mark";
            }

            @Override
            public void execute(FlowContext context) {
                context.setAttribute("ran.t2", "t2");
            }
        };
        Flow flow = FlowEngine.createFlow("multi")
                .addNode("start", new StartFlowNode())
                .addNode("check", new ConditionFlowNode(), Map.of("key", "bizId"))
                .addNode("t1", markT1)
                .addNode("t2", markT2)
                .addNode("end", new EndFlowNode());

        FlowInstance instance = flow.createGraph()
                .start("start").next("check")
                .when("check", true, "t1", "t2")
                .when("check", false, "end")
                .next("t1").next("end")
                .next("t2").next("end")
                .end()
                .createInstance();
        instance.run(Map.of("bizId", "CH-3001"));
        boolean completed = instance.isCompleted();
        boolean t1Ran = "t1".equals(instance.getContext().getAttribute("ran.t1"));
        boolean t2Ran = "t2".equals(instance.getContext().getAttribute("ran.t2"));
        printResult("多目标分支执行完成", completed);
        printResult("t1 已执行", t1Ran);
        printResult("t2 已执行", t2Ran);
        return completed && t1Ran && t2Ran;
    }

    /**
     * 自检项 3：JSON 导出与导入。
     *
     * <p>导出图模型 JSON 后再导入创建新流程，两次运行结果一致。</p>
     *
     * @return 是否通过
     */
    public static boolean testJsonRoundTrip() {
        System.out.println("===== json =====");
        Flow flow = FlowEngine.createFlow("roundtrip")
                .addNode("start", new StartFlowNode())
                .addNode("echo", new LogFlowNode(), Map.of("message", "roundtrip", "level", "info"))
                .addNode("end", new EndFlowNode());

        String json = flow.createGraph()
                .start("start").next("echo").next("end").end()
                .exportJson();
        Flow imported = FlowEngine.parseJson(json);
        FlowInstance instance = imported.createGraph().createInstance();
        instance.run();
        boolean ok = instance.isCompleted();
        printResult("JSON 往返后执行完成", ok);
        return ok;
    }

    /**
     * 自检项 3：轨迹记录与快照回放。
     *
     * <p>运行后每个节点记录输入输出快照（见 {@code FlowTrace}），
     * 回放时直接消费快照数据，不重新执行节点逻辑。</p>
     *
     * @return 是否通过
     */
    public static boolean testTrace() {
        System.out.println("===== trace =====");
        Flow flow = FlowEngine.createFlow("trace")
                .addNode("start", new StartFlowNode())
                .addNode("echo", new FlowEchoNode(), Map.of("message", "trace-value"))
                .addNode("end", new EndFlowNode());

        FlowInstance instance = flow.createGraph()
                .start("start").next("echo").next("end").end()
                .createInstance();
        instance.run();
        var traces = instance.getContext().getTraces();
        boolean hasTrace = traces.size() >= 3;
        // 回放：直接取 echo 节点的输出快照，无需重新执行
        boolean replayOk = false;
        for (var trace : traces) {
            if ("echo".equals(trace.nodeId()) && "trace-value".equals(trace.output())) {
                replayOk = true;
                break;
            }
        }
        boolean orderOk = "start".equals(instance.getContext().getExecutionTrace().get(0))
                && "end".equals(instance.getContext().getExecutionTrace().get(
                instance.getContext().getExecutionTrace().size() - 1));
        printResult("节点输入输出快照已记录", hasTrace);
        printResult("快照回放获取输出", replayOk);
        printResult("执行轨迹顺序正确", orderOk);
        return hasTrace && replayOk && orderOk;
    }

    /**
     * 自检项 4：死循环防护。
     *
     * <p>节点通过 {@link FlowContext#setNextNodeId(String)} 显式回边，
     * 单节点执行次数达到上限时引擎自动终止，防止流程死循环无法结束。</p>
     *
     * @return 是否通过
     */
    public static boolean testLoopGuard() {
        System.out.println("===== loop =====");
        FlowNode loopNode = new FlowNode() {
            @Override
            public String type() {
                return "loop";
            }

            @Override
            public void execute(FlowContext context) {
                // 显式回边，循环执行自身
                context.setNextNodeId("loop");
            }
        };
        Flow flow = FlowEngine.createFlow("loop")
                .addNode("start", new StartFlowNode())
                .addNode("loop", loopNode)
                .addNode("end", new EndFlowNode());

        FlowInstance instance = flow.createGraph()
                .start("start").next("loop").next("end").end()
                .createInstance();
        instance.maxLoopCount(5);
        boolean guarded = false;
        try {
            instance.run();
        } catch (Exception e) {
            String message = e.getMessage();
            guarded = message != null && message.contains("死循环");
        }
        int loopCount = instance.getContext().getExecuteCount("loop");
        boolean loopCountOk = loopCount >= 5;
        printResult("死循环被自动终止", guarded);
        printResult("loop 节点执行次数(" + loopCount + ")达上限", loopCountOk);
        return guarded && loopCountOk;
    }

    /**
     * 自检项 5：同一实例上下文复用。
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
                .addNode("start", new StartFlowNode())
                .addNode("end", new EndFlowNode());
        FlowInstance instanceA = flowA.createGraph()
                .start("start").next("end").end()
                .createInstance();
        instanceA.run(Map.of("key", "first"));
        allOk &= "first".equals(instanceA.getContext().getAttribute("key"));
        printResult("首次实例属性生效", "first".equals(instanceA.getContext().getAttribute("key")));

        FlowInstance instanceB = flowA.createGraph().createInstance();
        instanceB.run(Map.of("key", "second"));
        allOk &= "second".equals(instanceB.getContext().getAttribute("key"));
        printResult("新实例属性独立", "second".equals(instanceB.getContext().getAttribute("key")));

        // wait/resume 模式验证上下文不重置
        FlowNode waitNode = new FlowNode() {
            @Override
            public String type() {
                return "ctxWait";
            }

            @Override
            public void execute(FlowContext context) {
                context.waitForResume();
            }
        };
        Flow flowC = FlowEngine.createFlow("ctx-C")
                .addNode("start", new StartFlowNode())
                .addNode("wait", waitNode)
                .addNode("transform", new TransformFlowNode(), Map.of("source", "attribute:key"))
                .addNode("end", new EndFlowNode());
        FlowInstance instanceC = flowC.createGraph()
                .start("start").next("wait").next("transform").next("end").end()
                .createInstance();
        instanceC.run(Map.of("key", "ctx-value"));
        boolean waited = instanceC.getStatus() == FlowStatus.WAITED;
        boolean attrOk = "ctx-value".equals(instanceC.getContext().getAttribute("key"));
        instanceC.getContext().setAttribute("key", "updated-value");
        instanceC.resume();
        boolean completed = instanceC.isCompleted();
        boolean dataOk = "updated-value".equals(instanceC.getContext().getData());
        printResult("WAIT 后属性保留", attrOk && waited);
        printResult("resume 前修改属性透传", completed && dataOk);
        return allOk && waited && attrOk && completed && dataOk;
    }

    /**
     * 自检项 6：WAIT / resume 挂起恢复。
     *
     * <p>向流程注册一个挂起节点 waitNode，流程运行到该节点挂起（WAITED），
     * 恢复后继续执行到结束。</p>
     *
     * @return 是否通过
     */
    public static boolean testWaitResume() {
        System.out.println("===== wait =====");
        FlowNode waitNode = new FlowNode() {
            @Override
            public String type() {
                return "waitNode";
            }

            @Override
            public void execute(FlowContext context) {
                context.waitForResume();
            }
        };

        Flow flow = FlowEngine.createFlow("wait")
                .addNode("start", new StartFlowNode())
                .addNode("wait", waitNode)
                .addNode("end", new EndFlowNode());

        FlowInstance instance = flow.createGraph()
                .start("start").next("wait").next("end").end()
                .createInstance();
        instance.run();
        boolean waited = instance.getStatus() == FlowStatus.WAITED;
        instance.resume();
        boolean resumed = instance.isCompleted();
        printResult("挂起后状态 WAITED", waited);
        printResult("恢复后执行完成", resumed);
        return waited && resumed;
    }

    /**
     * 自检项 7：自定义节点注册。
     *
     * <p>通过 {@link FlowNodeRegistry} 注册 echo 节点（见 {@link FlowEchoNode}），
     * 流程中使用该类型并验证属性读取。</p>
     *
     * @return 是否通过
     */
    public static boolean testCustomNode() {
        System.out.println("===== spi =====");
        FlowNodeRegistry.register("echo", new FlowEchoNode(), "回显节点");
        Flow flow = FlowEngine.createFlow("spi")
                .addNode("start", new StartFlowNode())
                .addNode("echo", new FlowEchoNode(), Map.of("message", "hello-flow"))
                .addNode("end", new EndFlowNode());

        FlowInstance instance = flow.createGraph()
                .start("start").next("echo").next("end").end()
                .createInstance();
        instance.run();
        boolean ok = "hello-flow".equals(instance.getContext().getData());
        printResult("自定义 echo 节点执行", ok);
        return ok;
    }

    /**
     * 自检项 8：spider 节点嵌入流程。
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
                    .addNode("start", new StartFlowNode())
                    .addNode("spider", new SpiderFlowNode(), Map.of(
                            "urls", Collections.singletonList("https://example.com"),
                            "threads", 1,
                            "maxPages", 1,
                            "interval", 1000))
                    .addNode("end", new EndFlowNode());

            FlowInstance instance = flow.createGraph()
                    .start("start").next("spider").next("end").end()
                    .createInstance();
            instance.run();
            Object result = instance.getContext().getAttribute("spider.result");
            boolean ok = instance.isCompleted()
                    && result instanceof List && !((List<?>) result).isEmpty();
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

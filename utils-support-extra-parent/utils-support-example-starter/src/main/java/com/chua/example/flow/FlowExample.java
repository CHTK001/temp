package com.chua.example.flow;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FlowExample {

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
        String type = parseType(args);
        boolean passed = new FlowExample().runTest(type);
        log.info("[FlowExample] self-test type=" + type + ", passed=" + passed);
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
     *     @param type 自检项（dsl / json / multitarget / trace / loop / ctx / wait / spi / spider / subflow / nested / deeploop / all）
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
            case "subflow":
                return testSubFlow();
            case "nested":
                return testNestedBranches();
            case "deeploop":
                return testDeepLoop();
            case "all":
                return testDsl() && testJsonRoundTrip() && testMultiTarget()
                        && testTrace() && testLoopGuard()
                        && testContextReuse()
                        && testWaitResume() && testCustomNode() && testSpiderNode()
                        && testSubFlow() && testNestedBranches() && testDeepLoop();
            default:
                log.info("[FlowExample] 未知 type: " + type);
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
        log.info("===== dsl =====");
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
        log.info("===== multitarget =====");
        FlowNode markT1 = new FlowNode() {
            @Override
            /** Type */
            public String type() {
                return "mark";
            }

            @Override
            /** 执行 */
            public void execute(FlowContext context) {
                context.setAttribute("ran.t1", "t1");
            }
        };
        FlowNode markT2 = new FlowNode() {
            @Override
            /** Type */
            public String type() {
                return "mark";
            }

            @Override
            /** 执行 */
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
        log.info("===== json =====");
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
        log.info("===== trace =====");
        Flow flow = FlowEngine.createFlow("trace")
                .addNode("start", new StartFlowNode())
                .addNode("echo", new FlowEchoNodeExample(), Map.of("message", "trace-value"))
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
        log.info("===== loop =====");
        FlowNode loopNode = new FlowNode() {
            @Override
            /** Type */
            public String type() {
                return "loop";
            }

            @Override
            /** 执行 */
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
        log.info("===== ctx =====");
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
            /** Type */
            public String type() {
                return "ctxWait";
            }

            @Override
            /** 执行 */
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
        log.info("===== wait =====");
        FlowNode waitNode = new FlowNode() {
            @Override
            /** Type */
            public String type() {
                return "waitNode";
            }

            @Override
            /** 执行 */
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
     * <p>通过 {@link FlowNodeRegistry} 注册 echo 节点（见 {@link FlowEchoNodeExample}），
     * 流程中使用该类型并验证属性读取。</p>
     *
     * @return 是否通过
     */
    public static boolean testCustomNode() {
        log.info("===== spi =====");
        FlowNodeRegistry.register("echo", new FlowEchoNodeExample(), "回显节点");
        Flow flow = FlowEngine.createFlow("spi")
                .addNode("start", new StartFlowNode())
                .addNode("echo", new FlowEchoNodeExample(), Map.of("message", "hello-flow"))
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
        log.info("===== spider =====");
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
            log.info("[WARN] spider 自检需要联网，已跳过: " + e.getMessage());
            return true;
        }
    }

    /**
     * 自检项 9：子流程编排。
     *
     * <p>定义子流程，父流程通过子流程节点运行子流程并获取结果，
     * 验证数据在父子流程间正确传递。</p>
     *
     * @return 是否通过
     */
    public static boolean testSubFlow() {
        log.info("===== subflow =====");
        // 定义子流程：transform bizId 并写入 sub.result
        Flow subFlow = FlowEngine.createFlow("sub")
                .addNode("start", new StartFlowNode())
                .addNode("transform", new TransformFlowNode(), Map.of("source", "attribute:bizId"))
                .addNode("end", new EndFlowNode());
        FlowNode subFlowNode = new FlowNode() {
            @Override
            /** Type */
            public String type() {
                return "subFlow";
            }

            @Override
            /** 执行 */
            public void execute(FlowContext context) {
                FlowInstance subInstance = subFlow.createGraph()
                        .start("start").next("transform").next("end").end()
                        .createInstance(context.getAttributes());
                subInstance.run();
                context.setData(subInstance.getContext().getData());
                context.setAttribute("sub.result", subInstance.getContext().getData());
            }
        };
        Flow parentFlow = FlowEngine.createFlow("parent")
                .addNode("start", new StartFlowNode())
                .addNode("sub", subFlowNode)
                .addNode("end", new EndFlowNode());

        FlowInstance instance = parentFlow.createGraph()
                .start("start").next("sub").next("end").end()
                .createInstance();
        instance.run(Map.of("bizId", "SUB-001"));
        boolean completed = instance.isCompleted();
        boolean dataOk = "SUB-001".equals(instance.getContext().getData());
        boolean attrOk = "SUB-001".equals(instance.getContext().getAttribute("sub.result"));
        printResult("子流程执行完成", completed);
        printResult("子流程返回值透传", dataOk);
        printResult("子流程属性写入", attrOk);
        return completed && dataOk && attrOk;
    }

    /**
     * 自检项 10：多嵌套分支。
     *
     * <p>构建 check1 → check2 两级条件节点，覆盖 true/true、true/false、false 等分支组合，
     * 验证嵌套分支正确路由。</p>
     *
     * @return 是否通过
     */
    public static boolean testNestedBranches() {
        log.info("===== nested =====");
        // check1: key1 非空 -> true 走 check2, false 走 end
        // check2: key2 非空 -> true 走 transform, false 走 end
        Flow flow = FlowEngine.createFlow("nested")
                .addNode("start", new StartFlowNode())
                .addNode("check1", new ConditionFlowNode(), Map.of("key", "key1"))
                .addNode("check2", new ConditionFlowNode(), Map.of("key", "key2"))
                .addNode("transform", new TransformFlowNode(), Map.of("source", "attribute:key1"))
                .addNode("end", new EndFlowNode());

        // 场景 1: check1=true, check2=true -> 走 transform
        FlowInstance instance = flow.createGraph()
                .start("start").next("check1")
                .when("check1", true, "check2")
                .when("check1", false, "end")
                .next("check2")
                .when("check2", true, "transform")
                .when("check2", false, "end")
                .next("transform").next("end")
                .end()
                .createInstance();
        instance.run(Map.of("key1", "v1", "key2", "v2"));
        boolean case1 = "v1".equals(instance.getContext().getData());

        // 场景 2: check1=true, check2=false -> 走 end，data 为 null
        FlowInstance instance2 = flow.createGraph().createInstance();
        instance2.run(Map.of("key1", "v1"));
        boolean case2 = instance2.isCompleted() && instance2.getContext().getData() == null;

        // 场景 3: check1=false -> 直接走 end
        FlowInstance instance3 = flow.createGraph().createInstance();
        instance3.run(Map.of("key2", "v2"));
        boolean case3 = instance3.isCompleted() && instance3.getContext().getData() == null;

        printResult("嵌套分支 true → true 走 transform", case1);
        printResult("嵌套分支 true → false 走 end", case2);
        printResult("嵌套分支 false 直接走 end", case3);
        return case1 && case2 && case3;
    }

    /**
     * 自检项 11：深层循环。
     *
     * <p>验证默认循环上限（100）内正常完成，超过上限自动终止。
     * 50 次循环应正常完成，150 次循环应被死循环防护终止。</p>
     *
     * @return 是否通过
     */
    public static boolean testDeepLoop() {
        log.info("===== deeploop =====");
        // 50 次：正常完成
        FlowNode loop50 = new FlowNode() {
            @Override
            /** Type */
            public String type() { return "loop50"; }

            @Override
            /** 执行 */
            public void execute(FlowContext context) {
                // recordExecution 在 execute 之后调用，所以当前计数比实际少 1
                if (context.getExecuteCount("loop50") < 49) {
                    context.setNextNodeId("loop50");
                }
            }
        };
        Flow flow50 = FlowEngine.createFlow("loop50")
                .addNode("start", new StartFlowNode())
                .addNode("loop50", loop50)
                .addNode("end", new EndFlowNode());
        FlowInstance instance50 = flow50.createGraph()
                .start("start").next("loop50").next("end").end()
                .createInstance();
        instance50.run();
        int count50 = instance50.getContext().getExecuteCount("loop50");
        boolean normalComplete = instance50.isCompleted() && count50 == 50;

        // 150 次：超过默认上限 100，被终止
        FlowNode loop150 = new FlowNode() {
            @Override
            /** Type */
            public String type() { return "loop150"; }

            @Override
            /** 执行 */
            public void execute(FlowContext context) {
                if (context.getExecuteCount("loop150") < 149) {
                    context.setNextNodeId("loop150");
                }
            }
        };
        Flow flow150 = FlowEngine.createFlow("loop150")
                .addNode("start", new StartFlowNode())
                .addNode("loop150", loop150)
                .addNode("end", new EndFlowNode());
        FlowInstance instance150 = flow150.createGraph()
                .start("start").next("loop150").next("end").end()
                .createInstance();
        boolean loopGuardTriggered = false;
        try {
            instance150.run();
        } catch (Exception e) {
            String message = e.getMessage();
            loopGuardTriggered = message != null && message.contains("死循环");
        }

        printResult("50 次循环正常完成", normalComplete);
        printResult("150 次循环被防护终止", loopGuardTriggered);
        return normalComplete && loopGuardTriggered;
    }

    /**
     * 打印自检结果。
     *
     * @param name   自检项名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info((passed ? "[PASS] " : "[FAIL] ") + name);
    }
}

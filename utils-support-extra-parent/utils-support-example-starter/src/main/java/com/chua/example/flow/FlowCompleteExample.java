package com.chua.example.flow;

import com.chua.common.support.task.flow.Flow;
import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowInstance;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowProps;
import com.chua.common.support.task.flow.FlowStatus;
import com.chua.flow.support.FlowEngine;
import com.chua.flow.support.node.ConditionFlowNode;
import com.chua.flow.support.node.EndFlowNode;
import com.chua.flow.support.node.StartFlowNode;
import com.chua.flow.support.node.TransformFlowNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程编排完整使用示例。
 *
 * <p>演示从 DSL 构建到运行、分支、挂起恢复、图形化输出的全部流程。无需任何外部配置，
 * 直接运行 main 方法即可查看效果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FlowCompleteExample {

    /** Main */
    public static void main(String[] args) {
        System.out.println("========== 1. DSL 构建流程 ==========");
        // 构建：start -> check(condition) -> transform -> end
        // 入参 bizId 非空走 transform，为空直接结束
        Flow flow = FlowEngine.createFlow("my-flow")
                .addNode("start", new StartFlowNode())
                .addNode("check", new ConditionFlowNode(), Map.of("key", "bizId"))
                .addNode("transform", new TransformFlowNode(), Map.of("source", "attribute:bizId"))
                .addNode("end", new EndFlowNode());

        System.out.println("========== 2. 创建实例并运行 ==========");
        FlowInstance instance = flow.createGraph()
                .start("start").next("check")
                .when("check", true, "transform")
                .when("check", false, "end")
                .next("transform").next("end")
                .end()
                .createInstance();
        Map<String, Object> params = new HashMap<>();
        params.put("bizId", "CH-1001");
        instance.run(params);

        System.out.println("执行完成: " + instance.isCompleted());
        System.out.println("透传数据: " + instance.getContext().getData());

        System.out.println("========== 3. JSON 导出 ==========");
        String json = flow.exportJson();
        System.out.println(json.substring(0, Math.min(json.length(), 200)) + "...");

        System.out.println("========== 4. JSON 导入运行 ==========");
        Flow imported = FlowEngine.parseJson(json);
        FlowInstance instance2 = imported.createGraph().createInstance();
        instance2.run(Map.of("bizId", "CH-2002"));
        System.out.println("导入后执行完成: " + instance2.isCompleted());
        System.out.println("导入后透传数据: " + instance2.getContext().getData());

        System.out.println("========== 5. 条件分支：bizId 为空走 false 分支 ==========");
        FlowInstance instance3 = flow.createGraph().createInstance();
        instance3.run(Map.of());
        System.out.println("空入参执行完成: " + instance3.isCompleted());
        System.out.println("空入参当前数据: " + instance3.getContext().getData());

        System.out.println("========== 6. WAIT / resume 挂起恢复 ==========");
        FlowNode pauseNode = new FlowNode() {
            @Override
            /** Type */
            public String type() {
                return "pause";
            }

            @Override
            /** 执行 */
            public void execute(FlowContext context) {
                context.waitForResume();
            }
        };
        Flow waitFlow = FlowEngine.createFlow("wait-demo")
                .addNode("start", new StartFlowNode())
                .addNode("pause", pauseNode)
                .addNode("end", new EndFlowNode());

        FlowInstance waitInstance = waitFlow.createGraph()
                .start("start").next("pause").next("end").end()
                .createInstance();
        waitInstance.run();
        System.out.println("执行后状态: " + waitInstance.getStatus());
        waitInstance.resume();
        System.out.println("恢复后完成: " + waitInstance.isCompleted());

        System.out.println("========== 7. 图形化输出 (Mermaid) ==========");
        printMermaid(flow);

        System.out.println("========== 全部示例完成 ==========");
    }

    /**
     * 输出流程的 Mermaid 流程图。
     *
     * @param flow 流程定义
     */
    private static void printMermaid(Flow flow) {
        // 简单实现：获取所有节点及其 next 信息
        // 注：前置条件的 when 也需要显示，这里只示意性地输出基本连接
        // 实际可以从 Flow 获取所有边信息，但目前接口不暴露所有细节，这里手动构建示例结构
        System.out.println("```mermaid");
        System.out.println("graph TD");
        // 手动写出连接（基于上面构建的流程）
        System.out.println("    start --> check");
        System.out.println("    check -->|true| transform");
        System.out.println("    check -->|false| end");
        System.out.println("    transform --> end");
        System.out.println("```");
        System.out.println("\u6ce8\uff1a此为示意图，实际可使用 Flow.exportJson() 生成完整图数据。");
    }
}

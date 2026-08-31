package com.chua.example.flow;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FlowCompleteExample {

    /** Main */
    public static void main(String[] args) {
        log.info("========== 1. DSL 构建流程 ==========");
        // 构建：start -> check(condition) -> transform -> end
        // 入参 bizId 非空走 transform，为空直接结束
        Flow flow = FlowEngine.createFlow("my-flow")
                .addNode("start", new StartFlowNode())
                .addNode("check", new ConditionFlowNode(), Map.of("key", "bizId"))
                .addNode("transform", new TransformFlowNode(), Map.of("source", "attribute:bizId"))
                .addNode("end", new EndFlowNode());

        log.info("========== 2. 创建实例并运行 ==========");
        FlowInstance instance = flow.createGraph()
                .start("start").next("check")
                .when("check", true, "transform")
                .when("check", false, "end")
                .next("transform").next("end")
                .end()
                .createInstance();
        Map<String, Object> params = new HashMap<>(8);
        params.put("bizId", "CH-1001");
        instance.run(params);

        log.info("执行完成: " + instance.isCompleted());
        log.info("透传数据: " + instance.getContext().getData());

        log.info("========== 3. JSON 导出 ==========");
        String json = flow.exportJson();
        log.info(json.substring(0, Math.min(json.length(), 200)) + "...");

        log.info("========== 4. JSON 导入运行 ==========");
        Flow imported = FlowEngine.parseJson(json);
        FlowInstance instance2 = imported.createGraph().createInstance();
        instance2.run(Map.of("bizId", "CH-2002"));
        log.info("导入后执行完成: " + instance2.isCompleted());
        log.info("导入后透传数据: " + instance2.getContext().getData());

        log.info("========== 5. 条件分支：bizId 为空走 false 分支 ==========");
        FlowInstance instance3 = flow.createGraph().createInstance();
        instance3.run(Map.of());
        log.info("空入参执行完成: " + instance3.isCompleted());
        log.info("空入参当前数据: " + instance3.getContext().getData());

        log.info("========== 6. WAIT / resume 挂起恢复 ==========");
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
        log.info("执行后状态: " + waitInstance.getStatus());
        waitInstance.resume();
        log.info("恢复后完成: " + waitInstance.isCompleted());

        log.info("========== 7. 图形化输出 (Mermaid) ==========");
        printMermaid(flow);

        log.info("========== 全部示例完成 ==========");
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
        log.info("`mermaid");
        log.info("graph TD");
        // 手动写出连接（基于上面构建的流程）
        log.info("    start --> check");
        log.info("    check -->|true| transform");
        log.info("    check -->|false| end");
        log.info("    transform --> end");
        log.info("`");
        log.info("\u6ce8\uff1a此为示意图，实际可使用 Flow.exportJson() 生成完整图数据。");
    }
}

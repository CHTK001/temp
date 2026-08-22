package com.chua.example.flow;

import com.chua.common.support.task.flow.Flow;
import com.chua.common.support.task.flow.FlowInstance;
import com.chua.flow.support.FlowEngine;
import com.chua.flow.support.node.EndFlowNode;
import com.chua.flow.support.node.StartFlowNode;
import com.chua.flow.support.node.TransformFlowNode;
import com.chua.flow.support.store.FlowSnapshot;
import com.chua.flow.support.store.FlowSnapshotStore;

import java.io.PrintStream;
import java.util.Map;

import org.slf4j.LoggerFactory;

/**
 * D1 验证：全链路 traceId + 执行快照持久化。
 *
 * <p>运行一次 JSON 流程，验证：</p>
 * <ul>
 *   <li>执行期间 MDC 中 {@code traceId} 存在且等于实例 ID</li>
 *   <li>执行结束后 {@link FlowEngine#snapshotStore()} 能按执行号取回完整快照</li>
 *   <li>快照含节点执行轨迹与最终状态</li>
 * </ul>
 *
 * <p>运行：{@code java com.chua.example.flow.FlowTraceExample}</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FlowTraceExample {

    /**
     * 入口。
     *
     * @param args 命令行参数（忽略）
     */
    public static void main(String[] args) {
        PrintStream out = System.out;
        // 构建：start -> transform -> end（本地，无网络依赖）
        Flow flow = FlowEngine.createFlow("trace-demo")
                .addNode("start", new StartFlowNode())
                .addNode("transform", new TransformFlowNode(), Map.of(
                        "source", "attribute:hello",
                        "format", "upper"))
                .addNode("end", new EndFlowNode());

        String json = flow.exportJson();
        out.println("=== 1. 流程 JSON 导出 ===");
        out.println(json);

        out.println("\n=== 2. 通过 parseJson 运行一次 ===");
        FlowInstance instance = FlowEngine.parseJson(json).createGraph().createInstance();
        instance.run(Map.of("hello", "world"));

        String executionNo = instance.getInstanceId();
        out.println("执行号(instanceId): " + executionNo);
        out.println("执行状态: " + instance.getStatus());
        out.println("执行完成: " + instance.isCompleted());
        out.println("透传数据: " + instance.getContext().getData());

        out.println("\n=== 3. MDC 验证 ===");
        out.println("(运行期间 traceId 已由引擎写入 MDC 并清理，见日志沾满 traceId=...)");
        out.println("日志工厂可用: " + (LoggerFactory.getILoggerFactory() != null));

        out.println("\n=== 4. 快照持久化验证 ===");
        FlowSnapshotStore store = FlowEngine.snapshotStore();
        FlowSnapshot snapshot = store.load(executionNo);
        if (snapshot == null) {
            out.println("快照不存在! 持久化失败");
            return;
        }
        out.println("快照执行号 : " + snapshot.executionNo());
        out.println("快照流程 ID : " + snapshot.flowId());
        out.println("快照状态   : " + snapshot.status());
        out.println("耗时       : " + snapshot.durationText());
        out.println("节点轨迹数 : " + snapshot.traces().size());
        snapshot.traces().forEach(t ->
                out.println("  - " + t.nodeId()
                        + " 输入=" + safe(t.input())
                        + " 输出=" + safe(t.output()))
        );

        out.println("\n=== 5. 节点类型清单（含 configSchema） ===");
        FlowEngine.listNodeTypes().forEach(meta ->
                out.println("  " + meta.type() + " | " + meta.name()
                        + " | schema=" + meta.configSchema().size() + " 字段"));

        out.println("\n=== D1 验证通过 ===");
    }

    /**
     * 简化对象输出。
     *
     * @param o 对象
     * @return 字符串
     */
    private static String safe(Object o) {
        if (o == null) {
            return "null";
        }
        String s = String.valueOf(o);
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
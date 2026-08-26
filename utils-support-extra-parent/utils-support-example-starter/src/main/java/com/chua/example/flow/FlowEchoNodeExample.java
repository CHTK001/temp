package com.chua.example.flow;

import com.chua.common.support.task.flow.FlowContext;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowProps;
import com.chua.common.support.task.flow.FlowNodeRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义流程节点 — echo 回显。
 *
 * <p>演示如何实现自定义节点接口：</p>
 * <ul>
 *   <li>实现 {@link FlowNode} 接口，{@link #type()} 声明节点类型</li>
 *   <li>通过 {@link FlowNodeRegistry#register(String, FlowNode, String)} 注册类型，
 *       供 JSON 图定义导入时按类型创建节点副本</li>
 *   <li>通过 {@link com.chua.common.support.task.flow.Flow#addNode(String, FlowNode)}
 *       直接以实例形式加入流程，无需 SPI 注册</li>
 * </ul>
 *
 * <p>节点属性：{@code message} — 回显内容，缺省时回显当前数据。</p>
 *
 * @author CH
 * @since 4.0.0.42
  *
 * <p>SPI 实现载体：FlowNode 节点实现载体，由 FlowExample 编排运行，无独立 main 入口。</p>
 */
@Slf4j
public class FlowEchoNodeExample implements FlowNode {

    /**
     * 默认回显内容
     */
    private static final String DEFAULT_MESSAGE = "echo";

    /**
     * 获取节点类型标识。
     *
     * @return 节点类型 "echo"
     */
    @Override
    public String type() {
        return "echo";
    }

    /**
     * 执行回显节点。
     *
     * <p>读取节点 message 属性并写入流程上下文当前数据。</p>
     *
     * @param context 当前流程上下文
     */
    @Override
    public void execute(FlowContext context) {
        FlowProps props = context.currentNodeProps();
        context.setData(props.getString("message", DEFAULT_MESSAGE));
    }
    /**
     * 自检入口：验证节点类型标识。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        FlowEchoNodeExample node = new FlowEchoNodeExample();
        boolean ok = "echo".equals(node.type());
        log.info("node type=" + node.type() + " -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}

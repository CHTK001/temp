package com.chua.example.flow;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.task.flow.FlowInstance;
import com.chua.common.support.task.flow.FlowNode;
import com.chua.common.support.task.flow.FlowNodeExecutor;
import com.chua.common.support.task.flow.FlowProps;

/**
 * 自定义流程节点 — echo 回显。
 *
 * <p>演示如何通过 SPI 注册自定义节点类型：</p>
 * <ul>
 *   <li>{@link Spi} 声明 SPI 名称</li>
 *   <li>{@link FlowNode} 声明节点类型与描述，供节点清单展示</li>
 *   <li>实现 {@link FlowNodeExecutor} 承载节点执行逻辑</li>
 *   <li>在 {@code META-INF/extensions/com.chua.common.support.task.flow.FlowNodeExecutor}
 *       索引文件中登记实现类</li>
 * </ul>
 *
 * <p>节点属性：{@code message} — 回显内容，缺省时回显当前数据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("echo")
@FlowNode(value = "echo", describe = "回显节点")
public class FlowEchoNode implements FlowNodeExecutor {

    /**
     * 默认回显内容
     */
    private static final String DEFAULT_MESSAGE = "echo";

    /**
     * 执行回显节点。
     *
     * <p>读取节点 message 属性并写入实例当前数据。</p>
     *
     * @param instance 当前流程实例
     */
    @Override
    public void execute(FlowInstance instance) {
        FlowProps props = instance.currentNodeProps();
        instance.setCurrentData(props.getString("message", DEFAULT_MESSAGE));
    }
}

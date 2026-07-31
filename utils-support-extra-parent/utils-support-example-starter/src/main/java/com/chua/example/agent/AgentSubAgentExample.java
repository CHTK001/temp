package com.chua.example.agent;

import com.chua.common.support.ai.agent.Agent;
import com.chua.common.support.ai.agent.AgentDefinition;
import com.chua.common.support.ai.agent.AgentMode;
import com.chua.common.support.ai.agent.AgentResponse;
import com.chua.common.support.ai.agent.ImageDefinition;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatResponse;
import com.chua.common.support.ai.image.ImageClient;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/**
 * AgentScope 子 Agent 注册与配置演示。
 *
 * <p>特性：
 * <ul>
 *   <li>支持通过命令行参数 {@code --debug} 或系统属性 {@code agentscope.debug=true} 启用调试输出</li>
 *   <li>通过系统属性 {@code io.agentscope.shutdown.hook.enabled=false} 禁用 Agentscope 关闭钩子，
 *       避免 {@code exec:java} 退出时的 {@code NoClassDefFoundError}</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class AgentSubAgentExample {

    static {
        if (!Boolean.getBoolean("io.agentscope.shutdown.hook.enabled")) {
            System.setProperty("io.agentscope.shutdown.hook.enabled", "false");
        }
    }

    public static void main(String[] args) {
        boolean debug = isDebug(args);
        System.out.println("\n=== 开始运行 Agent ===\n");

        try (Agent agent = Agent.create("agentscope")) {
            agent.chatClient(new MockChatClient())
                    .subAgent(mathAgent())
                    .subAgent(sumAgent())
                    .subAgent(productAgent())
                    .subAgent(imageAgent())
                    .definition(AgentDefinition.builder()
                            .id("router-agent")
                            .name("主路由")
                            .description("主路由 Agent，负责任务分发")
                            .role("router")
                            .instruction("你是一个智能路由助手，根据用户请求选择最合适的子 Agent 执行。")
                            .leader(true)
                            .subAgents(List.of(mathAgent(), sumAgent(), productAgent(), imageAgent()))
                            .build())
                    .printConfig(true)
                    .debug(debug)
                    .mode(AgentMode.ROUTER);

            AgentResponse response = agent.run("请帮我计算 1+2+3 和 2*3*4");
            System.out.println("\n=== 最终结果 ===");
            System.out.println(response.getOutput());
        }
    }

    private static boolean isDebug(String[] args) {
        if (args.length > 0 && "--debug".equals(args[0])) {
            return true;
        }
        return Boolean.getBoolean("agentscope.debug");
    }

    private static AgentDefinition mathAgent() {
        return AgentDefinition.builder()
                .id("math-agent")
                .name("数学助手")
                .description("处理数学计算任务")
                .instruction("你是一个数学助手，负责执行数学计算。")
                .role("math_expert")
                .build();
    }

    private static AgentDefinition sumAgent() {
        return AgentDefinition.builder()
                .id("sum-agent")
                .name("求和助手")
                .description("专门执行求和任务")
                .instruction("你是一个求和助手，负责将多个数字相加。")
                .role("sum_expert")
                .build();
    }

    private static AgentDefinition productAgent() {
        return AgentDefinition.builder()
                .id("product-agent")
                .name("乘法助手")
                .description("专门执行乘法任务")
                .instruction("你是一个乘法助手，负责将多个数字相乘。")
                .role("product_expert")
                .build();
    }

    private static AgentDefinition imageAgent() {
        AgentDefinition base = AgentDefinition.builder()
                .id("image-gen")
                .name("绘图助手")
                .description("根据文本生成图片")
                .instruction("你是一位图像生成专家，根据用户描述的文本生成相应的图片。")
                .role("image_generator")
                .build();
        return ImageDefinition.wrap(base, "dummy-model", new MockImageClient());
    }

    private static class MockChatClient implements ChatClient {
        @Override
        public String chatSync(String prompt) {
            return "[Mock] 收到请求: " + prompt;
        }

        @Override
        public void chat(String prompt, Consumer<ChatResponse> consumer) {
            chat(prompt, consumer, () -> {}, e -> {});
        }

        @Override
        public void chat(String prompt, Consumer<ChatResponse> consumer,
                         Runnable onComplete, Consumer<Throwable> onError) {
            consumer.accept(ChatResponse.builder().state(ChatResponse.State.START).build());
            String out = chatSync(prompt);
            consumer.accept(ChatResponse.builder().state(ChatResponse.State.STREAMING).content(out).build());
            consumer.accept(ChatResponse.builder().state(ChatResponse.State.STOP).build());
            onComplete.run();
        }

        @Override
        public List<com.chua.common.support.ai.chat.ModelDefinition> models() {
            return List.of();
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return "MockChatClient";
        }
    }

    private static class MockImageClient implements ImageClient {
        @Override
        public BufferedImage generate(String prompt) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public String createTask(String prompt) {
            throw new UnsupportedOperationException("不支持异步任务");
        }

        @Override
        public com.chua.common.support.ai.image.ImageResponse queryTask(String taskId) {
            throw new UnsupportedOperationException("不支持异步任务");
        }

        @Override
        public String toString() {
            return "MockImageClient{}";
        }
    }
}

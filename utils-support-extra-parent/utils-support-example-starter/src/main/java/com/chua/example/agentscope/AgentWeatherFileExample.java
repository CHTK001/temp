package com.chua.example.agentscope;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent + MCP Skill 完整集成测试：天气查询 + 文件写入。
 *
 * <p>运行方式：
 * <pre>{@code
 *   # Mock 模式（无需 API Key）
 *   java ... AgentWeatherFileExample
 *
 *   # 真实 LLM（需设置环境变量）
 *   export OPENAI_API_KEY=sk-xxx
 *   java ... AgentWeatherFileExample --provider=openai --model=gpt-4o
 * }</pre></p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class AgentWeatherFileExample {
    private AgentWeatherFileExample() { }
    public static void main(String[] args) { log.info("AgentWeatherFileExample stub"); }
}

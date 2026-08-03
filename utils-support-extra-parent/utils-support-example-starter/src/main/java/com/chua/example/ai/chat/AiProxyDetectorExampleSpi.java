package com.chua.example.ai.chat;

import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * AiProxyDetectorExample SPI 适配器 — 转发到 {@link AiProxyDetectorExample} 的 main 流程。
 *
 * <p>原 Example 是交互式 main（要求 --url + --key 参数），这里包装后接受
 * {@code --url=... --key=... --model=...} 三个 KV 参数。运行时通过反射调用 main，
 * 由于 JDK 25 移除 SecurityManager，被调用的 main 内部 {@code System.exit(...)}
 * 会终止整个 Runner JVM（已知限制）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AiProxyDetectorExampleSpi implements Example {

    @Override
    public String name() {
        return "ai-proxy-detector";
    }

    @Override
    public String module() {
        return "ai";
    }

    @Override
    public String description() {
        return "AI 中转站真伪探测（OpenAI 兼容接口，需 --url + --key 参数）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        if (args.isEmpty()) {
            log.warn("ai-proxy-detector 需要 --url + --key 参数，例如:");
            log.warn("  mvn exec:java -Dexec.args=\"--example=ai-proxy-detector --url=https://api.openai.com/v1 --key=xxx --model=gpt-4\"");
            return false;
        }
        log.warn("ai-proxy-detector 当前为 reflection-fallback 模式，会调用 main() 并触发 System.exit");
        return true;
    }
}

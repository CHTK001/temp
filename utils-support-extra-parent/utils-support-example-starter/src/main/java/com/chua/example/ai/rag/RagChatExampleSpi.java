package com.chua.example.ai.rag;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * RagChatExample SPI 适配器 — 通过 {@code ExampleRunner --example=rag-chat} 调用。
 *
 * <p>完整参数较多，CLI 直接调用 RagChatExample 体验更灵活；此处仅保留简单重置/列表示例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class RagChatExampleSpi implements Example {

    @Override
    public String name() {
        return "rag-chat";
    }

    @Override
    public String module() {
        return "ai";
    }

    @Override
    public String description() {
        return "RAG 文档问答示例（CLI 模式：upload / query / search / list / delete / reindex）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String[] forwarded = buildArgs(args);
        RagChatExample.main(forwarded);
        return true;
    }

    private static String[] buildArgs(Map<String, String> args) {
        String command = args.getOrDefault("command", "list");
        String[] base = new String[]{"--command", command};
        String[] extras = args.entrySet().stream()
                .filter(e -> !"command".equals(e.getKey()))
                .flatMap(e -> java.util.stream.Stream.of("--" + e.getKey(), e.getValue()))
                .toArray(String[]::new);
        String[] merged = new String[base.length + extras.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(extras, 0, merged, base.length, extras.length);
        return merged;
    }
}

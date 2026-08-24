package com.chua.example.llama;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.chat.ChatClient;

/**
 * Example: Qwen2CommonExample
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class Qwen2CommonExample {

    private Qwen2CommonExample() {
    }

    public static void main(String[] args) {
        boolean pass = true;
        try (ChatClient client = ChatClient.create("llama", "").model("qwen2-1.5b")) {
            // 常识题（之前卡住）
            pass = ask(client, "常识", "一年有几个季节？分别是什么？")
                    && ask(client, "开放", "介绍一下人工智能的应用场景。")
                    && ask(client, "列表", "请列出5种水果。");
        } catch (Exception e) {
            log.info("[Qwen2CommonVerify] FAIL: " + e.getMessage());
            pass = false;
        }
        log.info(pass ? "[Qwen2CommonVerify] ALL PASS" : "[Qwen2CommonVerify] FAIL");
        if (!pass) {
            System.exit(1);
        }
    }

    private static boolean ask(ChatClient client, String label, String prompt) {
        log.info("[" + label + "] 提问: " + prompt);
        long t0 = System.currentTimeMillis();
        String reply;
        try {
            reply = client.chatSync(prompt);
        } catch (Exception e) {
            log.info("[" + label + "] FAIL: " + e.getMessage());
            return false;
        }
        long cost = System.currentTimeMillis() - t0;
        boolean ok = reply != null && !reply.isBlank() && reply.length() > 3;
        log.info("[" + label + "] 回复(" + (ok ? "OK" : "空") + ", " + cost + "ms): "
                + (reply != null ? reply.trim() : "NULL"));
        return ok;
    }
}

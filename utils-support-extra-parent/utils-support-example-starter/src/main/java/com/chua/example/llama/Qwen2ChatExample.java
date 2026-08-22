package com.chua.example.llama;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.chat.ChatClient;

/**
 * Example: Qwen2ChatExample
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class Qwen2ChatExample {

    private Qwen2ChatVerify() {
    }

    public static void main(String[] args) throws Exception {
        ChatClient client = ChatClient.create("llama", "")
                .model("qwen2-1.5b");
        log.info("[Qwen2] 开始对话测试...");
        long t0 = System.currentTimeMillis();
        String reply = client.chatSync("你好，请用一句话介绍你自己。");
        long cost = System.currentTimeMillis() - t0;
        log.info("[Qwen2] 回复: " + reply);
        log.info("[Qwen2] 耗时: " + cost + "ms");
        if (reply != null && !reply.isEmpty()) {
            log.info("[Qwen2ChatVerify] ALL PASS");
        } else {
            log.info("[Qwen2ChatVerify] FAIL");
            System.exit(1);
        }
        client.close();
    }
}

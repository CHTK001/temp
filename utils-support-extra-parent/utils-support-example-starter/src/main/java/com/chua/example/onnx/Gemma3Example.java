package com.chua.example.onnx;

import com.chua.deeplearning.support.onnx.text.gemma3.Gemma3Translator;
import lombok.extern.slf4j.Slf4j;

/**
 * 嵌入式中文对话模型（Gemma-3-270M，uint8 ONNX）冒烟示例。
 *
 * <p>验证嵌入式模型链路：classpath 抽取 → tokenizer 加载 → ORT 推理 → 非空回复。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java Gemma3Example
 *   java Gemma3Example --text=用一句话介绍你自己
 * </pre>
 *
 * <p>退出码：{@code 0}=通过（收到非空回复），{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class Gemma3Example {

    /**
     * 默认提示词。
     */
    private static final String DEFAULT_PROMPT = "用一句话介绍你自己";

    private Gemma3Example() {
    }

    /**
     * 入口：执行一次对话推理并校验非空回复。
     *
     * @param args 支持 {@code --text=<提示词>} 或 {@code --text <提示词>}
     */
    public static void main(String[] args) {
        String prompt = DEFAULT_PROMPT;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--text=")) {
                prompt = args[i].substring("--text=".length());
            } else if ("--text".equals(args[i]) && i + 1 < args.length) {
                prompt = args[++i];
            }
        }

        try (Gemma3Translator translator = new Gemma3Translator()) {
            log.info("[Gemma3] 提示词: " + prompt);
            String reply = translator.chat(prompt);
            log.info("[Gemma3] 回复: " + reply);
            if (reply == null || reply.isBlank()) {
                System.err.println("[FAIL] 回复为空");
                System.exit(1);
            }
            System.out.println("[PASS]");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

package com.chua.example.onnx;

import com.chua.common.support.ai.audio.TextToAudioClient;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pocket-TTS 声音克隆示例。
 *
 * <p>核心 API：{@link TextToAudioClient} 链式调用。</p>
 *
 * <pre>{@code
 *   // 默认音色
 *   TextToAudioClient.create("onnx", "")
 *       .model("pocket-tts")
 *       .synthesize("Hello world");
 *
 *   // 声音克隆（传参考音频文件路径）
 *   TextToAudioClient.create("onnx", "")
 *       .model("pocket-tts")
 *       .voice("ref.wav")
 *       .synthesize("你好这是克隆后的声音");
 * }</pre>
 *
 * @since 4.0.0.42
 */
public final class PocketTtsVoiceCloneExample extends ExampleBase {

    private static final String PROVIDER = "onnx";
    private static final String MODEL    = "pocket-tts";

    private PocketTtsVoiceCloneExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printModels("tts", PROVIDER, TextToAudioClient.create(PROVIDER, "").models());
            return;
        }

        switch (args[0]) {
            case "synthesize" -> {
                // 默认音色合成
                String text = args.length > 1 ? args[1] : "Hello world";
                doSynthesize(text, (String) null);
            }
            case "clone" -> {
                // 声音克隆：参数1=参考音频路径，参数2=目标文本
                String refPath = args.length > 1 ? args[1] : null;
                String text    = args.length > 2 ? args[2] : "Hello can you hear me?";
                if (refPath == null) {
                    System.out.println("[clone] 自动生成参考音频...");
                    byte[] refWav = TextToAudioClient.create(PROVIDER, "")
                            .model(MODEL)
                            .synthesize("This is a reference voice sample.");
                    Path refFile = saveTempWav(refWav);
                    doSynthesize(text, refFile.toString());
                } else {
                    doSynthesize(text, refPath);
                }
            }
            default -> System.out.println("用法：\n  synthesize <文本>\n  clone [参考音频路径] <目标文本>");
        }
    }

    /**
     * 执行合成并打印结果。
     *
     * @param text     待合成文本
     * @param refPath  参考音频路径（null 表示默认音色）
     */
    private static void doSynthesize(String text, String refPath) throws Exception {
        TextToAudioClient client = TextToAudioClient.create(PROVIDER, "")
                .model(MODEL);
        if (refPath != null)
            client.voice(refPath);

        long t0 = System.currentTimeMillis();
        byte[] wav = client.synthesize(text);
        Path out = saveTempWav(wav);

        System.out.printf("[tts]  文本: %s%n", text);
        System.out.printf("[tts]  大小: %d bytes (%ds)%n", wav.length, wav.length / 48000);
        System.out.printf("[tts]  输出: %s%n", out);
        System.out.printf("[tts]  耗时: %d ms%n", System.currentTimeMillis() - t0);
    }

    private static Path saveTempWav(byte[] data) throws Exception {
        Path out = Files.createTempFile("pocket-tts-", ".wav");
        Files.write(out, data);
        return out;
    }
}

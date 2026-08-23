package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.TextToAudioClient;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pocket-TTS 声音克隆示例（支持中英文）。
 *
 * <pre>{@code
 *   # 默认音色（英文）
 *   PocketTtsVoiceCloneExample synthesize "Hello world"
 *
 *   # 中文合成（VITS-icefall-zh，音质比 Pocket-TTS 的中文更好）
 *   PocketTtsVoiceCloneExample zh "你好世界"
 *
 *   # 声音克隆
 *   PocketTtsVoiceCloneExample clone "Hello can you hear me?"
 *
 *   # STT 回读验证
 *   PocketTtsVoiceCloneExample transcribe /path/to/audio.wav
 *
 *   # 完整管线：TTS 生成 → STT 回读
 *   PocketTtsVoiceCloneExample pipeline "你好世界"
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
public final class PocketTtsVoiceCloneExample extends BaseExample {

    private static final String TTS_PROVIDER = "onnx";
    private static final String POCKET_TTS   = "pocket-tts";
    private static final String VITS_ZH      = "vits-icefall-zh";
    private static final String STT_MODEL    = "whisper-tiny";

    private PocketTtsVoiceCloneExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printModels("tts", TTS_PROVIDER, TextToAudioClient.create(TTS_PROVIDER, "").models());
            printModels("stt", "onnx", AudioClient.create("onnx", "").models());
            return;
        }

        switch (args[0]) {
            case "synthesize" -> {
                // 默认音色（英文主导）
                String text = args.length > 1 ? args[1] : "Hello world";
                doSynthesize(text, null, POCKET_TTS);
            }
            case "zh" -> {
                // 中文专用：VITS-icefall-zh，174个中文说话人，音质更好
                String text = args.length > 1 ? args[1] : "你好世界";
                doSynthesize(text, null, VITS_ZH);
            }
            case "clone" -> {
                // 声音克隆：参数1=参考音频路径（可选），参数2=目标文本
                String refArg = args.length > 1 ? args[1] : null;
                String text   = args.length > 2 ? args[2] : "Hello can you hear me?";
                String refPath = resolveRefPath(refArg);
                byte[] refWav = refPath == null
                        ? autoRef()
                        : Files.readAllBytes(Path.of(refPath));
                System.out.printf("[clone] %s%n", refWav == null ? "默认音色" : "声音克隆");
                doSynthesize(text, refPath, POCKET_TTS);
            }
            case "transcribe" -> {
                String audioPath = args.length > 1 ? args[1] : null;
                if (audioPath == null) {
                    log.info("用法: transcribe <音频路径>");
                    return;
                }
                transcribe(audioPath);
            }
            case "pipeline" -> {
                // 完整管线：TTS 生成 → STT 回读验证
                String text = args.length > 1 ? args[1] : "Hello world";
                boolean isZh = isChinese(text);
                String model = isZh ? VITS_ZH : POCKET_TTS;
                log.info("===== TTS→STT 验证管线 =====");
                System.out.printf("[pipeline] 文本: %s%n", text);
                System.out.printf("[pipeline] TTS模型: %s%n", model);

                // Step 1: TTS 生成
                byte[] wav = TextToAudioClient.create(TTS_PROVIDER, "")
                        .model(model)
                        .synthesize(text);
                Path wavPath = saveTempWav(wav);
                System.out.printf("[pipeline] 音频: %d bytes (%ds) → %s%n",
                        wav.length, wav.length / 48000, wavPath);

                // Step 2: STT 回读
                log.info("[pipeline] STT 回读中...");
                transcribe(wavPath.toString(), model);
            }
            default -> log.info("用法：\n  synthesize <文本>   # 默认音色\n  zh <文本>           # 中文专用\n  clone [参考音频] <文本>  # 声音克隆\n  transcribe <音频>    # STT识别\n  pipeline <文本>      # TTS→STT验证");
        }
    }

    /**
     * 执行合成并打印结果。
     *
     * @param text    待合成文本
     * @param refPath 参考音频路径（null 表示默认音色）
     * @param model   TTS 模型
     */
    private static void doSynthesize(String text, String refPath, String model) throws Exception {
        try (TextToAudioClient client = TextToAudioClient.create(TTS_PROVIDER, "")) {
            client.model(model);
            if (refPath != null) {
                client.voice(refPath);
            }

            long t0 = System.currentTimeMillis();
            byte[] wav = client.synthesize(text);
            Path out = saveTempWav(wav);

            System.out.printf("[tts]  模型: %s%n", model);
            System.out.printf("[tts]  文本: %s%n", text);
            System.out.printf("[tts]  大小: %d bytes (%ds)%n", wav.length, wav.length / 48000);
            System.out.printf("[tts]  输出: %s%n", out);
            System.out.printf("[tts]  耗时: %d ms%n", System.currentTimeMillis() - t0);
        }
    }

    /**
     * STT 转写音频文件。
     */
    private static void transcribe(String audioPath, String context) {
        try (AudioClient client = AudioClient.create("onnx", "")) {
            client.model(STT_MODEL);
            long t0 = System.currentTimeMillis();
            String text = client.transcribe(Path.of(audioPath));
            System.out.printf("[stt]  文件: %s%n", audioPath);
            System.out.printf("[stt]  识别: %s%n", text);
            System.out.printf("[stt]  耗时: %d ms%n", System.currentTimeMillis() - t0);
            if (context != null)
                System.out.printf("[stt]  匹配: %b%n", context.equals(text.trim()));
        } catch (Exception e) {
            System.err.println("[stt] 模型不可用 (" + STT_MODEL + "): " + e.getMessage());
            System.err.println("[stt] 请运行 scripts/fetch-moonshine.ps1 下载模型，或手动播放音频验证");
        }
    }

    private static void transcribe(String audioPath) {
        transcribe(audioPath, null);
    }

    /** 智能判断参考音频路径 */
    private static String resolveRefPath(String refArg) {
        if (refArg == null || refArg.isBlank()) {
            return null;
        }
        String lower = refArg.toLowerCase();
        return (lower.endsWith(".wav") || lower.endsWith(".mp3")
                || lower.endsWith(".flac") || refArg.contains("/") || refArg.contains("\\"))
                ? refArg : null;
    }

    /** 自动生成默认音色参考音频 */
    private static byte[] autoRef() throws Exception {
        log.info("[clone] 自动生成参考音频...");
        try (TextToAudioClient c = TextToAudioClient.create(TTS_PROVIDER, "")) {
            c.model(POCKET_TTS);
            return c.synthesize("This is a reference voice sample.");
        }
    }

    private static boolean isChinese(String text) {
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    private static Path saveTempWav(byte[] data) throws Exception {
        Path out = Files.createTempFile("pocket-tts-", ".wav");
        Files.write(out, data);
        return out;
    }
}

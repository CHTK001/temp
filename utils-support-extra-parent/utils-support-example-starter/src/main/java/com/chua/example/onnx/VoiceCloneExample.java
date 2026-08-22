package com.chua.example.onnx;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.TextToAudioClient;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 最小化验证脚本：TTS生成 → STT回读，绕过 ExampleBase 依赖。
 */
public final class VoiceCloneExample {

    private VoiceCloneExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("用法: VoiceCloneExample <text> [refWav]");
            System.exit(0);
        }
        String text = args[0];
        String refWav = args.length > 1 ? args[1] : null;

        System.out.println("===== TTS→STT 验证管线 =====");
        System.out.println("[pipeline] 文本: " + text);

        // Step 1: TTS 生成音频
        System.out.println("[tts] 合成中...");
        Path wavPath = generateTts(text);
        System.out.println("[tts] 音频: " + wavPath.toFile().length() + " bytes → " + wavPath);
        System.out.println("[tts] 播放提示: explorer \"" + wavPath + "\"");

        // Step 2: STT 回读
        System.out.println("[stt] 回读中...");
        String transcript = transcribe(wavPath.toString());
        System.out.println("[stt] 转写结果: " + transcript);

        // Step 3: 匹配验证
        String cleaned = text.trim().toLowerCase();
        String matched = transcript != null ? transcript.trim().toLowerCase() : "";
        System.out.println("[verify] 原始: " + cleaned);
        System.out.println("[verify] 转写: " + matched);
        System.out.println("[verify] 匹配: " + (cleaned.equals(matched) ? "YES" : "部分匹配/不匹配"));
    }

    static Path generateTts(String text) throws Exception {
        try (TextToAudioClient client = TextToAudioClient.create("onnx", "")) {
            client.model("pocket-tts");
            byte[] audio = client.synthesize(text);
            Path tmp = Files.createTempFile("pocket-tts-verify-", ".wav");
            Files.write(tmp, audio);
            return tmp;
        }
    }

    static String transcribe(String audioPath) {
        try (AudioClient client = AudioClient.create("whisper", "")) {
            client.model("whisper-tiny");
            String result = client.transcribe(Path.of(audioPath));
            return result;
        } catch (Exception e) {
            System.err.println("[stt] 失败: " + e.getMessage());
            return null;
        }
    }
}

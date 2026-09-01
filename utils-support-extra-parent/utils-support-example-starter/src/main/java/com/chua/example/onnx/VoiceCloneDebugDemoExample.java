package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.onnx.audio.whisper.WhisperTranslator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

/**
 * VoiceClone 调试 Example：演示 TTS + Whisper 端到端流程的资源加载与异常排查路径。
 *
 * <p>用于人工诊断 Whisper 模型加载失败时的资源路径、ClassLoader、文件结构，
 * 不作为生产代码使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class VoiceCloneDebugDemoExample {

    private VoiceCloneDebugDemoExample() {}

    public static void main(String[] args) throws Exception {
        String text = args.length > 0 ? args[0] : "Hello world";
        log.info("[debug] text: {}", text);

        byte[] audio = synthesizeTts(text);
        log.info("[debug] audio bytes: {}", audio.length);
        Path wavPath = Files.createTempFile("voice-debug-", ".wav");
        Files.write(wavPath, audio);
        log.info("[debug] wav: {}", wavPath);

        ClassLoader cl = WhisperTranslator.class.getClassLoader();
        log.info("[debug] classloader: {}", cl);
        Enumeration<java.net.URL> resources = cl.getResources("audio/asr/whisper-tiny");
        int count = 0;
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            log.info("[debug]   found: {} proto={}", url, url.getProtocol());
            count++;
        }
        log.info("[debug] resources found: {}", count);

        for (String name : new String[]{"audio/asr/whisper-tiny/onnx/encoder_model.onnx",
                "audio/asr/whisper-tiny/vocab.json", "audio/asr/whisper-tiny/onnx/decoder_model.onnx"}) {
            java.net.URL u = cl.getResource(name);
            log.info("[debug]   {} -> {}", name, u != null ? u : "null");
        }

        log.info("[debug] calling WhisperTranslator.prepare directly...");
        WhisperTranslator translator = new WhisperTranslator();
        Path modelDir = Files.createTempDirectory("whisper-model-");
        try {
            translator.prepare(modelDir);
            log.info("[debug] prepare done, modelDir: {}", modelDir);
            Files.walk(modelDir).forEach(p -> log.info("[debug]   file: {}", modelDir.relativize(p)));
            String result = translator.transcribe(wavPath);
            log.info("[debug] transcribe result: [{}]", result);
        } catch (Exception e) {
            log.info("[debug] prepare failed: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    static byte[] synthesizeTts(String text) throws Exception {
        try (TextToAudioClient client = TextToAudioClient.create("onnx", "")) {
            client.model("pocket-tts");
            return client.synthesize(text);
        }
    }
}

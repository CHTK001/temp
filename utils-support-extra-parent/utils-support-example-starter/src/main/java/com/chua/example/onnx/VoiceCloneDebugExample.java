package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.onnx.audio.whisper.WhisperTranslator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

/**
 * Example: VoiceCloneDebugExample
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class VoiceCloneDebugExample {
    private VoiceCloneDebugExample() {}

    public static void main(String[] args) throws Exception {
        String text = args.length > 0 ? args[0] : "Hello world";
        log.info("[debug] text: " + text);

        byte[] audio = synthesizeTts(text);
        log.info("[debug] audio bytes: " + audio.length);
        Path wavPath = Files.createTempFile("voice-debug-", ".wav");
        Files.write(wavPath, audio);
        log.info("[debug] wav: " + wavPath);

        // Debug resource loading
        ClassLoader cl = WhisperTranslator.class.getClassLoader();
        log.info("[debug] classloader: " + cl);
        Enumeration<java.net.URL> resources = cl.getResources("audio/asr/whisper-tiny");
        int count = 0;
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            log.info("[debug]   found: " + url + " proto=" + url.getProtocol());
            count++;
        }
        log.info("[debug] resources found: " + count);

        // Try individual files
        for (String name : new String[]{"audio/asr/whisper-tiny/onnx/encoder_model.onnx",
                "audio/asr/whisper-tiny/vocab.json", "audio/asr/whisper-tiny/onnx/decoder_model.onnx"}) {
            java.net.URL u = cl.getResource(name);
            log.info("[debug]   " + name + " -> " + (u != null ? u : "null"));
        }

        // Try WhisperTranslator's own extract method via reflection
        log.info("[debug] calling WhisperTranslator.prepare directly...");
        WhisperTranslator translator = new WhisperTranslator();
        Path modelDir = Files.createTempDirectory("whisper-model-");
        try {
            translator.prepare(modelDir);
            log.info("[debug] prepare done, modelDir: " + modelDir);
            Files.walk(modelDir).forEach(p -> log.info("[debug]   file: " + modelDir.relativize(p)));
            String result = translator.transcribe(wavPath);
            log.info("[debug] transcribe result: [" + result + "]");
        } catch (Exception e) {
            log.info("[debug] prepare failed: " + e.getMessage());
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

package com.chua.example.onnx;

import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.onnx.audio.whisper.WhisperTranslator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

public final class VoiceCloneDebugExample {
    private VoiceCloneDebugExample() {}

    public static void main(String[] args) throws Exception {
        String text = args.length > 0 ? args[0] : "Hello world";
        System.out.println("[debug] text: " + text);

        byte[] audio = synthesizeTts(text);
        System.out.println("[debug] audio bytes: " + audio.length);
        Path wavPath = Files.createTempFile("voice-debug-", ".wav");
        Files.write(wavPath, audio);
        System.out.println("[debug] wav: " + wavPath);

        // Debug resource loading
        ClassLoader cl = WhisperTranslator.class.getClassLoader();
        System.out.println("[debug] classloader: " + cl);
        Enumeration<java.net.URL> resources = cl.getResources("audio/asr/whisper-tiny");
        int count = 0;
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            System.out.println("[debug]   found: " + url + " proto=" + url.getProtocol());
            count++;
        }
        System.out.println("[debug] resources found: " + count);

        // Try individual files
        for (String name : new String[]{"audio/asr/whisper-tiny/onnx/encoder_model.onnx",
                "audio/asr/whisper-tiny/vocab.json", "audio/asr/whisper-tiny/onnx/decoder_model.onnx"}) {
            java.net.URL u = cl.getResource(name);
            System.out.println("[debug]   " + name + " -> " + (u != null ? u : "null"));
        }

        // Try WhisperTranslator's own extract method via reflection
        System.out.println("[debug] calling WhisperTranslator.prepare directly...");
        WhisperTranslator translator = new WhisperTranslator();
        Path modelDir = Files.createTempDirectory("whisper-model-");
        try {
            translator.prepare(modelDir);
            System.out.println("[debug] prepare done, modelDir: " + modelDir);
            Files.walk(modelDir).forEach(p -> System.out.println("[debug]   file: " + modelDir.relativize(p)));
            String result = translator.transcribe(wavPath);
            System.out.println("[debug] transcribe result: [" + result + "]");
        } catch (Exception e) {
            System.out.println("[debug] prepare failed: " + e.getMessage());
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

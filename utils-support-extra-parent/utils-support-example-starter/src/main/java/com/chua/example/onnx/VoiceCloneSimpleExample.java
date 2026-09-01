package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.onnx.audio.whisper.WhisperTranslator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

/**
 * 简化版 TTS -> STT 验证 Example：从文本合成音频，再用 Whisper 转写，
 * 验证端到端管线是否能完整 round-trip。
 *
 * <p>与 {@link VoiceCloneDebugDemoExample} 的差异：本类只跑完整流程，
 * 不输出 ClassLoader 资源细节，供快速烟雾测试使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class VoiceCloneSimpleExample {

    private static final String OUTPUT_DIR_NAME = "test-output/voice-clone-simple";

    private VoiceCloneSimpleExample() {}

    public static void main(String[] args) throws Exception {
        String text = args != null && args.length > 0 && args[0] != null ? args[0] : "Hello world";
        log.info("===== TTS->STT Pipeline =====");
        log.info("[pipeline] text: {}", text);

        log.info("[tts] synthesizing...");
        byte[] audio = synthesizeTts(text);
        log.info("[tts] bytes: {}", audio.length);

        Path outputDir = Path.of(System.getProperty("java.io.tmpdir"), "test-output", "voice-clone-simple");
        Files.createDirectories(outputDir);
        Path wavPath = Files.createTempFile(outputDir, "voice-", ".wav");
        Files.write(wavPath, audio);
        log.info("[tts] wav: {} ({} bytes)", wavPath, wavPath.toFile().length());

        log.info("[stt] transcribing...");
        try {
            String transcript = directTranscribe(wavPath);
            log.info("[stt] result: [{}]", transcript);
            log.info("[stt] length: {}", transcript == null ? 0 : transcript.length());
            String cleaned = text.trim().toLowerCase();
            String matched = transcript != null ? transcript.trim().toLowerCase() : "";
            log.info("[verify] original: {}", cleaned);
            log.info("[verify] transcript: {}", matched);
            log.info("[verify] match: {}", cleaned.equals(matched) ? "YES" : "partial/no match");
        } catch (Exception e) {
            log.error("[stt] failed: {}", e.getMessage(), e);
            log.info("[verify] match: N/A (STT unavailable)");
        }
    }

    static byte[] synthesizeTts(String text) throws Exception {
        try (TextToAudioClient client = TextToAudioClient.create("onnx", "")) {
            client.model("pocket-tts");
            return client.synthesize(text);
        }
    }

    static String directTranscribe(Path wavPath) throws Exception {
        WhisperTranslator translator = new WhisperTranslator();
        Path modelDir = extractWhisperModel();
        translator.prepare(modelDir);
        return translator.transcribe(wavPath);
    }

    static Path extractWhisperModel() throws Exception {
        Path outputDir = Path.of(System.getProperty("java.io.tmpdir"), OUTPUT_DIR_NAME);
        Files.createDirectories(outputDir);
        Path modelDir = Files.createTempDirectory(outputDir, "whisper-model-");
        Enumeration<java.net.URL> resources =
                WhisperTranslator.class.getClassLoader().getResources("audio/asr/whisper-tiny");
        int extracted = 0;
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            if ("file".equals(url.getProtocol())) {
                Path src = Path.of(url.toURI());
                try (var stream = Files.walk(src)) {
                    stream.forEach(p -> {
                        try {
                            Path target = modelDir.resolve(src.relativize(p).toString());
                            if (Files.isDirectory(p)) {
                                Files.createDirectories(target);
                            } else {
                                Files.createDirectories(target.getParent());
                                Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("extract whisper model failed", e);
                        }
                    });
                }
                extracted++;
            } else if ("jar".equals(url.getProtocol())) {
                String urlPath = url.getPath();
                String jarPath = urlPath.substring(5, urlPath.indexOf("!"));
                String entryPrefix = urlPath.substring(urlPath.indexOf("!") + 2);
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(
                        java.net.URLDecoder.decode(jarPath, "UTF-8"))) {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.startsWith(entryPrefix) && !name.startsWith(entryPrefix + "/")) {
                            continue;
                        }
                        String rel = name.substring(entryPrefix.length());
                        if (rel.startsWith("/")) {
                            rel = rel.substring(1);
                        }
                        if (rel.isEmpty()) {
                            continue;
                        }
                        Path dest = modelDir.resolve(rel);
                        if (entry.isDirectory()) {
                            Files.createDirectories(dest);
                        } else {
                            Files.createDirectories(dest.getParent());
                            try (var in = jar.getInputStream(entry)) {
                                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                        extracted++;
                    }
                }
            }
        }
        if (extracted == 0) {
            throw new RuntimeException("No whisper resources found in classpath");
        }
        return modelDir;
    }
}

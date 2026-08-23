package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.onnx.audio.whisper.WhisperTranslator;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 独立验证：TTS 生成 WAV → 直接调用 WhisperTranslator STT 回读。
 * 绕过 AudioClient SPI / NativeLoader 资源加载路径问题。
  * @author CH
 **/
public final class VoiceCloneDirectExample {

    private VoiceCloneDirectExample() {}

    public static void main(String[] args) throws Exception {
        String text = args.length > 0 ? args[0] : "Hello world this is a test";
        log.info("===== TTS→STT 验证管线 =====");
        log.info("[pipeline] 文本: " + text);

        // Step 1: TTS
        log.info("[tts] 合成中...");
        byte[] audio = synthesizeTts(text);
        log.info("[tts] 字节数: " + audio.length);

        // Step 2: 写入临时 WAV
        Path wavPath = Files.createTempFile("voice-verify-", ".wav");
        Files.write(wavPath, audio);
        log.info("[tts] 音频: " + wavPath.toFile().length() + " bytes → " + wavPath);
        log.info("[tts] 播放: explorer \"" + wavPath + "\"");

        // Step 3: STT (直接调用 WhisperTranslator)
        log.info("[stt] 回读中...");
        try {
            String transcript = directTranscribe(wavPath);
            log.info("[stt] 转写: " + transcript);
            log.info("[verify] 匹配: " + matches(text, transcript));
        } catch (Exception e) {
            System.err.println("[stt] 失败: " + e.getMessage());
            e.printStackTrace();
            log.info("[verify] 匹配: N/A（STT 不可用）");
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
        Path modelDir = Files.createTempDirectory("whisper-model-");
        java.net.URL res = WhisperTranslator.class.getClassLoader()
                .getResource("audio/asr/whisper-tiny");
        if (res == null) {
            throw new RuntimeException("whisper-tiny resources not found on classpath");
        }
        java.util.Enumeration<java.net.URL> resources =
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
                            throw new RuntimeException(e);
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
            throw new RuntimeException("No whisper-tiny resources found on classpath");
        }
        return modelDir;
    }

    static boolean matches(String text, String transcript) {
        if (transcript == null) {
            return false;
        }
        String a = text.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "");
        String b = transcript.trim().toLowerCase().replaceAll("[^a-z0-9 ]", "");
        return a.equals(b) || b.contains(a) || a.contains(b);
    }
}

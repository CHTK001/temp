package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.onnx.text.BertSquadTranslator;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 用已嵌入的 moonshine-base 模型做 TTS→STT 验证。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class VoiceCloneMoonshineExample {

    private VoiceCloneMoonshineExample() {}

    public static void main(String[] args) throws Exception {
        String text = args.length > 0 ? args[0] : "Hello world this is a test";
        log.info("===== TTS→STT 验证管线（moonshine-base） =====");
        log.info("[pipeline] 文本: " + text);

        // Step 1: TTS
        log.info("[tts] 合成中...");
        byte[] audio = synthesizeTts(text);
        log.info("[tts] 字节数: " + audio.length);

        Path wavPath = Files.createTempFile("voice-moonshine-", ".wav");
        Files.write(wavPath, audio);
        log.info("[tts] 音频: " + wavPath.toFile().length() + " bytes → " + wavPath);

        // Step 2: STT via moonshine
        log.info("[stt] 回读中...");
        try {
            String transcript = directTranscribe(wavPath);
            log.info("[stt] 转写: [" + transcript + "]");
            log.info("[verify] 长度: TTS=" + text.length() + " STT=" + (transcript != null ? transcript.length() : 0));
        } catch (Exception e) {
            System.err.println("[stt] 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static byte[] synthesizeTts(String text) throws Exception {
        try (TextToAudioClient client = TextToAudioClient.create("onnx", "")) {
            client.model("pocket-tts");
            return client.synthesize(text);
        }
    }

    static String directTranscribe(Path wavPath) throws Exception {
        // 使用 moonshine-base（已嵌入 in utils-support-deeplearning-onnx-starter.jar）
        BertSquadTranslator translator = new BertSquadTranslator();
        Path modelDir = extractMoonshineModel();
        translator.prepare(modelDir);
        return translator.transcribe(wavPath);
    }

    static Path extractMoonshineModel() throws Exception {
        Path modelDir = Files.createTempDirectory("moonshine-model-");
        java.net.Enumeration<java.net.URL> resources =
                BertSquadTranslator.class.getClassLoader().getResources("nlp/audio/moonshine");
        int extracted = 0;
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            if ("file".equals(url.getProtocol())) {
                Path src = Path.of(url.toURI());
                copyRecursive(src, modelDir);
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
            throw new RuntimeException("No moonshine resources found");
        }
        return modelDir;
    }

    static void copyRecursive(Path src, Path dest) throws Exception {
        try (var stream = Files.walk(src)) {
            stream.forEach(p -> {
                try {
                    Path target = dest.resolve(src.relativize(p).toString());
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
    }
}

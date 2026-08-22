package com.chua.example.onnx;

import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.model.PredictRectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pocket-TTS 零样本声音克隆门户示例。
 *
 * <p>通过 {@link TextToAudioClient} 门户演示：默认音色合成 → 参考音频 → 声音克隆。</p>
 *
 * <pre>{@code
 *   # 列出可用 TTS 模型
 *   PocketTtsVoiceCloneExample list
 *
 *   # 默认音色合成
 *   PocketTtsVoiceCloneExample synthesize "Hello world this is a default voice test"
 *
 *   # 声音克隆：自动生成参考音频后克隆到新文本
 *   PocketTtsVoiceCloneExample clone "Hello world" "Can you hear me clearly now?"
 *
 *   # 使用已有参考音频路径进行克隆
 *   PocketTtsVoiceCloneExample clone /path/to/ref.wav "你好这是克隆后的声音"
 *
 *   # 人脸检测（与声音克隆联动）
 *   PocketTtsVoiceCloneExample face-detect face.jpg
 * }</pre>
 *
 * @since 4.0.0.42
 */
public final class PocketTtsVoiceCloneExample extends ExampleBase {

    private static final String TTS_PROVIDER = "onnx";
    private static final String POCKET_TTS_MODEL = "pocket-tts";

    private PocketTtsVoiceCloneExample() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printModels("tts", TTS_PROVIDER, TextToAudioClient.create(TTS_PROVIDER, "").models());
            printModels("face-detect", "onnx", FaceDetector.listModels().stream()
                    .map(id -> com.chua.common.support.ai.chat.ModelDefinition.builder()
                            .id(id).description("人脸检测模型").build())
                    .toList());
            return;
        }

        String cmd = args[0];
        switch (cmd) {
            case "list" -> {
                printModels("tts", TTS_PROVIDER, TextToAudioClient.create(TTS_PROVIDER, "").models());
            }
            case "synthesize" -> {
                String text = args.length > 1 ? args[1] : "Hello world this is a default voice test.";
                synthesize(text, null);
            }
            case "clone" -> {
                String refArg = args.length > 1 ? args[1] : null;
                String text = args.length > 2 ? args[2] : "Hello can you hear me clearly now?";
                byte[] refWav = loadRefAudio(refArg);
                clone(refWav, text);
            }
            case "face-detect" -> {
                String imagePath = args.length > 1 ? args[1] : null;
                if (imagePath == null) {
                    System.out.println("[face-detect] 请提供图片路径，如：PocketTtsVoiceCloneExample face-detect face.jpg");
                    return;
                }
                faceDetect(imagePath);
            }
            default -> System.out.println("[PocketTtsVoiceClone] 未知命令: " + cmd
                    + "\n可用: list / synthesize / clone / face-detect");
        }
    }

    /**
     * 默认音色或声音克隆合成。
     *
     * @param text   输入文本
     * @param refWav 参考音频字节（null 表示默认音色）
     */
    private static void synthesize(String text, byte[] refWav) throws Exception {
        try (TextToAudioClient client = TextToAudioClient.create(TTS_PROVIDER, "")) {
            client.model(POCKET_TTS_MODEL);
            if (refWav != null) {
                client.voice(saveTempWav("ref-", refWav).toString());
            }
            long t0 = System.currentTimeMillis();
            byte[] wav = client.synthesize(text);
            Path out = saveTempWav("tts-", wav);
            System.out.println("[synthesize] 文本: " + text);
            System.out.println("         WAV: " + wav.length + " bytes (" + wav.length / 48000 + "s)");
            System.out.println("         输出: " + out);
            printResult("tts", TTS_PROVIDER, POCKET_TTS_MODEL, t0);
        }
    }

    /**
     * 声音克隆。
     *
     * @param refWav 参考音频字节
     * @param text   目标文本
     */
    private static void clone(byte[] refWav, String text) throws Exception {
        System.out.println("[clone] 模式: " + (refWav != null ? "声音克隆" : "默认音色回退"));
        System.out.println("[clone] 文本: " + text);
        synthesize(text, refWav);
    }

    /**
     * 人脸检测。
     *
     * @param imagePath 图片路径
     */
    private static void faceDetect(String imagePath) {
        try {
            byte[] img = Files.readAllBytes(Path.of(imagePath));
            FaceDetector detector = FaceDetector.create("onnx", "");
            long t0 = System.currentTimeMillis();
            List<PredictRectangle> boxes = detector.detect(img);
            System.out.println("[face-detect] 图片: " + imagePath);
            System.out.println("        人脸数: " + boxes.size());
            for (PredictRectangle b : boxes) {
                System.out.println(String.format("        框: (%.0f, %.0f) %.0fx%.0f  置信度=%.2f",
                        b.x(), b.y(), b.width(), b.height(), b.confidence()));
            }
            printResult("face-detect", "onnx", "scrfd-face-detector", t0);
        } catch (Exception e) {
            System.err.println("[face-detect] 失败: " + e.getMessage());
        }
    }

    /**
     * 加载参考音频：支持文件路径或自动生成默认参考音频。
     *
     * @param refArg 文件路径或 null（自动生成）
     * @return 参考音频字节；null 表示无参考音频
     */
    private static byte[] loadRefAudio(String refArg) throws Exception {
        if (refArg == null || refArg.isBlank()) {
            System.out.println("[clone] 未指定参考音频，自动生成默认音色参考...");
            try (TextToAudioClient client = TextToAudioClient.create(TTS_PROVIDER, "")) {
                client.model(POCKET_TTS_MODEL);
                return client.synthesize("This is a reference voice sample for cloning.");
            }
        }
        Path p = Path.of(refArg.trim());
        if (!Files.exists(p)) {
            System.out.println("[clone] 警告: 参考音频文件不存在: " + p + "，将使用默认音色");
            return null;
        }
        return Files.readAllBytes(p);
    }

    /**
     * 将字节写入临时 WAV 文件。
     *
     * @param prefix 文件名前缀
     * @param data   音频字节
     * @return 临时文件路径
     */
    private static Path saveTempWav(String prefix, byte[] data) throws Exception {
        Path out = Files.createTempFile(prefix, ".wav");
        Files.write(out, data);
        return out;
    }
}

package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.audio.AudioFingerprinter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 音频指纹提取能力示例。
 *
 * <p>通过 {@link AudioFingerprinter#create(String, String)} 切换提供商（onnx/pytorch），
 * 通过 {@code .model(modelId)} 切换模型，输出固定维度浮点特征向量。</p>
 *
 * <pre>{@code
 *   // 列出所有可用模型
 *   AudioFingerprintExample list
 *
 *   // 提取音频指纹
 *   AudioFingerprintExample onnx wav2vec2-zh-fingerprint audio.wav
 *
 *   // 指定归一化
 *   AudioFingerprintExample onnx wespeaker-resnet34 audio.wav true
 *
 *   // 计算两段音频相似度
 *   AudioFingerprintExample onnx wav2vec2-zh-fingerprint audio1.wav audio2.wav
 * }</pre>
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class AudioFingerprintExample extends BaseExample {

    /** 创建 AudioFingerprintExample 实例 */
    private AudioFingerprintExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printModelIds("audio-fingerprint", "onnx",
                    AudioFingerprinter.listModels());
            printModelIds("audio-fingerprint", "pytorch",
                    AudioFingerprinter.listModels());
            return;
        }

        String provider = args[0];
        String model = args.length > 1 ? args[1] : null;
        String audioPath1 = args.length > 2 ? args[2] : null;
        String audioPath2 = args.length > 3 ? args[3] : null;
        boolean normalize = args.length > 4 && Boolean.parseBoolean(args[4]);

        // 列出模型
        if (model == null) {
            List<String> models = AudioFingerprinter.listModels();
            log.info("===== [audio-fingerprint] provider=" + provider + " =====");
            if (models.isEmpty()) {
                log.info("  (无可用模型，请确认 OnnxModelRegistrar 已加载 wav2vec2/wespeaker 注册项)");
            } else {
                models.forEach(m -> log.info("  - " + m));
            }
            return;
        }

        AudioFingerprinter fp = AudioFingerprinter.create(provider, "")
                .model(model)
                .normalize(normalize);

        // 单文件：提取指纹并打印向量信息
        if (audioPath1 != null && audioPath2 == null) {
            Path path = Path.of(audioPath1);
            if (!Files.exists(path)) {
                log.info("[error] 文件不存在: " + path);
                return;
            }
            long t0 = System.currentTimeMillis();
            float[] vec = fp.extract(path);
            log.info("[audio-fingerprint] provider=" + provider + " model=" + model);
            log.info("       file:  " + audioPath1);
            log.info("       dim:   " + vec.length);
            log.info("       norm:  " + normalize);
            log.info("       vec[0..7]: " + Arrays.toString(Arrays.copyOf(vec, 8)));
            printResult("audio-fingerprint", provider, model, t0);
            return;
        }

        // 双文件：提取两段指纹并计算余弦相似度
        if (audioPath1 != null && audioPath2 != null) {
            Path p1 = Path.of(audioPath1);
            Path p2 = Path.of(audioPath2);
            if (!Files.exists(p1)) {
                log.info("[error] 文件不存在: " + p1);
                return;
            }
            if (!Files.exists(p2)) {
                log.info("[error] 文件不存在: " + p2);
                return;
            }
            long t0 = System.currentTimeMillis();
            float[] vec1 = fp.extract(p1);
            float[] vec2 = fp.extract(p2);
            float sim = cosineSimilarity(vec1, vec2);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[audio-fingerprint] provider=" + provider + " model=" + model);
            log.info("       file1: " + audioPath1 + "  dim=" + vec1.length);
            log.info("       file2: " + audioPath2 + "  dim=" + vec2.length);
            log.info("       cos_sim: " + String.format("%.4f", sim));
            log.info("       耗时: " + elapsed + "ms");
            log.info("");
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    private static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0f;
        }
        float dot = 0f, nA = 0f, nB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            nA += a[i] * a[i];
            nB += b[i] * b[i];
        }
        if (nA < 1e-12f || nB < 1e-12f) {
            return 0f;
        }
        return dot / (float) (Math.sqrt(nA) * Math.sqrt(nB));
    }
}

package com.chua.example.ai.vision;

import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.image.ImageClassifier;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * 特征提取维度实测 — 对合成图与文本样本实际推理，验证输出向量维度。
 *
 * <h2>用法</h2>
 * <pre>java FeatureDimensionInferenceExample</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FeatureDimensionInferenceExample {

    /**
     * 生成合成测试图（512×512 白底 + 红色圆形）。
     */
    private static byte[] syntheticImage() throws Exception {
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 512, 512);
        g.setColor(java.awt.Color.RED);
        g.fillOval(156, 156, 200, 200);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        byte[] image = syntheticImage();
        System.out.println("===== 嵌入式图片特征 维度实测 =====");
        testImageFeature("dino-v2-small-embedding", 384, image);
        testImageFeature("mobileclip-s0-vision", 512, image);

        System.out.println("\n===== 嵌入式文本嵌入 维度实测 =====");
        testTextEmbedding("minilm-embedding", 384, "你好世界");
        testTextEmbedding("bge-small-en-embedding", 384, "Hello world");
        testTextEmbedding("bge-small-zh-embedding", 512, "你好世界");
    }

    private static void testImageFeature(String modelId, int expectedDim, byte[] image) {
        try {
            long t0 = System.currentTimeMillis();
            FeatureExtractor ex = FeatureExtractor.create(modelId);
            if (ex == null) {
                System.out.printf("[FAIL] %s 未能创建 FeatureExtractor%n", modelId);
                return;
            }
            // FeatureExtractor.extract 接受 byte[]/Image，返回 float[]
            float[] vec = ex.extract(image);
            long ms = System.currentTimeMillis() - t0;
            boolean ok = vec != null && vec.length == expectedDim;
            System.out.printf("[%s] %s | dim=%d 期望=%d | %dms%n",
                    ok ? "PASS" : "WARN",
                    modelId, vec == null ? -1 : vec.length, expectedDim, ms);
            if (vec != null) {
                double norm = 0;
                for (float v : vec) {
                    norm += v * v;
                }
                System.out.printf("       L2=%.4f%n", Math.sqrt(norm));
            }
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (msg.length() > 120) {
                msg = msg.substring(0, 120) + "...";
            }
            System.out.printf("[FAIL] %s <- %s%n", modelId, msg);
        }
    }

    private static void testTextEmbedding(String modelId, int expectedDim, String text) {
        try {
            FeatureExtractor ex = FeatureExtractor.create(modelId);
            if (ex == null) {
                System.out.printf("[FAIL] %s 未能创建 FeatureExtractor%n", modelId);
                return;
            }
            long t0 = System.currentTimeMillis();
            float[] vec = ex.extract(text);
            long ms = System.currentTimeMillis() - t0;
            boolean ok = vec != null && vec.length == expectedDim;
            System.out.printf("[%s] %s | dim=%d 期望=%d | %dms | text=\"%s\"%n",
                    ok ? "PASS" : "WARN",
                    modelId, vec == null ? -1 : vec.length, expectedDim, ms, text);
        } catch (Throwable e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (msg.length() > 120) {
                msg = msg.substring(0, 120) + "...";
            }
            System.out.printf("[FAIL] %s <- %s%n", modelId, msg);
        }
    }
}

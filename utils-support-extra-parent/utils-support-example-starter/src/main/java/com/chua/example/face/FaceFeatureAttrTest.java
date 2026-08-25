package com.chua.example.face;

import com.chua.common.support.task.branch.Branch;
import com.chua.deeplearning.support.engine.DjlModelFactory;
import com.chua.deeplearning.support.onnx.classification.AnimeRealClsTranslator;
import com.chua.deeplearning.support.onnx.emotion.EmotionFerplusTranslator;
import com.chua.deeplearning.support.onnx.insightface.InsightFaceGenderAgeTranslator;
import com.chua.deeplearning.support.onnx.face.ArcFaceTranslator;
import com.chua.deeplearning.support.onnx.face.AdaFaceTranslator;
import ai.djl.modality.cv.Image;
import java.nio.file.*;
import java.io.*;
import java.util.function.Supplier;

/**
 * 特征提取 + 属性分类模型逐个验证。
 *
 * <p>对同一张人脸裁剪图，测试 ArcFace/AdaFace 特征一致性、
 * FER+ 表情识别、GenderAge 年龄性别预测。</p>
 */
public class FaceFeatureAttrTest {

    private static final String FACE_CROP = "D:\\images\\anime_face1.png";
    private static final String REAL_FACE = "D:\\images\\1peopleman.png";

    /** 模型 jar → ONNX 入口路径。 */
    private static final String[][] MODELS = {
            {"arc-face",            "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-arcface\\4.0.0.42\\utils-support-models-onnx-arcface-4.0.0.42.jar",
                                    "face/swap/common/buffalo_l/w600k_r50.onnx"},
            {"insightface-adaface", "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-insightface-adaface\\4.0.0.42\\utils-support-models-onnx-insightface-adaface-4.0.0.42.jar",
                                    "models/onnx/face/recognition/adaface/adaface.onnx"},
    };

    /** 属性模型（单图 → 分类结果）。 */
    private static final String[][] ATTR_MODELS = {
            {"emotion-ferplus",      "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-fer\\4.0.0.42\\utils-support-models-onnx-fer-4.0.0.42.jar",
                                      "face/expression/FER/FER.onnx"},
            {"insightface-genderage","D:\\maven-repo\\com\\chua\\utils-support-models-onnx-insightface-genderage\\4.0.0.42\\utils-support-models-onnx-insightface-genderage-4.0.0.42.jar",
                                      "models/onnx/face/attribute/genderage/genderage.onnx"},
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== 特征提取 + 属性分类模型测试 ===\n");

        /* ── ① 特征提取模型：同图两次特征应一致 ── */
        for (String[] m : MODELS) {
            String id = m[0], jarPath = m[1], onnxEntry = m[2];
            try {
                Path tmp = extractModel(jarPath, onnxEntry);
                var translatorClass = getTranslator(id);
                DjlModelFactory.TranslatorFactory tf = () -> {
                    try { return (ai.djl.translate.Translator<Object,Object>)
                            translatorClass.getDeclaredConstructor().newInstance(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                };
                DjlModelFactory factory = new DjlModelFactory(id, tmp, tf);

                // 同图提两次特征
                Image img1 = loadImage(FACE_CROP);
                Image img2 = loadImage(FACE_CROP);
                float[] f1 = toFloat(factory.predict(img1));
                float[] f2 = toFloat(factory.predict(img2));

                if (f1 != null && f2 != null && f1.length > 0) {
                    double dot = 0, n1 = 0, n2 = 0;
                    for (int i = 0; i < f1.length; i++) { dot += f1[i]*f2[i]; n1 += f1[i]*f1[i]; n2 += f2[i]*f2[i]; }
                    double cos = dot / (Math.sqrt(n1) * Math.sqrt(n2) + 1e-8);
                    System.out.printf("[PASS] %-25s dim=%d self-cosine=%.6f%n", id, f1.length, cos);
                } else {
                    System.out.printf("[FAIL] %-25s null or empty feature%n", id);
                }

                // 跨图（动漫 vs 真人）
                Image img3 = loadImage(REAL_FACE);
                float[] f3 = toFloat(factory.predict(img3));
                if (f3 != null && f1 != null && f3.length == f1.length) {
                    double dot = 0, n1 = 0, n3 = 0;
                    for (int i = 0; i < f1.length; i++) { dot += f1[i]*f3[i]; n1 += f1[i]*f1[i]; n3 += f3[i]*f3[i]; }
                    double cos = dot / (Math.sqrt(n1) * Math.sqrt(n3) + 1e-8);
                    System.out.printf("       %-25s cross-domain cosine=%.6f (anime vs real)%n", "", cos);
                }

                factory.close();
                Files.deleteIfExists(tmp);
            } catch (Exception e) {
                System.out.printf("[FAIL] %-25s %s: %s%n", id, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        /* ── ② 属性分类模型 ── */
        System.out.println("\n=== 属性分类 ===");
        for (String[] m : ATTR_MODELS) {
            String id = m[0], jarPath = m[1], onnxEntry = m[2];
            try {
                Path tmp = extractModel(jarPath, onnxEntry);
                var translatorClass = getTranslatorForAttr(id);
                DjlModelFactory.TranslatorFactory tf = () -> {
                    try { return (ai.djl.translate.Translator<Object,Object>)
                            translatorClass.getDeclaredConstructor().newInstance(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                };
                DjlModelFactory factory = new DjlModelFactory(id, tmp, tf);
                Image img = loadImage(REAL_FACE);
                Object result = factory.predict(img);
                System.out.printf("[PASS] %-25s result=%s%n", id, result);
                factory.close();
                Files.deleteIfExists(tmp);
            } catch (Exception e) {
                System.out.printf("[FAIL] %-25s %s: %s%n", id, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        System.out.println("\n[OK] done");
        System.exit(0);
    }

    /* -- helpers -- */

    private static Path extractModel(String jarPath, String entry) throws Exception {
        Path tmp = Files.createTempFile("model-", ".onnx");
        try (var zf = new java.util.zip.ZipFile(jarPath);
             InputStream is = zf.getInputStream(zf.getEntry(entry))) {
            Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    @SuppressWarnings("unchecked")
    private static Class<?> getTranslator(String modelId) throws Exception {
        return switch (modelId) {
            case "arc-face" -> Class.forName(
                "com.chua.deeplearning.support.onnx.face.ArcFaceTranslator");
            case "insightface-adaface" -> Class.forName(
                "com.chua.deeplearning.support.onnx.insightface.InsightFaceAdaFaceTranslator");
            default -> throw new IllegalArgumentException(modelId);
        };
    }

    @SuppressWarnings("unchecked")
    private static Class<?> getTranslatorForAttr(String modelId) throws Exception {
        return switch (modelId) {
            case "emotion-ferplus" -> Class.forName(
                "com.chua.deeplearning.support.onnx.emotion.EmotionFerplusTranslator");
            case "insightface-genderage" -> Class.forName(
                "com.chua.deeplearning.support.onnx.insightface.InsightFaceGenderAgeTranslator");
            default -> throw new IllegalArgumentException(modelId);
        };
    }

    private static Image loadImage(String path) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
        return ai.djl.modality.cv.ImageFactory.getInstance()
                .fromImage(javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes)));
    }

    private static float[] toFloat(Object obj) {
        if (obj instanceof float[] f) return f;
        return null;
    }
}

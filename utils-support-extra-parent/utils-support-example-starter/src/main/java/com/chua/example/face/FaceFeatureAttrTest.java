package com.chua.example.face;

import com.chua.deeplearning.support.engine.DjlModelFactory;
import ai.djl.modality.cv.Image;
import java.nio.file.*;
import java.io.*;

/**
 * 特征提取 + 属性分类模型逐个验证。
 */
public class FaceFeatureAttrTest {

    private static final String FACE_CROP = "D:\\images\\anime_face1.png";
    private static final String REAL_FACE = "D:\\images\\1peopleman.png";

    public static void main(String[] args) throws Exception {
        try { nu.pattern.OpenCV.loadShared(); } catch (Throwable ignored) {}

        System.out.println("=== Feature + Attr Models ===\n");

        // ── Feature models ──
        String[][] featModels = {
            {"arc-face", "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-arcface\\4.0.0.42\\utils-support-models-onnx-arcface-4.0.0.42.jar",
             "face/swap/common/buffalo_l/w600k_r50.onnx",
             "com.chua.deeplearning.support.onnx.face.ArcFaceTranslator"},
            {"insightface-adaface", "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-insightface-adaface\\4.0.0.42\\utils-support-models-onnx-insightface-adaface-4.0.0.42.jar",
             "models/onnx/face/recognition/adaface/adaface.onnx",
             "com.chua.deeplearning.support.onnx.insightface.InsightFaceAdaFaceTranslator"},
        };
        for (var m : featModels) testFeature(m[0], m[1], m[2], m[3]);

        // ── Attribute models ──
        System.out.println("\n--- Attributes ---");
        String[][] attrModels = {
            {"emotion-ferplus", "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-fer\\4.0.0.42\\utils-support-models-onnx-fer-4.0.0.42.jar",
             "face/expression/FER/FER.onnx",
             "com.chua.deeplearning.support.onnx.emotion.EmotionFerplusTranslator"},
            {"insightface-genderage", "D:\\maven-repo\\com\\chua\\utils-support-models-onnx-insightface-genderage\\4.0.0.42\\utils-support-models-onnx-insightface-genderage-4.0.0.42.jar",
             "models/onnx/face/attribute/genderage/genderage.onnx",
             "com.chua.deeplearning.support.onnx.insightface.InsightFaceGenderAgeTranslator"},
        };
        for (var m : attrModels) testAttr(m[0], m[1], m[2], m[3]);

        System.out.println("\n[DONE]");
        System.exit(0);
    }

    static void testFeature(String id, String jar, String entry, String transCls) {
        try {
            Path tmp = extract(jar, entry);
            var tf = makeTf(transCls);
            var factory = new DjlModelFactory(id, tmp, tf);

            Image img1 = load(FACE_CROP);
            Image img2 = load(FACE_CROP);
            Image img3 = load(REAL_FACE);

            float[] f1 = toFloatArr(factory.predict(img1));
            float[] f2 = toFloatArr(factory.predict(img2));
            float[] f3 = toFloatArr(factory.predict(img3));

            if (f1 != null && f1.length > 0) {
                double self = cosine(f1, f2);
                System.out.printf("[PASS] %-25s dim=%d self-cos=%.6f%n", id, f1.length, self);
                if (f3 != null && f3.length == f1.length) {
                    double cross = cosine(f1, f3);
                    System.out.printf("       %-25s cross(anime-vs-real) cos=%.6f%n", "", cross);
                }
            } else {
                System.out.printf("[FAIL] %-25s null feature%n", id);
            }
            factory.close(); Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.printf("[FAIL] %-25s %s: %s%n", id, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    static void testAttr(String id, String jar, String entry, String transCls) {
        try {
            Path tmp = extract(jar, entry);
            var tf = makeTf(transCls);
            var factory = new DjlModelFactory(id, tmp, tf);
            Image img = load(REAL_FACE);
            Object result = factory.predict(img);
            System.out.printf("[PASS] %-25s result=%s%n", id, result);
            factory.close(); Files.deleteIfExists(tmp);
        } catch (Exception e) {
            System.out.printf("[FAIL] %-25s %s: %s%n", id, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    static DjlModelFactory.TranslatorFactory makeTf(String cls) throws Exception {
        Class<?> c = Class.forName(cls);
        return () -> (ai.djl.translate.Translator<Object,Object>) c.getDeclaredConstructor().newInstance();
    }

    static Path extract(String jarPath, String entry) throws Exception {
        Path tmp = Files.createTempFile("model-", ".onnx");
        try (var zf = new java.util.zip.ZipFile(jarPath);
             InputStream is = zf.getInputStream(zf.getEntry(entry))) {
            Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    static Image load(String path) throws Exception {
        byte[] b = Files.readAllBytes(Path.of(path));
        return ai.djl.modality.cv.ImageFactory.getInstance()
                .fromImage(javax.imageio.ImageIO.read(new ByteArrayInputStream(b)));
    }

    static float[] toFloatArr(Object o) { return o instanceof float[] f ? f : null; }

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-8);
    }
}

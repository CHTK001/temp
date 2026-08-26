package com.chua.example.face;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.PredictRectangle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * InsightFace buffalo_l 人脸全链路测试 — scrfd 检测 + AdaFace 特征 + 2d106 关键点 + genderage 属性。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class InsightFaceExample {
    private InsightFaceExample() { }


    /** 输出根目录 */
    private static final String OUTPUT_ROOT = "D:\\images\\output\\";

    public static void main(String[] args) throws Exception {
        String[] files = {"1people.png", "1people2.png", "3peoplebeauty.jpg", "test_1.jpg", "anime.jpg"};
        String outputDir = OUTPUT_ROOT + "insightface\\";
        Files.createDirectories(Path.of(outputDir));

        FeatureExtractor adaface = FeatureExtractor.create("insightface-adaface");
        FeatureExtractor landmark = FeatureExtractor.create("insightface-landmark-2d106");
        FeatureExtractor genderage = FeatureExtractor.create("insightface-genderage");

        FacePipeline face = FacePipeline.builder()
                .detector("insightface-scrfd")
                .build();

        for (String file : files) {
            Path f = Path.of("D:\\images", file);
            if (!Files.exists(f)) {
                continue;
            }
            log.info("\n===== " + file + " =====");
            byte[] imageData = Files.readAllBytes(f);
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageData));

            List<FaceDetectionHit> hits = face.detect(imageData);
            List<DetectionInfo> boxes = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (FaceDetectionHit hit : hits) {
                PredictRectangle box = hit.box();
                if (box == null) {
                    continue;
                }
                byte[] crop = crop(src, box);
                if (crop == null) {
                    continue;
                }
                try {
                    float[] feat = adaface.extract(crop);
                    float[] lm = landmark.extract(crop);
                    float[] ga = genderage.extract(crop);
                    String gender = ga.length >= 2 && ga[1] > ga[0] ? "男" : "女";
                    int age = ga.length >= 3 ? Math.max(0, Math.min(100, (int) (ga[2] * 100))) : -1;
                    boxes.add(new DetectionInfo("face", box.confidence(), box.x(), box.y(), box.width(), box.height()));
                    labels.add(String.format("face C%.2f %s%d lm=%d", box.confidence(), gender, age, lm.length / 2));
                    System.out.printf("  box=(%.0f,%.0f %.0fx%.0f) C=%.2f | feat=%d维 | landmark=%d点 | %s %d岁%n",
                            box.x(), box.y(), box.width(), box.height(), box.confidence(),
                            feat.length, lm.length / 2, gender, age);
                } catch (Exception e) {
                    log.info("  下游模型 FAIL: " + e.getMessage());
                }
            }

            if (!boxes.isEmpty()) {
                byte[] drawn = new DrawerPipeline(0.5f)
                        .target(imageData).boxes(boxes, labels).done();
                Files.write(Path.of(outputDir + file), drawn);
                log.info("  输出: " + outputDir + file);
            }
        }

        // AdaFace 特征比对验证：同人图相似度应显著高于异人图
        log.info("\n===== AdaFace 特征比对 =====");
        try {
            float[] f1 = largestFeature(face, adaface, "1people.png");
            float[] f2 = largestFeature(face, adaface, "1people2.png");
            float[] f3 = largestFeature(face, adaface, "3peoplebeauty.jpg");
            float[] f4 = largestFeature(face, adaface, "anime.jpg");
            System.out.printf("  cos(1people, 1people2)  = %.3f %s%n",
                    cosine(f1, f2), f1 != null && f2 != null && cosine(f1, f2) > 0.5 ? "(同人?)" : "(异人?)");
            System.out.printf("  cos(1people, 3peoplebeauty) = %.3f %s%n",
                    cosine(f1, f3), f1 != null && f3 != null && cosine(f1, f3) > 0.5 ? "(同人?)" : "(异人?)");
            System.out.printf("  cos(1people, anime)    = %.3f %s%n",
                    cosine(f1, f4), f1 != null && f4 != null && cosine(f1, f4) > 0.5 ? "(同人?)" : "(异人?)");
        } catch (Exception e) {
            log.info("  特征比对 FAIL: " + e.getMessage());
        }
    }

    /** 取最大人脸特征。 */
    private static float[] largestFeature(FacePipeline face, FeatureExtractor extractor, String file) {
        Path f = Path.of("D:\\images", file);
        if (!Files.exists(f)) {
            return null;
        }
        try {
            byte[] imageData = Files.readAllBytes(f);
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageData));
            FaceDetectionHit largest = face.detectLargest(imageData);
            if (largest == null || largest.box() == null) {
                return null;
            }
            byte[] crop = crop(src, largest.box());
            return crop == null ? null : extractor.extract(crop);
        } catch (Exception e) {
            return null;
        }
    }

    /** 余弦相似度。 */
    private static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return Float.NaN;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9));
    }

    /** 从原图裁剪人脸区域（外扩 20%）。 */
    private static byte[] crop(BufferedImage src, PredictRectangle box) {
        int x = (int) Math.max(0, box.x());
        int y = (int) Math.max(0, box.y());
        int w = (int) Math.min(src.getWidth() - x, box.width());
        int h = (int) Math.min(src.getHeight() - y, box.height());
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage faceImg = src.getSubimage(x, y, w, h);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(faceImg, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}

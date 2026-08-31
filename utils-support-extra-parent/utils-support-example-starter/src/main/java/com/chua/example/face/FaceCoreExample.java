package com.chua.example.face;

import com.chua.common.support.utils.MathUtils;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.UtilsExample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 人脸核心能力诊断示例 — 检测 + 特征提取 + 1:1 比对。
 *
 * <p>验证核心管线：detect → extractFeature → compare。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FaceCoreExample {
    private FaceCoreExample() { }


    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? UtilsExample.SUCCESS : UtilsExample.FAILURE);
    }

    public static boolean runTest() throws Exception {
        // 核心管线：检测 + 特征提取（不注入活体/属性等，逐步加）
        FacePipeline face = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")
                .feature("arc-face")
                .build();
        face.setCallback(new FacePipelineDiskCallback(Path.of("D:\\images\\output\\face_core")));

        try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
            files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                 .filter(f -> !f.toString().contains("output"))
                 .sorted()
                 .limit(10)
                 .forEach(f -> {
                     try {
                         String name = f.getFileName().toString();
                         System.out.print(name + " ... ");
                         long t0 = System.currentTimeMillis();
                         byte[] imageData = Files.readAllBytes(f);

                         // 1. 检测人脸
                         List<FaceDetectionHit> hits = face.detect(imageData);
                         long t1 = System.currentTimeMillis();

                         // 2. 每张人事脸提取特征
                         for (int i = 0; i < hits.size(); i++) {
                             FaceDetectionHit hit = hits.get(i);
                             float[] feat = face.extractFeature(hit.faceImage());
                             int dim = feat == null ? 0 : feat.length;
                             System.out.print("  脸" + (i + 1) + ": " + hit.box().x() + "," + hit.box().y()
                                     + " " + Math.round(hit.box().width()) + "x" + Math.round(hit.box().height())
                                     + " 特征=" + dim + "维");
                             if (feat != null && feat.length > 0) {
                                 System.out.print(" 置信=" + String.format("%.3f", hit.box().confidence()));
                             }
                             log.info("");
                         }
                         log.info("  总耗时: " + (System.currentTimeMillis() - t0) + "ms");
                     } catch (Exception e) {
                         log.info("FAIL: " + e.getMessage());
                     }
                 });
        }

        // 3. 1:1 比对：同一张图两次提取特征应高度相似
        log.info("\n===== 1:1 比对测试 =====");
        Path p1 = Path.of("D:\\images\\1people.png");
        Path p2 = Path.of("D:\\images\\1people2.png");
        byte[] img1 = Files.readAllBytes(p1);
        byte[] img2 = Files.readAllBytes(p2);

        float[] feat1 = face.extractFeature(img1);
        float[] feat2 = face.extractFeature(img2);
        // 同一张图重新提取特征
        float[] feat3 = face.extractFeature(img1);

        log.info("  1people vs 1people(重提): " + String.format("%.4f", MathUtils.cosineSimilarity(feat1, feat3)));
        log.info("  1people vs 1people2:      " + String.format("%.4f", MathUtils.cosineSimilarity(feat1, feat2)));
        return true;
    }

}

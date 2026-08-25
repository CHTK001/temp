package com.chua.example.face;

import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FacePipeline;
import java.nio.file.*;
import java.util.List;

/**
 * 全量人脸检测器对比测试 —— 同一组图跑所有可用检测器，输出能力矩阵。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FaceAllDetectorsTest {

    private static final String[] IMAGES = {
            "D:\\images\\largest_selfie.jpg",
            "D:\\images\\anime.jpg",
            "D:\\images\\1peopleman.png"
    };
    private static final String[] IMAGE_NAMES = {"selfie", "anime", "man"};

    /** 待测检测器列表。 */
    private static final String[][] DETECTORS = {
            {"yolo-face-detector",          "YOLOv11n-Face"},
            {"anime-face-detector",         "AnimeFace-YOLOv8"},
            {"scrfd-face-detector",         "SCRFD-2.5G"},
            {"insightface-scrfd",           "SCRFD-buffalo_l"},
            {"retinaface-r34",              "RetinaFace-R34"},
            {"tinaface",                    "TinaFace-R50"},
            {"ultra-face",                  "UltraFace-Slim"},
            {"faceplugin-face-detect-slim", "FacePlugin-Detect"},
    };

    public static void main(String[] args) throws Exception {
        // 预加载 OpenCV native（SCRFD 等需要）
        try { nu.pattern.OpenCV.loadShared(); } catch (Throwable ignored) {}

        System.out.println("=== 人脸检测器全量对比 ===");
        System.out.printf("%-20s", "Detector");
        for (String n : IMAGE_NAMES) System.out.printf(" | %-12s", n);
        System.out.println(" | Status");
        System.out.println("-".repeat(90));

        int okCount = 0, failCount = 0;
        for (String[] det : DETECTORS) {
            String modelId = det[0], label = det[1];
            StringBuilder row = new StringBuilder(String.format("%-20s", label));
            boolean anyOk = false;
            try {
                FacePipeline pipeline = FacePipeline.builder()
                        .detector(modelId)
                        .minConfidence(0.3f)
                        .build();
                for (int i = 0; i < IMAGES.length; i++) {
                    byte[] img = Files.readAllBytes(Path.of(IMAGES[i]));
                    try {
                        long t0 = System.currentTimeMillis();
                        List<FaceDetectionHit> hits = pipeline.detect(img);
                        long dt = System.currentTimeMillis() - t0;
                        row.append(String.format(" | %2d faces %4dms", hits.size(), dt));
                        if (!hits.isEmpty()) anyOk = true;
                    } catch (Exception e) {
                        row.append(" | ERR");
                    }
                }
                pipeline.close();
                row.append(String.format(" | %s", anyOk ? "OK" : "NO_DETECT"));
                if (anyOk) okCount++; else failCount++;
            } catch (Exception e) {
                row.append(String.format(" | INIT_FAIL: %.40s", e.getMessage()));
                failCount++;
            }
            System.out.println(row);
        }
        System.out.println("-".repeat(90));
        System.out.printf("Total: %d OK, %d FAIL / %d detectors%n", okCount, failCount, DETECTORS.length);
        System.exit(0);
    }
}

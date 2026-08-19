package com.chua.example.face;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.PredictRectangle;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 人脸检测诊断示例 — 对 D:/images 下所有图片检测人脸并输出标注图。
 *
 * <p>输出到 D:/images/output/face-detect/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FaceDetectExample {

    /**
     * 输出目录
     */
    private static final String OUTPUT_DIR = "D:\\images\\output\\face-detect\\";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public static boolean runTest() throws Exception {
        Files.createDirectories(Path.of(OUTPUT_DIR));
        FacePipeline face = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")
                .feature("arc-face")
                .build();

        try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
            files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                 .filter(f -> !f.toString().contains("output"))
                 .sorted()
                 .forEach(f -> {
                     try {
                         String name = f.getFileName().toString();
                         System.out.print(name + " ... ");
                         long t0 = System.currentTimeMillis();
                         byte[] imageData = Files.readAllBytes(f);

                         List<FaceDetectionHit> hits = face.detect(imageData);
                         for (int i = 0; i < hits.size(); i++) {
                             FaceDetectionHit hit = hits.get(i);
                             PredictRectangle b = hit.box();
                             System.out.printf("  框%d: x=%.1f y=%.1f w=%.1f h=%.1f conf=%.3f%n",
                                     i, b.x(), b.y(), b.width(), b.height(), b.confidence());
                         }

                         // 绘制标注图
                         byte[] drawn = drawBoxes(imageData, hits);
                         Files.write(Path.of(OUTPUT_DIR + name), drawn);

                         int withFeature = 0;
                         for (FaceDetectionHit hit : hits) {
                             float[] feat = face.extractFeature(hit.faceImage());
                             if (feat != null && feat.length > 0) {
                                 withFeature++;
                             }
                         }
                         System.out.println("检测=" + hits.size() + " 特征=" + withFeature
                                 + " " + (System.currentTimeMillis() - t0) + "ms");
                     } catch (Exception e) {
                         System.out.println("FAIL: " + e.getMessage());
                     }
                 });
        }
        return true;
    }

    /**
     * 绘制人脸检测框到原图。
     *
     * @param imageData 原图
     * @param hits      检测结果
     * @return 标注图字节
     */
    private static byte[] drawBoxes(byte[] imageData, List<FaceDetectionHit> hits) {
        DrawerPipeline drawer = new DrawerPipeline(0.5f);
        List<DetectionInfo> boxes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (FaceDetectionHit hit : hits) {
            PredictRectangle box = hit.box();
            if (box == null) {
                continue;
            }
            boxes.add(new DetectionInfo(
                    "face", box.confidence(), box.x(), box.y(), box.width(), box.height()));
            labels.add(String.format("face %.2f", box.confidence()));
        }
        return drawer.target(imageData).boxes(boxes, labels).done();
    }
}
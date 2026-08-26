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
 * 人脸检测诊断示例 — 仅用 faceplugin-face-detect-slim 检测人脸并标注。
 *
 * <p>每张人脸框标注检测置信度。输出到 D:/images/output/faceplugin-face-detect-slim/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FaceDetectExample {
    private FaceDetectExample() { }


    /**
     * 输出目录（按模型名）
     */
    private static final String OUTPUT_DIR = "D:\\images\\output\\faceplugin-face-detect-slim\\";

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
        // 仅人脸检测模型，不注入其他能力
        FacePipeline face = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")
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

                         // 检测人脸
                         List<FaceDetectionHit> hits = face.detect(imageData);

                         List<DetectionInfo> boxes = new ArrayList<>();
                         List<String> labels = new ArrayList<>();
                         for (FaceDetectionHit hit : hits) {
                             PredictRectangle box = hit.box();
                             if (box == null) {
                                 continue;
                             }
                             boxes.add(new DetectionInfo(
                                     "face", box.confidence(), box.x(), box.y(), box.width(), box.height()));
                             labels.add(String.format("face C%.2f", box.confidence()));
                         }

                         // 绘制标注图
                         byte[] drawn = new DrawerPipeline(0.5f)
                                 .target(imageData)
                                 .boxes(boxes, labels)
                                 .done();
                         Files.write(Path.of(OUTPUT_DIR + name), drawn);

                         log.info("面孔=" + hits.size()
                                 + " " + (System.currentTimeMillis() - t0) + "ms");
                     } catch (Exception e) {
                         log.info("FAIL: " + e.getMessage());
                     }
                 });
        }
        return true;
    }
}
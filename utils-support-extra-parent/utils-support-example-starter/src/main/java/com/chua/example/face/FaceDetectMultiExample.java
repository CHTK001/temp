package com.chua.example.face;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.PredictRectangle;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 人脸检测多模型诊断 — 对比 faceplugin / anime-face 检测效果。
 *
 * <p>每个模型输出到独立目录：D:/images/output/{模型名}/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FaceDetectMultiExample {
    private FaceDetectMultiExample() { }


    /**
     * 输出根目录
     */
    private static final String OUTPUT_ROOT = "D:\\images\\output\\";

    /**
     * 待测试的人脸检测模型（真人检测）
     */
    private static final String[] MODELS = {
            "faceplugin-face-detect-slim",
            "scrfd-face-detector",
            "yolo-face-detector",
            "retinaface-r34",
            "tinaface",
            "insightface-scrfd"
    };

    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    public static boolean runTest() throws Exception {
        for (String model : MODELS) {
            String outputDir = OUTPUT_ROOT + model + "\\";
            Files.createDirectories(Path.of(outputDir));
            log.info("\n===== 模型: " + model + " =====");

            FacePipeline face = FacePipeline.builder()
                    .detector(model)
                    .build();
            face.setCallback(new FacePipelineDiskCallback(Path.of(outputDir)));

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

                             byte[] drawn = new DrawerPipeline(0.5f)
                                     .target(imageData).boxes(boxes, labels).done();
                             Files.write(Path.of(outputDir + name), drawn);

                             log.info("面孔=" + hits.size()
                                     + " " + (System.currentTimeMillis() - t0) + "ms");
                         } catch (Exception e) {
                             log.info("FAIL: " + e.getMessage());
                         }
                     });
            }
        }
        return true;
    }
}

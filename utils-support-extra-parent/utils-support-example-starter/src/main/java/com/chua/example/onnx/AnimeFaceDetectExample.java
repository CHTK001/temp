package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 动漫人脸检测实测 — 对比 anime-face-detector（嵌入式 YOLOv8 v1.4_n）与
 * anime-face-yolov8（自动下载 Fuyucchi）的检测效果。
 *
 * <p>每个模型输出到独立目录：D:/images/output/{模型名}/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */

/**
 * Example: AnimeFaceDetectExample
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AnimeFaceDetectExample {
    private AnimeFaceDetectExample() { }


    /**
     * 输出根目录
     */
    private static final String OUTPUT_ROOT = "D:\\images\\output\\";

    /**
     * 待测试的动漫人脸检测模型
     */
    private static final String[] MODELS = {
            "anime-face-detector",
            "anime-face-yolov8"
    };

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
        boolean allOk = true;
        for (String model : MODELS) {
            String outputDir = OUTPUT_ROOT + model + "\\";
            Files.createDirectories(Path.of(outputDir));
            log.info(String.valueOf("\n===== 模型: " + model + " ====="));

            ImageDetector detector;
            try {
                detector = ImageDetector.create(model);
            } catch (Exception e) {
                log.info(String.valueOf("模型加载失败（跳过）: " + e.getMessage()));
                allOk = false;
                continue;
            }

            try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
                files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                     .filter(f -> !f.toString().contains("output"))
                     .filter(f -> f.toString().toLowerCase().contains("anime"))
                     .sorted()
                     .forEach(f -> {
                         try {
                             String name = f.getFileName().toString();
                             System.out.print(name + " ... ");
                             long t0 = System.currentTimeMillis();
                             byte[] imageData = Files.readAllBytes(f);

                             List<DetectionInfo> hits = detector.detect(imageData);

                             List<DetectionInfo> boxes = new ArrayList<>();
                             List<String> labels = new ArrayList<>();
                             for (DetectionInfo hit : hits) {
                                 boxes.add(new DetectionInfo(
                                         hit.label(), hit.confidence(), hit.x(), hit.y(), hit.width(), hit.height()));
                                 labels.add(String.format("%s C%.2f", hit.label(), hit.confidence()));
                             }

                             byte[] drawn = new DrawerPipeline(0.5f)
                                     .target(imageData).boxes(boxes, labels).done();
                             Files.write(Path.of(outputDir + name), drawn);

                             log.info(String.valueOf("面孔=" + hits.size())
                                     + " " + (System.currentTimeMillis() - t0) + "ms");
                         } catch (Exception e) {
                             log.info(String.valueOf("FAIL: " + e));
                             e.printStackTrace();
                         }
                     });
            }
        }
        return allOk;
    }
}


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
 * 人脸检测诊断示例 — 对 D:/images 下所有图片检测人脸并标注效果图。
 *
 * <p>每张人脸框标注：live 活体分数 + feat 特征维数 + lm 关键点数。
 * 输出到 D:/images/output/face-detect/</p>
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
                .feature("faceplugin-face-feature")
                .liveness("face-liveness-flrgb")
                .landmark("faceplugin-face-landmark")
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
                         int withFeat = 0;
                         for (int i = 0; i < hits.size(); i++) {
                             FaceDetectionHit hit = hits.get(i);
                             PredictRectangle box = hit.box();
                             if (box == null) {
                                 continue;
                             }
                             // 活体
                             float live = 1.0f;
                             try {
                                 live = face.liveScore(hit.faceImage());
                             } catch (Exception ignored) {
                             }
                             // 特征
                             int featDim = 0;
                             try {
                                 float[] feat = face.extractFeature(hit.faceImage());
                                 featDim = feat == null ? 0 : feat.length;
                             } catch (Exception ignored) {
                             }
                             if (featDim > 0) {
                                 withFeat++;
                             }
                             // 关键点
                             int lmCount = 0;
                             try {
                                 float[] lm = face.landmark(hit.faceImage());
                                 lmCount = lm == null ? 0 : lm.length;
                             } catch (Exception ignored) {
                             }

                             boxes.add(new DetectionInfo(
                                     "face", box.confidence(), box.x(), box.y(), box.width(), box.height()));
                             labels.add(String.format("LV%.2f F%d P%d",
                                     live, featDim, lmCount));
                         }

                         // 绘制标注图
                         byte[] drawn = new DrawerPipeline(0.5f)
                                 .target(imageData)
                                 .boxes(boxes, labels)
                                 .done();
                         Files.write(Path.of(OUTPUT_DIR + name), drawn);

                         System.out.println("面孔=" + hits.size() + " 特征=" + withFeat
                                 + " " + (System.currentTimeMillis() - t0) + "ms");
                     } catch (Exception e) {
                         System.out.println("FAIL: " + e.getMessage());
                     }
                 });
        }
        return true;
    }
}
package com.chua.example.ocr;

import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.DrawerPipeline;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.ocr.OcrResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * OCR 管线诊断示例 — 对 D:/images 下所有图片执行检测+识别+方向矫正，输出标注图。
 *
 * <p>用法：直接运行，输出到 D:/images/output/paddleocrv6-tiny/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class OcrPipelineExample {

    /**
     * 输出目录
     */
    private static final String OUTPUT_DIR = "D:\\images\\output\\paddleocrv6-tiny\\";

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
        OcrPipeline ocr = OcrPipeline.builder()
                .detector("paddleocrv6-det")
                .recognizer("paddleocrv6-rec")
                .direction("doc-orientation")
                .cropPadding(8)
                .minConfidence(0.6f)
                .build();

        try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
            files.filter(f -> f.toString().matches(".*\\.(jpg|png)$"))
                 .filter(f -> !f.toString().contains("output"))
                 .sorted()
                 .forEach(f -> {
                     try {
                         String name = f.getFileName().toString();
                         System.out.print(name + " ... ");
                         byte[] imageData = Files.readAllBytes(f);
                         long t0 = System.currentTimeMillis();

                         // 使用 DrawerPipeline 逐框标注
                         byte[] corrected = ocr.correct(imageData);
                         List<DetectionInfo> boxes = ocr.detector().detect(corrected);
                         List<OcrResult> results = ocr.recognizeDetail(imageData);

                         DrawerPipeline drawer = ocr.withInitDrawer().target(corrected);
                         for (DetectionInfo b : boxes) {
                             String bestText = "";
                             float bestConf = 0;
                             double bestDist = Double.MAX_VALUE;
                             double bx = b.x() + b.width() / 2.0, by = b.y() + b.height() / 2.0;
                             for (OcrResult r : results) {
                                 var rb = r.boundingBox();
                                 double rx = rb.x() + rb.width() / 2.0, ry = rb.y() + rb.height() / 2.0;
                                 double dist = Math.abs(bx - rx) + Math.abs(by - ry);
                                 if (dist < bestDist) {
                                     bestDist = dist;
                                     bestText = r.text();
                                     bestConf = r.confidence();
                                 }
                             }
                             double maxDist = (b.width() + b.height()) * 0.5;
                             if (bestDist <= maxDist) {
                                 drawer.onProcess(b, bestText, bestConf);
                             }
                         }
                         byte[] drawn = drawer.done();
                         Files.write(Path.of(OUTPUT_DIR + name), drawn);
                         System.out.println((System.currentTimeMillis() - t0) + "ms");
                     } catch (Exception e) {
                         System.out.println("FAIL: " + e.getMessage());
                     }
                 });
        }
        return true;
    }
}
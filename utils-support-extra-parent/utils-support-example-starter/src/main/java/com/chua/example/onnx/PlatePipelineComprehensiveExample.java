package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.plate.LicensePlateRecognizer;
import com.chua.deeplearning.support.plate.PlateDetectHit;
import com.chua.deeplearning.support.plate.PlateDetector;
import com.chua.deeplearning.support.plate.PlatePipeline;
import com.chua.deeplearning.support.plate.PlateResult;
import com.chua.deeplearning.support.recognition.PlateNumberPipeline;
import com.chua.deeplearning.support.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class PlatePipelineComprehensiveExample {

    private static final String[] TEST_IMAGES = {
        "D:/images/car plate1.webp",
        "D:/images/car plate2.webp",
        "D:/images/car plate3.webp",
        "D:/images/more car plate.webp"
    };

    private static final String DETECTOR_MODEL = "yolov5-plate-detect";
    private static final String RECOGNIZER_MODEL = "yolov5-plate-recognize";

    private static final String OUTPUT_DIR = "D:\\ch\\output\\plate\\";

    // 统计
    private static int scenePassed = 0;
    private static int sceneTotal = 0;
    private static StringBuilder summary = new StringBuilder();

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        Files.createDirectories(Path.of(OUTPUT_DIR));

        summary.append("==========================================\n");
        summary.append("  车牌识别管线全面测试 — ").append(java.time.LocalDateTime.now()).append("\n");
        summary.append("==========================================\n");
        summary.append("检测模型: ").append(DETECTOR_MODEL).append("\n");
        summary.append("识别模型: ").append(RECOGNIZER_MODEL).append("\n");
        summary.append("测试图片: ").append(TEST_IMAGES.length).append(" 张\n");
        summary.append("结果图输出: ").append(OUTPUT_DIR).append("\n\n");
        log.info("结果图输出: {}", OUTPUT_DIR);

        // 逐图测试
        for (String imgPath : TEST_IMAGES) {
            testOneImage(imgPath);
        }

        // 汇总
        summary.append("==========================================\n");
        summary.append("  测试汇总\n");
        summary.append("==========================================\n");
        summary.append("  场景图片检测: ").append(scenePassed).append("/").append(sceneTotal).append(" 通过\n");
        summary.append("==========================================\n");

        Path reportPath = Path.of(OUTPUT_DIR, "report.txt");
        Files.writeString(reportPath, summary.toString());
        log.info("测试报告: {}", reportPath.toAbsolutePath());
        log.info(summary.toString());

        boolean allOk = scenePassed == sceneTotal && sceneTotal > 0;
        log.info(allOk ? "[PLATE] ALL PASS" : "[PLATE] 部分未通过 (" + scenePassed + "/" + sceneTotal + ")");
        if (!allOk) { System.exit(1); }
    }

    private static void testOneImage(String imgPath) throws Exception {
        String fileName = Path.of(imgPath).getFileName().toString();
        String baseName = fileName.replaceAll("\\.(jpg|jpeg|webp|png)$", "");
        boolean isScene = fileName.contains("more car plate");

        log.info("=== {} {} ===", fileName, isScene ? "(场景图)" : "(裁剪车牌)");
        summary.append("=== ").append(fileName).append(" ===\n");

        byte[] img = Files.readAllBytes(Path.of(imgPath));

        // 1. 检测
        long t0 = System.currentTimeMillis();
        List<PredictRectangle> boxes = PlateDetector.create(DETECTOR_MODEL).detect(img);
        long detCost = System.currentTimeMillis() - t0;

        // 保存标注图
        if (!boxes.isEmpty()) {
            List<DetectionInfo> detInfos = boxes.stream()
                .map(b -> new DetectionInfo(
                    b.labelName() + " " + String.format("%.2f", b.confidence()),
                    b.confidence(), b.x(), b.y(), b.width(), b.height(), 0f, 0f, 0f, 0f, 0f))
                .toList();
            List<String> labels = detInfos.stream().map(d -> d.label()).toList();
            byte[] annotated = new DrawerPipeline(0f)
                .target(img)
                .boxes(detInfos, labels)
                .done();
            Path outPath = Path.of(OUTPUT_DIR, baseName + "_detect.png");
            Files.write(outPath, annotated);
            log.info("  标注图: {} ({} 个框)", outPath, boxes.size());
        }

        log.info("  检测: {} 个车牌框 [{}ms]", boxes.size(), detCost);

        // 2. 识别（场景图用端到端管线，裁剪图直接识别）
        if (isScene) {
            long t1 = System.currentTimeMillis();
            PlatePipeline pipeline = PlatePipeline.builder()
                .detector(DETECTOR_MODEL).recognizer(RECOGNIZER_MODEL).build();
            List<PlateDetectHit> hits = pipeline.detect(img);
            long recCost = System.currentTimeMillis() - t1;

            sceneTotal++;
            if (!hits.isEmpty()) { scenePassed++; }

            for (PlateDetectHit hit : hits) {
                String line = String.format("  车牌: %s  颜色: %s  框: [%.0f,%.0f,%.0f,%.0f]  %.2f",
                    hit.plateText(), hit.plateColor(),
                    hit.box().x(), hit.box().y(), hit.box().width(), hit.box().height(),
                    hit.box().confidence());
                log.info(line);
                summary.append(line).append("\n");
            }
            if (hits.isEmpty()) {
                String line = "  未检测到车牌";
                log.info("  {}", line);
                summary.append("  ").append(line).append("\n");
            }
            log.info("  识别耗时: {}ms\n", recCost);
        } else {
            long t1 = System.currentTimeMillis();
            PlateResult r = LicensePlateRecognizer.create(RECOGNIZER_MODEL).recognizePlate(img);
            long recCost = System.currentTimeMillis() - t1;
            String line = r != null
                ? String.format("  车牌: %s  颜色: %s  [%dms]", r.plateNo(), r.plateColor(), recCost)
                : "  未识别出车牌 [" + recCost + "ms]";
            log.info("  {}", line);
            summary.append("  ").append(line).append("\n");
        }
        summary.append("\n");
    }
}
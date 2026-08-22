package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.plate.LicensePlateRecognizer;
import com.chua.deeplearning.support.plate.PlateDetectHit;
import com.chua.deeplearning.support.plate.PlateDetector;
import com.chua.deeplearning.support.plate.PlatePipeline;
import com.chua.deeplearning.support.plate.PlateResult;
import com.chua.deeplearning.support.recognition.PlateNumberPipeline;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PlatePipelineComprehensiveExample {

    private static final String[] TEST_IMAGES = {
        "D:/images/car plate1.webp",
        "D:/images/car plate2.webp",
        "D:/images/car plate3.webp",
        "D:/images/more car plate.webp"
    };

    private static final String DETECTOR_MODEL = "yolov5-plate-detect";
    private static final String RECOGNIZER_MODEL = "yolov5-plate-recognize";

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();

        log.info("==========================================");
        log.info("  车牌识别管线全面测试");
        log.info("==========================================");
        log.info("检测模型: " + DETECTOR_MODEL);
        log.info("识别模型: " + RECOGNIZER_MODEL);
        log.info("测试图片: " + TEST_IMAGES.length + " 张");
        log.info("");

        int totalTests = 0;
        int passed = 0;

        // 测试1: 检测 + 识别 (PlateNumberPipeline)
        log.info("--- 测试1: PlateNumberPipeline 端到端 ---");
        for (String imgPath : TEST_IMAGES) {
            if (!Files.exists(Path.of(imgPath))) {
                log.info("  [SKIP] 文件不存在: " + imgPath);
                continue;
            }
            totalTests++;
            byte[] img = Files.readAllBytes(Path.of(imgPath));
            long t0 = System.currentTimeMillis();
            List<PlateResult> results = PlateNumberPipeline.builder()
                    .model(RECOGNIZER_MODEL)
                    .detector(DETECTOR_MODEL)
                    .build()
                    .recognize(img);
            long cost = System.currentTimeMillis() - t0;
            boolean ok = !results.isEmpty();
            for (PlateResult r : results) {
                log.info("  " + Path.of(imgPath).getFileName() + " -> 车牌: " + r.plateNo() + "  颜色: " + r.plateColor() + "  [" + cost + "ms]");
            }
            if (!ok) {
                log.info("  " + Path.of(imgPath).getFileName() + " -> 未检测到车牌 [" + cost + "ms]");
            }
            if (ok) { passed++; }
        }
        log.info("  测试1 通过: " + passed + "/" + totalTests);
        log.info("");

        // 测试2: 仅检测 (PlateDetector)
        log.info("--- 测试2: PlateDetector 仅检测 ---");
        int detPassed = 0;
        int detTotal = 0;
        for (String imgPath : TEST_IMAGES) {
            if (!Files.exists(Path.of(imgPath))) { continue; }
            detTotal++;
            byte[] img = Files.readAllBytes(Path.of(imgPath));
            long t0 = System.currentTimeMillis();
            List<PredictRectangle> boxes = PlateDetector.create(DETECTOR_MODEL).detect(img);
            long cost = System.currentTimeMillis() - t0;
            boolean ok = !boxes.isEmpty();
            log.info("  " + Path.of(imgPath).getFileName() + " -> " + boxes.size() + " 个车牌框 [" + cost + "ms]");
            if (ok) { detPassed++; }
        }
        log.info("  测试2 通过: " + detPassed + "/" + detTotal);
        log.info("");

        // 测试3: 仅识别 (LicensePlateRecognizer) - 需要先检测再裁切
        log.info("--- 测试3: LicensePlateRecognizer 仅识别 ---");
        int recPassed = 0;
        int recTotal = 0;
        for (String imgPath : TEST_IMAGES) {
            if (!Files.exists(Path.of(imgPath))) { continue; }
            recTotal++;
            byte[] img = Files.readAllBytes(Path.of(imgPath));
            List<PredictRectangle> boxes = PlateDetector.create(DETECTOR_MODEL).detect(img);
            if (boxes.isEmpty()) {
                log.info("  " + Path.of(imgPath).getFileName() + " -> 检测无结果，跳过识别");
                continue;
            }
            LicensePlateRecognizer recognizer = LicensePlateRecognizer.create(RECOGNIZER_MODEL);
            for (int i = 0; i < boxes.size(); i++) {
                long t0 = System.currentTimeMillis();
                PlateResult pr = recognizer.recognizePlate(img);
                long cost = System.currentTimeMillis() - t0;
                if (pr != null) {
                    log.info("  " + Path.of(imgPath).getFileName() + "[" + i + "] -> " + pr.plateNo() + "  " + pr.plateColor() + " [" + cost + "ms]");
                    recPassed++;
                }
            }
        }
        log.info("  测试3 通过: " + recPassed + "/" + recTotal);
        log.info("");

        // 测试4: PlatePipeline 端到端 (新版管线)
        log.info("--- 测试4: PlatePipeline 端到端 ---");
        int ppPassed = 0;
        int ppTotal = 0;
        for (String imgPath : TEST_IMAGES) {
            if (!Files.exists(Path.of(imgPath))) { continue; }
            ppTotal++;
            byte[] img = Files.readAllBytes(Path.of(imgPath));
            long t0 = System.currentTimeMillis();
            PlatePipeline pipeline = PlatePipeline.builder()
                    .detector(DETECTOR_MODEL)
                    .recognizer(RECOGNIZER_MODEL)
                    .build();
            List<PlateDetectHit> hits = pipeline.detect(img);
            long cost = System.currentTimeMillis() - t0;
            boolean ok = !hits.isEmpty();
            for (PlateDetectHit hit : hits) {
                log.info("  " + Path.of(imgPath).getFileName() + " -> 车牌: " + hit.plateText() + "  颜色: " + hit.plateColor() + "  框: " + hit.box() + " [" + cost + "ms]");
            }
            if (!ok) {
                log.info("  " + Path.of(imgPath).getFileName() + " -> 未检测到车牌 [" + cost + "ms]");
            }
            if (ok) { ppPassed++; }
        }
        log.info("  测试4 通过: " + ppPassed + "/" + ppTotal);
        log.info("");

        // 汇总
        log.info("==========================================");
        log.info("  测试汇总");
        log.info("==========================================");
        log.info("  PlateNumberPipeline:  " + passed + "/" + totalTests + " 通过");
        log.info("  PlateDetector:        " + detPassed + "/" + detTotal + " 通过");
        log.info("  LicensePlateRec:      " + recPassed + "/" + recTotal + " 通过");
        log.info("  PlatePipeline:        " + ppPassed + "/" + ppTotal + " 通过");
        log.info("==========================================");

        int allPassed = passed + detPassed + recPassed + ppPassed;
        int allTotal = totalTests + detTotal + recTotal + ppTotal;
        boolean allOk = allPassed == allTotal && allTotal > 0;
        log.info(allOk ? "[COMPREHENSIVE] ALL PASS" : "[COMPREHENSIVE] 部分未通过 (" + allPassed + "/" + allTotal + ")");
        if (!allOk) { System.exit(1); }
    }
}
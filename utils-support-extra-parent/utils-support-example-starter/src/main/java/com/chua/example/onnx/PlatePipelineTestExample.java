package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.plate.PlateResult;
import com.chua.deeplearning.support.recognition.PlateNumberPipeline;
import com.chua.deeplearning.support.recognition.PlateNumberPipelineDiskCallback;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Example: PlatePipelineVerifyExample
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class PlatePipelineTestExample {
    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();

        String[] images = {"D:/images/car plate1.webp", "D:/images/car plate2.webp", "D:/images/car plate3.webp", "D:/images/more car plate.webp"};
        boolean allOk = true;

        PlateNumberPipeline pipeline = PlateNumberPipeline.builder()
                .model("yolov5-plate-recognize")
                .detector("yolov5-plate-detect")
                .build();
        pipeline.setCallback(new PlateNumberPipelineDiskCallback());

        for (String imgPath : images) {
            log.info("=== " + Path.of(imgPath).getFileName() + " ===");
            byte[] img = Files.readAllBytes(Path.of(imgPath));
            long t0 = System.currentTimeMillis();
            List<PlateResult> results = pipeline.recognize(img);
            long cost = System.currentTimeMillis() - t0;
            if (results.isEmpty()) {
                log.info("  未检测到车牌  耗时=" + cost + "ms");
                allOk = false;
            } else {
                for (PlateResult r : results) {
                    log.info("  车牌: " + r.plateNo() + "  颜色: " + r.plateColor() + "  耗时=" + cost + "ms");
                }
            }
        }
        log.info(allOk ? "[PlatePipelineVerify] ALL PASS" : "[PlatePipelineVerify] 部分未识别");
        if (!allOk) { System.exit(1); }
    }
}

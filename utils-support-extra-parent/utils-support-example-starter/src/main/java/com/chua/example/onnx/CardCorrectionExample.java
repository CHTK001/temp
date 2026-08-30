package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 卡片矫正检测验证（读光票证检测矫正）。
 *
 * <pre>{@code
 *   CardCorrectionExample G:\images\card_test.jpg
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class CardCorrectionExample extends BaseExample {

    /** 创建 CardCorrectionExample 实例 */
    private CardCorrectionExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String modelId = "card-correction-detector";
        java.io.File inputDir = new java.io.File("D:/images");
        java.io.File outputRoot = new java.io.File("G:/images/output/" + modelId);
        outputRoot.mkdirs();
        java.io.File[] files = inputDir.listFiles((d, n) -> {
            String s = n.toLowerCase();
            return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png") || s.endsWith(".webp");
        });
        if (files == null || files.length == 0) {
            log.info("[card-correction] D:/images 无图片");
            return;
        }
        long t0 = System.currentTimeMillis();
        int total = 0;
        for (java.io.File f : files) {
            byte[] img = Files.readAllBytes(f.toPath());
            List<DetectionInfo> corners = ImageDetector.create("card-correction-detector").detect(img);
            log.info("[card-correction] " + f.getName() + " 角点数=" + corners.size());
            for (DetectionInfo c : corners) {
                log.info(String.format("       角点[%s] conf=%.3f box=(%.0f,%.0f) %.0fx%.0f",
                        c.label(), c.confidence(), c.x(), c.y(), c.width(), c.height()));
            }
            com.chua.deeplearning.support.onnx.classification.CardCorrectionTranslator translator =
                    com.chua.deeplearning.support.onnx.classification.CardCorrectionTranslator.shared();
            byte[] corrected = translator.correct(img);
            if (corrected != null) {
                java.nio.file.Path out = outputRoot.toPath().resolve(f.getName().replaceAll("\\.[^.]+$", "_corrected.png"));
                java.nio.file.Files.write(out, corrected);
                log.info("[card-correction] 已输出矫正图: " + out);
            } else {
                log.info("[card-correction] 未检测到卡片，无矫正图");
            }
            total++;
        }
        printResult("card-correction", "onnx", modelId, t0);
        log.info("[card-correction] 共处理 " + total + " 张，输出目录: " + outputRoot.getAbsolutePath());
    }
}

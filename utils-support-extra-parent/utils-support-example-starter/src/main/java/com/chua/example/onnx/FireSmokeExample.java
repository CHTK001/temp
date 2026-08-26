package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 火灾烟雾检测示例（CCCCTV fire-smoke，YOLOv8n 320，2 类：fire/smoke）。
 *
 * <pre>{@code
 *   FireSmokeExample D:/images/test.jpg G:/images/output/fire-smoke/result.jpg 0.50
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FireSmokeExample extends BaseExample {

    private FireSmokeExample() {
    }

    public static void main(String[] args) throws Exception {
        String model = "fire-smoke";
        String imagePath = args.length > 0 ? args[0] : null;
        String outPath = args.length > 1 ? args[1] : null;
        float thr = 0.50f;
        if (args.length > 2) {
            try { thr = Float.parseFloat(args[2]); } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        }
        }
        if (imagePath == null) {
            printModels("fire", "onnx", ModelRegistry.getAll().stream()
                    .filter(e -> "fire-smoke".equals(e.modelId()))
                    .map(e -> com.chua.common.support.ai.chat.ModelDefinition.builder().id(e.modelId()).build())
                    .toList());
            return;
        }
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        ImageDetector detector = ImageDetector.create(model);
        long t0 = System.currentTimeMillis();
        List<DetectionInfo> results = detector.detect(img);
        final float thrFinal = thr;
        results = results.stream().filter(d -> d.confidence() >= thrFinal).toList();
        log.info("[fire-smoke] model: {} 图片: {} 阈值: {}", model, imagePath, thrFinal);
        log.info("     目标数: {}", results.size());
        for (DetectionInfo d : results) {
            log.info("       {} conf={} [{},{},{},{}]", d.label(), String.format("%.2f", d.confidence()), d.x(), d.y(), d.width(), d.height());
        }
        if (outPath != null && !results.isEmpty()) {
            byte[] annotated = new com.chua.deeplearning.support.draw.DrawerPipeline(thrFinal)
                    .target(img)
                    .boxes(results, results.stream().map(d -> d.label() + " " + String.format("%.2f", d.confidence())).toList())
                    .done();
            Files.write(Path.of(outPath), annotated);
            log.info("     已标注出图: {}", outPath);
        }
        printResult("fire-smoke", "onnx", model, t0);
    }
}
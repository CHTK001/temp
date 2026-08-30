package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 印章检测示例（SDT Seal inspection，YOLO 640，4 类：公章/个人章/审核章/其他）。
 *
 * <pre>{@code
 *   SealInspectionExample list
 *   SealInspectionExample seal-inspection seal.jpg out.jpg 0.60
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class SealInspectionExample extends BaseExample {

    private SealInspectionExample() {
    }

    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : null;
        String imagePath = args.length > 1 ? args[1] : null;
        String outPath = args.length > 2 ? args[2] : null;
        float thr = 0.60f;
        if (args.length > 3) {
            try {
                thr = Float.parseFloat(args[3]);
            } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
            }
        }
        if (model == null) {
            printModels("seal", "onnx", ModelRegistry.getAll().stream()
                    .filter(e -> "seal-inspection".equals(e.modelId()))
                    .map(e -> com.chua.common.support.ai.chat.ModelDefinition.builder().id(e.modelId()).build())
                    .toList());
            return;
        }
        if (imagePath == null) {
            log.info("[seal] 需要图片路径");
            return;
        }
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        // 阈值可在 ImageDetector 门面层注入（DefaultImageDetector 会在 translate 后过滤）。
        // YOLO Translator 层默认阈值亦为 0.60，满足“默认更准”，用户可通过 thr 参数自定义。
        ImageDetector detector = ImageDetector.create(model);
        long t0 = System.currentTimeMillis();
        List<DetectionInfo> results = detector.detect(img);
        // 门面层二次过滤（便于运行时自定义阈值，不受 Translator 默认限制）
        final float thrFinal = thr;
        results = results.stream().filter(d -> d.confidence() >= thrFinal).toList();
        log.info("[seal] model: {} 图片: {} 阈值: {}", model, imagePath, thrFinal);
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
        printResult("seal", "onnx", model, t0);
    }
}

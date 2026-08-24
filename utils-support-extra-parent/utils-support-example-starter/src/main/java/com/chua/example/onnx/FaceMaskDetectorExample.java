package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 口罩检测示例（YOLOv8，2 类：cloth/surgical，DrawerPipeline 画真实口罩框）。
 *
 * <pre>{@code
 *   FaceMaskDetectorExample D:/images G:/images/output/face-mask-detector
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FaceMaskDetectorExample extends BaseExample {

    private FaceMaskDetectorExample() {
    }

    public static void main(String[] args) throws Exception {
        String modelId = "face-mask-detector";
        String inputDirPath = args.length > 0 ? args[0] : "G:/images";
        String outputDirPath = args.length > 1 ? args[1] : "G:/images/output/" + modelId;
        File outputRoot = new File(outputDirPath);
        outputRoot.mkdirs();
        ModelRegistry.discoverAll();
        ImageDetector detector = ImageDetector.create(modelId);
        log.info("[face-mask-detector] 输出目录: {}", outputRoot.getAbsolutePath());
        int total = 0, maskCount = 0;
        try (Stream<Path> paths = Files.list(Path.of(inputDirPath))) {
            for (Path p : paths.filter(p -> {
                String s = p.toString().toLowerCase();
                return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png") || s.endsWith(".webp");
            }).sorted().toList()) {
                byte[] img = Files.readAllBytes(p);
                long t0 = System.currentTimeMillis();
                List<DetectionInfo> results = detector.detect(img);
                results = results.stream().filter(d -> d.confidence() >= 0.35f).toList();
                long cost = System.currentTimeMillis() - t0;
                List<String> labels = results.stream()
                        .map(d -> d.label() + " " + String.format("%.2f", d.confidence())).toList();
                byte[] drawn = new com.chua.deeplearning.support.draw.DrawerPipeline(0f)
                        .target(img)
                        .boxes(results, labels)
                        .done();
                String out = p.getFileName().toString().replaceAll("\\.[^.]+$", "_mask.jpg");
                Files.write(Path.of(outputDirPath, out), drawn);
                maskCount += results.size();
                total++;
                log.info("[face-mask-detector] {} -> {} 口罩 [{}/{}ms] -> {}", p.getFileName(), results.size(), total, cost, out);
                for (DetectionInfo d : results) {
                    log.info("     {} conf={} [{},{},{},{}]", d.label(), String.format("%.2f", d.confidence()), d.x(), d.y(), d.width(), d.height());
                }
            }
        }
        log.info("[face-mask-detector] 共处理 {} 张, 检出口罩 {} 个 -> {}", total, maskCount, outputRoot.getAbsolutePath());
    }
}
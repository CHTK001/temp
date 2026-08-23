package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.model.DetectionInfo;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 口罩检测示例（SigLIP2，2 类：Face_Mask Found / Not_Found）。
 *
 * <p>遍历 D:/images 与 G:/images 下图片，分类并用 DrawerPipeline 整图框标注输出到
 * G:/images/output/face-mask/。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FaceMaskExample extends ExampleBase {

    private FaceMaskExample() {
    }

    public static void main(String[] args) throws Exception {
        String modelId = "face-mask";
        File outputRoot = new File("G:/images/output/" + modelId);
        outputRoot.mkdirs();
        ModelRegistry.discoverAll();
        ImageClassifier classifier = ImageClassifier.create(modelId);
        log.info("[face-mask] 输出目录: {}", outputRoot.getAbsolutePath());
        AtomicInteger total = new AtomicInteger();
        AtomicInteger found = new AtomicInteger();
        // D:/images 无图则 G:/images
        for (String dir : new String[]{"D:/images", "G:/images"}) {
            File inputDir = new File(dir);
            if (!inputDir.isDirectory()) continue;
            try (Stream<java.nio.file.Path> paths = Files.list(inputDir.toPath())) {
                paths.filter(p -> {
                    String s = p.toString().toLowerCase();
                    return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png") || s.endsWith(".webp");
                }).sorted().forEach(p -> {
                    try {
                        byte[] img = Files.readAllBytes(p);
                        String label = classifier.classify(img);
                        // 整图框标注分类结果
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(img));
                        int w = bi == null ? 640 : bi.getWidth();
                        int h = bi == null ? 480 : bi.getHeight();
                        DetectionInfo full = new DetectionInfo(label, 1.0f, 0, 0, w, h);
                        byte[] drawn = new com.chua.deeplearning.support.draw.DrawerPipeline(0f)
                                .target(img)
                                .boxes(List.of(full), List.of("face-mask: " + label))
                                .done();
                        File out = new File(outputRoot, p.getFileName().toString().replaceAll("\\.[^.]+$", "_face-mask.jpg"));
                        Files.write(out.toPath(), drawn);
                        if ("Face_Mask Found".equals(label)) found.incrementAndGet();
                        total.incrementAndGet();
                        log.info("[face-mask] {} -> {} -> {}", p.getFileName(), label, out.getName());
                    } catch (Exception e) {
                        log.info("[face-mask] {} FAIL: {}", p.getFileName(), e.getMessage());
                    }
                });
            }
        }
        log.info("[face-mask] 共处理 {} 张, Face_Mask Found {} 张", total.get(), found.get());
    }
}
package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.plate.PlateDetector;
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

    private static final String DETECTOR = "yolov5-plate-detect";

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        Path outDir = Path.of("D:/images/output/" + DETECTOR + "/");
        Files.createDirectories(outDir);

        int passed = 0;
        for (String imgPath : TEST_IMAGES) {
            String name = Path.of(imgPath).getFileName().toString();
            byte[] img = Files.readAllBytes(Path.of(imgPath));

            var rects = PlateDetector.create(DETECTOR).detect(img);
            var labels = rects.stream().map(b -> String.format("%.2f", b.confidence())).toList();
            byte[] drawn = new DrawerPipeline(0f).target(img).predictBoxes(rects, labels).done();
            Files.write(outDir.resolve(name.replaceAll("\\.(jpg|jpeg|webp|png)$", ".png")), drawn);

            if (!rects.isEmpty()) passed++;
            log.info("{} -> {} 个框", name, rects.size());
        }
        log.info("[PLATE] {}/{} 通过", passed, TEST_IMAGES.length);
        if (passed == 0) System.exit(1);
    }
}
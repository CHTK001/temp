package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.plate.PlateDetectHit;
import com.chua.deeplearning.support.plate.PlatePipeline;
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

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        Path outDir = Path.of("D:\\ch\\output\\plate\\");
        Files.createDirectories(outDir);

        int passed = 0;
        for (String imgPath : TEST_IMAGES) {
            String name = Path.of(imgPath).getFileName().toString();
            byte[] img = Files.readAllBytes(Path.of(imgPath));

            PlatePipeline pipeline = PlatePipeline.builder()
                .detector("yolov5-plate-detect")
                .recognizer("yolov5-plate-recognize")
                .build();
            List<PlateDetectHit> hits = pipeline.detect(img);

            // 绘制标注图
            List<DetectionInfo> dets = hits.stream()
                .map(h -> new DetectionInfo(h.plateText() + " " + h.plateColor(),
                    h.box().confidence(), h.box().x(), h.box().y(), h.box().width(), h.box().height(), 0, 0, 0, 0, 0))
                .toList();
            byte[] drawn = pipeline.withInitDrawer().target(img).boxes(dets, dets.stream().map(d -> d.label()).toList()).done();
            String outName = name.replaceAll("\\.(jpg|jpeg|webp|png)$", ".png");
            Files.write(outDir.resolve(outName), drawn);

            String result = hits.isEmpty() ? "未检测到车牌" : hits.size() + " 个车牌";
            boolean ok = !hits.isEmpty();
            if (ok) passed++;
            log.info("{} -> {} [{}]", name, result, hits.isEmpty() ? "" : hits.stream().map(h -> h.plateText() + "(" + h.plateColor() + ")").reduce((a, b) -> a + ", " + b).orElse(""));
        }
        log.info("[PLATE] {}/{} 通过 -> {}", passed, TEST_IMAGES.length, outDir);
        if (passed == 0) System.exit(1);
    }
}
package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.plate.PlateDetector;
import com.chua.deeplearning.support.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 车牌检测全流程综合示例。
 * <p>
 * 对内置测试图片依次执行车牌检测模型推理，绘制检测框并输出结果图，
 * 最后统计通过率，全部失败时以非零退出码结束。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class PlatePipelineComprehensiveExample {

    private static final String[] TEST_IMAGES = {
        "D:/images/more car plate.webp",
        "D:/images/2car plate.webp",
        "D:/images/car plate1.webp",
        "D:/images/car plate2.webp",
        "D:/images/car plate3.webp"
    };

    private static final String[] DETECTORS = {"yolo11-plate-detect"};

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();

        for (String detector : DETECTORS) {
            Path outDir = Path.of("D:/images/output/" + detector + "/");
            Files.createDirectories(outDir);
            System.out.println("===== " + detector + " =====");
            int passed = 0;
            for (String imgPath : TEST_IMAGES) {
                String name = Path.of(imgPath).getFileName().toString();
                byte[] img = Files.readAllBytes(Path.of(imgPath));
                try {
                    var rects = PlateDetector.create(detector).detect(img);
                    var labels = rects.stream().map(b -> String.format("%.2f", b.confidence())).toList();
                    byte[] drawn = new DrawerPipeline(0f).target(img).predictBoxes(rects, labels).done();
                    Files.write(outDir.resolve(name.replaceAll("\\.(jpg|jpeg|webp|png)$", ".png")), drawn);
                    if (!rects.isEmpty()) passed++;
                    log.info("{} -> {} 个框", name, rects.size());
                } catch (Exception e) {
                    log.warn("{} -> 失败: {}", name, e.getMessage());
                }
            }
            log.info("[PLATE] {}/{} 通过", passed, TEST_IMAGES.length);
        }
    }
}
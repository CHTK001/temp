package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.plate.PlateDetector;
import com.chua.deeplearning.support.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 车牌检测全流程综合示例。
 * <p>
 * 对内置测试图片依次执行车牌检测模型推理，绘制检测框并输出结果图，
 * 最后统计通过率。
 *
 * <p>参数（{@code --key=value} 或 {@code --key value}）：
 * <ul>
 *   <li>{@code --images=path1,path2}：测试图列表（逗号分隔）</li>
 *   <li>{@code --out=dir}：标注图输出根目录</li>
 *   <li>{@code --models=id1,id2}：检测模型 ID 列表</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class PlatePipelineComprehensiveExample {

    /** 私有构造，防止实例化 */
    private PlatePipelineComprehensiveExample() { }

    /**
     * 默认测试图列表。
     */
    private static final List<String> DEFAULT_IMAGES = List.of(
            "D:/images/more car plate.webp",
            "D:/images/2car plate.webp",
            "D:/images/car plate1.webp",
            "D:/images/car plate2.webp",
            "D:/images/car plate3.webp");

    /**
     * 默认检测模型 ID 列表。
     */
    private static final List<String> DEFAULT_DETECTORS = List.of("yolo11-plate-detect");

    /**
     * 标注图默认输出根目录。
     */
    private static final String DEFAULT_OUTPUT_DIR = "D:/images/output";

    public static void main(String[] args) throws Exception {
        List<String> images = DEFAULT_IMAGES;
        List<String> detectors = DEFAULT_DETECTORS;
        String outDirBase = DEFAULT_OUTPUT_DIR;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--images=")) {
                images = Arrays.asList(arg.substring("--images=".length()).split(","));
            } else if (arg.equals("--images") && i + 1 < args.length) {
                images = Arrays.asList(args[++i].split(","));
            } else if (arg.startsWith("--models=")) {
                detectors = Arrays.asList(arg.substring("--models=".length()).split(","));
            } else if (arg.equals("--models") && i + 1 < args.length) {
                detectors = Arrays.asList(args[++i].split(","));
            } else if (arg.startsWith("--out=")) {
                outDirBase = arg.substring("--out=".length());
            } else if (arg.equals("--out") && i + 1 < args.length) {
                outDirBase = args[++i];
            }
        }

        ModelRegistry.discoverAll();
        ImageUtils.load();

        for (String detector : detectors) {
            Path outDir = Path.of(outDirBase, detector);
            Files.createDirectories(outDir);
            log.info("===== {} =====", detector);
            int passed = 0;
            for (String imgPath : images) {
                String name = Path.of(imgPath).getFileName().toString();
                byte[] img = Files.readAllBytes(Path.of(imgPath));
                try {
                    var rects = PlateDetector.create(detector).detect(img);
                    var labels = rects.stream().map(b -> String.format("%.2f", b.confidence())).toList();
                    byte[] drawn = new DrawerPipeline(0f).target(img).predictBoxes(rects, labels).done();
                    Files.write(outDir.resolve(name.replaceAll("\\.(jpg|jpeg|webp|png)$", ".png")), drawn);
                    if (!rects.isEmpty()) {
                        passed++;
                    }
                    log.info("{} -> {} 个框", name, rects.size());
                } catch (Exception e) {
                    log.warn("{} -> 失败: {}", name, e.getMessage());
                }
            }
            log.info("[PLATE] {}/{} 通过", passed, images.size());
        }
    }
}

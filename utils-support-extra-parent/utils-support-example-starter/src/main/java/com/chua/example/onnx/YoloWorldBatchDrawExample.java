package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import ai.djl.modality.cv.output.DetectedObjects;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * YOLOv8s-World 开放词表检测 + DrawerPipeline 批量标注示例。
 *
 * <h2>用法</h2>
 * <pre>
 *   java YoloWorldBatchDrawExample [输入目录] [输出目录]
 * </pre>
 *
 * <p>默认输入 {@code G:/images}，输出 {@code G:/images/output/yolov8s-world}。
 * 退出码：{@code 0}=全部成功，{@code 1}=存在失败文件。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YoloWorldBatchDrawExample {

    private YoloWorldBatchDrawExample() {
    }

    /**
     * 入口：批量检测并绘制红框+标签。
     *
     * @param args args[0]=输入目录，args[1]=输出目录
     * @throws Exception 目录创建失败
     */
    public static void main(String[] args) throws Exception {
        String inputDir = "G:/images";
        String outputDir = "G:/images/output/yolov8s-world";
        if (args.length > 0) {
            inputDir = args[0];
        }
        if (args.length > 1) {
            outputDir = args[1];
        }
        Path in = Path.of(inputDir);
        Path outRoot = Path.of(outputDir);
        Files.createDirectories(outRoot);
        log.info("输入: " + in);
        log.info("输出: " + outRoot);

        ImageDetector detector;
        try {
            detector = ImageDetector.create("yolov8s-world");
            log.info("detector: " + detector);
        } catch (Exception e) {
            System.err.println("[FAIL] 创建 detector 失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
            return;
        }

        File[] files = new File(inputDir).listFiles(
                (d, n) -> n.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp)"));
        if (files == null || files.length == 0) {
            System.err.println("[FAIL] 无图片");
            System.exit(1);
            return;
        }

        int ok = 0;
        int fail = 0;
        for (File f : files) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                long t0 = System.currentTimeMillis();
                List<DetectionInfo> infos = detector.detect(bytes);
                long dt = System.currentTimeMillis() - t0;
                System.out.printf("%s -> %d 个目标 %dms%n",
                        f.getName(), infos == null ? 0 : infos.size(), dt);
                if (infos != null) {
                    for (DetectionInfo d : infos) {
                        System.out.printf("  %s %.2f [%.1f,%.1f %.1fx%.1f]%n",
                                d.label(), d.confidence(), d.x(), d.y(), d.width(), d.height());
                    }
                }
                // DrawerPipeline 统一绘制（红框+标签底，自动适配已归一化框）
                List<String> labels = infos == null ? List.of()
                        : infos.stream().map(d -> d.label() + " "
                                + String.format("%.2f", d.confidence())).toList();
                DetectedObjects unused = null;
                byte[] drawn = new DrawerPipeline(0.1f)
                        .target(bytes)
                        .boxes(infos == null ? List.of() : infos, labels)
                        .done();
                Files.write(new File(outRoot.toFile(),
                        f.getName().replaceAll("\\.[^.]+$", "") + ".png").toPath(), drawn);
                ok++;
            } catch (Throwable e) {
                log.info("失败 " + f.getName() + ": " + e.getMessage());
                e.printStackTrace();
                fail++;
            }
        }
        System.out.printf("完成 ok=%d fail=%d%n", ok, fail);
        System.exit(fail > 0 ? 1 : 0);
    }
}

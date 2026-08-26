package com.chua.example.ai.vision;

import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * YOLO-World 零样本检测示例（嵌入式友好）。
 *
 * <p>输入图片 + 文本类别（中/英文）→ 输出检测框坐标 + 类别 + 置信度。
 * 模型通过 downloadUrl 自动下载（首次运行联网）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 检测单张图片
 *   java YoloWorldExample "D:\\images\\scene.jpg"
 *
 *   # 检测目录下全部图片
 *   java YoloWorldExample "D:\\images"
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YoloWorldExample {
    private YoloWorldExample() { }


    /** 默认图片路径 */
    private static final String DEFAULT_PATH = "D:\\images";

    /** 默认候选类别（中英文混合） */
    private static final String DEFAULT_CANDIDATES = "人,汽车,自行车,摩托车,狗,猫,卡车,公交车";

    /** 成功退出码 */
    private static final int EXIT_CODE_SUCCESS = 0;

    /** 失败退出码 */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 入口。
     *
     * @param args 参数[0] 图片路径，可选
     * @throws IOException 读取文件异常
     */
    public static void main(String[] args) throws IOException {
        String imagePath = args.length > 0 ? args[0] : DEFAULT_PATH;
        boolean passed = runTest(imagePath, DEFAULT_CANDIDATES);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 运行零样本检测测试。
     *
     * @param imagePath  图片文件或目录
     * @param candidates 候选类别（逗号分隔，支持中英文）
     * @return 是否成功执行
     * @throws IOException 读取文件异常
     */
    public static boolean runTest(String imagePath, String candidates) throws IOException {
        log.info("===== YOLO-World 零样本检测 =====");
        log.info("候选类别: " + candidates);

        ImageDetector detector = ImageDetector.create("yolov8s-world", "");
        if (detector == null) {
            System.err.println("[FAIL] 未通过 SPI 获取 ImageDetector");
            return false;
        }

        Path path = Paths.get(imagePath);
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                        .sorted()
                        .limit(20)
                        .forEach(f -> detectFile(detector, f));
            }
        } else if (Files.isRegularFile(path)) {
            detectFile(detector, path);
        } else {
            System.err.println("[FAIL] 路径不存在: " + imagePath);
            return false;
        }
        return true;
    }

    /**
     * 检测单张图片。
     *
     * @param detector 检测器
     * @param file     图片文件
     */
    private static void detectFile(ImageDetector detector, Path file) {
        try {
            byte[] imageData = Files.readAllBytes(file);
            long t0 = System.currentTimeMillis();
            List<DetectionInfo> results = detector.detect(imageData);
            long ms = System.currentTimeMillis() - t0;
            String name = file.getFileName().toString();
            System.out.printf("[%s] %dms 检出 %d 个目标%n", name, ms, results.size());
            for (DetectionInfo info : results) {
                System.out.printf("  - %s (%.2f) @ [x=%.0f,y=%.0f,w=%.0f,h=%.0f]%n",
                        info.label(), info.confidence(),
                        info.x(), info.y(), info.width(), info.height());
            }
        } catch (Exception e) {
            log.info("[FAIL] " + file.getFileName() + ": " + e.getMessage());
        }
    }
}

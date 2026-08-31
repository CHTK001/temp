package com.chua.example.image;

import com.chua.deeplearning.support.face.FaceClarityDetector;
import com.chua.deeplearning.support.image.ImageClarityDetector;
import com.chua.deeplearning.support.model.FaceQualityInfo;
import com.chua.deeplearning.support.model.ImageQualityInfo;
import com.chua.example.util.UtilsExample;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 清晰度检测示例 — 对 D:/images 下图片做图片/人脸清晰度评估。
 *
 * <p>演示两种后端：OpenCV（纯算法、嵌入式、零下载）与 ONNX（NIMA 模型）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ImageClarityDetectorExample {
    private ImageClarityDetectorExample() { }


    /** 默认图片目录 */
    private static final String DEFAULT_IMAGE_DIR = "D:\\images";

    /**
     * 入口。
     *
     * @param args 参数[0] 图片路径，可选
     * @throws IOException 读取文件异常
     */
    public static void main(String[] args) throws IOException {
        String imagePath = args.length > 0 ? args[0] : DEFAULT_IMAGE_DIR;
        boolean passed = runTest(imagePath);
        System.exit(passed ? UtilsExample.SUCCESS : UtilsExample.FAILURE);
    }

    /**
     * 运行清晰度检测测试。
     *
     * @param imagePath 图片文件或目录
     * @return 是否全部通过
     * @throws IOException 读取文件异常
     */
    public static boolean runTest(String imagePath) throws IOException {
        log.info("===== 图片清晰度检测 (OpenCV 嵌入式) =====");
        ImageClarityDetector imageDetector = ImageClarityDetector.create("opencv", "");
        if (imageDetector == null) {
            System.err.println("[FAIL] OpenCV 图片清晰度 SPI 未加载");
            return false;
        }

        Path path = Paths.get(imagePath);
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                        .sorted()
                        .limit(20)
                        .forEach(f -> testImageFile(imageDetector, f));
            }
        } else if (Files.isRegularFile(path)) {
            testImageFile(imageDetector, path);
        } else {
            System.err.println("[FAIL] 路径不存在: " + imagePath);
            return false;
        }
        return true;
    }

    /**
     * 测试单张图片清晰度。
     *
     * @param detector 清晰度检测器
     * @param file     图片文件
     */
    private static void testImageFile(ImageClarityDetector detector, Path file) {
        try {
            byte[] imageData = Files.readAllBytes(file);
            String name = file.getFileName().toString();

            // 图片清晰度
            long t0 = System.currentTimeMillis();
            ImageQualityInfo info = detector.blurThreshold(100.0).assess(imageData);
            long ms = System.currentTimeMillis() - t0;
            System.out.printf("[图片] %-40s %dms blur=%.1f bright=%.1f -> %s%n",
                    name, ms, info.blurScore(), info.brightness(),
                    info.sharpnessOk() ? "清晰" : "模糊");

            // 人脸清晰度
            FaceClarityDetector faceDetector = FaceClarityDetector.create("opencv", "");
            FaceQualityInfo faceInfo = faceDetector.blurThreshold(80.0).minFaceRatio(0.05f).assess(imageData);
            System.out.printf("[人脸] %-40s faces=%d -> %s%n",
                    name, faceInfo.faceCount(), faceInfo.message());
        } catch (Exception e) {
            log.info("[FAIL] " + file.getFileName() + ": " + e.getMessage());
        }
    }
}

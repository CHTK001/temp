package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.ImageProcessors;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ImageProcessor SPI 机制 + 全操作综合示例
 *
 * <p>本示例验证 {@link ImageProcessor} 的完整能力：</p>
 * <h3>SPI 机制能力点</h3>
 * <ul>
 *   <li>discover 发现：发现全部实现并按优先级排序</li>
 *   <li>subclass 子类：自定义子类参与优先级竞争</li>
 *   <li>proxy 代理：getExtensionFactory 返回自动降级代理</li>
 *   <li>degrade 降级：高优先级失败自动回退</li>
 *   <li>all-fail 全败：全部失败抛异常</li>
 * </ul>
 *
 * <h3>图像操作能力点（13 种 process 操作 + processBatch）</h3>
 * <ul>
 *   <li>resize 缩放</li>
 *   <li>grayscale 灰度</li>
 *   <li>rotate 旋转</li>
 *   <li>crop 裁剪</li>
 *   <li>blur 模糊</li>
 *   <li>flip 翻转</li>
 *   <li>brightness 亮度</li>
 *   <li>contrast 对比度</li>
 *   <li>border 边框</li>
 *   <li>binarize 二值化</li>
 *   <li>denoise 降噪</li>
 *   <li>erode 腐蚀</li>
 *   <li>dilate 膨胀</li>
 *   <li>batch 批量处理</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ImageProcessorSpiExample                          # 全部能力点
 *   java ImageProcessorSpiExample --type=resize            # 仅测试 resize
 *   java ImageProcessorSpiExample --type=operations        # 全部图像操作
 *   java ImageProcessorSpiExample --input=D:/images/test_1.jpg --output=D:/images/utils
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class ImageProcessorSpiExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;
    private static final String DEFAULT_TYPE = "all";

    /** 默认输入图片路径 */
    private static final String DEFAULT_INPUT = "D:/images/test_1.jpg";
    /** 默认输出目录 */
    private static final String DEFAULT_OUTPUT = "D:/images/utils";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ImageProcessorSpiExample")
                .register("type", "t", "能力点（spi|operations|resize|grayscale|rotate|crop|blur|flip|brightness|contrast|border|binarize|denoise|erode|dilate|batch|all）", DEFAULT_TYPE)
                .register("input", "i", "输入图片路径", DEFAULT_INPUT)
                .register("output", "o", "输出目录", DEFAULT_OUTPUT)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);
        String input = cli.get("input", DEFAULT_INPUT);
        String output = cli.get("output", DEFAULT_OUTPUT);
        ImageProcessorSpiExample example = new ImageProcessorSpiExample();
        boolean passed = example.runTest(type, input, output);
        log.info("[ImageProcessorSpiExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口
     */
    public boolean runTest(String type, String inputPath, String outputPath) {
        if (type == null || type.isEmpty()) type = DEFAULT_TYPE;
        // 确保输出目录存在
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        switch (type.toLowerCase()) {
            case "spi":
                return testSpiAll();
            case "operations":
                return testAllOperations(inputPath, outputPath);
            case "resize":
                return testOperation(inputPath, outputPath, "resize", params("width", 200, "height", 150));
            case "grayscale":
                return testOperation(inputPath, outputPath, "grayscale", Map.of());
            case "rotate":
                return testOperation(inputPath, outputPath, "rotate", params("angle", 90));
            case "crop":
                return testOperation(inputPath, outputPath, "crop", params("x", 50, "y", 50, "width", 200, "height", 150));
            case "blur":
                return testOperation(inputPath, outputPath, "blur", params("sigma", 5));
            case "flip":
                return testOperation(inputPath, outputPath, "flip", params("axis", "h"));
            case "brightness":
                return testOperation(inputPath, outputPath, "brightness", params("value", 50));
            case "contrast":
                return testOperation(inputPath, outputPath, "contrast", params("value", 30));
            case "border":
                return testOperation(inputPath, outputPath, "border", params("width", 10, "color", "#FF0000"));
            case "binarize":
                return testOperation(inputPath, outputPath, "binarize", params("threshold", 128));
            case "denoise":
                return testOperation(inputPath, outputPath, "denoise", params("radius", 1));
            case "erode":
                return testOperation(inputPath, outputPath, "erode", params("kernel", 3));
            case "dilate":
                return testOperation(inputPath, outputPath, "dilate", params("kernel", 3));
            case "batch":
                return testBatch(inputPath, outputPath);
            case "all":
            default:
                return testSpiAll() & testAllOperations(inputPath, outputPath);
        }
    }

    // ==================== SPI 机制测试 ====================

    private boolean testSpiAll() {
        log.info("===== SPI 机制测试 =====");
        boolean passed = true;
        passed &= testDiscover();
        passed &= testSubclass();
        passed &= testProxy();
        passed &= testDegrade();
        passed &= testAllFail();
        log.info("===== SPI 机制测试 {} =====", passed ? "PASSED" : "FAILED");
        return passed;
    }

    /** 能力点1：SPI 自动发现 */
    public static boolean testDiscover() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        List<ImageProcessor> extensions = provider.getNewExtensions("image-processor");
        log.info("[discover] 发现 {} 个 ImageProcessor 实现:", extensions.size());
        boolean passed = !extensions.isEmpty();
        for (ImageProcessor p : extensions) {
            log.info("           name={}, available={}", p.name(), p.available());
            passed &= p.name() != null && !p.name().isEmpty();
        }
        return passed;
    }

    /** 能力点2：自定义子类 */
    public static boolean testSubclass() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        List<ImageProcessor> extensions = provider.getNewExtensions("image-processor");
        boolean found = false;
        for (ImageProcessor p : extensions) {
            if (p instanceof CustomImageProcessor) {
                found = true;
                log.info("[subclass] 发现 CustomImageProcessor (order=200)");
            }
        }
        boolean ordered = !extensions.isEmpty() && "custom".equals(extensions.get(0).name());
        log.info("[subclass] 最高优先级: {} (期望 custom)", extensions.isEmpty() ? "none" : extensions.get(0).name());
        return found && ordered;
    }

    /** 能力点3：代理 */
    public static boolean testProxy() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) { log.warn("[proxy] 代理为 null"); return false; }
        String name = proxy.name();
        ImageProcessor newProxy = provider.getNewExtensionFactory("image-processor");
        boolean newProxyOk = newProxy != null && "custom".equals(newProxy.name());
        log.info("[proxy] name={} (期望 custom), newProxy={}", name, newProxyOk);
        return "custom".equals(name) && newProxyOk;
    }

    /** 能力点4：降级 */
    public static boolean testDegrade() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) { log.warn("[degrade] 代理为 null"); return false; }
        // 用真实图片测试降级
        byte[] image = readTestImage(DEFAULT_INPUT);
        if (image == null) { log.warn("[degrade] 无法读取测试图片"); return false; }
        Map<String, Object> params = new HashMap<>();
        params.put("width", 100);
        params.put("height", 80);
        byte[] result = proxy.process(image, "resize", params);
        boolean passed = result != null && result.length > 0;
        log.info("[degrade] resize result={} bytes, passed={}", result == null ? 0 : result.length, passed);
        return passed;
    }

    /** 能力点5：全败 */
    public static boolean testAllFail() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) { log.warn("[all-fail] 代理为 null"); return false; }
        boolean threw = false;
        try {
            proxy.process(new byte[]{1, 2, 3}, "resize", params("width", 8, "height", 8));
        } catch (Exception e) {
            threw = true;
            log.info("[all-fail] 异常: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return threw;
    }

    // ==================== 图像操作测试 ====================

    /**
     * 测试全部 13 种操作 + batch
     */
    private boolean testAllOperations(String inputPath, String outputPath) {
        log.info("===== 图像操作测试（输入: {}）=====", inputPath);
        byte[] imageData = readTestImage(inputPath);
        if (imageData == null) {
            log.error("无法读取输入图片: {}", inputPath);
            return false;
        }
        log.info("[operations] 输入图片: {} bytes", imageData.length);

        ImageProcessor processor = ImageProcessors.getProcessor();
        log.info("[operations] 使用处理器: {} (available={})", processor.name(), processor.available());

        boolean allPassed = true;

        // 1. resize
        allPassed &= testOp(processor, imageData, outputPath, "resize", params("width", 200, "height", 150));
        // 2. grayscale
        allPassed &= testOp(processor, imageData, outputPath, "grayscale", Map.of());
        // 3. rotate
        allPassed &= testOp(processor, imageData, outputPath, "rotate", params("angle", 90));
        // 4. crop
        allPassed &= testOp(processor, imageData, outputPath, "crop", params("x", 50, "y", 50, "width", 200, "height", 150));
        // 5. blur
        allPassed &= testOp(processor, imageData, outputPath, "blur", params("sigma", 5));
        // 6. flip
        allPassed &= testOp(processor, imageData, outputPath, "flip", params("axis", "h"));
        // 7. brightness
        allPassed &= testOp(processor, imageData, outputPath, "brightness", params("value", 50));
        // 8. contrast
        allPassed &= testOp(processor, imageData, outputPath, "contrast", params("value", 30));
        // 9. border
        allPassed &= testOp(processor, imageData, outputPath, "border", params("width", 10, "color", "#FF0000"));
        // 10. binarize
        allPassed &= testOp(processor, imageData, outputPath, "binarize", params("threshold", 128));
        // 11. denoise
        allPassed &= testOp(processor, imageData, outputPath, "denoise", params("radius", 1));
        // 12. erode
        allPassed &= testOp(processor, imageData, outputPath, "erode", params("kernel", 3));
        // 13. dilate
        allPassed &= testOp(processor, imageData, outputPath, "dilate", params("kernel", 3));

        // 14. processBatch
        allPassed &= testBatch(inputPath, outputPath);

        // 15. 输出格式测试 (jpeg)
        allPassed &= testOp(processor, imageData, outputPath, "resize_jpeg",
                params("width", 100, "height", 80, "format", "jpeg"));

        log.info("===== 图像操作测试 {} =====", allPassed ? "PASSED" : "FAILED");
        return allPassed;
    }

    /**
     * 测试单个操作并输出到文件
     */
    private boolean testOperation(String inputPath, String outputPath, String operation, Map<String, Object> params) {
        byte[] imageData = readTestImage(inputPath);
        if (imageData == null) return false;
        ImageProcessor processor = ImageProcessors.getProcessor();
        return testOp(processor, imageData, outputPath, operation, params);
    }

    /**
     * 执行单个操作测试
     */
    private boolean testOp(ImageProcessor processor, byte[] imageData, String outputPath, String operation, Map<String, Object> params) {
        try {
            byte[] result = processor.process(imageData, operation, params);
            if (result == null || result.length == 0) {
                log.warn("[{}] 结果为空", operation);
                return false;
            }
            // 验证结果是否为有效图像
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.warn("[{}] 结果不是有效图像 ({} bytes)", operation, result.length);
                return false;
            }
            // 保存到输出目录
            String filename = operation.replace("_", "-") + "_" + img.getWidth() + "x" + img.getHeight() + ".png";
            Path outFile = Paths.get(outputPath, filename);
            Files.write(outFile, result);
            log.info("[{}] OK — {}x{} ({} bytes) -> {}", operation, img.getWidth(), img.getHeight(), result.length, outFile);
            return true;
        } catch (Exception e) {
            log.error("[{}] FAILED: {} - {}", operation, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * 测试 processBatch 批量处理
     */
    private boolean testBatch(String inputPath, String outputPath) {
        try {
            byte[] imageData = readTestImage(inputPath);
            if (imageData == null) return false;

            ImageProcessor processor = ImageProcessors.getProcessor();
            // 构造3张图片的批量请求
            byte[][] images = new byte[][]{imageData, imageData, imageData};
            Map<String, Object> params = params("width", 100, "height", 80);

            byte[][] results = processor.processBatch(images, "resize", params);
            if (results == null || results.length != 3) {
                log.warn("[batch] 结果数量不匹配: expected=3, actual={}", results == null ? 0 : results.length);
                return false;
            }

            boolean passed = true;
            for (int i = 0; i < results.length; i++) {
                if (results[i] == null || results[i].length == 0) {
                    log.warn("[batch] 第{}张结果为空", i);
                    passed = false;
                    continue;
                }
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(results[i]));
                if (img == null) {
                    log.warn("[batch] 第{}张不是有效图像", i);
                    passed = false;
                    continue;
                }
                Path outFile = Paths.get(outputPath, "batch_" + i + "_" + img.getWidth() + "x" + img.getHeight() + ".png");
                Files.write(outFile, results[i]);
                log.info("[batch] 第{}张 OK — {}x{} -> {}", i, img.getWidth(), img.getHeight(), outFile);
            }
            log.info("[batch] {}", passed ? "PASSED" : "FAILED");
            return passed;
        } catch (Exception e) {
            log.error("[batch] FAILED: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 读取测试图片
     */
    private static byte[] readTestImage(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                log.warn("图片文件不存在: {}", path);
                return null;
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            log.warn("读取图片失败: {} - {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 构建参数 Map（便捷方法）
     */
    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}
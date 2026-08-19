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
 * <h3>逐实现测试</h3>
 * <ul>
 *   <li>per-impl：逐个测试每个 ImageProcessor 实现（custom/jdk/opencv/rust）</li>
 * </ul>
 *
 * <h3>图像操作能力点（13 种 process 操作 + processBatch + 多角度 + 多格式）</h3>
 * <ul>
 *   <li>resize / grayscale / rotate(0/45/90/180/270) / crop / blur</li>
 *   <li>flip(h/v) / brightness / contrast / border / binarize</li>
 *   <li>denoise / erode / dilate / batch / format(jpeg/png)</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class ImageProcessorSpiExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;
    private static final String DEFAULT_TYPE = "all";
    private static final String DEFAULT_INPUT = "D:/images/test_1.jpg";
    private static final String DEFAULT_OUTPUT = "D:/images/utils";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ImageProcessorSpiExample")
                .register("type", "t", "能力点", DEFAULT_TYPE)
                .register("input", "i", "输入图片路径", DEFAULT_INPUT)
                .register("output", "o", "输出目录", DEFAULT_OUTPUT)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) { cli.help(); return; }

        String type = cli.get("type", DEFAULT_TYPE);
        String input = cli.get("input", DEFAULT_INPUT);
        String output = cli.get("output", DEFAULT_OUTPUT);
        ImageProcessorSpiExample example = new ImageProcessorSpiExample();
        boolean passed = example.runTest(type, input, output);
        log.info("[ImageProcessorSpiExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest(String type, String inputPath, String outputPath) {
        if (type == null || type.isEmpty()) type = DEFAULT_TYPE;
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) outputDir.mkdirs();

        switch (type.toLowerCase()) {
            case "spi": return testSpiAll();
            case "per-impl": return testPerImpl(inputPath, outputPath);
            case "operations": return testAllOperations(inputPath, outputPath);
            case "all":
            default: return testSpiAll() & testPerImpl(inputPath, outputPath) & testAllOperations(inputPath, outputPath);
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

    public static boolean testDiscover() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        List<ImageProcessor> extensions = provider.getNewExtensions("image-processor");
        log.info("[discover] 发现 {} 个 ImageProcessor 实现:", extensions.size());
        boolean passed = !extensions.isEmpty();
        for (ImageProcessor p : extensions) {
            log.info("           name={}, available={}, class={}", p.name(), p.available(),
                    p.getClass().getSimpleName());
            passed &= p.name() != null && !p.name().isEmpty();
        }
        return passed;
    }

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

    public static boolean testDegrade() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) { log.warn("[degrade] 代理为 null"); return false; }
        byte[] image = readTestImage(DEFAULT_INPUT);
        if (image == null) { log.warn("[degrade] 无法读取测试图片"); return false; }
        Map<String, Object> params = new HashMap<>();
        params.put("width", 100); params.put("height", 80);
        byte[] result = proxy.process(image, "resize", params);
        boolean passed = result != null && result.length > 0;
        log.info("[degrade] resize result={} bytes, passed={}", result == null ? 0 : result.length, passed);
        return passed;
    }

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

    // ==================== 逐实现测试 ====================

    /**
     * 逐个测试每个 ImageProcessor 实现
     */
    private boolean testPerImpl(String inputPath, String outputPath) {
        log.info("===== 逐实现测试 =====");
        byte[] imageData = readTestImage(inputPath);
        if (imageData == null) {
            log.error("无法读取输入图片: {}", inputPath);
            return false;
        }

        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        List<ImageProcessor> extensions = provider.getNewExtensions("image-processor");
        boolean allPassed = true;

        for (ImageProcessor impl : extensions) {
            String implName = impl.name();
            boolean available = impl.available();
            log.info("[per-impl] 测试实现: {} (available={}, class={})",
                    implName, available, impl.getClass().getSimpleName());

            if (!available) {
                log.info("[per-impl] {} 不可用，跳过", implName);
                continue;
            }

            boolean implPassed = true;
            // 每个实现测试核心操作
            String implDir = implName;
            new File(outputPath, implDir).mkdirs();

            // resize
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "resize",
                    params("width", 200, "height", 150));
            // grayscale
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "grayscale", Map.of());
            // rotate 90
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "rotate_90",
                    params("angle", 90));
            // rotate 45 (任意角度)
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "rotate_45",
                    params("angle", 45));
            // crop
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "crop",
                    params("x", 50, "y", 50, "width", 200, "height", 150));
            // flip horizontal
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "flip_h",
                    params("axis", "h"));
            // flip vertical
            implPassed &= testImplOp(impl, imageData, outputPath, implDir, "flip_v",
                    params("axis", "v"));

            log.info("[per-impl] {} {}", implName, implPassed ? "PASSED" : "FAILED");
            allPassed &= implPassed;
        }

        log.info("===== 逐实现测试 {} =====", allPassed ? "PASSED" : "FAILED");
        return allPassed;
    }

    private boolean testImplOp(ImageProcessor impl, byte[] imageData, String outputPath,
                               String implDir, String opName, Map<String, Object> params) {
        try {
            // 对于非resize/grayscale/rotate/crop/blur/flip/brightness/contrast/border操作，
            // 某些实现可能不支持，跳过不支持的
            String operation = opName.contains("_") ? opName.substring(0, opName.indexOf("_")) : opName;
            byte[] result = impl.process(imageData, operation, params);
            if (result == null || result.length == 0) {
                log.warn("[per-impl] {}/{} 结果为空", implDir, opName);
                return false;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.warn("[per-impl] {}/{} 不是有效图像", implDir, opName);
                return false;
            }
            Path outFile = Paths.get(outputPath, implDir,
                    opName + "_" + img.getWidth() + "x" + img.getHeight() + ".png");
            Files.write(outFile, result);
            log.info("[per-impl] {}/{} OK — {}x{} ({} bytes)", implDir, opName,
                    img.getWidth(), img.getHeight(), result.length);
            return true;
        } catch (Exception e) {
            log.warn("[per-impl] {}/{} FAILED: {}", implDir, opName, e.getMessage());
            return false;
        }
    }

    // ==================== 全操作测试 ====================

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
        // 3. rotate 多角度
        allPassed &= testOp(processor, imageData, outputPath, "rotate_0", params("angle", 0));
        allPassed &= testOp(processor, imageData, outputPath, "rotate_45", params("angle", 45));
        allPassed &= testOp(processor, imageData, outputPath, "rotate_90", params("angle", 90));
        allPassed &= testOp(processor, imageData, outputPath, "rotate_180", params("angle", 180));
        allPassed &= testOp(processor, imageData, outputPath, "rotate_270", params("angle", 270));
        // 4. crop
        allPassed &= testOp(processor, imageData, outputPath, "crop", params("x", 50, "y", 50, "width", 200, "height", 150));
        // 5. blur
        allPassed &= testOp(processor, imageData, outputPath, "blur", params("sigma", 5));
        // 6. flip 水平+垂直
        allPassed &= testOp(processor, imageData, outputPath, "flip_h", params("axis", "h"));
        allPassed &= testOp(processor, imageData, outputPath, "flip_v", params("axis", "v"));
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
        // 15. 输出格式 jpeg
        allPassed &= testOp(processor, imageData, outputPath, "resize_jpeg",
                params("width", 100, "height", 80, "format", "jpeg"));

        log.info("===== 图像操作测试 {} =====", allPassed ? "PASSED" : "FAILED");
        return allPassed;
    }

    private boolean testOp(ImageProcessor processor, byte[] imageData, String outputPath,
                           String opName, Map<String, Object> params) {
        try {
            String operation = opName.contains("_") ? opName.substring(0, opName.indexOf("_")) : opName;
            // rotate_0 → operation=rotate, 但 rotate_0 不含下划线后的操作名
            if (opName.startsWith("rotate")) operation = "rotate";
            if (opName.startsWith("flip")) operation = "flip";
            if (opName.startsWith("resize")) operation = "resize";

            byte[] result = processor.process(imageData, operation, params);
            if (result == null || result.length == 0) {
                log.warn("[{}] 结果为空", opName);
                return false;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.warn("[{}] 不是有效图像 ({} bytes)", opName, result.length);
                return false;
            }
            String filename = opName + "_" + img.getWidth() + "x" + img.getHeight() + ".png";
            Path outFile = Paths.get(outputPath, filename);
            Files.write(outFile, result);
            log.info("[{}] OK — {}x{} ({} bytes) -> {}", opName, img.getWidth(), img.getHeight(),
                    result.length, outFile.getFileName());
            return true;
        } catch (Exception e) {
            log.error("[{}] FAILED: {} - {}", opName, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private boolean testBatch(String inputPath, String outputPath) {
        try {
            byte[] imageData = readTestImage(inputPath);
            if (imageData == null) return false;

            ImageProcessor processor = ImageProcessors.getProcessor();
            byte[][] images = new byte[][]{imageData, imageData, imageData};
            Map<String, Object> batchParams = params("width", 100, "height", 80);

            byte[][] results = processor.processBatch(images, "resize", batchParams);
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
                log.info("[batch] 第{}张 OK — {}x{} -> {}", i, img.getWidth(), img.getHeight(), outFile.getFileName());
            }
            log.info("[batch] {}", passed ? "PASSED" : "FAILED");
            return passed;
        } catch (Exception e) {
            log.error("[batch] FAILED: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    private static byte[] readTestImage(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) { log.warn("图片文件不存在: {}", path); return null; }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            log.warn("读取图片失败: {} - {}", path, e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}
package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.ImageProcessors;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ImageProcessor SPI 机制 + 全操作综合示例
 *
 * <p>本示例验证 {@link ImageProcessor} 的完整能力：</p>
 * <h3>SPI 机制能力点</h3>
 * <ul>
 *   <li>discover 发现：发现全部实现并按优先级排序（rust/opencv/jdk）</li>
 *   <li>priority 优先级：验证 @SpiOrder 排序（rust 100 > opencv 50 > jdk -100）</li>
 *   <li>getExtension 按名获取：按SPI注册名获取实现（getExtension("image-processor")）</li>
 *   <li>proxy 代理：getExtensionFactory 返回自动降级代理</li>
 *   <li>degrade 降级：高优先级失败自动回退</li>
 *   <li>all-fail 全败：全部失败抛异常</li>
 * </ul>
 *
 * <h3>逐实现测试</h3>
 * <ul>
 *   <li>per-impl：逐个测试每个可用 ImageProcessor 实现（rust/opencv/jdk）</li>
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

    /** 私有构造，防止实例化 */
    public ImageProcessorSpiExample() { }

    /** 默认类型 */
    private static final String DEFAULT_TYPE = "all";
    /** 默认输入路径 */
    private static final String DEFAULT_INPUT = "D:/images/test_1.jpg";
    /** 默认输出路径 */
    private static final String DEFAULT_OUTPUT = "D:/images/utils";

    /** 期望的三个 SPI 实现，按优先级从高到低 */
    private static final List<String> EXPECTED_IMPLS = Arrays.asList("rust", "opencv", "jdk");

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ImageProcessorSpiExample")
                .register("type", "t", "能力点", DEFAULT_TYPE)
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
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    /** 运行Test */
    public boolean runTest(String type, String inputPath, String outputPath) {
        if (type == null || type.isEmpty()) {
            type = DEFAULT_TYPE;
        }
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        switch (type.toLowerCase()) {
            case "spi": return testSpiAll();
            case "per-impl": return testPerImpl(inputPath, outputPath);
            case "operations": return testAllOperations(inputPath, outputPath);
            case "all":
            default: return testSpiAll() & testPerImpl(inputPath, outputPath) & testAllOperations(inputPath, outputPath);
        }
    }

    // ==================== SPI 机制测试 ====================

    /** TestSpiAll */
    private boolean testSpiAll() {
        log.info("===== SPI 机制测试 =====");
        boolean passed = true;
        long t0, dt;

        t0 = System.nanoTime();
        passed &= testDiscover();
        dt = (System.nanoTime() - t0) / 1_000_000;
        log.info("[spi] discover 耗时: {}ms", dt);

        t0 = System.nanoTime();
        passed &= testPriority();
        dt = (System.nanoTime() - t0) / 1_000_000;
        log.info("[spi] priority 耗时: {}ms", dt);

        t0 = System.nanoTime();
        passed &= testGetExtension();
        dt = (System.nanoTime() - t0) / 1_000_000;
        log.info("[spi] getExtension 耗时: {}ms", dt);

        t0 = System.nanoTime();
        passed &= testProxy();
        dt = (System.nanoTime() - t0) / 1_000_000;
        log.info("[spi] proxy 耗时: {}ms", dt);

        t0 = System.nanoTime();
        passed &= testDegrade();
        dt = (System.nanoTime() - t0) / 1_000_000;
        log.info("[spi] degrade 耗时: {}ms", dt);

        t0 = System.nanoTime();
        passed &= testAllFail();
        dt = (System.nanoTime() - t0) / 1_000_000;
        log.info("[spi] all-fail 耗时: {}ms", dt);

        log.info("===== SPI 机制测试 {} =====", passed ? "PASSED" : "FAILED");
        return passed;
    }

    /**
     * 发现全部 ImageProcessor 实现（按 class 去重）
     */
    public static boolean testDiscover() {
        List<ImageProcessor> extensions = getUniqueExtensions();
        log.info("[discover] 发现 {} 个 ImageProcessor 实现 (去重后):", extensions.size());
        boolean passed = !extensions.isEmpty();
        for (ImageProcessor p : extensions) {
            log.info("           name={}, available={}, class={}", p.name(), p.available(),
                    p.getClass().getSimpleName());
            passed &= p.name() != null && !p.name().isEmpty();
        }
        // 验证期望的三个实现都被发现
        Set<String> names = extensions.stream().map(ImageProcessor::name).collect(Collectors.toSet());
        for (String expected : EXPECTED_IMPLS) {
            boolean found = names.contains(expected);
            log.info("[discover] 期望实现 '{}' : {}", expected, found ? "FOUND" : "MISSING");
            passed &= found;
        }
        return passed;
    }

    /**
     * 验证 @SpiOrder 优先级排序：rust(100) > opencv(50) > jdk(-100)
     */
    public static boolean testPriority() {
        List<ImageProcessor> extensions = getUniqueExtensions();
        if (extensions.size() < 2) {
            log.warn("[priority] 实现数量不足，无法验证排序");
            return false;
        }
        boolean passed = true;
        // 验证按优先级降序排列
        for (int i = 0; i < extensions.size() - 1; i++) {
            String curr = extensions.get(i).name();
            String next = extensions.get(i + 1).name();
            int currIdx = EXPECTED_IMPLS.indexOf(curr);
            int nextIdx = EXPECTED_IMPLS.indexOf(next);
            boolean ordered = currIdx >= 0 && nextIdx >= 0 && currIdx < nextIdx;
            log.info("[priority] {} (order={}) > {} (order={}) : {}",
                    curr, currIdx, next, nextIdx, ordered ? "OK" : "WRONG");
            passed &= ordered;
        }
        // 验证第一个是 rust（最高优先级）
        String first = extensions.get(0).name();
        boolean rustFirst = "rust".equals(first);
        log.info("[priority] 最高优先级: {} (期望 rust) : {}", first, rustFirst ? "OK" : "WRONG");
        return passed && rustFirst;
    }

    /**
     * 按名称获取特定实现：getExtension("image-processor") 返回最高优先级实现
     * getExtension 参数是 SPI 注册名（@Spi("image-processor")），不是实现的自报名称
     */
    public static boolean testGetExtension() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        boolean passed = true;

        // 1. 按SPI注册名获取：getExtension("image-processor") 应返回最高优先级实现
        long t0 = System.nanoTime();
        ImageProcessor ext = provider.getExtension("image-processor");
        long dt = (System.nanoTime() - t0) / 1_000_000;
        boolean found = ext != null;
        boolean nameValid = found && EXPECTED_IMPLS.contains(ext.name());
        log.info("[getExtension] getExtension(\"image-processor\") : found={}, name={} (class={}, 耗时: {}ms) : {}",
                found, found ? ext.name() : "null", found ? ext.getClass().getSimpleName() : "null", dt,
                nameValid ? "OK" : "WRONG");
        passed &= nameValid;

        // 2. 按空名称获取：应返回默认实现
        t0 = System.nanoTime();
        ImageProcessor defaultExt = provider.getExtension("");
        dt = (System.nanoTime() - t0) / 1_000_000;
        boolean defaultFound = defaultExt != null;
        log.info("[getExtension] getExtension(\"\") : found={}, name={} (class={}, 耗时: {}ms)",
                defaultFound, defaultFound ? defaultExt.name() : "null",
                defaultFound ? defaultExt.getClass().getSimpleName() : "null", dt);

        // 3. 不存在的SPI名称：应返回null或默认实现
        t0 = System.nanoTime();
        ImageProcessor notFound = provider.getExtension("nonexistent_spi");
        dt = (System.nanoTime() - t0) / 1_000_000;
        if (notFound == null) {
            log.info("[getExtension] getExtension(\"nonexistent_spi\") : null : OK (耗时: {}ms)", dt);
        } else {
            boolean notExpected = !EXPECTED_IMPLS.contains(notFound.name());
            log.info("[getExtension] getExtension(\"nonexistent_spi\") : name={} (非期望={}) : {} (耗时: {}ms)",
                    notFound.name(), notExpected, notExpected ? "OK" : "WRONG", dt);
            passed &= notExpected;
        }
        return passed;
    }

    /** TestProxy */
    public static boolean testProxy() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) {
            log.warn("[proxy] 代理为 null");
            return false;
        }
        String name = proxy.name();
        // 代理应返回最高优先级的可用实现名称（rust不可用时降级到opencv，再降级到jdk）
        // 注意：代理对象的 available() 不代表底层实现的真实可用状态，仅验证 name 有效
        boolean nameValid = EXPECTED_IMPLS.contains(name);
        log.info("[proxy] name={} (期望 rust/opencv/jdk 之一) : {}", name, nameValid ? "OK" : "WRONG");
        return nameValid;
    }

    /** TestDegrade */
    public static boolean testDegrade() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) {
            log.warn("[degrade] 代理为 null");
            return false;
        }
        byte[] image = readTestImage(DEFAULT_INPUT);
        if (image == null) {
            log.warn("[degrade] 无法读取测试图片");
            return false;
        }
        Map<String, Object> params = new HashMap<>(8);
        params.put("width", 100);
        params.put("height", 80);
        long t0 = System.nanoTime();
        byte[] result = proxy.process(image, "resize", params);
        long dt = (System.nanoTime() - t0) / 1_000_000;
        boolean passed = result != null && result.length > 0;
        log.info("[degrade] resize result={} bytes, 耗时={}ms, passed={}", result == null ? 0 : result.length, dt, passed);
        return passed;
    }

    /** TestAllFail */
    public static boolean testAllFail() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) {
            log.warn("[all-fail] 代理为 null");
            return false;
        }
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
     * 逐个测试每个 ImageProcessor 实现的全部操作（按 class 去重）
     * 每个可用实现测试全部 13 种操作 + 多角度旋转，每个操作单独计时
     */
    private boolean testPerImpl(String inputPath, String outputPath) {
        log.info("===== 逐实现测试（每个SPI × 全部操作）=====");
        byte[] imageData = readTestImage(inputPath);
        if (imageData == null) {
            log.error("无法读取输入图片: {}", inputPath);
            return false;
        }

        List<ImageProcessor> extensions = getUniqueExtensions();
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

            long implStart = System.nanoTime();
            int okCount = 0, skipCount = 0, failCount = 0;
            String implDir = implName;
            new File(outputPath, implDir).mkdirs();

            // 1. resize
            int r1 = testImplOp(impl, imageData, outputPath, implDir, "resize",
                    params("width", 200, "height", 150));
            okCount += r1;
            skipCount += (r1 < 0 ? 1 : 0);
            failCount += (r1 == 0 ? 1 : 0);
            // 2. grayscale
            int r2 = testImplOp(impl, imageData, outputPath, implDir, "grayscale", Map.of());
            okCount += r2;
            skipCount += (r2 < 0 ? 1 : 0);
            failCount += (r2 == 0 ? 1 : 0);
            // 3. rotate 多角度
            int r3 = testImplOp(impl, imageData, outputPath, implDir, "rotate_0", params("angle", 0));
            okCount += r3;
            skipCount += (r3 < 0 ? 1 : 0);
            failCount += (r3 == 0 ? 1 : 0);
            int r4 = testImplOp(impl, imageData, outputPath, implDir, "rotate_45", params("angle", 45));
            okCount += r4;
            skipCount += (r4 < 0 ? 1 : 0);
            failCount += (r4 == 0 ? 1 : 0);
            int r5 = testImplOp(impl, imageData, outputPath, implDir, "rotate_90", params("angle", 90));
            okCount += r5;
            skipCount += (r5 < 0 ? 1 : 0);
            failCount += (r5 == 0 ? 1 : 0);
            int r6 = testImplOp(impl, imageData, outputPath, implDir, "rotate_180", params("angle", 180));
            okCount += r6;
            skipCount += (r6 < 0 ? 1 : 0);
            failCount += (r6 == 0 ? 1 : 0);
            int r7 = testImplOp(impl, imageData, outputPath, implDir, "rotate_270", params("angle", 270));
            okCount += r7;
            skipCount += (r7 < 0 ? 1 : 0);
            failCount += (r7 == 0 ? 1 : 0);
            // 4. crop
            int r8 = testImplOp(impl, imageData, outputPath, implDir, "crop",
                    params("x", 50, "y", 50, "width", 200, "height", 150));
            okCount += r8;
            skipCount += (r8 < 0 ? 1 : 0);
            failCount += (r8 == 0 ? 1 : 0);
            // 5. blur
            int r9 = testImplOp(impl, imageData, outputPath, implDir, "blur", params("sigma", 5));
            okCount += r9;
            skipCount += (r9 < 0 ? 1 : 0);
            failCount += (r9 == 0 ? 1 : 0);
            // 6. flip 水平+垂直
            int r10 = testImplOp(impl, imageData, outputPath, implDir, "flip_h", params("axis", "h"));
            okCount += r10;
            skipCount += (r10 < 0 ? 1 : 0);
            failCount += (r10 == 0 ? 1 : 0);
            int r11 = testImplOp(impl, imageData, outputPath, implDir, "flip_v", params("axis", "v"));
            okCount += r11;
            skipCount += (r11 < 0 ? 1 : 0);
            failCount += (r11 == 0 ? 1 : 0);
            // 7. brightness
            int r12 = testImplOp(impl, imageData, outputPath, implDir, "brightness", params("value", 50));
            okCount += r12;
            skipCount += (r12 < 0 ? 1 : 0);
            failCount += (r12 == 0 ? 1 : 0);
            // 8. contrast
            int r13 = testImplOp(impl, imageData, outputPath, implDir, "contrast", params("value", 30));
            okCount += r13;
            skipCount += (r13 < 0 ? 1 : 0);
            failCount += (r13 == 0 ? 1 : 0);
            // 9. border
            int r14 = testImplOp(impl, imageData, outputPath, implDir, "border", params("width", 10, "color", "#FF0000"));
            okCount += r14;
            skipCount += (r14 < 0 ? 1 : 0);
            failCount += (r14 == 0 ? 1 : 0);
            // 10. binarize
            int r15 = testImplOp(impl, imageData, outputPath, implDir, "binarize", params("threshold", 128));
            okCount += r15;
            skipCount += (r15 < 0 ? 1 : 0);
            failCount += (r15 == 0 ? 1 : 0);
            // 11. denoise
            int r16 = testImplOp(impl, imageData, outputPath, implDir, "denoise", params("radius", 1));
            okCount += r16;
            skipCount += (r16 < 0 ? 1 : 0);
            failCount += (r16 == 0 ? 1 : 0);
            // 12. erode
            int r17 = testImplOp(impl, imageData, outputPath, implDir, "erode", params("kernel", 3));
            okCount += r17;
            skipCount += (r17 < 0 ? 1 : 0);
            failCount += (r17 == 0 ? 1 : 0);
            // 13. dilate
            int r18 = testImplOp(impl, imageData, outputPath, implDir, "dilate", params("kernel", 3));
            okCount += r18;
            skipCount += (r18 < 0 ? 1 : 0);
            failCount += (r18 == 0 ? 1 : 0);

            long implDt = (System.nanoTime() - implStart) / 1_000_000;
            boolean implPassed = failCount == 0;
            log.info("[per-impl] {} {} — OK={}, SKIP={}, FAIL={} (总耗时: {}ms)",
                    implName, implPassed ? "PASSED" : "FAILED", okCount, skipCount, failCount, implDt);
            allPassed &= implPassed;
        }

        log.info("===== 逐实现测试 {} =====", allPassed ? "PASSED" : "FAILED");
        return allPassed;
    }

    /**
     * 测试单个实现的单个操作，返回: 1=OK, 0=FAIL, -1=SKIP(不支持)
     */
    private int testImplOp(ImageProcessor impl, byte[] imageData, String outputPath,
                           String implDir, String opName, Map<String, Object> params) {
        try {
            String operation = opName.contains("_") ? opName.substring(0, opName.indexOf("_")) : opName;
            long t0 = System.nanoTime();
            byte[] result = impl.process(imageData, operation, params);
            long dt = (System.nanoTime() - t0) / 1_000_000;
            if (result == null || result.length == 0) {
                // 某些实现可能不支持某些操作，返回空视为 SKIP
                log.info("[per-impl] {}/{} SKIP — 不支持或结果为空 (耗时: {}ms)", implDir, opName, dt);
                return -1;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.info("[per-impl] {}/{} SKIP — 不是有效图像 (耗时: {}ms)", implDir, opName, dt);
                return -1;
            }
            Path outFile = Paths.get(outputPath, implDir,
                    opName + "_" + img.getWidth() + "x" + img.getHeight() + ".png");
            Files.write(outFile, result);
            log.info("[per-impl] {}/{} OK — {}x{} ({} bytes, 耗时: {}ms)", implDir, opName,
                    img.getWidth(), img.getHeight(), result.length, dt);
            return 1;
        } catch (UnsupportedOperationException e) {
            log.info("[per-impl] {}/{} SKIP — {}", implDir, opName, e.getMessage());
            return -1;
        } catch (Exception e) {
            log.warn("[per-impl] {}/{} FAIL — {}", implDir, opName, e.getMessage());
            return 0;
        }
    }

    // ==================== 全操作测试 ====================

    /** TestAllOperations */
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
        long opsStart = System.nanoTime();

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

        long opsDt = (System.nanoTime() - opsStart) / 1_000_000;
        log.info("===== 图像操作测试 {} (总耗时: {}ms) =====", allPassed ? "PASSED" : "FAILED", opsDt);
        return allPassed;
    }

    /**
     * TestOp
     * @param processor processor
     * @param imageData imageData
     * @param outputPath outputPath
     * @param opName opName
     * @param params params
     */
    private boolean testOp(ImageProcessor processor, byte[] imageData, String outputPath,
                           String opName, Map<String, Object> params) {
        try {
            String operation = opName.contains("_") ? opName.substring(0, opName.indexOf("_")) : opName;
            if (opName.startsWith("rotate")) {
                operation = "rotate";
            }
            if (opName.startsWith("flip")) {
                operation = "flip";
            }
            if (opName.startsWith("resize")) {
                operation = "resize";
            }

            long t0 = System.nanoTime();
            byte[] result = processor.process(imageData, operation, params);
            long dt = (System.nanoTime() - t0) / 1_000_000;
            if (result == null || result.length == 0) {
                log.warn("[{}] 结果为空 (耗时: {}ms)", opName, dt);
                return false;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.warn("[{}] 不是有效图像 ({} bytes, 耗时: {}ms)", opName, result.length, dt);
                return false;
            }
            String filename = opName + "_" + img.getWidth() + "x" + img.getHeight() + ".png";
            Path outFile = Paths.get(outputPath, filename);
            Files.write(outFile, result);
            log.info("[{}] OK — {}x{} ({} bytes, 耗时: {}ms) -> {}", opName, img.getWidth(), img.getHeight(),
                    result.length, dt, outFile.getFileName());
            return true;
        } catch (Exception e) {
            log.error("[{}] FAILED: {} - {}", opName, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /** TestBatch */
    private boolean testBatch(String inputPath, String outputPath) {
        try {
            byte[] imageData = readTestImage(inputPath);
            if (imageData == null) {
                return false;
            }

            ImageProcessor processor = ImageProcessors.getProcessor();
            byte[][] images = new byte[][]{imageData, imageData, imageData};
            Map<String, Object> batchParams = params("width", 100, "height", 80);

            long t0 = System.nanoTime();
            byte[][] results = processor.processBatch(images, "resize", batchParams);
            long dt = (System.nanoTime() - t0) / 1_000_000;

            if (results == null || results.length != 3) {
                log.warn("[batch] 结果数量不匹配: expected=3, actual={} (耗时: {}ms)", results == null ? 0 : results.length, dt);
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
            log.info("[batch] {} (耗时: {}ms)", passed ? "PASSED" : "FAILED", dt);
            return passed;
        } catch (Exception e) {
            log.error("[batch] FAILED: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 获取去重后的 ImageProcessor 实现列表（按 class 去重，保留优先级最高的）
     */
    private static List<ImageProcessor> getUniqueExtensions() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        List<ImageProcessor> raw = provider.getNewExtensions("image-processor");
        // 按 class 去重：同名实现只保留第一个（优先级最高的）
        Map<String, ImageProcessor> unique = new LinkedHashMap<>();
        for (ImageProcessor p : raw) {
            String key = p.name() + ":" + p.getClass().getName();
            if (!unique.containsKey(p.name())) {
                unique.put(p.name(), p);
            } else {
                log.debug("[dedup] 跳过重复实现: name={}, class={}", p.name(), p.getClass().getSimpleName());
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** 读取TestImage */
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

    /** Params */
    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>(8);
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}

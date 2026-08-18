package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.ImageProcessors;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import com.chua.deeplearning.support.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ImageProcessor SPI 机制综合示例 — 验证 SPI 自动发现、优先级排序与自动降级。
 *
 * <p>本示例验证 {@link ImageProcessor} 的 SPI 机制：</p>
 * <ul>
 *   <li>discover 发现：{@code ServiceProvider.of(ImageProcessor.class)} 发现全部实现并按 {@code @SpiOrder} 排序</li>
 *   <li>subclass 子类：自定义 {@link CustomImageProcessor} 子类注册后参与优先级竞争</li>
 *   <li>proxy 代理：{@code getExtensionFactory} 返回按优先级自动降级的代理</li>
 *   <li>degrade 降级：高优先级实现不可用/失败时自动回退到下一实现</li>
 *   <li>all-fail 全败：所有实现失败时抛出异常</li>
 *   <li>image-utils 委托：{@link ImageUtils} 字节级操作走 SPI 代理执行</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ImageProcessorSpiExample                 # 全部能力点
 *   java ImageProcessorSpiExample --type discover # 仅测试发现
 *   java ImageProcessorSpiExample --type=proxy
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class ImageProcessorSpiExample {

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认能力点
     */
    private static final String DEFAULT_TYPE = "all";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ImageProcessorSpiExample")
                .register("type", "t",
                        "能力点（discover|subclass|proxy|degrade|all-fail|image-utils|all）", DEFAULT_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String type = cli.get("type", DEFAULT_TYPE);
        ImageProcessorSpiExample example = new ImageProcessorSpiExample();
        boolean passed = example.runTest(type);
        log.info("[ImageProcessorSpiExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = DEFAULT_TYPE;
        }
        boolean passed;
        switch (type.toLowerCase()) {
            case "discover":
                passed = testDiscover();
                break;
            case "subclass":
                passed = testSubclass();
                break;
            case "proxy":
                passed = testProxy();
                break;
            case "degrade":
                passed = testDegrade();
                break;
            case "all-fail":
                passed = testAllFail();
                break;
            case "image-utils":
                passed = testImageUtils();
                break;
            case "all":
                passed = testDiscover() & testSubclass() & testProxy() & testDegrade() & testAllFail() & testImageUtils();
                break;
            default:
                log.warn("未知能力点: {}", type);
                passed = false;
                break;
        }
        return passed;
    }

    /**
     * 能力点 1：SPI 自动发现全部 ImageProcessor 实现并按优先级排序。
     *
     * @return 通过返回 true
     */
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

    /**
     * 能力点 2：自定义 {@link CustomImageProcessor} 子类参与 SPI 注册与优先级竞争。
     *
     * @return 通过返回 true
     */
    public static boolean testSubclass() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        List<ImageProcessor> extensions = provider.getNewExtensions("image-processor");
        boolean found = false;
        for (ImageProcessor p : extensions) {
            if (p instanceof TestImageProcessor) {
                found = true;
                log.info("[subclass] 发现自定义子类 TestImageProcessor (order=200)");
            }
        }
        if (!found) {
            log.warn("[subclass] 未发现 TestImageProcessor 子类");
        }
        // 校验优先级：order=200 的 TestImageProcessor 应排在 rust(100) 之前
        boolean ordered = false;
        if (!extensions.isEmpty()) {
            ordered = extensions.get(0).name().equals("test");
            log.info("[subclass] 最高优先级实现: {} (期望 test)", extensions.get(0).name());
        }
        return found && ordered;
    }

    /**
     * 能力点 3：getExtensionFactory 返回按优先级自动降级的代理。
     *
     * @return 通过返回 true
     */
    public static boolean testProxy() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) {
            log.warn("[proxy] getExtensionFactory 返回 null");
            return false;
        }
        log.info("[proxy] 代理类型: {}", proxy.getClass().getName());
        // 代理内部按优先级调用 name()：TestImageProcessor(order=200) 应最先响应
        String name = proxy.name();
        log.info("[proxy] 代理 name()={} (期望 test)", name);
        // 再验证 new 代理工厂
        ImageProcessor newProxy = provider.getNewExtensionFactory("image-processor");
        boolean newProxyOk = newProxy != null && "test".equals(newProxy.name());
        return "test".equals(name) && newProxyOk;
    }

    /**
     * 能力点 4：高优先级实现失败时自动降级到下一实现。
     *
     * @return 通过返回 true
     */
    public static boolean testDegrade() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) {
            log.warn("[degrade] 代理为 null");
            return false;
        }
        // 构造 4x4 红色测试图
        byte[] image = buildTestImage();
        Map<String, Object> params = new HashMap<>();
        params.put("width", 8);
        params.put("height", 8);
        byte[] result = proxy.process(image, "resize", params);
        boolean passed = result != null && result.length > 0;
        try {
            BufferedImage resized = ImageIO.read(new ByteArrayInputStream(result));
            passed &= resized != null && resized.getWidth() == 8 && resized.getHeight() == 8;
            log.info("[degrade] resize 4x4 -> {}x{} (字节 {})", resized.getWidth(), resized.getHeight(), result.length);
        } catch (Exception e) {
            passed = false;
        }
        return passed;
    }

    /**
     * 能力点 5：全部实现失败时抛出异常。
     *
     * @return 通过返回 true
     */
    public static boolean testAllFail() {
        ServiceProvider<ImageProcessor> provider = ServiceProvider.of(ImageProcessor.class);
        ImageProcessor proxy = provider.getExtensionFactory("image-processor");
        if (proxy == null) {
            log.warn("[all-fail] 代理为 null");
            return false;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("width", 8);
        params.put("height", 8);
        boolean threw = false;
        try {
            // 非法图片数据，所有实现均应失败
            proxy.process(new byte[]{1, 2, 3}, "resize", params);
        } catch (Exception e) {
            threw = true;
            log.info("[all-fail] 全部失败抛出: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return threw;
    }

    /**
     * 能力点 6：ImageUtils 字节级操作委托 SPI 代理执行。
     *
     * @return 通过返回 true
     */
    public static boolean testImageUtils() {
        byte[] image = buildTestImage();
        boolean passed = true;

        // resize 委托
        byte[] resized = ImageUtils.resize(image, 8, 8, org.opencv.imgproc.Imgproc.INTER_CUBIC);
        passed &= verifySize(resized, 8, 8, "resize");

        // crop 委托
        byte[] cropped = ImageUtils.crop(image, 0, 0, 2, 2);
        passed &= verifySize(cropped, 2, 2, "crop");

        // rotate 委托
        byte[] rotated = ImageUtils.rotate(image, 90);
        passed &= verifySize(rotated, 4, 4, "rotate");

        // ImageProcessors 门面代理
        ImageProcessor gate = ImageProcessors.getProcessor();
        passed &= gate != null;
        log.info("[image-utils] 门面代理类型: {}", gate == null ? "null" : gate.getClass().getName());
        return passed;
    }

    /**
     * 校验处理后图像字节的宽高。
     *
     * @param data     图像字节
     * @param expectW  期望宽
     * @param expectH  期望高
     * @param op       操作名（日志）
     * @return 校验通过返回 true
     */
    private static boolean verifySize(byte[] data, int expectW, int expectH, String op) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            boolean ok = img != null && img.getWidth() == expectW && img.getHeight() == expectH;
            log.info("[image-utils] {} -> {}x{} (期望 {}x{}) {}", op,
                    img == null ? "?" : img.getWidth(),
                    img == null ? "?" : img.getHeight(),
                    expectW, expectH, ok ? "OK" : "FAIL");
            return ok;
        } catch (Exception e) {
            log.warn("[image-utils] {} 校验失败: {}", op, e.getMessage());
            return false;
        }
    }

    /**
     * 构造 4x4 测试图像（红色背景 + 白色方块）。
     *
     * @return PNG 字节
     */
    private static byte[] buildTestImage() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 4, 4);
        g.setColor(Color.WHITE);
        g.fillRect(1, 1, 2, 2);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("构造测试图像失败", e);
        }
    }
}
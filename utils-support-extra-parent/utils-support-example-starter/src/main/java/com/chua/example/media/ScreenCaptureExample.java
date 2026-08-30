package com.chua.example.media;

import com.chua.common.support.media.codec.ScreenCature;
import org.bytedeco.javacv.Frame;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 屏幕采集综合示例 — 基于 ScreenCature SPI，支持全部实现切换与自检。
 *
 * <p>通过命令行参数指定 {@code @Spi} 类型，自检覆盖基础能力矩阵。</p>
 *
 * <h2>用法</h2>
 * <pre>
 * # 默认 javacv 实现，640x480，30fps，采集 10 帧
 * java ScreenCaptureExample
 *
 * # 指定采集参数
 * java ScreenCaptureExample --width 1920 --height 1080 --fps 60 --frames 30
 *
 * # 自检模式：初始化 → 采集 5 帧 → 关闭
 * java ScreenCaptureExample --test
 *
 * # 保存采集帧到目录
 * java ScreenCaptureExample --output /tmp/screen-frames
 * </pre>
 *
 * <h2>SPI 类型与能力</h2>
 * <table border="1">
 * <tr><th>--type</th><th>实现类</th><th>平台</th><th>零拷贝</th></tr>
 * <tr><td>javacv</td><td>JavaCVScreenCapture</td><td>Win/Linux/macOS</td><td>✅</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ScreenCaptureExample {
    private ScreenCaptureExample() { }

    /** runDemo 参数 */
    private static record DemoArgs(String captureType, int width, int height, int fps, int frameCount, File outputDir) {}


    /**
     * 默认采集器类型
     */
    private static final String DEFAULT_CAPTURE_TYPE = "javacv";

    /**
     * 默认采集宽度
     */
    private static final int DEFAULT_WIDTH = 640;

    /**
     * 默认采集高度
     */
    private static final int DEFAULT_HEIGHT = 480;

    /**
     * 默认帧率
     */
    private static final int DEFAULT_FPS = 30;

    /**
     * 默认采集帧数
     */
    private static final int DEFAULT_FRAME_COUNT = 10;

    /**
     * 自检模式采集帧数
     */
    private static final int TEST_FRAME_COUNT = 5;

    /**
     * 通过 SPI 创建 ScreenCature 实例。
     *
     * @param captureType 采集器 SPI 类型标识
     * @return ScreenCature 实例，创建失败返回 null
     */
    private static ScreenCature createScreenCapture(String captureType) {
        try {
            ScreenCature capture = ServiceProvider.of(ScreenCature.class)
                    .getNewExtension(captureType);
            if (capture == null) {
                log.error("[ERROR] 未找到 ScreenCature 实现: {}", captureType);
                return null;
            }
            return capture;
        } catch (Exception e) {
            log.error("[ERROR] 创建采集器失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存 BufferedImage 到文件。
     *
     * @param image     图像
     * @param outputDir 输出目录
     * @param index     帧序号
     * @return 保存的文件，失败返回 null
     */
    private static File saveFrame(BufferedImage image, File outputDir, int index) {
        File file = new File(outputDir, "frame_" + String.format("%04d", index) + ".png");
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            ImageIO.write(image, "png", file);
            return file;
        } catch (IOException e) {
            log.error("[ERROR] 保存帧失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 把 JavaCV Frame 转为 BufferedImage（BGRA → TYPE_3BYTE_BGR）。
     */
    private static BufferedImage frameToBufferedImage(Frame frame, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        if (frame.image != null && frame.image.length > 0 && frame.image[0] != null) {
            java.nio.ByteBuffer buf = (java.nio.ByteBuffer) frame.image[0];
            byte[] pixels = new byte[buf.capacity()];
            buf.get(pixels);
            image.getRaster().setDataElements(0, 0, width, height, pixels);
        }
        return image;
    }

    /**
     * 运行自检模式。
     *
     * @param captureType 采集器类型
     * @param width       采集宽度
     * @param height      采集高度
     * @param fps         帧率
     * @return true 表示自检通过
     */
    private static boolean runTest(String captureType, int width, int height, int fps) {
        log.info("===== ScreenCaptureExample --test [type={}, {}x{}@{}fps] =====",
                captureType, width, height, fps);

        ScreenCature capture = createScreenCapture(captureType);
        if (capture == null) {
            log.error("[FAIL] 无法创建采集器");
            return false;
        }

        if (!capture.init(width, height, fps)) {
            log.error("[FAIL] 采集器初始化失败");
            capture.close();
            return false;
        }

        int successCount = 0;
        for (int i = 0; i < TEST_FRAME_COUNT; i++) {
            Frame frame = capture.grabFrame();
            if (frame == null) {
                log.warn("帧 {} 采集失败（null）", i + 1);
                continue;
            }
            successCount++;
            log.info("帧 {}: {}x{} size={}", i + 1, capture.getWidth(), capture.getHeight(), frame.image != null && frame.image.length > 0 ? ((java.nio.ByteBuffer) frame.image[0]).capacity() : 0);
        }

        capture.close();
        log.info("采集器已关闭");

        if (successCount == 0) {
            log.error("[FAIL] 所有帧采集失败");
            return false;
        }

        log.info("[PASS] 采集 {} 帧成功", successCount);
        return true;
    }

    /**
     * 运行演示模式。
     *
     * @param captureType 采集器类型
     * @param width       采集宽度
     * @param height      采集高度
     * @param fps         帧率
     * @param frameCount  采集帧数
     * @param outputDir   输出目录（null 表示不保存）
     */
    private static void runDemo(DemoArgs a) {
        String captureType = a.captureType();
        int width = a.width();
        int height = a.height();
        int fps = a.fps();
        int frameCount = a.frameCount();
        File outputDir = a.outputDir();
        ScreenCature capture = createScreenCapture(captureType);
        if (capture == null) {
            log.error("[ERROR] 无法创建采集器，请检查依赖是否完整");
            throw new IllegalStateException("无法创建采集器: " + captureType);
        }

        if (!capture.init(width, height, fps)) {
            log.error("[ERROR] 采集器初始化失败");
            capture.close();
            throw new IllegalStateException("采集器初始化失败: " + captureType);
        }

        log.info("采集器已就绪: {} {}x{}@{}fps", capture.getClass().getSimpleName(),
                capture.getWidth(), capture.getHeight(), fps);

        int successCount = 0;
        for (int i = 0; i < frameCount; i++) {
            Frame frame = capture.grabFrame();
            if (frame == null) {
                log.warn("帧 {} 采集失败（null）", i + 1);
                continue;
            }
            successCount++;
            if (outputDir != null) {
                // Frame → BufferedImage 保存
                BufferedImage image = frameToBufferedImage(frame, capture.getWidth(), capture.getHeight());
                File saved = saveFrame(image, outputDir, i + 1);
                if (saved != null) {
                    log.info("帧 {} 已保存: {}", i + 1, saved.getAbsolutePath());
                }
            } else {
                int size = frame.image != null && frame.image.length > 0 ? frame.image[0].capacity() : 0;
                log.info("帧 {}: {}x{} size={}", i + 1, capture.getWidth(), capture.getHeight(), size);
            }
        }

        capture.close();
        log.info("采集器已关闭，成功采集 {} / {} 帧", successCount, frameCount);
    }

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("ScreenCaptureExample")
                .register("type", "t", "采集器类型", DEFAULT_CAPTURE_TYPE)
                .register("width", "w", "采集宽度", String.valueOf(DEFAULT_WIDTH))
                .register("height", "h", "采集高度", String.valueOf(DEFAULT_HEIGHT))
                .register("fps", "f", "帧率", String.valueOf(DEFAULT_FPS))
                .register("frames", "n", "采集帧数", String.valueOf(DEFAULT_FRAME_COUNT))
                .register("output", "o", "保存帧到目录（PNG 格式）")
                .register("test", "运行自检并退出")
                .register("help", "h", "显示此帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String captureType = cli.get("type", DEFAULT_CAPTURE_TYPE);
        int width = cli.getInt("width", DEFAULT_WIDTH);
        int height = cli.getInt("height", DEFAULT_HEIGHT);
        int fps = cli.getInt("fps", DEFAULT_FPS);
        int frameCount = cli.getInt("frames", DEFAULT_FRAME_COUNT);
        File outputDir = cli.has("output") ? new File(cli.get("output")) : null;

        log.info("配置: type={}, {}x{}@{}fps, frames={}", captureType, width, height, fps, frameCount);

        if (cli.has("test")) {
            boolean passed = runTest(captureType, width, height, fps);
            if (!passed) {
                System.exit(1);
            }
            return;
        }

        runDemo(new DemoArgs(captureType, width, height, fps, frameCount, outputDir));
    }
}

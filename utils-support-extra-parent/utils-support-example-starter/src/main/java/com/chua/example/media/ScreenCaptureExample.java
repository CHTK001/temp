package com.chua.example.media;

import com.chua.common.support.media.codec.ScreenCature;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

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
     * 输出目录参数名
     */
    private static final String ARG_OUTPUT = "--output";

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
                System.err.println("[ERROR] 未找到 ScreenCature 实现: " + captureType);
                return null;
            }
            return capture;
        } catch (Exception e) {
            System.err.println("[ERROR] 创建采集器失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 保存 BufferedImage 到文件。
     *
     * @param image 图像
     * @param outputDir 输出目录
     * @param index 帧序号
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
            System.err.println("[ERROR] 保存帧失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 运行自检模式。
     *
     * @param captureType 采集器类型
     * @param width 采集宽度
     * @param height 采集高度
     * @param fps 帧率
     * @return true 表示自检通过
     */
    private static boolean runTest(String captureType, int width, int height, int fps) {
        log.info("===== ScreenCaptureExample --test [type={}, {}x{}@{}fps] =====",
                captureType, width, height, fps);

        ScreenCature capture = createScreenCapture(captureType);
        if (capture == null) {
            System.err.println("[FAIL] 无法创建采集器");
            return false;
        }

        if (!capture.init(width, height, fps)) {
            System.err.println("[FAIL] 采集器初始化失败");
            capture.close();
            return false;
        }

        int successCount = 0;
        for (int i = 0; i < TEST_FRAME_COUNT; i++) {
            BufferedImage frame = capture.grabFrame();
            if (frame == null) {
                log.warn("帧 {} 采集失败（null）", i + 1);
                continue;
            }
            successCount++;
            log.info("帧 {}: {}x{} channels={}", i + 1, frame.getWidth(), frame.getHeight(), frame.getColorModel().getPixelSize());
        }

        capture.close();
        log.info("采集器已关闭");

        if (successCount == 0) {
            System.err.println("[FAIL] 所有帧采集失败");
            return false;
        }

        log.info("[PASS] 采集 {} 帧成功", successCount);
        return true;
    }

    /**
     * 运行演示模式。
     *
     * @param captureType 采集器类型
     * @param width 采集宽度
     * @param height 采集高度
     * @param fps 帧率
     * @param frameCount 采集帧数
     * @param outputDir 输出目录（null 表示不保存）
     */
    private static void runDemo(String captureType, int width, int height, int fps, int frameCount, File outputDir) {
        ScreenCature capture = createScreenCapture(captureType);
        if (capture == null) {
            System.err.println("[ERROR] 无法创建采集器，请检查依赖是否完整");
            System.exit(1);
        }

        if (!capture.init(width, height, fps)) {
            System.err.println("[ERROR] 采集器初始化失败");
            capture.close();
            System.exit(1);
        }

        log.info("采集器已就绪: {} {}x{}@{}fps", capture.getClass().getSimpleName(),
                capture.getWidth(), capture.getHeight(), fps);

        int successCount = 0;
        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = capture.grabFrame();
            if (frame == null) {
                log.warn("帧 {} 采集失败（null）", i + 1);
                continue;
            }
            successCount++;
            if (outputDir != null) {
                File saved = saveFrame(frame, outputDir, i + 1);
                if (saved != null) {
                    log.info("帧 {} 已保存: {}", i + 1, saved.getAbsolutePath());
                }
            } else {
                log.info("帧 {}: {}x{}", i + 1, frame.getWidth(), frame.getHeight());
            }
        }

        capture.close();
        log.info("采集器已关闭，成功采集 {} / {} 帧", successCount, frameCount);
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("屏幕采集综合示例 — 基于 ScreenCature SPI");
        System.out.println();
        System.out.println("用法: java ScreenCaptureExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println(" --type, -t <key> 采集器类型（默认: " + DEFAULT_CAPTURE_TYPE + "）");
        System.out.println(" --width, -w <width> 采集宽度（默认: " + DEFAULT_WIDTH + "）");
        System.out.println(" --height, -h <height> 采集高度（默认: " + DEFAULT_HEIGHT + "）");
        System.out.println(" --fps, -f <fps> 帧率（默认: " + DEFAULT_FPS + "）");
        System.out.println(" --frames, -n <count> 采集帧数（默认: " + DEFAULT_FRAME_COUNT + "）");
        System.out.println(" --output, -o <dir> 保存帧到目录（PNG 格式）");
        System.out.println(" --test 运行自检并退出");
        System.out.println(" --help, -? 显示此帮助");
    }

    /**
     * 解析命令行参数。
     *
     * @param args 命令行参数
     * @return 参数对象
     */
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--type", "-t" -> {
                    if (index + 1 < args.length) {
                        result = result.withType(args[++index]);
                    }
                }
                case "--width", "-w" -> {
                    if (index + 1 < args.length) {
                        result = result.withWidth(Integer.parseInt(args[++index]));
                    }
                }
                case "--height", "-h" -> {
                    if (index + 1 < args.length) {
                        result = result.withHeight(Integer.parseInt(args[++index]));
                    }
                }
                case "--fps", "-f" -> {
                    if (index + 1 < args.length) {
                        result = result.withFps(Integer.parseInt(args[++index]));
                    }
                }
                case "--frames", "-n" -> {
                    if (index + 1 < args.length) {
                        result = result.withFrameCount(Integer.parseInt(args[++index]));
                    }
                }
                case "--output", "-o" -> {
                    if (index + 1 < args.length) {
                        result = result.withOutputDir(args[++index]);
                    }
                }
                case "--test" -> result = result.withTest(true);
                case "--help", "-?", "?" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.help()) {
            printHelp();
            return;
        }

        String captureType = parsed.type() != null ? parsed.type() : DEFAULT_CAPTURE_TYPE;
        int width = parsed.width() > 0 ? parsed.width() : DEFAULT_WIDTH;
        int height = parsed.height() > 0 ? parsed.height() : DEFAULT_HEIGHT;
        int fps = parsed.fps() > 0 ? parsed.fps() : DEFAULT_FPS;
        int frameCount = parsed.frameCount() > 0 ? parsed.frameCount() : DEFAULT_FRAME_COUNT;
        File outputDir = parsed.outputDir() != null ? new File(parsed.outputDir()) : null;

        log.info("配置: type={}, {}x{}@{}fps, frames={}", captureType, width, height, fps, frameCount);

        if (parsed.test()) {
            boolean passed = runTest(captureType, width, height, fps);
            if (!passed) {
                System.exit(1);
            }
            return;
        }

        runDemo(captureType, width, height, fps, frameCount, outputDir);
    }

    /**
     * 命令行参数容器。
     *
     * @param type 采集器类型标识
     * @param width 采集宽度
     * @param height 采集高度
     * @param fps 帧率
     * @param frameCount 采集帧数
     * @param outputDir 输出目录路径
     * @param test 是否自检模式
     * @param help 是否打印帮助
     * @author CH
     * @since 4.0.0.42
     */
    private record Args(
            String type,
            int width,
            int height,
            int fps,
            int frameCount,
            String outputDir,
            boolean test,
            boolean help
    ) {
        /**
         * 带默认值的空参构造。
         */
        Args() {
            this(DEFAULT_CAPTURE_TYPE, DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS, DEFAULT_FRAME_COUNT, null, false, false);
        }

        /**
         * 替换 type 字段，返回新实例。
         *
         * @param type 采集器类型
         * @return 新 Args 实例
         */
        public Args withType(String type) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 width 字段，返回新实例。
         *
         * @param width 采集宽度
         * @return 新 Args 实例
         */
        public Args withWidth(int width) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 height 字段，返回新实例。
         *
         * @param height 采集高度
         * @return 新 Args 实例
         */
        public Args withHeight(int height) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 fps 字段，返回新实例。
         *
         * @param fps 帧率
         * @return 新 Args 实例
         */
        public Args withFps(int fps) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 frameCount 字段，返回新实例。
         *
         * @param frameCount 采集帧数
         * @return 新 Args 实例
         */
        public Args withFrameCount(int frameCount) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 outputDir 字段，返回新实例。
         *
         * @param outputDir 输出目录路径
         * @return 新 Args 实例
         */
        public Args withOutputDir(String outputDir) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 test 字段，返回新实例。
         *
         * @param test 是否自检
         * @return 新 Args 实例
         */
        public Args withTest(boolean test) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }

        /**
         * 替换 help 字段，返回新实例。
         *
         * @param help 是否帮助
         * @return 新 Args 实例
         */
        public Args withHelp(boolean help) {
            return new Args(type, width, height, fps, frameCount, outputDir, test, help);
        }
    }
}

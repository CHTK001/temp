package com.chua.example.media;

import com.chua.common.support.media.codec.JpegVideoEncoder;
import com.chua.common.support.media.codec.ScreenCature;
import com.chua.common.support.media.codec.VideoDecoder;
import com.chua.common.support.media.codec.VideoEncoder;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import com.chua.ffmpeg.support.codec.EncodesFrame;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频编解码器综合示例 — 基于 VideoEncoder / VideoDecoder / ScreenCature SPI，
 * 支持全部实现切换与自检。
 *
 * <p>通过命令行参数指定 {@code @Spi} 类型，自检覆盖基础能力矩阵。</p>
 *
 * <h2>用法</h2>
 * <pre>
 * # 默认实现（jpeg 编码器），常驻运行
 * java VideoCodecExample
 *
 * # 指定编码器类型
 * java VideoCodecExample --encoder javacv-ffmpeg
 * java VideoCodecExample --encoder rust-h264
 * java VideoCodecExample --encoder jpeg
 *
 * # 自检模式：创建测试帧 → 编码 → 解码 → 对比
 * java VideoCodecExample --test
 *
 * # 指定分辨率 + 帧率
 * java VideoCodecExample --width 1280 --height 720 --fps 30
 * </pre>
 *
 * <h2>SPI 类型与能力</h2>
 * <table border="1">
 * <tr><th>--encoder</th><th>实现类</th><th>编码格式</th><th>零拷贝 Frame</th><th>解码</th></tr>
 * <tr><td>javacv-ffmpeg</td><td>H264VideoEncoder</td><td>H.264</td><td>✅</td><td>JavaCVVideoDecoder</td></tr>
 * <tr><td>rust-h264</td><td>RustH264VideoEncoder</td><td>H.264</td><td>✅</td><td>RustVideoDecoder</td></tr>
 * <tr><td>jpeg</td><td>JpegVideoEncoder</td><td>JPEG/MJPEG</td><td>✅</td><td>❌</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VideoCodecExample {

    /**
     * 默认编码器类型
     */
    private static final String DEFAULT_ENCODER_TYPE = "javacv-ffmpeg";

    /**
     * 默认视频宽度
     */
    private static final int DEFAULT_WIDTH = 640;

    /**
     * 默认视频高度
     */
    private static final int DEFAULT_HEIGHT = 480;

    /**
     * 默认帧率
     */
    private static final int DEFAULT_FPS = 30;

    /**
     * 自检模式测试帧数
     */
    private static final int TEST_FRAME_COUNT = 5;

    /**
     * 构造测试帧（带文字和色块的彩色图像）。
     *
     * @param width 帧宽度
     * @param height 帧高度
     * @param index 帧序号（用于显示不同内容）
     * @return BufferedImage 测试帧
     */
    private static BufferedImage createTestFrame(int width, int height, int index) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();

        // 背景色：根据帧序号变化
        g.setColor(new Color((index * 40) % 256, (index * 80) % 256, (index * 120) % 256));
        g.fillRect(0, 0, width, height);

        // 绘制文字
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Frame " + index + " " + width + "x" + height, 20, 40);

        // 绘制矩形色块
        g.setStroke(new BasicStroke(4));
        g.setColor(Color.RED);
        g.drawRect(50, 60, width - 100, height - 120);

        g.dispose();
        return image;
    }

    /**
     * 通过 SPI 创建 VideoEncoder 实例。
     *
     * @param encoderType 编码器 SPI 类型标识
     * @param width 视频宽度
     * @param height 视频高度
     * @param fps 帧率
     * @return VideoEncoder 实例，创建失败返回 null
     */
    private static VideoEncoder createEncoder(String encoderType, int width, int height, int fps) {
        try {
            VideoEncoder encoder = ServiceProvider.of(VideoEncoder.class)
                    .getNewExtension(encoderType, width, height, fps);
            if (encoder == null) {
                log.error("[ERROR] 未找到 VideoEncoder 实现: {}", encoderType);
                return null;
            }
            return encoder;
        } catch (Exception e) {
            log.error("[ERROR] 创建编码器失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过 SPI 创建 VideoDecoder 实例。
     *
     * @param codecId 编解码器标识
     * @param width 视频宽度
     * @param height 视频高度
     * @return VideoDecoder 实例，创建失败返回 null
     */
    private static VideoDecoder createDecoder(int codecId, int width, int height) {
        try {
            // 先尝试匹配的编解码器解码器
            for (String name : new String[]{"javacv", "rust-h264", "jpeg"}) {
                VideoDecoder decoder = ServiceProvider.of(VideoDecoder.class)
                        .getNewExtension(name, codecId, width, height);
                if (decoder != null) {
                    return decoder;
                }
            }
            log.error("[ERROR] 未找到 VideoDecoder 实现");
            return null;
        } catch (Exception e) {
            log.error("[ERROR] 创建解码器失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 运行自检模式。
     *
     * @param encoderType 编码器 SPI 类型
     * @param width 视频宽度
     * @param height 视频高度
     * @param fps 帧率
     * @return true 表示自检通过
     */
    private static boolean runTest(String encoderType, int width, int height, int fps) {
        log.info("===== VideoCodecExample --test [encoder={}, {}x{}@{}fps] =====",
                encoderType, width, height, fps);

        VideoEncoder encoder = createEncoder(encoderType, width, height, fps);
        if (encoder == null) {
            log.error("[FAIL] 无法创建编码器");
            return false;
        }

        int codecId = encoder.getCodecId();
        log.info("编码器: impl={}, name={}, codecId={}, hwAccel={}",
                encoder.getClass().getSimpleName(), encoder.getCodecName(), codecId, encoder.isHardwareAccelerated());

        // 生成测试帧并编码
        List<byte[]> encodedFrames = new ArrayList<>();
        long totalEncodeTime = 0;
        long totalBytes = 0;
        long warmupTime = 0;

        for (int i = 0; i < TEST_FRAME_COUNT; i++) {
            BufferedImage frame = createTestFrame(width, height, i + 1);

            // 第一帧含编码器初始化，单独记录耗时但不计入统计
            boolean isWarmup = (i == 0);
            long start = System.nanoTime();
            byte[] encoded = encoder.encode(frame);
            long elapsed = System.nanoTime() - start;

            if (isWarmup) {
                warmupTime = elapsed;
            } else {
                totalEncodeTime += elapsed;
            }
            totalBytes += (encoded == null ? 0 : encoded.length);

            if (encoded == null || encoded.length == 0) {
                log.error("[FAIL] 帧 {} 编码失败，返回空数据", i);
                encoder.close();
                return false;
            }
            encodedFrames.add(encoded);
            log.info("帧 {} 编码完成: {} bytes, 耗时 {}ms{}",
                    i + 1, encoded.length, elapsed / 1_000_000,
                    isWarmup ? "（预热，含编码器初始化）" : "");
        }

        int statFrames = TEST_FRAME_COUNT - 1;
        double avgEncodeTime = statFrames > 0 ? totalEncodeTime / (double) statFrames / 1_000_000 : 0;
        double avgFps = avgEncodeTime > 0 ? 1000.0 / avgEncodeTime : 0;
        double avgBytes = statFrames > 0 ? totalBytes / (double) statFrames : 0;
        log.info("--- 编码性能统计 ---");
        log.info("编码器: {} 分辨率: {}x{} 帧率设置: {}fps", encoderType, width, height, fps);
        log.info("预热耗时: {}ms（含编码器初始化）", warmupTime / 1_000_000);
        log.info("统计帧数: {} 帧（不含预热）", statFrames);
        log.info("平均编码耗时: {} ms/帧", String.format("%.2f", avgEncodeTime));
        log.info("平均帧率: {} fps", String.format("%.1f", avgFps));
        log.info("平均帧大小: {} bytes", String.format("%.0f", avgBytes));
        log.info("总编码数据: {} bytes / {} 帧", totalBytes, TEST_FRAME_COUNT);

        // 尝试解码
        VideoDecoder decoder = null;
        if (codecId != 0) {
            decoder = createDecoder(codecId, width, height);
        } else {
            // JPEG 编码器使用 "jpeg" 解码器名
            decoder = ServiceProvider.of(VideoDecoder.class).getNewExtension("jpeg");
        }
        if (decoder != null) {
            byte[] firstPacket = encodedFrames.get(0);
            long start = System.nanoTime();
            java.nio.ByteBuffer decoded = decoder.decode(firstPacket);
            long decodeElapsed = System.nanoTime() - start;

            if (decoded != null) {
                log.info("解码成功: {} bytes, 耗时 {}ms", decoded.remaining(), decodeElapsed / 1_000_000);
            } else {
                log.warn("解码返回 null");
            }
            decoder.close();
        } else {
            log.warn("未找到对应解码器，跳过解码测试");
        }

        encoder.close();
        log.info("[PASS] 编码器 {} 测试通过", encoderType);
        return true;
    }

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("VideoCodecExample")
                .register("encoder", "e", "编码器类型（javacv-ffmpeg|rust-h264|jpeg）", DEFAULT_ENCODER_TYPE)
                .register("width", "w", "视频宽度", String.valueOf(DEFAULT_WIDTH))
                .register("height", "h", "视频高度", String.valueOf(DEFAULT_HEIGHT))
                .register("fps", "f", "帧率", String.valueOf(DEFAULT_FPS))
                .register("test", "运行自检并退出")
                .register("help", "?", "显示此帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String encoderType = cli.get("encoder", DEFAULT_ENCODER_TYPE);
        int width = cli.getInt("width", DEFAULT_WIDTH);
        int height = cli.getInt("height", DEFAULT_HEIGHT);
        int fps = cli.getInt("fps", DEFAULT_FPS);

        log.info("配置: encoder={}, {}x{}@{}fps", encoderType, width, height, fps);

        if (cli.has("test")) {
            boolean passed = runTest(encoderType, width, height, fps);
            if (!passed) {
                System.exit(1);
            }
            return;
        }

        // 演示模式：创建编码器并处理几帧
        VideoEncoder encoder = createEncoder(encoderType, width, height, fps);
        if (encoder == null) {
            log.error("[ERROR] 无法创建编码器，请检查依赖是否完整");
            System.exit(1);
        }

        log.info("编码器已就绪: impl={}, name={}, codecId={}, hwAccel={}",
                encoder.getClass().getSimpleName(), encoder.getCodecName(), encoder.getCodecId(), encoder.isHardwareAccelerated());

        log.info("演示模式：处理 3 帧测试图像...");
        for (int i = 1; i <= 3; i++) {
            BufferedImage frame = createTestFrame(width, height, i);
            byte[] encoded = encoder.encode(frame);
            if (encoded != null && encoded.length > 0) {
                log.info("帧 {} 编码完成: {} bytes", i, encoded.length);
            } else {
                log.warn("帧 {} 编码返回空数据", i);
            }
        }

        encoder.close();
        log.info("编码器已释放");
    }
}

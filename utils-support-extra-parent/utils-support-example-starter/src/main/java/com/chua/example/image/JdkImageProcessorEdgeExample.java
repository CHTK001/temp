package com.chua.example.image;

import com.chua.common.support.image.processor.JdkImageProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * JdkImageProcessor edge 操作示例：验证 JDK AWT 实现的 Sobel 边缘检测各方向输出。
 *
 * <p>改写自 common-starter 测试代码 processor/JdkImageProcessorEdgeTest，覆盖场景：
 * 处理器可用性检查、edge 的 both / h / v 三个显式方向、不指定 direction 的默认方向，
 * 输出须为非空且可解码图像。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class JdkImageProcessorEdgeExample {

    /** 私有构造，防止实例化 */
    private JdkImageProcessorEdgeExample() {
    }

    /**
     * 入口：校验处理器可用性后依次执行四个方向的 edge 用例。
     *
     * @param args 未使用
     * @throws IOException 测试图编码失败时抛出
     */
    public static void main(String[] args) throws IOException {
        JdkImageProcessor processor = new JdkImageProcessor();
        if (!processor.available()) {
            log.info("[FAIL] jdk-processor-unavailable");
            System.exit(1);
        }
        log.info("[PASS] jdk-processor-ready name=" + processor.name());

        byte[] input = buildEdgeTestImagePng();
        log.info("[input] generated png " + input.length + " B");

        String[] directions = {"both", "h", "v"};
        for (String direction : directions) {
            if (!runEdge(processor, input, direction)) {
                log.info("[FAIL] jdk-edge-" + direction);
                System.exit(1);
            }
            log.info("[PASS] jdk-edge-" + direction);
        }

        if (!runEdge(processor, input, null)) {
            log.info("[FAIL] jdk-edge-default");
            System.exit(1);
        }
        log.info("[PASS] jdk-edge-default");
    }

    /**
     * 执行单方向 edge 用例并校验输出有效性。
     *
     * @param processor JDK 图像处理器
     * @param input     输入 PNG 字节
     * @param direction 方向参数，null 表示不指定（默认 both）
     * @return 输出非空且可解码返回 true
     */
    private static boolean runEdge(JdkImageProcessor processor, byte[] input, String direction) {
        String label = direction == null ? "default" : direction;
        try {
            Map<String, Object> params = new HashMap<>(8);
            if (direction != null) {
                params.put("direction", direction);
            }
            long start = System.nanoTime();
            byte[] result = processor.process(input, "edge", params);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            if (result == null || result.length == 0) {
                log.info("  fail edge[direction=" + label + "] empty-result");
                return false;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.info("  fail edge[direction=" + label + "] invalid-image");
                return false;
            }
            log.info("  ok edge[direction=" + label + "] " + result.length + " B "
                    + img.getWidth() + "x" + img.getHeight() + " " + elapsed + "ms");
            return true;
        } catch (IOException | RuntimeException e) {
            log.info("  fail edge[direction=" + label + "] exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * 现场生成 96x64 边缘测试图（横向渐变底 + 中央白色矩形）并编码为 PNG 字节。
     *
     * @return PNG 字节
     * @throws IOException 编码失败时抛出
     */
    private static byte[] buildEdgeTestImagePng() throws IOException {
        BufferedImage img = new BufferedImage(96, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 96; x++) {
                int level = x * 255 / 96;
                img.setRGB(x, y, (level << 16) | (level << 8) | level);
            }
        }
        fillRect(img, 24, 16, 48, 32);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /**
     * 在图像上填充白色矩形以制造强边缘。
     *
     * @param img 目标图像
     * @param rx  矩形起点横坐标
     * @param ry  矩形起点纵坐标
     * @param rw  矩形宽度
     * @param rh  矩形高度
     */
    private static void fillRect(BufferedImage img, int rx, int ry, int rw, int rh) {
        for (int y = ry; y < ry + rh; y++) {
            for (int x = rx; x < rx + rw; x++) {
                img.setRGB(x, y, 0xFFFFFF);
            }
        }
    }
}

package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.ImageProcessors;
import com.chua.common.support.image.processor.JdkImageProcessor;
import com.chua.common.support.image.processor.RustImageProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * ImageProcessor edge 操作全实现示例：SPI 发现全部可用实现并逐一验证 edge 各方向。
 *
 * <p>改写自 common-starter 测试代码 processor/ImageProcessorEdgeTest，覆盖场景：
 * SPI 代理发现、jdk 兜底注册、rust 直连发现（原生库不可用时打印 [SKIP] rust-unavailable
 * 继续执行，不计为失败）、edge 的 both / h / v / 默认方向共 4 组参数，
 * 输出须为非空且可解码图像。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ImageProcessorEdgeCaseExample {

    /** 私有构造，防止实例化 */
    private ImageProcessorEdgeCaseExample() {
    }

    /**
     * 入口：生成测试图并遍历全部实现的 edge 方向组合。
     *
     * @param args 未使用
     * @throws IOException 测试图编码失败时抛出
     */
    public static void main(String[] args) throws IOException {
        byte[] input = buildEdgeTestImagePng();
        List<ImageProcessor> impls = discoverProcessors();
        log.info("[input] generated png " + input.length + " B");

        if (impls.isEmpty()) {
            System.out.println("[FAIL] processor-discovery");
            System.exit(1);
        }
        System.out.println("[PASS] processor-discovery count=" + impls.size());

        int failCount = runAllEdges(input, impls);
        if (failCount > 0) {
            System.out.println("[FAIL] edge-all-implementations failedCases=" + failCount);
            System.exit(1);
        }
        System.out.println("[PASS] edge-all-implementations");
    }

    /**
     * 通过 SPI 代理、jdk 兜底与 rust 直连三条路径收集可用实现。
     *
     * @return 可用 ImageProcessor 列表（可能为空）
     */
    private static List<ImageProcessor> discoverProcessors() {
        List<ImageProcessor> result = new ArrayList<>();
        appendProxy(result);
        appendJdk(result);
        appendRust(result);
        return result;
    }

    /**
     * 通过 SPI 代理自动降级获取默认实现并登记。
     *
     * @param result 收集列表
     */
    private static void appendProxy(List<ImageProcessor> result) {
        try {
            ImageProcessor proxy = ImageProcessors.getProcessor();
            if (proxy != null && proxy.available()) {
                result.add(proxy);
                log.info("  discovered spi-proxy: " + proxy.name());
            }
        } catch (RuntimeException e) {
            log.info("  spi-proxy discovery skipped: " + e.getMessage());
        }
    }

    /**
     * 直接实例化 JdkImageProcessor 兜底实现并去重登记。
     *
     * @param result 收集列表
     */
    private static void appendJdk(List<ImageProcessor> result) {
        try {
            JdkImageProcessor jdk = new JdkImageProcessor();
            if (jdk.available() && !containsName(result, jdk.name())) {
                result.add(jdk);
                log.info("  discovered jdk: " + jdk.name());
            }
        } catch (RuntimeException | LinkageError e) {
            log.info("  jdk processor skipped: " + e.getMessage());
        }
    }

    /**
     * 直接实例化 RustImageProcessor；原生库不可用时打印 [SKIP] rust-unavailable 并继续。
     *
     * @param result 收集列表
     */
    private static void appendRust(List<ImageProcessor> result) {
        try {
            RustImageProcessor rust = new RustImageProcessor();
            if (!rust.available()) {
                log.info("[SKIP] rust-unavailable");
                return;
            }
            if (!containsName(result, rust.name())) {
                result.add(rust);
                log.info("  discovered rust: " + rust.name());
            }
        } catch (RuntimeException | LinkageError e) {
            log.info("[SKIP] rust-unavailable (" + e.getMessage() + ")");
        }
    }

    /**
     * 遍历全部实现执行 edge 四组方向参数用例并统计失败数。
     *
     * @param input 输入 PNG 字节
     * @param impls 可用实现列表
     * @return 失败用例数量
     */
    private static int runAllEdges(byte[] input, List<ImageProcessor> impls) {
        String[] directions = {"both", "h", "v", null};
        int passCount = 0;
        int failCount = 0;
        int skipCount = 0;
        for (ImageProcessor processor : impls) {
            if ("rust".equals(processor.name()) && !processor.available()) {
                log.info("[SKIP] rust-unavailable");
                skipCount++;
                continue;
            }
            for (String direction : directions) {
                if (runSingleEdge(processor, input, direction)) {
                    passCount++;
                } else {
                    failCount++;
                }
            }
        }
        log.info("  total pass=" + passCount + " fail=" + failCount + " skip=" + skipCount);
        return failCount;
    }

    /**
     * 执行单个实现的单方向 edge 用例并校验输出有效性。
     *
     * @param processor 图像处理器
     * @param input     输入 PNG 字节
     * @param direction 方向参数，null 表示不指定（默认 both）
     * @return 输出非空且可解码返回 true
     */
    private static boolean runSingleEdge(ImageProcessor processor, byte[] input, String direction) {
        String label = processor.name() + "/" + (direction == null ? "default" : direction);
        try {
            Map<String, Object> params = new HashMap<>();
            if (direction != null) {
                params.put("direction", direction);
            }
            long start = System.nanoTime();
            byte[] result = processor.process(input, "edge", params);
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            if (result == null || result.length == 0) {
                log.info("  fail edge[" + label + "] empty-result");
                return false;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
            if (img == null) {
                log.info("  fail edge[" + label + "] invalid-image");
                return false;
            }
            System.out.println("  ok edge[" + label + "] " + result.length + " B "
                    + img.getWidth() + "x" + img.getHeight() + " " + elapsed + "ms");
            return true;
        } catch (IOException | RuntimeException e) {
            log.info("  fail edge[" + label + "] exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * 判断列表中是否已存在同名实现。
     *
     * @param impls 实现列表
     * @param name  处理器名称
     * @return 已存在返回 true
     */
    private static boolean containsName(List<ImageProcessor> impls, String name) {
        for (ImageProcessor impl : impls) {
            if (impl.name().equals(name)) {
                return true;
            }
        }
        return false;
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

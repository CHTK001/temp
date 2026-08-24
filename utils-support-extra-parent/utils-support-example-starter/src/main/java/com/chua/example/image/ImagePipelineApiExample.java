package com.chua.example.image;

import com.chua.common.support.image.ImagePipeline;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * ImagePipeline API 示例：验证默认全关闭、灰度化、二值化与形态学操作的输出正确性。
 *
 * <p>改写自 common-starter 测试代码 ImagePipelineTest，覆盖场景：
 * 默认管线保持原图尺寸、灰度化后 RGB 三分量相近、二值化后仅 0/255 两值、
 * denoise / erode / dilate 及全链组合均保持尺寸。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ImagePipelineApiExample {

    /** 私有构造，防止实例化 */
    private ImagePipelineApiExample() {
    }

    /**
     * 入口：依次执行四组场景校验，任一失败即打印 [FAIL] 并以退出码 1 结束。
     *
     * @param args 未使用
     * @throws IOException 图像编码或解码失败时抛出
     */
    public static void main(String[] args) throws IOException {
        if (!checkDefaultKeepsSize()) {
            System.out.println("[FAIL] pipeline-default-size");
            System.exit(1);
        }
        System.out.println("[PASS] pipeline-default-size");

        if (!checkGrayscalePixels()) {
            System.out.println("[FAIL] pipeline-grayscale-gray-pixels");
            System.exit(1);
        }
        System.out.println("[PASS] pipeline-grayscale-gray-pixels");

        if (!checkBinarizePixels()) {
            System.out.println("[FAIL] pipeline-binarize-black-white");
            System.exit(1);
        }
        System.out.println("[PASS] pipeline-binarize-black-white");

        if (!checkMorphologicalOpsKeepSize()) {
            System.out.println("[FAIL] pipeline-morphological-size");
            System.exit(1);
        }
        System.out.println("[PASS] pipeline-morphological-size");
    }

    /**
     * 默认全关闭管线：处理后的图应保持尺寸与原图一致。
     *
     * @return 尺寸一致返回 true
     * @throws IOException 解码失败时抛出
     */
    private static boolean checkDefaultKeepsSize() throws IOException {
        byte[] in = buildTestImagePng();
        BufferedImage src = decode(in);
        BufferedImage dst = decode(ImagePipeline.builder().build().process(in));
        return src != null && dst != null
                && src.getWidth() == dst.getWidth()
                && src.getHeight() == dst.getHeight();
    }

    /**
     * 灰度化：抽样像素的 RGB 三分量应相近（容差 2）。
     *
     * @return 全部抽样点为灰色返回 true
     * @throws IOException 解码失败时抛出
     */
    private static boolean checkGrayscalePixels() throws IOException {
        BufferedImage img = decode(ImagePipeline.builder().grayscale(true).build().process(buildTestImagePng()));
        if (img == null) {
            return false;
        }
        for (int y = 0; y < img.getHeight(); y += 8) {
            for (int x = 0; x < img.getWidth(); x += 8) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (Math.abs(r - g) > 2 || Math.abs(g - b) > 2) {
                    System.out.println("  non-gray pixel at (" + x + "," + y + "): " + r + "," + g + "," + b);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 二值化：抽样像素应只有 0 / 255 两个取值。
     *
     * @return 全部抽样点为纯黑或纯白返回 true
     * @throws IOException 解码失败时抛出
     */
    private static boolean checkBinarizePixels() throws IOException {
        BufferedImage img = decode(ImagePipeline.builder().binarize(true, 128).build().process(buildTestImagePng()));
        if (img == null) {
            return false;
        }
        for (int y = 0; y < img.getHeight(); y += 4) {
            for (int x = 0; x < img.getWidth(); x += 4) {
                int val = img.getRGB(x, y) & 0xFF;
                if (val != 0 && val != 255) {
                    System.out.println("  non-binary pixel at (" + x + "," + y + "): " + val);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 降噪 / 腐蚀 / 膨胀及全链组合：各步骤处理后应保持原图尺寸。
     *
     * @return 全部步骤尺寸一致返回 true
     * @throws IOException 解码失败时抛出
     */
    private static boolean checkMorphologicalOpsKeepSize() throws IOException {
        byte[] in = buildTestImagePng();
        BufferedImage src = decode(in);
        if (src == null) {
            return false;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean denoised = sameSize(ImagePipeline.builder().denoise(true, 1).build().process(in), w, h);
        boolean eroded = sameSize(ImagePipeline.builder().erode(true, 3).build().process(in), w, h);
        boolean dilated = sameSize(ImagePipeline.builder().dilate(true, 3).build().process(in), w, h);
        boolean all = sameSize(ImagePipeline.builder()
                .grayscale(true)
                .binarize(true, 128)
                .denoise(true, 1)
                .erode(true, 3)
                .dilate(true, 3)
                .build()
                .process(in), w, h);
        return denoised && eroded && dilated && all;
    }

    /**
     * 校验处理后字节解码出的图像与期望尺寸一致。
     *
     * @param bytes 处理后的 PNG 字节
     * @param w     期望宽度
     * @param h     期望高度
     * @return 尺寸一致且可解码返回 true
     * @throws IOException 解码失败时抛出
     */
    private static boolean sameSize(byte[] bytes, int w, int h) throws IOException {
        BufferedImage img = decode(bytes);
        return img != null && img.getWidth() == w && img.getHeight() == h;
    }

    /**
     * 解码 PNG 字节为 BufferedImage。
     *
     * @param bytes PNG 字节
     * @return 解码结果，失败时为 null
     * @throws IOException 读取失败时抛出
     */
    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /**
     * 现场生成 64x64 测试图（左黑 / 中灰 / 右白三段竖条）并编码为 PNG 字节。
     *
     * @return PNG 字节
     * @throws IOException 编码失败时抛出
     */
    private static byte[] buildTestImagePng() throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, stripeColor(x));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /**
     * 按列区间返回黑 / 灰 / 白三段颜色。
     *
     * @param x 像素横坐标
     * @return RGB 颜色值
     */
    private static int stripeColor(int x) {
        if (x < 21) {
            return 0x000000;
        }
        if (x < 43) {
            return 0x808080;
        }
        return 0xFFFFFF;
    }
}

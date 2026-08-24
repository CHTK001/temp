package com.chua.example.image;

import com.chua.common.support.image.ImagePipeline;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ImagePipeline 落地示例：现场生成测试图并保存各处理步骤的结果图片。
 *
 * <p>改写自 common-starter 测试代码 ImagePipelineSave，覆盖场景：
 * original / grayscale / binarize / denoise / erode / dilate / full 共 7 种输出 PNG，
 * 全部写入 {@code {java.io.tmpdir}/test-output/common-image}，结束后在 finally 中递归清理。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ImagePipelineSaveExample {

    /** 输出子目录名，位于 java.io.tmpdir/test-output 下 */
    private static final String OUTPUT_DIR_NAME = "common-image";

    /** 私有构造，防止实例化 */
    private ImagePipelineSaveExample() {
    }

    /**
     * 入口：生成测试图、逐步骤落盘校验后在 finally 中清理输出目录。
     *
     * @param args 未使用
     * @throws IOException 目录创建或文件写入失败时抛出
     */
    public static void main(String[] args) throws IOException {
        Path outDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-output", OUTPUT_DIR_NAME);
        try {
            Files.createDirectories(outDir);
            byte[] input = buildTestImagePng();
            System.out.println("[input] generated png " + input.length + " B");
            boolean saved = saveAll(outDir, input);
            if (!saved) {
                System.out.println("[FAIL] pipeline-save -> " + outDir);
                System.exit(1);
            }
            System.out.println("[PASS] pipeline-save -> " + outDir);
        } finally {
            cleanup(outDir);
        }
    }

    /**
     * 将各管线步骤结果依次写盘并逐一校验可解码。
     *
     * @param outDir 输出目录
     * @param input  原始 PNG 字节
     * @return 全部写盘成功返回 true，任一文件缺失或不可解码返回 false
     * @throws IOException 文件写入失败时抛出
     */
    private static boolean saveAll(Path outDir, byte[] input) throws IOException {
        Map<String, byte[]> results = buildResults(input);
        for (Map.Entry<String, byte[]> entry : results.entrySet()) {
            Path target = outDir.resolve(entry.getKey());
            Files.write(target, entry.getValue());
            if (!isDecodable(target)) {
                System.out.println("  write failed: " + entry.getKey());
                return false;
            }
            System.out.println("  saved " + entry.getKey() + " " + entry.getValue().length + " B");
        }
        return true;
    }

    /**
     * 构建各管线步骤的输出字节集合。
     *
     * @param input 原始 PNG 字节
     * @return 文件名到 PNG 字节的有序映射
     */
    private static Map<String, byte[]> buildResults(byte[] input) {
        Map<String, byte[]> results = new LinkedHashMap<>();
        results.put("original.png", input);
        results.put("grayscale.png", ImagePipeline.builder().grayscale(true).build().process(input));
        results.put("binarize.png", ImagePipeline.builder().binarize(true, 128).build().process(input));
        results.put("denoise.png", ImagePipeline.builder().denoise(true, 1).build().process(input));
        results.put("erode.png", ImagePipeline.builder().erode(true, 3).build().process(input));
        results.put("dilate.png", ImagePipeline.builder().dilate(true, 3).build().process(input));
        results.put("full.png", ImagePipeline.builder()
                .grayscale(true)
                .binarize(true, 128)
                .denoise(true, 1)
                .erode(true, 3)
                .dilate(true, 3)
                .build()
                .process(input));
        return results;
    }

    /**
     * 校验目标文件存在、非空且可解码为图像。
     *
     * @param path 目标文件路径
     * @return 有效返回 true
     * @throws IOException 读取失败时抛出
     */
    private static boolean isDecodable(Path path) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return false;
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(path)));
        return img != null;
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

    /**
     * 递归删除输出目录及其内容（finally 兜底清理）。
     *
     * @param outDir 输出目录
     */
    private static void cleanup(Path outDir) {
        if (!Files.exists(outDir)) {
            return;
        }
        try (var paths = Files.walk(outDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(ImagePipelineSaveExample::deleteQuietly);
        } catch (IOException e) {
            System.out.println("  cleanup skipped: " + e.getMessage());
        }
    }

    /**
     * 静默删除单个文件或目录。
     *
     * @param path 目标路径
     */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            System.out.println("  cleanup skipped: " + path.getFileName());
        }
    }
}

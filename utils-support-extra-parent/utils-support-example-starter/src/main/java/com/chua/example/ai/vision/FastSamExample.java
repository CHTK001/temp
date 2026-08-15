package com.chua.example.ai.vision;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.chua.common.support.utils.CommandLine;
import com.chua.deeplearning.support.onnx.seg.FastSamSegmentTranslator;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * FastSAM 本地分割示例 — 自动检测并分割所有物体（类无关，不分类）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FastSamExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;
    private static final int TEST_SIZE = 256;
    private static final int TEST_RECT = 60;
    private static final String DEFAULT_TEST_IMAGE = System.getProperty("java.io.tmpdir") + "fastsam_test.png";
    private static final String DEFAULT_MASK_IMAGE = System.getProperty("java.io.tmpdir") + "fastsam_mask.png";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("FastSamExample")
                .register("file", "f", "输入图片路径")
                .register("out", "o", "掩码输出路径")
                .register("test", "自检模式")
                .register("help", "h", "显示帮助");
        if (cli.isHelp()) { cli.help(); return; }

        FastSamExample example = new FastSamExample();
        boolean passed;
        if (cli.has("test") || args.length == 0) {
            passed = example.runSelfTest();
        } else {
            passed = example.run(cli);
        }
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean run(CommandLine cli) {
        String file = cli.get("file");
        String out = cli.get("out", DEFAULT_MASK_IMAGE);
        try {
            Image image = ImageFactory.getInstance().fromFile(Path.of(file));
            FastSamSegmentTranslator t = new FastSamSegmentTranslator();
            try {
                Image mask = t.segment(image);
                save(mask, out);
                log.info("[PASS] 分割完成 → {}", out);
                return true;
            } finally { t.close(); }
        } catch (Exception e) {
            log.error("[FAIL] 分割异常: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean runSelfTest() {
        try {
            BufferedImage testImage = createTestImage();
            save(testImage, DEFAULT_TEST_IMAGE);
            Image input = ImageFactory.getInstance().fromImage(testImage);

            long start = System.currentTimeMillis();
            FastSamSegmentTranslator t = new FastSamSegmentTranslator();
            Image mask;
            try { mask = t.segment(input); } finally { t.close(); }
            long elapsed = System.currentTimeMillis() - start;

            BufferedImage maskBuf = (BufferedImage) mask.getWrappedImage();
            save(maskBuf, DEFAULT_MASK_IMAGE);

            float[] box = {TEST_RECT, TEST_RECT, TEST_RECT + (TEST_SIZE - 2 * TEST_RECT),
                    TEST_RECT + (TEST_SIZE - 2 * TEST_RECT)};
            float coverage = coverageInside(maskBuf, box);
            boolean passed = coverage > 0.8f;
            java.nio.file.Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "fastsam_cos.txt"),
                    String.format("coverage=%.3f elapsed=%dms", coverage, elapsed));
            return passed;
        } catch (Exception e) {
            log.error("[FAIL] FastSAM 自检异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private BufferedImage createTestImage() {
        BufferedImage img = new BufferedImage(TEST_SIZE, TEST_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, TEST_SIZE, TEST_SIZE);
        g.setColor(new Color(200, 40, 40)); g.fillRect(TEST_RECT, TEST_RECT, TEST_SIZE - 2 * TEST_RECT, TEST_SIZE - 2 * TEST_RECT);
        g.dispose();
        return img;
    }

    private float coverageInside(BufferedImage mask, float[] box) {
        int x1 = Math.max(0, Math.round(box[0]));
        int y1 = Math.max(0, Math.round(box[1]));
        int x2 = Math.min(mask.getWidth() - 1, Math.round(box[2]));
        int y2 = Math.min(mask.getHeight() - 1, Math.round(box[3]));
        int fg = 0, total = 0;
        for (int y = y1; y <= y2; y++)
            for (int x = x1; x <= x2; x++) {
                if (mask.getRaster().getSample(x, y, 0) > 127) fg++;
                total++;
            }
        return total == 0 ? 0f : (float) fg / total;
    }

    private void save(BufferedImage image, String path) throws Exception {
        ImageIO.write(image, "png", new File(path));
    }
    private void save(Image image, String path) throws Exception {
        save((BufferedImage) image.getWrappedImage(), path);
    }
}
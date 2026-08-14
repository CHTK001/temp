package com.chua.example.ai.vision;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.chua.common.support.utils.CommandLine;
import com.chua.deeplearning.support.onnx.seg.EdgeSamSegmentTranslator;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * EdgeSAM 本地分割示例 — 提示框分割（Image + bbox → 前景掩码）。
 *
 * <p>模型来自 {@code utils-support-models-onnx-edgesam} jar（约 36MB，FP32），
 * 完全离线推理，无需联网。适合边缘设备 / 嵌入式部署。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检模式：生成合成测试图（白底红方块）并校验掩码覆盖率
 *   java EdgeSamExample --test
 *
 *   # 指定图片 + 提示框（像素坐标，可省略 bbox 则用整图）
 *   java EdgeSamExample --file "C:/photo.jpg" --box "100,100,300,300"
 *
 *   # 输出掩码
 *   java EdgeSamExample --file "C:/photo.jpg" --box "100,100,300,300" --out "C:/mask.png"
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class EdgeSamExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 合成测试图尺寸
     */
    private static final int TEST_SIZE = 256;

    /**
     * 合成测试图内红方块区域
     */
    private static final int TEST_RECT = 60;

    /**
     * 默认测试图片输出路径
     */
    private static final String DEFAULT_TEST_IMAGE =
            System.getProperty("java.io.tmpdir") + "edgesam_test.png";

    /**
     * 默认掩码输出路径
     */
    private static final String DEFAULT_MASK_IMAGE =
            System.getProperty("java.io.tmpdir") + "edgesam_mask.png";

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("EdgeSamExample")
                .register("file", "f", "输入图片路径")
                .register("box", "b", "提示框 x1,y1,x2,y2（像素坐标）")
                .register("out", "o", "掩码输出路径（默认: 临时目录 edgesam_mask.png）")
                .register("test", "自检模式：生成合成图并校验掩码")
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        EdgeSamExample example = new EdgeSamExample();
        boolean passed;
        if (cli.has("test") || args.length == 0) {
            passed = example.runSelfTest();
        } else {
            passed = example.run(cli);
        }
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 运行示例。
     *
     * @param cli 命令行参数
     * @return 是否成功
     */
    public boolean run(CommandLine cli) {
        if (cli.has("test")) {
            return runSelfTest();
        }

        String file = cli.get("file");
        if (file == null || file.isBlank()) {
            log.error("[FAIL] 未指定 --file 输入图片，使用 --test 自检或 --file 指定图片");
            return false;
        }
        String out = cli.get("out", DEFAULT_MASK_IMAGE);
        String boxArg = cli.get("box");
        try {
            Image image = ImageFactory.getInstance().fromFile(Path.of(file));
            float[] box = boxArg == null ? null : parseBox(boxArg, image.getWidth(), image.getHeight());
            Image mask = segment(image, box);
            save(mask, out);
            log.info("[PASS] 分割完成 → {}", out);
            return true;
        } catch (Exception e) {
            log.error("[FAIL] 分割异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 自检：生成白底红方块合成图，用框住红方块的 bbox 分割并校验掩码覆盖率。
     *
     * @return 是否通过
     */
    public boolean runSelfTest() {
        try {
            BufferedImage testImage = createTestImage();
            save(testImage, DEFAULT_TEST_IMAGE);
            Image input = ImageFactory.getInstance().fromImage(testImage);

            float[] box = {TEST_RECT, TEST_RECT, TEST_RECT + (TEST_SIZE - 2 * TEST_RECT),
                    TEST_RECT + (TEST_SIZE - 2 * TEST_RECT)};
            long start = System.currentTimeMillis();
            Image mask = segment(input, box);
            long elapsed = System.currentTimeMillis() - start;

            BufferedImage maskBuf = (BufferedImage) mask.getWrappedImage();
            save(maskBuf, DEFAULT_MASK_IMAGE);

            float coverage = coverageInside(maskBuf, box);
            boolean passed = coverage > 0.8f;
            log.info("[{}] EdgeSAM 自检 → 耗时 {}ms, 框内掩码覆盖率 {:.0%}",
                    passed ? "PASS" : "FAIL", elapsed, coverage);
            log.info("      测试图: {}, 掩码图: {}", DEFAULT_TEST_IMAGE, DEFAULT_MASK_IMAGE);
            return passed;
        } catch (Exception e) {
            log.error("[FAIL] EdgeSAM 自检异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行 EdgeSAM 分割。
     *
     * @param image 输入图像
     * @param box   提示框（可空，空则用整图）
     * @return 灰度掩码图
     * @throws Exception 推理异常
     */
    public Image segment(Image image, float[] box) throws Exception {
        if (box == null) {
            box = new float[]{0, 0, image.getWidth(), image.getHeight()};
        }
        EdgeSamSegmentTranslator translator = new EdgeSamSegmentTranslator();
        try {
            return translator.segment(image, box);
        } finally {
            translator.close();
        }
    }

    private BufferedImage createTestImage() {
        BufferedImage image = new BufferedImage(TEST_SIZE, TEST_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, TEST_SIZE, TEST_SIZE);
            g.setColor(new Color(200, 40, 40));
            g.fillRect(TEST_RECT, TEST_RECT, TEST_SIZE - 2 * TEST_RECT, TEST_SIZE - 2 * TEST_RECT);
        } finally {
            g.dispose();
        }
        return image;
    }

    private float coverageInside(BufferedImage mask, float[] box) {
        int x1 = Math.max(0, Math.round(box[0]));
        int y1 = Math.max(0, Math.round(box[1]));
        int x2 = Math.min(mask.getWidth() - 1, Math.round(box[2]));
        int y2 = Math.min(mask.getHeight() - 1, Math.round(box[3]));
        int foreground = 0;
        int total = 0;
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                int gray = mask.getRaster().getSample(x, y, 0);
                if (gray > 127) {
                    foreground++;
                }
                total++;
            }
        }
        return total == 0 ? 0f : (float) foreground / total;
    }

    private float[] parseBox(String boxArg, int width, int height) {
        String[] parts = boxArg.split("[,， ]+");
        if (parts.length != 4) {
            throw new IllegalArgumentException("--box 必须为 x1,y1,x2,y2");
        }
        float x1 = clamp(Float.parseFloat(parts[0]), 0, width);
        float y1 = clamp(Float.parseFloat(parts[1]), 0, height);
        float x2 = clamp(Float.parseFloat(parts[2]), 0, width);
        float y2 = clamp(Float.parseFloat(parts[3]), 0, height);
        return new float[]{x1, y1, x2, y2};
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void save(BufferedImage image, String path) throws Exception {
        File out = new File(path);
        if (out.getParentFile() != null && !out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", out);
    }

    private void save(Image image, String path) throws Exception {
        save((BufferedImage) image.getWrappedImage(), path);
    }
}
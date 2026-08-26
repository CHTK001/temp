package com.chua.example.ai.vision;


import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 多模型批量绘图压测 — 对 G:\images 下全部图片逐模型跑检测，输出到 G:\images\output\<模型ID>\。
 *
 * <p>跑通：yoloworld(s/m/l) / visdrone-small-detector / 嵌入(384/512) / 翻译(opus-mt) 时标注输出。</p>
 *
 * <h2>用法</h2>
 * <pre>java MultiModelBatchDrawExample [图片目录]</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */

@Slf4j
public class MultiModelBatchDrawExample {
    private MultiModelBatchDrawExample() { }


    /** 默认输入目录 */
    private static final String DEFAULT_INPUT = "G:\\images";

    /** 默认输出根目录 */
    private static final String DEFAULT_OUTPUT_ROOT = "G:\\images\\output";

    /** 成功退出码 */
    private static final int EXIT_CODE_SUCCESS = 0;

    /** 失败退出码 */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 需压测的模型 ID 列表（与 OnnxModelRegistrar 注册一致）。
     */
    private static final String[] MODELS = {
            "yolov8s-world",
            "visdrone-small-detector",
            "yolov8n-pose",
    };

    public static void main(String[] args) throws Exception {
        String input = args.length > 0 ? args[0] : DEFAULT_INPUT;
        boolean passed = runTest(Path.of(input), Path.of(DEFAULT_OUTPUT_ROOT));
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public static boolean runTest(Path inputDir, Path outputRoot) throws Exception {
        if (!Files.isDirectory(inputDir)) {
            System.err.println("[FAIL] 输入非目录: " + inputDir);
            return false;
        }
        Files.createDirectories(outputRoot);
        log.info("===== 多模型批量绘图 =====");
        log.info("输入: {}, 输出根: {}", inputDir, outputRoot);

        try (var files = Files.list(inputDir)) {
            List<Path> images = files
                    .filter(p -> p.toString().matches("(?i).*\\.(jpg|png|jpeg|webp)$"))
                    .sorted()
                    .toList();
            if (images.isEmpty()) {
                System.err.println("[FAIL] 无图片文件: " + inputDir);
                return false;
            }
            boolean allOk = true;
            for (String modelId : MODELS) {
                log.info("--- 模型: {} ---", modelId);
                for (Path img : images) {
                    try {
                        drawAndSave(modelId, img, outputRoot);
                    } catch (Throwable e) {
                        System.err.println("[FAIL] " + modelId + " <- " + img.getFileName() + ": " + e.getMessage());
                        log.debug("[drawAndSave] exception", e);
                        allOk = false;
                    }
                }
            }
            return allOk;
        }
    }

    private static void drawAndSave(String modelId, Path imagePath, Path outputRoot) throws Exception {
        byte[] bytes = Files.readAllBytes(imagePath);
        ImageDetector det = ImageDetector.create(modelId);
        if (det == null) {
            throw new IllegalStateException("未通过 ImageDetector.create 获取: " + modelId);
        }
        long t0 = System.currentTimeMillis();
        List<DetectionInfo> infos = det.detect(bytes);
        long ms = System.currentTimeMillis() - t0;

        BufferedImage bi = ImageUtils.toBufferedImage(bytes);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.RED);
        g.setStroke(new java.awt.BasicStroke(2f));
        if (infos != null) {
            for (DetectionInfo info : infos) {
                if (info.x() < 0 || info.y() < 0 || info.width() <= 0 || info.height() <= 0) {
                    continue;
                }
                int x = Math.round(info.x());
                int y = Math.round(info.y());
                int w = Math.round(info.width());
                int h = Math.round(info.height());
                g.drawRect(x, y, w, h);
                g.drawString(String.format("%s %.2f", info.label(), info.confidence()), x + 2, Math.max(12, y - 4));
            }
        }
        g.dispose();

        String name = imagePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        Path dir = outputRoot.resolve(modelId);
        Files.createDirectories(dir);
        Path out = dir.resolve(stem + ".png");
        javax.imageio.ImageIO.write(bi, "png", out.toFile());
        System.out.printf("  [%s] %s | %d目标 %dms -> %s%n",
                modelId, name, infos == null ? 0 : infos.size(), ms, out);
    }
}

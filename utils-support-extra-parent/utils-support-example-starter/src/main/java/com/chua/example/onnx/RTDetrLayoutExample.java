package com.chua.example.onnx;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import com.chua.deeplearning.support.engine.DjlModelFactory;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.onnx.layout.RTDetrLayoutTranslator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RTDetrLayoutExample {

    static {
        try { nu.pattern.OpenCV.loadShared(); } catch (Throwable ignored) {}
    }

    private RTDetrLayoutExample() {}

    private static final Color[] COLORS = {
            Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.MAGENTA,
            Color.CYAN, Color.PINK, Color.YELLOW, new Color(128, 0, 255), new Color(0, 128, 0)
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java RTDetrLayoutExample <image> [output] [threshold]");
            System.exit(1);
        }
        String imagePath = args[0];
        String outPath = args.length > 1 ? args[1]
                : imagePath.replaceAll("\\.[^.]+$", "") + "_layout.png";
        float threshold = args.length > 2 ? Float.parseFloat(args[2]) : 0.4f;

        ModelRegistry.discoverAll();
        Path weights = ModelRegistry.resolveModelPath("rtdetr-layout");
        if (weights == null || !Files.exists(weights)) {
            System.err.println("[FAIL] weight resolve failed");
            System.exit(1);
        }
        System.out.println("[model] " + weights);

        try (DjlModelFactory factory =
                     new DjlModelFactory("rtdetr-layout", weights, () -> new RTDetrLayoutTranslator(threshold))) {
            Image djlImg = ImageFactory.getInstance().fromFile(Path.of(imagePath));
            DetectedObjects result = factory.predict(djlImg);

            // 用 Graphics2D 手动画粗框（保证可见）
            BufferedImage orig = ImageIO.read(new File(imagePath));
            Graphics2D g = orig.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            var items = result.<ai.djl.modality.cv.output.DetectedObjects.DetectedObject>items();
            int colorIdx = 0;
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                var bb = item.getBoundingBox();
                var rect = bb.getBounds();

                // 归一化 → 像素
                int px = Math.max(0, (int) (rect.getX() * orig.getWidth()));
                int py = Math.max(0, (int) (rect.getY() * orig.getHeight()));
                int pw = Math.min(orig.getWidth() - px, (int) (rect.getWidth() * orig.getWidth()));
                int ph = Math.min(orig.getHeight() - py, (int) (rect.getHeight() * orig.getHeight()));

                Color color = COLORS[colorIdx % COLORS.length];
                g.setColor(color);
                g.setStroke(new BasicStroke(Math.max(3, orig.getWidth() / 200)));
                g.drawRect(px, py, pw, ph);

                // 标签背景
                String label = item.getClassName() + " " + String.format("%.0f%%", item.getProbability() * 100);
                Font font = new Font("Arial", Font.BOLD, Math.max(14, orig.getWidth() / 50));
                g.setFont(font);
                int textY = py > 30 ? py - 5 : py + ph + 20;
                g.fillRect(px, textY - font.getSize(), g.getFontMetrics().stringWidth(label) + 8, font.getSize() + 4);
                g.setColor(Color.WHITE);
                g.drawString(label, px + 4, textY);
                g.setColor(color);

                System.out.printf("[%s] %s %.2f  (%d,%d %dx%d)%n",
                        color, item.getClassName(), item.getProbability(), px, py, pw, ph);
                colorIdx++;
            }
            g.dispose();

            File outFile = new File(outPath);
            ImageIO.write(orig, "png", outFile);
            System.out.println("[saved] " + outFile.getAbsolutePath()
                    + " (" + Math.round(outFile.length() / 1024.0) + " KB)");
            System.out.println("[PASS] 检出 " + items.size() + " 个区域");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

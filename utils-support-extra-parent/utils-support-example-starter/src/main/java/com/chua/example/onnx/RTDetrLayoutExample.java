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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * RT-DETR v2 文档版面检测示例。
 *
 * <h2>用法</h2>
 * <pre>
 *   java RTDetrLayoutExample &lt;图片路径&gt; [输出路径] [阈值]
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class RTDetrLayoutExample {

    static {
        try { nu.pattern.OpenCV.loadShared(); } catch (Throwable ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        }
    }

    private static final Color[] COLORS = {
            Color.RED, new Color(0, 100, 255), new Color(0, 180, 0),
            Color.ORANGE, Color.MAGENTA, Color.CYAN
    };

    private RTDetrLayoutExample() {}

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
            System.err.println("[FAIL] weight resolve");
            System.exit(1);
        }

        try (DjlModelFactory factory =
                     new DjlModelFactory("rtdetr-layout", weights, () -> new RTDetrLayoutTranslator(threshold))) {
            Image djlImg = ImageFactory.getInstance().fromFile(Path.of(imagePath));
            DetectedObjects result = factory.predict(djlImg);

            // 加载原图为 BufferedImage
            BufferedImage orig = ImageIO.read(new File(imagePath));
            Graphics2D g = orig.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            var items = result.<ai.djl.modality.cv.output.DetectedObjects.DetectedObject>items();
            int colorIdx = 0;

            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                var bb = item.getBoundingBox();
                var rect = bb.getBounds();

                int px = Math.max(0, (int) Math.round(rect.getX() * orig.getWidth()));
                int py = Math.max(0, (int) Math.round(rect.getY() * orig.getHeight()));
                int pw = Math.min(orig.getWidth() - px, (int) Math.round(rect.getWidth() * orig.getWidth()));
                int ph = Math.min(orig.getHeight() - py, (int) Math.round(rect.getHeight() * orig.getHeight()));

                if (pw < 5 || ph < 5) { continue; }

                Color color = COLORS[colorIdx % COLORS.length];
                g.setColor(color);
                g.setStroke(new BasicStroke(Math.max(4, orig.getWidth() / 150)));
                g.drawRect(px, py, pw, ph);

                String label = item.getClassName() + " " + String.format("%.0f%%", item.getProbability() * 100);
                g.setFont(new Font("Arial", Font.BOLD, Math.max(16, orig.getWidth() / 40)));
                int textY = py > 25 ? py - 5 : py + ph + 20;
                g.setColor(Color.WHITE);
                g.fillRect(px - 2, textY - 18, g.getFontMetrics().stringWidth(label) + 10, 22);
                g.setColor(color);
                g.drawString(label, px + 3, textY);

                System.out.printf("[%s] %s %.2f (%d,%d %dx%d)%n",
                        color, item.getClassName(), item.getProbability(), px, py, pw, ph);
                colorIdx++;
            }
            g.dispose();

            File outFile = new File(outPath);
            ImageIO.write(orig, "png", outFile);
            System.out.println("[saved] " + outFile.getAbsolutePath()
                    + " (" + colorIdx + " boxes)");
            System.out.println("[PASS]");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

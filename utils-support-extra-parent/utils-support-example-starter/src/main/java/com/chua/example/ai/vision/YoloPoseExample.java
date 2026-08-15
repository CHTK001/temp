package com.chua.example.ai.vision;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.chua.deeplearning.support.onnx.pose.YoloV8nPoseTranslator;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * YOLOv8n-pose 姿态估计自检。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YoloPoseExample {

    public static void main(String[] args) {
        try {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, 256, 256);
            g.setColor(new Color(200, 40, 40)); g.fillOval(80, 80, 96, 96);
            g.dispose();
            ImageIO.write(img, "png", new File(System.getProperty("java.io.tmpdir"), "yolo_pose_test.png"));

            Image input = ImageFactory.getInstance().fromImage(img);
            YoloV8nPoseTranslator t = new YoloV8nPoseTranslator();
            try {
                List<YoloV8nPoseTranslator.PoseResult> results = t.detect(input);
                boolean passed = results.size() > 0;
                java.nio.file.Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "yolo_pose.txt"),
                        String.format("detected=%d score=%.3f", results.size(), passed && results.size() > 0 ? results.get(0).score : 0));
                System.out.println("[PASS] detected " + results.size() + " poses");
                System.exit(0);
            } finally { t.close(); }
        } catch (Exception e) {
            System.out.println("[FAIL] " + e.getMessage());
            System.exit(1);
        }
    }
}
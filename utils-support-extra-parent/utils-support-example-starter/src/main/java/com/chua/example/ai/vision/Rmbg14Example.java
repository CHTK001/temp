package com.chua.example.ai.vision;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.translator.ITranslator;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * RMBG-1.4 抠图自检。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class Rmbg14Example {

    private static final int TEST_SIZE = 256;

    public static void main(String[] args) {
        try {
            ModelRegistry.discoverAll();
            BufferedImage testImg = new BufferedImage(TEST_SIZE, TEST_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = testImg.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, TEST_SIZE, TEST_SIZE);
            g.setColor(new Color(200, 40, 40)); g.fillRect(60, 60, 136, 136);
            g.dispose();
            ImageIO.write(testImg, "png", new File(System.getProperty("java.io.tmpdir"), "rmbg14_test.png"));

            Image input = ImageFactory.getInstance().fromImage(testImg);
            Path modelPath = ModelRegistry.resolveModelPath("rmbg14");
            log.info("rmbg14 model path: {}", modelPath);
            ITranslator<Object, Object> translator = ModelRegistry.createTranslator("rmbg14", modelPath);
            Image result = (Image) translator.translate(input);
            BufferedImage buf = (BufferedImage) result.getWrappedImage();
            ImageIO.write(buf, "png", new File(System.getProperty("java.io.tmpdir"), "rmbg14_mask.png"));
            log.info("[PASS] RMBG-1.4 抠图完成");
            System.exit(0);
        } catch (Exception e) {
            log.error("[FAIL] RMBG-1.4 异常: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
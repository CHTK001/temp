package com.chua.example.onnx;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import com.chua.deeplearning.support.engine.DjlModelFactory;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.onnx.layout.RTDetrLayoutTranslator;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RTDetrLayoutExample {

    static {
        try { nu.pattern.OpenCV.loadShared(); } catch (Throwable ignored) {}
    }

    private RTDetrLayoutExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java RTDetrLayoutExample <image> [output] [threshold]");
            System.exit(1);
        }
        String imagePath = args[0];
        String outPath = args.length > 1 ? args[1]
                : imagePath.replaceAll("\\.[^.]+$", "") + "_rtdetr_layout.png";
        float threshold = args.length > 2 ? Float.parseFloat(args[2]) : 0.5f;

        ModelRegistry.discoverAll();
        Path weights = ModelRegistry.resolveModelPath("rtdetr-layout");
        if (weights == null || !Files.exists(weights)) {
            System.err.println("[FAIL] weight resolve: " + weights);
            System.exit(1);
        }

        try (DjlModelFactory factory =
                     new DjlModelFactory("rtdetr-layout", weights, () -> new RTDetrLayoutTranslator(threshold))) {
            Image img = ImageFactory.getInstance().fromFile(Path.of(imagePath));
            DetectedObjects result = factory.predict(img);
            System.out.println(result);
            img.drawBoundingBoxes(result);
            Path out = Path.of(outPath);
            img.save(Files.newOutputStream(out), "png");
            System.out.println("[saved] " + out);
            System.out.println("[PASS]");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

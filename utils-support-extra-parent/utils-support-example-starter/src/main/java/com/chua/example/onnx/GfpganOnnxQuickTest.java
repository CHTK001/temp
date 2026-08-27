package com.chua.example.onnx;

import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Quick GFPGAN ONNX smoke test.
 */
public class GfpganOnnxQuickTest {

    public static void main(String[] args) throws Exception {
        String imagePath = args.length > 0 ? args[0] : "D:/images/3peoplebeauty.jpg";
        String outDir = "D:/images/output";
        Files.createDirectories(Path.of(outDir));

        System.out.println("Forcing OnnxModelRegistrar SPI...");
        Class.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");

        System.out.println("Loading GFPGAN ONNX (onnx-gfpgan)...");
        long t0 = System.currentTimeMillis();
        ImageEnhancer gfpgan = ImageEnhancer.create("onnx-gfpgan");
        System.out.println("Model loaded in " + (System.currentTimeMillis() - t0) + "ms");

        System.out.println("Loading image: " + imagePath);
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        System.out.println("Image bytes: " + img.length);

        System.out.println("Running GFPGAN ONNX inference...");
        long t1 = System.currentTimeMillis();
        byte[] restored = gfpgan.enhance(img);
        long cost = System.currentTimeMillis() - t1;
        System.out.println("Inference done in " + cost + "ms");

        if (restored == null || restored.length == 0) {
            System.err.println("ERROR: output is null/empty!");
            System.exit(1);
        }

        Path outPath = Path.of(outDir, "gfpgan_onnx_test.png");
        Files.write(outPath, restored);
        System.out.println("Output bytes: " + restored.length + " -> " + outPath.toAbsolutePath());
        System.out.println("DONE");
    }
}

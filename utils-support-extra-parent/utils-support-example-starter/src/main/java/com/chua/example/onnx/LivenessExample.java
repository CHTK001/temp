package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.translator.ITranslator;
import com.chua.deeplearning.support.utils.ImageUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Example: LivenessExample
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class LivenessExample {

    private LivenessExample() {
    }

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();

        String[] models = {"face-liveness-flrgb", "face-liveness-flxc"};
        String imagePath = args.length > 0 ? args[0] : "D:/images/1ai.png";
        Path outDir = Path.of("D:/images/output/liveness");
        Files.createDirectories(outDir);

        for (String modelId : models) {
            log.info("=== " + modelId + " ===");
            var entry = ModelRegistry.get(modelId);
            if (entry == null) {
                log.info("  注册: FAIL");
                continue;
            }
            log.info("  注册: OK");
            var path = ModelRegistry.resolveModelPath(modelId);
            log.info("  路径: " + (path != null ? path : "null"));
            if (path == null || !path.toFile().exists()) {
                log.info("  模型文件不存在");
                continue;
            }

            byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
            var translator = (ITranslator<Object, Object>) ModelRegistry.createTranslator(modelId, null);
            long t0 = System.currentTimeMillis();
            Object result = translator.translate(imageBytes);
            long cost = System.currentTimeMillis() - t0;
            log.info("  耗时: " + cost + "ms");
            log.info("  结果: " + result);
            if (translator instanceof AutoCloseable ac) {
                try {
                    ac.close();
                } catch (Exception ignore) {
                    log.warn("Caught: {}", ignore.getMessage());
                }
            }
        }
        log.info("[LivenessVerify] DONE");
    }
}

package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.translator.ITranslator;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Example: DepthAnythingExample
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DepthAnythingExample {

    private DepthAnythingExample() {
    }

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        String imagePath = args.length > 0 ? args[0] : "D:/images/1ai.png";
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));

        var entry = ModelRegistry.get("depth-anything");
        log.info("[depth-anything] 注册: " + (entry != null ? "OK" : "FAIL"));
        if (entry == null) { System.exit(1); }

        var path = ModelRegistry.resolveModelPath("depth-anything");
        log.info("[depth-anything] 路径: " + path);
        if (path == null || !path.toFile().exists()) { System.exit(1); }

        Object translator = ModelRegistry.createTranslator("depth-anything", null);
        long t0 = System.currentTimeMillis();
        Object out = ((ITranslator<Object, Object>) translator).translate(imageBytes);
        long cost = System.currentTimeMillis() - t0;
        boolean ok = out != null;
        log.info("[depth-anything] 耗时=" + cost + "ms 输出类型=" + out.getClass().getName());
        log.info(ok ? "[DepthAnythingVerify] ALL PASS" : "[DepthAnythingVerify] FAIL");
        if (!ok) { System.exit(1); }
    }
}

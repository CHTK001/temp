package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.translator.ITranslator;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DepthAnythingExample {

    private DepthAnythingVerify() {
    }

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        String imagePath = args.length > 0 ? args[0] : "D:/images/1ai.png";
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));

        var entry = ModelRegistry.get("depth-anything");
        System.out.println("[depth-anything] 注册: " + (entry != null ? "OK" : "FAIL"));
        if (entry == null) { System.exit(1); }

        var path = ModelRegistry.resolveModelPath("depth-anything");
        System.out.println("[depth-anything] 路径: " + path);
        if (path == null || !path.toFile().exists()) { System.exit(1); }

        Object translator = ModelRegistry.createTranslator("depth-anything", null);
        long t0 = System.currentTimeMillis();
        Object out = ((ITranslator<Object, Object>) translator).translate(imageBytes);
        long cost = System.currentTimeMillis() - t0;
        boolean ok = out != null;
        System.out.println("[depth-anything] 耗时=" + cost + "ms 输出类型=" + out.getClass().getName());
        System.out.println(ok ? "[DepthAnythingVerify] ALL PASS" : "[DepthAnythingVerify] FAIL");
        if (!ok) { System.exit(1); }
    }
}
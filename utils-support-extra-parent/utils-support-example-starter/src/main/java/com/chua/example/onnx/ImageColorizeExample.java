package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.onnx.colorize.ImageColorizeTranslator;
import lombok.extern.slf4j.Slf4j;

/**
 * 老照片上色（image-colorize）冒烟示例：校验模型注册与 Translator 可实例化。
 *
 * <h2>用法</h2>
 * <pre>
 *   java ImageColorizeExample
 * </pre>
 *
 * <p>退出码：{@code 0}=通过，{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ImageColorizeExample {

    private ImageColorizeExample() {
    }

    /**
     * 入口。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        if (ModelRegistry.get("image-colorize") == null) {
            System.err.println("[FAIL] image-colorize 未注册到 ModelRegistry");
            System.exit(1);
        }
        // 嵌入式验证：注册表应能从 classpath 中的模型 jar 解析出权重文件
        java.nio.file.Path weights = ModelRegistry.resolveModelPath("image-colorize");
        if (weights == null || !java.nio.file.Files.exists(weights)) {
            System.err.println("[FAIL] 嵌入式权重解析失败: " + weights
                    + "（请确认已引入 utils-support-models-onnx-image-colorize）");
            System.exit(1);
        }
        log.info("[OK] 嵌入式权重已解析: " + weights);
        try {
            new ImageColorizeTranslator();
        } catch (Exception e) {
            System.err.println("[FAIL] ImageColorizeTranslator 创建失败: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("[PASS] image-colorize 嵌入式权重就绪且 Translator 创建成功");

        // 真实推理模式：--input=<灰度图> [--output=<彩色输出>]
        String input = null;
        String output = null;
        for (String a : args) {
            if (a.startsWith("--input=")) {
                input = a.substring("--input=".length());
            } else if (a.startsWith("--output=")) {
                output = a.substring("--output=".length());
            }
        }
        if (input != null) {
            try (com.chua.deeplearning.support.engine.DjlModelFactory factory =
                         new com.chua.deeplearning.support.engine.DjlModelFactory(
                                 "image-colorize", weights, ImageColorizeTranslator::new)) {
                ai.djl.modality.cv.Image src =
                        ai.djl.modality.cv.ImageFactory.getInstance().fromFile(java.nio.file.Path.of(input));
                ai.djl.modality.cv.Image colorized = factory.predict(src);
                String outPath = output != null ? output
                        : input.replaceAll("\\.[^.]+$", "") + "_colorized.png";
                colorized.save(java.nio.file.Files.newOutputStream(java.nio.file.Path.of(outPath)), "png");
                long size = java.nio.file.Files.size(java.nio.file.Path.of(outPath));
                if (size < 10240) {
                    System.err.println("[FAIL] 上色结果过小: " + size + " 字节");
                    System.exit(1);
                }
                log.info("[OK] 上色完成 -> " + outPath + " (" + (size / 1024) + " KB)");
                System.out.println("[PASS] 推理链路完整");
                System.exit(0);
            } catch (Exception e) {
                System.err.println("[FAIL] 推理失败: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        System.exit(0);
    }
}

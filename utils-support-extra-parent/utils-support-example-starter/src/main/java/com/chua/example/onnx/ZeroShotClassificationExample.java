package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.image.ImageClassifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 零样本分类端到端测试。
 *
 * <p>测试 SigLIP、CLIP-ViT-B-32、MobileCLIP-S0 三个零样本分类模型。</p>
 *
 * <p>运行方式：</p>
 * <pre>{@code
 *   # 测试所有零样本分类模型
 *   java ZeroShotClassificationTest
 *
 *   # 指定模型 + 图片
 *   java ZeroShotClassificationTest siglip-zero-shot-classification D:/images/test.jpg
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ZeroShotClassificationExample extends ExampleBase {

    /** 零样本分类模型列表 */
    private static final String[] MODELS = {
            "siglip-zero-shot-classification",
            "clip-vit-zero-shot",
            "mobileclip-zero-shot"
    };

    /** 默认测试图片路径 */
    private static final String DEFAULT_IMAGE = "D:/images/test.jpg";

    /** 创建 ZeroShotClassificationTest 实例 */
    private ZeroShotClassificationTest() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : null;
        String imagePath = args.length > 1 ? args[1] : DEFAULT_IMAGE;

        if ("list".equalsIgnoreCase(model)) {
            printModels("zero-shot-classify", "onnx",
                    java.util.Arrays.stream(MODELS)
                            .map(id -> com.chua.common.support.ai.chat.ModelDefinition.builder().id(id).build())
                            .toList());
            return;
        }

        // 读取测试图片
        Path imageFile = Path.of(imagePath);
        if (!Files.exists(imageFile)) {
            log.info("[ERROR] 测试图片不存在: " + imagePath);
            log.info("请提供有效的图片路径，例如: java ZeroShotClassificationTest siglip-zero-shot-classification D:/images/test.jpg");
            return;
        }
        byte[] imageData = Files.readAllBytes(imageFile);
        log.info("[INFO] 测试图片: " + imagePath + " (" + imageData.length + " bytes)");
        log.info("");

        if (model != null) {
            // 测试指定模型
            testModel(model, imageData);
        } else {
            // 测试所有模型
            for (String m : MODELS) {
                testModel(m, imageData);
            }
        }
    }

    /**
     * 测试单个零样本分类模型。
     *
     * @param modelId   模型 ID
     * @param imageData 图片数据
     */
    private static void testModel(String modelId, byte[] imageData) {
        log.info("===== [零样本分类] 模型: " + modelId + " =====");
        try {
            ImageClassifier classifier = ImageClassifier.create(modelId);
            if (classifier == null) {
                log.info("  ⚠️ 跳过 - 模型未注册: " + modelId);
                return;
            }
            long t0 = System.currentTimeMillis();
            String result = classifier.classify(imageData);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("  分类结果: " + result);
            log.info("  耗时: " + elapsed + "ms");
            log.info("  ✅ 通过");
        } catch (Exception e) {
            log.info("  ❌ 失败: " + e.getMessage());
        }
        log.info("");
    }
}

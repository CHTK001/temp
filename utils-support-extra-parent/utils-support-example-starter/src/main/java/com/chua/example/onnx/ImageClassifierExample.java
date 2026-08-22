package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageClassifier;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图像分类能力示例。
 *
 * <p>通过 {@link ImageClassifier#create(String)} 切换模型（efficient-net / mobilenet / fer-plus 等）。</p>
 *
 * <pre>{@code
 *   ImageClassifierExample list
 *   ImageClassifierExample efficient-net-lite0-classification cat.jpg
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
public final class ImageClassifierExample extends ExampleBase {

    /** 创建 ImageClassifierExample 实例 */
    private ImageClassifierExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : null;
        String imagePath = args.length > 1 ? args[1] : null;

        if (model == null) {
            printModels("classify", "onnx", ModelRegistry.getAll().stream()
                    .filter(e -> e.capabilityInterface() == com.chua.deeplearning.support.image.ImageClassifier.class)
                    .map(e -> com.chua.common.support.ai.chat.ModelDefinition.builder().id(e.modelId()).build())
                    .toList());
            return;
        }
        if (imagePath == null) {
            log.info("[classify] 需要图片路径");
            return;
        }
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        ImageClassifier classifier = ImageClassifier.create(model);
        long t0 = System.currentTimeMillis();
        String label = classifier.classify(img);
        log.info("[classify] model: " + model + " 图片: " + imagePath);
        log.info("       label: " + label);
        printResult("classify", "onnx", model, t0);
    }
}

package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.image.ImageSegmenter;

/**
 * ZeroShot 调试 Example：演示 ModelRegistry 中各 Image* 模型的 SPI 注册情况，
 * 配合 ReflectUtils.forName 强制触发 OnnxModelRegistrar 的 SPI 加载。
 *
 * <p>用于人工诊断模型注册是否成功，不作为生产代码使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ZeroShotDebugDemoExample {

    private ZeroShotDebugDemoExample() { }

    public static void main(String[] args) throws Exception {
        log.info("=== Debug: Loading OnnxModelRegistrar ===");
        ReflectUtils.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");
        log.info("=== Debug: OnnxModelRegistrar loaded ===");

        log.info("\n=== All registered models ===");
        var all = ModelRegistry.getAll();
        log.info("Total: {}", all.size());

        log.info("\n=== ImageClassifier models ===");
        var classifiers = ImageClassifier.listModels();
        log.info("Count: {}", classifiers.size());
        classifiers.forEach(id -> log.info("  - {}", id));

        log.info("\n=== ImageDetector models ===");
        var detectors = ImageDetector.listModels();
        log.info("Count: {}", detectors.size());
        detectors.forEach(id -> log.info("  - {}", id));

        log.info("\n=== ImageSegmenter models ===");
        var segmenters = ImageSegmenter.listModels();
        log.info("Count: {}", segmenters.size());
        segmenters.forEach(id -> log.info("  - {}", id));
    }
}

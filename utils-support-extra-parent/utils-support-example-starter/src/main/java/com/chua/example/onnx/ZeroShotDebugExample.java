package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.image.ImageSegmenter;

@Slf4j
/**
 * Example: ZeroShotDebugExample
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ZeroShotDebugExample {
    public static void main(String[] args) throws Exception {
        log.info("=== Debug: Loading OnnxModelRegistrar ===");
        Class.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");
        log.info("=== Debug: OnnxModelRegistrar loaded ===");
        
        log.info("\n=== All registered models ===");
        var all = ModelRegistry.getAll();
        log.info("Total: " + all.size());
        
        log.info("\n=== ImageClassifier models ===");
        var classifiers = ImageClassifier.listModels();
        log.info("Count: " + classifiers.size());
        classifiers.forEach(id -> log.info("  - " + id));
        
        log.info("\n=== ImageDetector models ===");
        var detectors = ImageDetector.listModels();
        log.info("Count: " + detectors.size());
        detectors.forEach(id -> log.info("  - " + id));
        
        log.info("\n=== ImageSegmenter models ===");
        var segmenters = ImageSegmenter.listModels();
        log.info("Count: " + segmenters.size());
        segmenters.forEach(id -> log.info("  - " + id));
    }
}

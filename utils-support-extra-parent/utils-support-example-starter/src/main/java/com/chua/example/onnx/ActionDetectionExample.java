package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ActionDetector;
import com.chua.deeplearning.support.model.ActionDetectionResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 视频动作检测示例。
 *
 * <p>通过 {@link ActionDetector#create(String)} 使用 c3d-action-detection 模型。</p>
 *
 * <pre>{@code
 *   ActionDetectionExample list
 *   ActionDetectionExample c3d-action-detection smoking.mp4
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ActionDetectionExample extends ExampleBase {

    private ActionDetectionExample() {
    }

    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : null;
        String videoPath = args.length > 1 ? args[1] : null;

        if (model == null) {
            printModels("action-detection", "onnx", ModelRegistry.getAll().stream()
                    .filter(e -> e.capabilityInterface() == com.chua.deeplearning.support.image.ActionDetector.class)
                    .map(e -> com.chua.common.support.ai.chat.ModelDefinition.builder().id(e.modelId()).build())
                    .toList());
            return;
        }
        if (videoPath == null) {
            log.info("[action-detection] 需要视频路径");
            return;
        }
        byte[] video = Files.readAllBytes(Path.of(videoPath));
        ActionDetector detector = ActionDetector.create(model);
        long t0 = System.currentTimeMillis();
        List<ActionDetectionResult> results = detector.detect(video);
        log.info("[action-detection] model: {} 视频: {}", model, videoPath);
        log.info("       动作数: {}", results.size());
        for (ActionDetectionResult r : results) {
            log.info("       {}s: {} (conf={}) [{},{},{},{}]",
                    r.timestamp(), r.label(), String.format("%.3f", r.confidence()),
                    Math.round(r.x()), Math.round(r.y()), Math.round(r.width()), Math.round(r.height()));
        }
        printResult("action-detection", "onnx", model, t0);
    }
}
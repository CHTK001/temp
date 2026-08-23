package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.pose.PoseEstimator;
import com.chua.deeplearning.support.pose.PoseKeypoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 姿态估计能力示例。
 *
 * <p>通过 {@link PoseEstimator#create(String)} 切换模型（yolov8n-pose 等）。</p>
 *
 * <pre>{@code
 *   PoseEstimatorExample yolov8n-pose person.jpg
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
public final class PoseEstimatorExample extends BaseExample {

    /** 创建 PoseEstimatorExample 实例 */
    private PoseEstimatorExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : "yolov8n-pose";
        String imagePath = args.length > 1 ? args[1] : null;
        if (imagePath == null) {
            log.info("[pose] 需要图片路径");
            return;
        }
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        PoseEstimator estimator = PoseEstimator.create(model);
        long t0 = System.currentTimeMillis();
        List<PoseKeypoint> keypoints = estimator.estimate(img);
        log.info("[pose] model: " + model + " 图片: " + imagePath);
        log.info("       关键点数: " + keypoints.size());
        if (!keypoints.isEmpty()) {
            PoseKeypoint first = keypoints.get(0);
            log.info(String.format("       首个关键点: %s (%.0f, %.0f) conf=%.2f",
                    first.name(), first.x(), first.y(), first.confidence()));
        }
        printResult("pose", "onnx", model, t0);
    }
}

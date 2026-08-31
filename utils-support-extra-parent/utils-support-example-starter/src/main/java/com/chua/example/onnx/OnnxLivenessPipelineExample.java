package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import com.chua.deeplearning.support.model.PredictRectangle;

import static com.chua.deeplearning.support.utils.ImageCropUtils.crop;
import static com.chua.deeplearning.support.liveness.LivenessDetector.create;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * FLRGB 活体链路验证：onnx-retinaface 检测 → FLRGB 活体判定。
 *
 * <pre>{@code
 *   OnnxLivenessPipelineExample G:\images\三个人.jpg
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class OnnxLivenessPipelineExample extends BaseExample {

    /** 创建 OnnxLivenessPipelineExample 实例 */
    private OnnxLivenessPipelineExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String imagePath = args.length > 0 ? args[0] : "G:\\images\\三个人.jpg";
        byte[] img = Files.readAllBytes(Path.of(imagePath));

        FacePipeline pipeline = FacePipeline.builder()
                .detector("onnx-retinaface")
                .liveness("face-liveness-flrgb")
                .requireLive(true)
                .build();
        pipeline.setCallback(new FacePipelineDiskCallback(Path.of("G:\\images\\output\\liveness")));

        long t0 = System.currentTimeMillis();
        var boxes = pipeline.detectBoxes(img);
        log.info("[liveness-pipeline] 检测框数=" + boxes.size());
        if (!boxes.isEmpty()) {
            var box = boxes.get(0);
            byte[] face = crop(img, box);
            log.info("[liveness-pipeline] 裁剪人脸 bytes=" + (face == null ? "null" : face.length));
            LivenessDetector ld =
                    create("face-liveness-flrgb");
            try {
                log.info("[liveness-pipeline] isLive=" + ld.isLive(face));
            } catch (Exception e) {
                log.info("[liveness-pipeline] isLive ERR: " + e.getMessage());
            }
            try {
                log.info("[liveness-pipeline] liveScore=" + ld.liveScore(face));
            } catch (Exception e) {
                log.info("[liveness-pipeline] liveScore ERR: " + e.getMessage());
            }
        }
        var hits = pipeline.detectPipeline(img);
        log.info("[liveness-pipeline] 图片=" + imagePath + " 检测耗时=" + (System.currentTimeMillis() - t0) + "ms");
        log.info("       人脸数: " + hits.size());
        for (var hit : hits) {
            PredictRectangle box = hit.box();
            log.info(String.format("       box=(%.0f,%.0f) %.0fx%.0f 活体=%s 分数=%.3f",
                    box.x(), box.y(), box.width(), box.height(),
                    hit.live() ? "Y" : "N", hit.liveScore()));
        }
        printResult("liveness-pipeline", "onnx", "face-liveness-flrgb", t0);
    }
}

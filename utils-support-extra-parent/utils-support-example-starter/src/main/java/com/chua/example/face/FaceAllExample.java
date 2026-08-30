package com.chua.example.face;

import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 人脸全能力综合测试 — 检测 + 特征 + 活体 + 关键点 + 属性 + 表情 + 质量 + 比对。
 *
 * <p>对含人脸图测试 FacePipeline 全部已注入能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FaceAllExample {
    private FaceAllExample() { }


    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    public static boolean runTest() throws Exception {
        // 注入全部可用能力
        FacePipeline face = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")
                .feature("faceplugin-face-feature")
                .liveness("face-liveness-flrgb")
                .landmark("faceplugin-face-landmark")
                .attribute("age-race-gender")
                .emotion("emotion-ferplus")
                .build();
        face.setCallback(new FacePipelineDiskCallback(Path.of("D:\\images\\output\\face_all")));

        log.info("===== 人脸全能力测试 =====");
        List<String> images = List.of(
                "1people.png", "1people2.png", "man.png", "mask2.jpeg", "mask6.jpeg",
                "lineart.png", "car plate1.webp", "fire.webp");

        for (String name : images) {
            try {
                Path path = Path.of("D:\\images\\" + name);
                if (!Files.exists(path)) {
                    continue;
                }
                log.info("\n=== " + name + " ===");
                long t0 = System.currentTimeMillis();
                byte[] imageData = Files.readAllBytes(path);

                // 1. 检测
                var hits = face.detect(imageData);
                log.info("  检测: " + hits.size() + " 张脸 (" + (System.currentTimeMillis() - t0) + "ms)");
                if (hits.isEmpty()) {
                    continue;
                }

                // 2. 对每张人脸跑能力（活体/表情/特征/关键点）
                byte[] faceCrop = hits.get(0).faceImage();
                try {
                    float[] feat = face.extractFeature(faceCrop);
                    log.info("  特征: " + (feat == null ? 0 : feat.length) + " 维");
                } catch (Exception e) {
                    log.info("  特征: FAIL " + shortMsg(e));
                }
                try {
                    String attrs = face.attributes(faceCrop);
                    log.info("  属性: " + (attrs == null || attrs.isBlank() ? "null" : attrs));
                } catch (Exception e) {
                    log.info("  属性: FAIL " + shortMsg(e));
                }
                try {
                    String emo = face.emotion(faceCrop);
                    log.info("  表情: " + (emo == null || emo.isBlank() ? "null" : emo));
                } catch (Exception e) {
                    log.info("  表情: FAIL " + shortMsg(e));
                }
                try {
                    float[] lm = face.landmark(faceCrop);
                    log.info("  关键点: " + (lm == null ? 0 : lm.length) + " 点");
                } catch (Exception e) {
                    log.info("  关键点: FAIL " + shortMsg(e));
                }
                for (int i = 0; i < hits.size(); i++) {
                    try {
                        float live = face.liveScore(hits.get(i).faceImage());
                        log.info("  活体[脸" + (i + 1) + "]: " + String.format("%.3f", live));
                    } catch (Exception e) {
                        log.info("  活体[脸" + (i + 1) + "]: FAIL " + shortMsg(e));
                    }
                }
                log.info("  总耗时: " + (System.currentTimeMillis() - t0) + "ms");
            } catch (Exception e) {
                log.info("  FAIL: " + shortMsg(e));
            }
        }

        // 1:1 比对
        log.info("\n===== 1:1 比对 =====");
        try {
            byte[] img1 = Files.readAllBytes(Path.of("D:\\images\\1people.png"));
            byte[] img2 = Files.readAllBytes(Path.of("D:\\images\\man.png"));
            var r = face.compareWithMeta(img1, img2);
            log.info(" 1people vs man: " + r);
        } catch (Exception e) {
            log.info("  FAIL: " + shortMsg(e));
        }
        return true;
    }

    /**
     * 截取异常信息。
     *
     * @param e 异常
     * @return 简短信息
     */
    private static String shortMsg(Throwable e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : (m.length() > 120 ? m.substring(0, 120) : m);
    }
}
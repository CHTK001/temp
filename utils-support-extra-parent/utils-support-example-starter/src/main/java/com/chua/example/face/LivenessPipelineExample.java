package com.chua.example.face;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.AbstractIdentificationEngine;
import com.chua.deeplearning.support.liveness.LivenessDetector;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 活体检测诊断 — 单独测试 face-liveness-flrgb 模型。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LivenessPipelineExample {
    private LivenessPipelineExample() { }


    public static void main(String[] args) throws Exception {
        try {
            LivenessDetector liveness = LivenessDetector.create("face-liveness-flrgb");
            byte[] img = Files.readAllBytes(Path.of("D:\\images\\1people.png"));
            try {
                float score = liveness.liveScore(img);
                log.info("FLRGB liveScore = " + score);
            } catch (Exception e) {
                log.info("liveScore FAIL:");
                e.printStackTrace();
            }
            try {
                boolean live = liveness.isLive(img);
                log.info("FLRGB isLive = " + live);
            } catch (Exception e) {
                log.info("isLive FAIL:");
                e.printStackTrace();
            }
        } catch (Throwable t) {
            log.info("create FAIL:");
            t.printStackTrace();
        }
        System.exit(0);
    }
}
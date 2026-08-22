package com.chua.example.face;

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
public class LivenessPipelineExample {

    public static void main(String[] args) throws Exception {
        try {
            LivenessDetector liveness = LivenessDetector.create("face-liveness-flrgb");
            byte[] img = Files.readAllBytes(Path.of("D:\\images\\1people.png"));
            try {
                float score = liveness.liveScore(img);
                System.out.println("FLRGB liveScore = " + score);
            } catch (Exception e) {
                System.out.println("liveScore FAIL:");
                e.printStackTrace();
            }
            try {
                boolean live = liveness.isLive(img);
                System.out.println("FLRGB isLive = " + live);
            } catch (Exception e) {
                System.out.println("isLive FAIL:");
                e.printStackTrace();
            }
        } catch (Throwable t) {
            System.out.println("create FAIL:");
            t.printStackTrace();
        }
        System.exit(0);
    }
}
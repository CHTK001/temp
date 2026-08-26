package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.feature.FeatureClient;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * ArcFace 人脸识别示例。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ArcFaceExample extends BaseExample {
    private ArcFaceExample() { }


    /** Main */
    public static void main(String[] args) throws Exception {
        String imagePath = args.length > 0 ? args[0] : "C:\\\\Users\\\\Administrator\\\\AppData\\\\Local\\\\Temp\\\\opencode\\\\face_test.jpg";
        byte[] img = Files.readAllBytes(Paths.get(imagePath));

        try (FeatureClient feature = FeatureClient.create("onnx", "")) {
            long t0 = System.currentTimeMillis();
            float[] vec = feature.model("arc-face").extractImage(img);
            long tLoad = System.currentTimeMillis() - t0;
            log.info("[arc-face] \u6a21\u578b=arc-face (w600k_r50 @ 112x112)");
            log.info("       \u8f93\u51fa\u7ef4\u5ea6: " + vec.length + " (\u671f\u671b 512)");
            log.info("       \u524d 6 \u4e2a\u503c: " + String.format("%.3f %.3f %.3f %.3f %.3f %.3f", vec[0], vec[1], vec[2], vec[3], vec[4], vec[5]));

            float norm = 0f;
            for (float v : vec) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            log.info(String.format("       \u5411\u91cf\u6a21\u957f(L2): %.3f (\u5e94\u22481.0)", norm));

            // warmup
            feature.model("arc-face").extractImage(img);
            long sum = 0;
            for (int i = 0; i < 5; i++) {
                long s = System.currentTimeMillis();
                feature.model("arc-face").extractImage(img);
                sum += System.currentTimeMillis() - s;
            }
            log.info("       \u9996\u6b21\u8017\u65f6(\u542b\u52a0\u8f7d)=" + tLoad + "ms, \u7eaf\u63a8\u7406\u5e73\u5747=" + (sum / 5) + "ms");
            printResult("arc-face", "onnx", "arc-face", t0);
        }
    }
}


package com.chua.example.deeplearning.face;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.deeplearning.support.engine.AbstractIdentificationEngine;
import com.chua.deeplearning.support.engine.ModelProvider;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.model.PredictRectangle;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Safetensors 人脸检测示例。
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class SafetensorsFaceDetectionExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认人脸检测模型名
     */
    private static final String DEFAULT_MODEL_NAME = "facade-face";

    /**
     * 最低置信度阈值
     */
    private static final float MIN_CONFIDENCE = 0.5f;

    static {
        // Clear the ServiceProvider cache so fresh classloader is used
        ServiceProvider.CACHE.clear();
    }

    public static void main(String[] args) {
        SafetensorsFaceDetectionExample example = new SafetensorsFaceDetectionExample();
        boolean passed = example.runTest(args);
        log.info("[SafetensorsFaceDetectionExample] self-test passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：通过 ServiceLoader 与 ServiceProvider 校验模型加载，然后执行默认模型人脸检测。
     *
     * @param args 命令行参数，args[0] 可指定模型名
     * @return true 表示检测到至少一张人脸且置信度达标
     */
    public boolean runTest(String[] args) {
        try {
            // Test: direct Java ServiceLoader (works)
            long slCount = ServiceLoader.load(ModelProvider.class).stream().count();
            log.info("Java ServiceLoader count: {}", slCount);
            // Test: custom ServiceProvider (may be cached wrongly)
            log.info("ServiceProvider names: {}", ServiceProvider.of(ModelProvider.class).names());

            // Get engine and check models
            AbstractIdentificationEngine engine = (AbstractIdentificationEngine) AbstractIdentificationEngine.getInstance();
            log.info("[DEBUG] Available models: {}",
                    engine.getModels().stream().map(m -> m.name()).toList());

            String modelName = args != null && args.length > 0 ? args[0] : DEFAULT_MODEL_NAME;
            FaceDetector detector = FaceDetector.create(modelName);
            long start = System.currentTimeMillis();
            List<PredictRectangle> faces = detector.detect(loadTestImage());
            long elapsed = System.currentTimeMillis() - start;

            log.info("检测耗时: {}ms", elapsed);
            log.info("检出人数: {}", faces.size());
            if (!faces.isEmpty()) {
                log.info("详情:");
                int idx = 1;
                for (PredictRectangle f : faces) {
                    log.info("  #{}: 置信度={}, 坐标=(x={}, y={}, w={}, h={})",
                            idx++, f.confidence(), f.x(), f.y(), f.width(), f.height());
                }
            }

            boolean passed = !faces.isEmpty()
                    && faces.stream().allMatch(f -> f.confidence() >= MIN_CONFIDENCE);
            log.info("{}", passed ? "[PASS]" : "[FAIL]");
            return passed;
        } catch (Exception e) {
            log.error("[ERROR] {}", e.getMessage(), e);
            return false;
        }
    }

    private static byte[] loadTestImage() {
        try {
            Path local = Path.of("tests/data/face/sample.jpg");
            if (Files.exists(local)) {
                return Files.readAllBytes(local);
            }
            String url = "https://raw.githubusercontent.com/opencv/opencv/master/samples/data/lena.jpg";
            log.info("下载测试图片: {}", url);
            HttpClient client = HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            var response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(response.body())) {
                    BufferedImage img = ImageIO.read(bis);
                    if (img != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(img, "jpg", baos);
                        return baos.toByteArray();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载测试图片失败: {}", e.getMessage());
        }
        return null;
    }
}
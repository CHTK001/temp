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
import java.util.Locale;
import java.util.ServiceLoader;

/**
 * Safetensors 人脸检测示例。
 *
 * @author CH
 */
@Slf4j
public class SafetensorsFaceDetectionExample {

    static {
        // Clear the ServiceProvider cache so fresh classloader is used
        ServiceProvider.CACHE.clear();
    }

    public static void main(String[] args) {
        try {
            // Test: direct Java ServiceLoader (works)
            long slCount = ServiceLoader.load(ModelProvider.class).stream().count();
            System.err.println("Java ServiceLoader count: " + slCount);
            // Test: custom ServiceProvider (may be cached wrongly)
            System.err.println("ServiceProvider names: " + ServiceProvider.of(ModelProvider.class).names());

            // Get engine and check models
            AbstractIdentificationEngine engine = (AbstractIdentificationEngine) AbstractIdentificationEngine.getInstance();
            System.err.println("[DEBUG] Available models: " +
                    engine.getModels().stream().map(m -> m.name()).toList());

            String modelName = args.length > 0 ? args[0] : "facade-face";
            FaceDetector detector = FaceDetector.create(modelName);
            long start = System.currentTimeMillis();
            List<PredictRectangle> faces = detector.detect(loadTestImage());
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("检测耗时: " + elapsed + "ms");
            System.out.println("检出人数: " + faces.size());
            if (!faces.isEmpty()) {
                System.out.println("详情:");
                int idx = 1;
                for (PredictRectangle f : faces) {
                    System.out.printf(Locale.ROOT,
                            "  #%d: 置信度=%.3f, 坐标=(x=%d, y=%d, w=%d, h=%d)%n",
                            idx++, f.confidence(), f.x(), f.y(), f.width(), f.height());
                }
            }

            boolean passed = !faces.isEmpty()
                    && faces.stream().allMatch(f -> f.confidence() >= 0.5f);
            System.out.println(passed ? "[PASS]" : "[FAIL]");
            System.exit(passed ? 0 : 1);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
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
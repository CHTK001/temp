package com.chua.example.recognition;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.image.ImagePipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Example: LayoutPipelineExample
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class LayoutPipelineExample {

    private LayoutPipelineExample() {
    }

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        String imagePath = args.length > 0 ? args[0] : "D:/images/paper-real-full.png";
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));

        LayoutPipeline pipeline = LayoutPipeline.builder()
                .model("doc-layout-yolo")
                .build();

        long t0 = System.currentTimeMillis();
        Object result = pipeline.recognizeSingle(imageBytes);
        long cost = System.currentTimeMillis() - t0;

        @SuppressWarnings("unchecked")
        List<PredictRectangle> boxes = (List<PredictRectangle>) result;
        int titles = 0;
        int texts = 0;
        for (PredictRectangle box : boxes) {
            if ("title".equals(box.labelName())) {
                titles++;
            } else if ("plainText".equals(box.labelName())) {
                texts++;
            }
        }
        log.info("[LayoutPipeline] image=" + imagePath);
        log.info("[LayoutPipeline] 检出=" + boxes.size() + "  title=" + titles
                + "  plainText=" + texts + "  耗时=" + cost + "ms");
        for (PredictRectangle box : boxes) {
            System.out.printf("  %-12s conf=%.2f [%.0f,%.0f,%.0f,%.0f]%n",
                    box.labelName(), box.confidence(), box.x(), box.y(), box.width(), box.height());
        }

        if (titles >= 4 && texts >= 20) {
            log.info("[LayoutPipelineVerify] ALL PASS");
        } else {
            log.info("[LayoutPipelineVerify] FAIL (title=" + titles + ", plainText=" + texts + ")");
            System.exit(1);
        }
    }
}

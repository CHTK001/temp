package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DocLayoutYoloExample {

    private DocLayoutYoloVerify() {
    }

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();
        ImageUtils.load();
        String imagePath = args.length > 0 ? args[0] : "D:/images/paper-test.png";
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));

        var translator = ModelRegistry.createTranslator("doc-layout-yolo", null);
        long t0 = System.currentTimeMillis();
        Object out;
        try {
            out = translator.translate(imageBytes);
        } finally {
            if (translator instanceof AutoCloseable ac) {
                try { ac.close(); } catch (Exception ignore) {}
            }
        }
        long cost = System.currentTimeMillis() - t0;

        @SuppressWarnings("unchecked")
        List<PredictRectangle> boxes = (List<PredictRectangle>) out;
        int titleCount = 0;
        for (PredictRectangle box : boxes) {
            if ("title".equals(box.labelName())) {
                titleCount++;
            }
            System.out.printf("  %-14s conf=%.2f box=[%.0f,%.0f,%.0f,%.0f]%n",
                    box.labelName(), box.confidence(), box.x(), box.y(), box.width(), box.height());
        }

        log.info("[doc-layout-yolo] image=" + imagePath);
        log.info("[doc-layout-yolo] 检出数=" + boxes.size() + "  耗时=" + cost + "ms");
        log.info("[doc-layout-yolo] title=" + titleCount);

        if (titleCount >= 1) {
            log.info("[DocLayoutYoloVerify] ALL PASS");
        } else {
            log.info("[DocLayoutYoloVerify] FAIL (无 title 检出)");
            System.exit(1);
        }
    }
}

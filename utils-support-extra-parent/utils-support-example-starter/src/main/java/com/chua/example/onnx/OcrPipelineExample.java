package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * OCR 完整管线示例（检测 → 矫正 → 修复 → 识别）。
 *
 * <p>验证 {@link OcrPipeline} 的完整能力编排：
 * 检测（paddleocrv6-det）→ 方向矫正（pp-word-rotate）→ 文字修复（text-bsr）→ 识别（paddleocrv6-rec）。</p>
 *
 * <pre>{@code
 *   OcrPipelineExample
 *   OcrPipelineExample G:\images\车票.png
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class OcrPipelineExample extends BaseExample {

    /** 创建 OcrPipelineExample 实例 */
    private OcrPipelineExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String imagePath = "G:\\images\\车票.png";
        String detector = "paddleocrv6-medium-det";
        String recognizer = "paddleocrv6-medium-rec";
        String direction = "doc-orientation";
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--image=")) imagePath = args[i].substring("--image=".length());
            else if (args[i].startsWith("--detector=")) detector = args[i].substring("--detector=".length());
            else if (args[i].startsWith("--recognizer=")) recognizer = args[i].substring("--recognizer=".length());
            else if (args[i].startsWith("--direction=")) direction = args[i].substring("--direction=".length());
            else if (!args[i].startsWith("--")) imagePath = args[i];
        }
        byte[] img = Files.readAllBytes(Path.of(imagePath));

        OcrPipeline ocr = OcrPipeline.builder()
                .detector(detector)
                .recognizer(recognizer)
                .direction(direction)
                .build();

        long t0 = System.currentTimeMillis();
        List<OcrResult> results = ocr.recognizeDetail(img);
        long elapsed = System.currentTimeMillis() - t0;

        log.info("[ocr-pipeline] 图片: " + imagePath);
        log.info("       文本块数: " + results.size() + " 耗时=" + elapsed + "ms");
        for (OcrResult r : results) {
            log.info("       text='" + r.text() + "' conf=" + String.format("%.2f", r.confidence())
                    + " box=" + (r.boundingBox() == null ? "null" : String.format("(%.0f,%.0f) %.0fx%.0f",
                    r.boundingBox().x(), r.boundingBox().y(),
                    r.boundingBox().width(), r.boundingBox().height())));
        }
        log.info("");
    }
}

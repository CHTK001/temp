package com.chua.example.ocr;

import com.chua.deeplearning.support.ocr.OcrPipeline;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR 文字识别 — 完整处理流程图 + Builder 配置标注。
 *
 * <h2>处理流程</h2>
 * <pre>
 *   图片 byte[]
 *     │
 *     ▼ ① 图像预处理（direction，可选）
 *     │   └─ 文档方向矫正：0°/90°/180°/270° 自动旋转
 *     │
 *     ▼ ② 图像增强（enhancer，可选）
 *     │   └─ 图片增强：去噪/锐化/对比度提升，改善 OCR 准确率
 *     │
 *     ▼ ③ 文字检测（detector，必填）
 *     │   └─ PaddleOCR / DB 检测器：定位图片中所有文字区域
 *     │       ├─ 输出：List&lt;DetectionHit&gt;（每个区域：box + confidence）
 *     │       └─ 可配置：cropPadding（框扩展像素）、minConfidence
 *     │
 *     ▼ ④ 文字识别（recognizer，必填）
 *     │   └─ 逐区域 OCR：裁切文字区域 → CRNN/Transformer 识别
 *     │       └─ 输出：List&lt;OcrHit&gt;（text + confidence + box）
 *     │
 *     ▼ ⑤ 排序 + 标注（pipeline 内置）
 *     │   ├─ sortReadingOrder: 按阅读顺序重排
 *     │   └─ toDrawer: 生成标注图
 * </pre>
 *
 * <h2>Builder 组件</h2>
 * <pre>
 *   ★ 必填   detector       文字检测器（PaddleOCR/DB/DBNet）
 *   ★ 必填   recognizer     文字识别器（CRNN/Transformer）
 *   ○ direction              文档方向矫正（自动旋转 0/90/180/270）
 *   ○ enhancer               图像增强（去噪/锐化/对比度）
 *   ○ cropPadding            检测框扩展像素
 *   ○ minConfidence          最低置信度过滤
 *   ○ sortReadingOrder       按阅读顺序重排
 * </pre>
 *
 * @author CH
 *
 * @since 4.0.0
 */
public class OcrRecognitionDocExample {
    private OcrRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== OCR 文字识别 — 完整配置 =====");
        OcrPipeline ocr = OcrPipeline.builder()
                // ★ 必填
                .detector("paddleocrv6-det")            // 文字检测：定位文字区域
                .recognizer("paddleocrv6-rec")          // 文字识别：区域→文本
                // ○ 选填
                .direction("doc-orientation")           // 方向矫正：自动旋转
                .enhancer("image-enhancer-v1")          // 图像增强：去噪/锐化
                .cropPadding(8)                         // 检测框扩展
                .minConfidence(0.6f)                    // 最低置信度
                .sortReadingOrder(true)                 // 按阅读顺序重排
                .build();

        System.out.println("  pipeline ready");

        // OCR 示例（输出标注图）
        byte[] drawn = ocr.toDrawer(
                Files.readAllBytes(Path.of("test.png")));
        Files.write(Path.of("output.png"), drawn);
    }
}


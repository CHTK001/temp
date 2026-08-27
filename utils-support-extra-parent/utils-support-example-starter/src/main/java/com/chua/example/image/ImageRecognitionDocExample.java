package com.chua.example.image;

import com.chua.common.support.image.ImagePipeline;

/**
 * 图片检索 — 完整处理流程图 + Builder 配置标注。
 *
 * <h2>处理流程</h2>
 * <pre>
 *   图片 byte[]
 *     │
 *     ▼ ① 特征提取（featureExtractor，必填）
 *     │   └─ FeatureExtractor.extract(image)
 *     │       ├─ 输入：原始图片 byte[]
 *     │       └─ 输出：固定维度向量（512/1024维）
 *     │
 *     ▼ ② 图像分割（segmentation，可选预处理）
 *     │   └─ ImageSegmenter.segment(image)
 *     │       ├─ 前景/背景分离
 *     │       └─ 分割后区域分别提取特征，提升检索精度
 *     │
 *     ▼ ③ 入库 / 检索
 *     │   ├─ enroll: Vector(id, feature, metadata) → VectorStorage.add()
 *     │   └─ search: query → cosine SIMD 排序 → TopK 结果
 * </pre>
 *
 * <h2>Builder 组件</h2>
 * <pre>
 *   ★ 必填   featureExtractor  特征提取器（图片 → 向量，模型自动加载）
 *   ★ 必填   vectorStorage     向量库（默认 FileVectorStorage）
 * </pre>
 *
 * <h2>⚠ 缺失能力</h2>
 * <pre>
 *   图像分割    分离前景/背景以提升检索（ImageSegmenter 未接入）
 *   目标检测    定位图中物体（检测器未接入）
 *   图像增强    超分辨率/去噪等预处理（未接入）
 * </pre>
 */
public class ImageRecognitionDocExample {
    private ImageRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 图片检索 — Builder 配置 =====");
        ImagePipeline searcher = ImagePipeline.builder()
                .grayscale(true)   // ★ 灰度化预处理
                .build();                                     // ★ 管线自动构建

        System.out.println("  pipeline ready");
    }
}

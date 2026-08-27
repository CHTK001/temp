package com.chua.example.image;

import com.chua.deeplearning.support.image.ImageSearcher;

/**
 * 图片检索完整能力配置示例 — Builder 组件必填/选填标注。
 *
 * <p>运行方式：{@code java com.chua.example.image.ImageRecognitionDocExample}
 *
 * <h3>组件清单</h3>
 * <pre>
 *   ★ 必填   featureExtractor   特征提取器      （build 必需，模型自动加载）
 *   ★ 必填   vectorStorage      向量库          （build 必需，默认 FileVectorStorage）
 * </pre>
 *
 * <p><b>与人脸/声纹对比</b>：</p>
 * <pre>
 *   FacePipeline:        19 字段 / 29 方法 / 59 API / 完整流水线
 *   VoiceprintPipeline:   5 字段 /  6 方法 / 11 API / 最小可用
 *   ImageSearcher:        2 字段 /  3 方法 /  3 API / 最小可用（仅检索）
 * </pre>
 *
 * @since 4.0.0.42
 */
public class ImageRecognitionDocExample {
    private ImageRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 图片检索 — Builder 完整配置 =====");
        ImageSearcher searcher = ImageSearcher.builder()
                // ★ 必填 — 特征提取器（模型自动加载）
                .featureExtractor("image-feature-resnet")
                // ★ 必填 — 向量库（默认文件落盘）
                // .vectorStorage(...)   // 不设则自动创建
                .build();

        System.out.println("  pipeline ready");

        // 1:N 搜索示例
        // List<ImageSearchHit> hits = searcher.search(imageData, 10);

        // 入库示例
        // searcher.enroll("product_001", imageData, "iPhone 15 Pro");
    }
}

package com.chua.example.face;

import com.chua.deeplearning.support.face.FacePipeline;

/**
 * 人脸识别完整能力配置示例 — Builder 组件必填/选填标注。
 *
 * <p>运行方式：{@code java com.chua.example.face.FaceRecognitionDocExample}
 *
 * <h3>组件清单</h3>
 * <pre>
 *   ★ 必填   detector       人脸检测器          （build 必需）
 *   ★ 必填   feature        特征提取器          （build 必需）
 *   ★ 必填   vectorStorage  向量库              （build 必需，默认 FileVectorStorage）
 *   ○ 选填   liveness       活体检测器          （不设则跳过活体判断）
 *   ○ 选填   quality        质量评估器          （不设则不做过滤）
 *   ○ 选填   topK           检索条数            （默认 5）
 *   ○ 选填   requireLive    是否要求活体通过    （默认 true）
 *   ○ 选填   livenessThreshold  活体分阈值      （默认 0.5）
 *   ● 高级   anime          动漫人脸检测        （仅二次元场景）
 *   ● 高级   superResolution 超分辨率           （模糊脸增强）
 *   ● 高级   restorer       图像修复            （瑕疵修复）
 *   ● 高级   attribute      属性分类            （年龄/性别/种族）
 *   ● 高级   emotion        表情分类            （喜怒哀乐）
 *   ● 高级   landmark       关键点提取          （人脸 68/98 点）
 *   ● 高级   deepfake       深伪检测            （真假人脸判断）
 *   ● 高级   cropPadding    检测框裁切扩展像素  （默认 0）
 *   ● 高级   minFaceArea    最小脸面积阈值      （默认 0 不过滤）
 *   ● 高级   minConfidence  最小检测置信度      （默认 0 不过滤）
 *   ● 高级   sigmoidRecognize  特征 sigmoid     （默认 true）
 * </pre>
 *
 * @since 4.0.0.42
 */
public class FaceRecognitionDocExample {
    private FaceRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 最小配置（仅必填）=====");
        FacePipeline minimal = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")   // ★ 必填
                .feature("faceplugin-face-feature")         // ★ 必填
                .build();                                    // ★ vectorStorage 自动创建
        System.out.println("  detect: " + (minimal.detectLargest(null) != null));

        System.out.println("\n===== 完整配置（必填 + 选填 + 高级）=====");
        FacePipeline full = FacePipeline.builder()
                // ★ 必填
                .detector("faceplugin-face-detect-slim")
                .feature("faceplugin-face-feature")
                .topK(10)
                // ○ 选填
                .liveness("face-liveness-flrgb")
                .requireLive(true)
                .livenessThreshold(0.6f)
                .cropPadding(10)
                .minFaceArea(0.01f)
                .minConfidence(0.7f)
                // ● 高级
                .landmark("faceplugin-face-landmark")
                .attribute("age-race-gender")
                .emotion("emotion-ferplus")
                .deepfake("deepfake-detector")
                // .quality(...)    // 质量评估（需传入 FaceQualityAssessor 实例）
                .build();
        System.out.println("  pipeline ready");
    }
}

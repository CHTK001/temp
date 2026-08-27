package com.chua.example.face;

import com.chua.deeplearning.support.face.FacePipeline;

/**
 * 人脸识别完整能力配置示例。
 *
 * <h3>组件说明</h3>
 * <pre>
 *   detector       人脸检测器：从图片中定位所有人脸位置/大小/置信度
 *   feature        特征提取器：将人脸裁切图转为固定维度向量（如 512/1024维）
 *   vectorStorage  向量库：存储人脸特征向量，支持检索（File/Milvus/JVector）
 *   liveness       活体检测：区分真人脸与打印照片/屏幕翻拍/深伪视频
 *   landmark       关键点提取：定位人脸五官 68/98 个坐标点
 *   emotion        表情分类：识别喜怒哀乐等 7 类基本情绪
 *   deepfake       深伪检测：判断人脸是否为 AI 生成或换脸伪造
 *   superResolution 超分辨率：将低分辨率/模糊人脸放大并增强清晰度
 *   restorer       图像修复：去除人脸遮挡/疤痕/噪声等瑕疵
 *   quality        质量评估：对裁切的人脸图片打分（模糊度/光照/角度）
 * </pre>
 *
 * <h3>配置示例</h3>
 * <pre>
 *   ★ 必填   detector, feature, vectorStorage（缺一不可）
 *   ○ 选填   liveness（不设则跳过活体判断）
 *   ● 可选   landmark/emotion/deepfake 等按需叠加
 * </pre>
 */
public class FaceRecognitionDocExample {
    private FaceRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 最小配置（仅必填）=====");
        FacePipeline minimal = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")   // ★ 人脸检测：从图中定位所有脸
                .feature("faceplugin-face-feature")         // ★ 特征提取：人脸→512维向量
                .build();                                    // ★ 向量库：自动创建 FileVectorStorage
        minimal.detectLargest(null);

        System.out.println("\n===== 完整配置（必填 + 选填 + 可选）=====");
        FacePipeline full = FacePipeline.builder()
                // ★ 必填 —— 三项缺一不可
                .detector("faceplugin-face-detect-slim")   // 人脸检测：从图中定位所有脸
                .feature("faceplugin-face-feature")         // 特征提取：人脸→向量
                .topK(10)                                   // 向量库检索返回条数

                // ○ 选填 —— 不设则跳过对应流程
                .liveness("face-liveness-flrgb")            // 活体检测：真人vs照片/屏幕
                .requireLive(true)                          // 活体必须通过才算有效
                .livenessThreshold(0.6f)                    // 活体分阈值
                .minConfidence(0.7f)                        // 最低检测置信度过滤

                // ● 可选 —— 按需叠加
                .landmark("faceplugin-face-landmark")       // 关键点：定位五官坐标
                .emotion("emotion-ferplus")                 // 表情分类：喜怒哀乐
                .deepfake("deepfake-detector")              // 深伪检测：AI伪造判断
                .build();
        System.out.println("  pipeline ready");
    }
}

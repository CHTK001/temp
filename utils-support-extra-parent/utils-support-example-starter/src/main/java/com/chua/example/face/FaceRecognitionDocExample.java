package com.chua.example.face;

import com.chua.deeplearning.support.face.FacePipeline;

/**
 * 人脸识别 — 完整处理流程图 + Builder 配置标注。
 *
 * <h2>处理流程</h2>
 * <pre>
 *   图片 byte[]
 *     │
 *     ▼ ① 人脸检测（detector，必填）
 *     │   └─ FaceDetector.detect(image)
 *     │       ├─ 输入：原始图片 byte[]
 *     │       ├─ 输出：List&lt;DetectionHit&gt;（每张脸：box + confidence + faceImage 裁切）
 *     │       └─ 可配置：minFaceArea / minConfidence 过滤低质量检测
 *     │
 *     ▼ ② 质量过滤（quality，可选）
 *     │   └─ FaceQualityAssessor.quality(faceImage)
 *     │       ├─ 输入：裁切后的人脸图片
 *     │       └─ 输出：FaceQualityInfo（模糊度/光照/角度/遮挡评分）
 *     │           低于阈值的检测结果会被丢弃
 *     │
 *     ▼ ③ 动漫检测（anime，可选）
 *     │   └─ FaceDetector.detect(faceImage)
 *     │       ├─ 识别二次元/虚拟人风格人脸
 *     │       └─ 与真实人脸分开走不同特征提取器
 *     │
 *     ▼ ④ 超分辨率（superResolution，可选）
 *     │   └─ ImageEnhancer.enhance(faceImage)
 *     │       ├─ 低分辨率/模糊人脸 → 高清增强
 *     │       └─ 增强后再做特征提取，提升模糊脸的识别精度
 *     │
 *     ▼ ⑤ 图像修复（restore，可选）
 *     │   └─ ImageEnhancer.enhance(faceImage)
 *     │       ├─ 去除遮挡/疤痕/噪声等瑕疵
 *     │       └─ 修复后再做特征提取
 *     │
 *     ▼ ⑥ 特征提取（feature，必填）
 *     │   └─ FeatureExtractor.extract(faceImage)
 *     │       ├─ 人脸裁切图 → 固定维度向量（512/1024维）
 *     │       └─ 可配置：sigmoidRecognize（默认 true）
 *     │
 *     ▼ ⑦ 活体检测（liveness，可选）
 *     │   └─ LivenessDetector.detect(faceImage)
 *     │       ├─ 区分真人脸 vs 照片/屏幕翻拍/深伪视频
 *     │       ├─ 输出：boolean alive + float score
 *     │       └─ 可配置：requireLive=true / livenessThreshold=0.6
 *     │
 *     ▼ ⑧ 关键点提取（landmark，可选）
 *     │   └─ FeatureExtractor.extract(faceImage)
 *     │       └─ 输出：float[] 坐标（68/98 个五官点）
 *     │
 *     ▼ ⑨ 深伪检测（deepfake，可选）
 *     │   └─ ImageClassifier.classify(faceImage)
 *     │       └─ 判断人脸是否为 AI 生成/换脸伪造
 *     │
 *     ▼ ⑩ 入库 / 比对 / 检索
 *     │   ├─ enroll: Vector(id, feature, metadata, content) → VectorStorage.add()
 *     │   ├─ compare: 1:1 比对两张脸的特征相似度
 *     │   └─ search: 查询向量 → 余弦排序 → TopK 结果
 * </pre>
 *
 * <h2>Builder 组件</h2>
 * <pre>
 *   ★ 必填   detector       人脸检测器（定位图片中所有人脸）
 *   ★ 必填   feature        特征提取器（人脸 → 512维向量）
 *   ★ 必填   vectorStorage  向量库（默认 FileVectorStorage）
 *   ○ topK                   检索返回条数上限
 *   ○ liveness               活体检测器（区分真人 vs 照片/屏幕/深伪）
 *   ○ requireLive            是否要求活体通过（默认 true）
 *   ○ livenessThreshold      活体分阈值（默认 0.5）
 *   ○ minConfidence          最低检测置信度过滤
 *   ● superResolution        超分辨率：模糊脸 → 高清增强
 *   ● restorer               图像修复：去除遮挡/瑕疵
 *   ● anime                  动漫检测：二次元/虚拟人区分
 *   ● landmark               关键点：定位 68/98 个五官坐标
 *   ● deepfake               深伪检测：AI 生成/换脸判断
 *   ● quality                质量评估：模糊度/光照/角度打分
 *   ● cropPadding            检测框扩展像素（默认 0）
 *   ● minFaceArea            最小脸面积阈值（默认 0）
 * </pre>
 */
public class FaceRecognitionDocExample {
    private FaceRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 最小配置（仅必填）=====");
        FacePipeline minimal = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")   // ★ 人脸检测
                .feature("faceplugin-face-feature")         // ★ 特征提取
                .build();                                    // ★ 向量库自动创建

        System.out.println("\n===== 完整配置（必填 + 选填 + 可选）=====");
        FacePipeline full = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")
                .feature("faceplugin-face-feature")
                .topK(10)
                .liveness("face-liveness-flrgb")
                .requireLive(true)
                .livenessThreshold(0.6f)
                .minConfidence(0.7f)
                .superResolution("super-res-v1")            // 超分：模糊→高清
                .restore("face-restore-v1")                 // 修复：去瑕疵
                .anime("anime-face-detector")               // 动漫：二次元区分
                .landmark("faceplugin-face-landmark")       // 关键点：五官坐标
                .deepfake("deepfake-detector")              // 深伪：AI伪造判断
                .build();
        System.out.println("  pipeline ready");
    }
}

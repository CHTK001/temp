package com.chua.example.face;

import com.chua.common.support.vector.Vector;

import java.nio.file.Files;
import java.nio.file.Path;

import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;

import java.util.List;

/**
 * 人脸识别 — 完整处理流程图 + Builder 配置标注（修正版）。
 *
 * <h2>处理流程（与 FacePipeline.java buildIdentifyPipeline() 完全一致）</h2>
 * <pre>
 *   图片 byte[]
 *     │
 *     ▼ ① 裁剪 (crop) —— 检测框四周扩展
 *     │
 *     ▼ ② **活体检测 (liveness)** ← **修正：特征提取前，业务优先拒非活体**
 *     │   └─ 真人 vs 照片/屏幕/深伪，通过 requireLive/threshold 过滤
 *     │
 *     ▼ ③ 人脸对齐 (align) —— 基于关键点五官坐标摆正
 *     │   └─ 关键点：68/98 点坐标 → 旋转至两眼水平，提升后续特征精度
 *     │
 *     ▼ ④ 图像修复 (restore) —— 去除遮挡/疤痕/噪声
 *     │
 *     ▼ ⑤ 超分辨率 (enhance/superResolution) —— 分辨率提升
 *     │
 *     ▼ ⑥ **特征提取 (feature)** —— 人脸 → 512/1024维向量
 *     │
 *     ▼ ⑦ 检索/比对 (search/compare) ──► 向量库
 *     │
 *     ▼ ⑧ 收集 (collectIdentify) ──► 结果封装
 * </pre>
 *
 * <h2>Builder 组件标注</h2>
 * <pre>
 *   ★ 必填   detector      人脸检测器（定位图片中所有人脸）
 *   ★ 必填   feature        特征提取器（人脸 → 固定维度向量）
 *   ★ 必填   vectorStorage  向量库（默认 FileVectorStorage）
 *   ○ 可选   topK          检索返回条数上限
 *   ○ 可选   liveness      活体检测器（区分真人 vs 照片/屏幕/深伪）
 *   ○ 可选   requireLive   是否要求活体通过（默认 true）
 *   ○ 可选   livenessThreshold 活体分阈值（默认 0.5）
 *   ○ 可选   minConfidence 最低检测置信度过滤
 *   ○ 可选   minFaceArea   最小脸面积阈值（默认 0）
 *   ○ 可选   cropPadding   检测框扩展像素（默认 0）
 *   ● 新增   anime          动漫检测：二次元/虚拟人区分
 *   ● 新增   superResolution 超分辨率：模糊脸 → 高清增强
 *   ● 新增   restorer       图像修复：去除遮挡/瑕疵
 *   ● 新增   landmark       关键点：定位 68/98 个五官坐标（用于对齐）
 *   ● 新增   deepfake       深伪检测：AI 生成/换脸判断（独立接口 isDeepfake）
 *   ● 新增   quality        质量评估：模糊度/光照/角度打分
 * </pre>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>活体检测(liveness)已放在特征提取前，避免浪费 compute 资源。</li>
 *   <li>关键点(landmark)用于 align 步骤，位于特征提取前，不单独“在提取后检测”。</li>
 *   <li>深伪检测(deepfake)是独立的辅助能力，通过 pipeline.isDeepfake(image) 调用，不在主流程中。</li>
 * </ul>
 *
 * @author CH
 *
 * @since 4.0.0
 */
public class FaceRecognitionDocExample {
    private FaceRecognitionDocExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 最小配置（仅必填）=====");
        FacePipeline minimal = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")   // ★ 人脸检测
                .feature("faceplugin-face-feature")         // ★ 特征提取
                .build();                                    // ★ 向量库自动创建
        minimal.setCallback(new FacePipelineDiskCallback(Path.of("D:\\images\\output\\minimal")));

        System.out.println("\n===== 完整配置（必填 + 关键流程项）=====");
        FacePipeline full = FacePipeline.builder()
                .detector("faceplugin-face-detect-slim")
                .feature("faceplugin-face-feature")
                // ○ 业务优先项：活体在特征前
                .liveness("face-liveness-flrgb")
                .requireLive(true)
                .livenessThreshold(0.6f)
                // ○ 几何对齐项：关键点引导对齐（在特征前）
                .landmark("faceplugin-face-landmark")
                // ○ 图像质量项
                .superResolution("gfpgan-face-super-resolution")   // 超分：模糊→高清
                .restore("codeformer")                            // 修复：去瑕疵
                // ● 可选高级项
                .anime("anime-face-detector")                      // 动漫：二次元区分
                .deepfake("deepfake-detector")                    // 深伪：AI伪造判断（单独接口）
                .minConfidence(0.7f)                             // 置信度过滤
                .build();
        full.setCallback(new FacePipelineDiskCallback(Path.of("D:\\images\\output\\full")));

        System.out.println("  pipeline ready");

        // 示例：执行完整识别流程
        byte[] imageData = Files.readAllBytes(Path.of("test.jpg"));

        // 1. 人脸检测 + 活体过滤
        List<FaceDetectionHit> hits = full.detectPipeline(imageData);
        if (hits.isEmpty()) {
            System.out.println("  检测无人脸");
            System.exit(0);
        }

        // 2. 完整识别（含活体→对齐→修复→超分→特征→检索）
        List<FaceIdentifyHit> results = full.identifyPipeline(imageData);
        System.out.println("  识别结果数: " + results.size());
    }
}

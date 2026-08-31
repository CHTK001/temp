package com.chua.example.onnx;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineCallback;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import com.chua.deeplearning.support.face.FaceRestoreResult;
import com.chua.deeplearning.support.model.PredictRectangle;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 人脸修复 A/B 对比（真实管线 + 回调落盘，多测试图）。
 *
 * <p>检测模型固定为 onnx scrfd-face-detector，保证两侧对齐输入完全一致；
 * 仅修复模型在 pytorch-gfpgan 与 onnx-gfpgan 间切换，通过
 * {@link FacePipeline#restoreWithAlign(byte[], FacePipelineCallback)} 统一入口跑真实管线，
 * 回调将每组对齐图与修复图按 图片名/模型标签 分目录落盘供对比。</p>
 *
 * <pre>{@code
 *   FaceRestorePipelineCompareTest D:\images\3peoplebeauty.jpg
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FaceRestorePipelineCompareExample {


    /** 输出根目录 */
    private static final String OUT_ROOT = "D:\\images\\output\\face_restore_compare";

    /** 检测模型（固定，保证对齐输入一致） */
    private static final String DETECTOR_ID = "scrfd-face-detector";

    /** 待对比的修复模型：pt 与 onnx */
    private static final String[][] RESTORE_MODELS = {
            {"pt", "pytorch-gfpgan"},
            {"onnx", "onnx-gfpgan"}
    };

    /** 默认测试图片（多图逐张处理） */
    private static final String[] TEST_IMAGES = {
            "D:\\images\\3peoplebeauty.jpg",
            "D:\\images\\1people.png",
            "D:\\images\\1people2.png",
            "D:\\images\\largest_selfie.jpg"
    };

    private FaceRestorePipelineCompareExample() {
    }

    /**
     * 入口。
     *
     * @param args args[0] 可选：单张输入图片路径（覆盖默认多图）
     * @throws Exception 运行异常
     */
    public static void main(String[] args) throws Exception {
        // 独立运行（非 Spring 容器）时 SPI 可能未触发注册器，显式加载
        ReflectUtils.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");
        ReflectUtils.forName("com.chua.deeplearning.support.pytorch.PytorchModelRegistrar");

        Path root = Path.of(OUT_ROOT);
        Files.createDirectories(root);

        String[] images = args.length > 0 ? new String[]{args[0]} : TEST_IMAGES;
        for (String imagePath : images) {
            if (!Files.exists(Path.of(imagePath))) {
                log.warn("[skip] 不存在: {}", imagePath);
                continue;
            }
            processImage(root, imagePath);
        }
        log.info("[done] A/B 对比完成，输出目录: {}", root);
    }

    /**
     * 单张图片跑两种修复模型。
     *
     * @param root      输出根目录
     * @param imagePath 图片路径
     * @throws Exception 运行异常
     */
    private static void processImage(Path root, String imagePath) throws Exception {
        String base = Path.of(imagePath).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        Path imgDir = root.resolve(base);
        Files.createDirectories(imgDir);
        log.info("");
        log.info("########## 测试图: {} ##########", imagePath);

        for (String[] m : RESTORE_MODELS) {
            String tag = m[0];
            String modelId = m[1];
            Path outDir = imgDir.resolve(tag);
            Files.createDirectories(outDir);

            log.info("===== 修复模型={} ({}) =====", tag, modelId);
            long t0 = System.currentTimeMillis();
            FacePipeline pipeline = FacePipeline.builder()
                    .detector(DETECTOR_ID)
                    .restore(modelId)
                    .build();
            pipeline.setCallback(new FacePipelineDiskCallback(outDir));

            List<FaceRestoreResult> results = pipeline.restoreWithAlign(img);
            long cost = System.currentTimeMillis() - t0;
            log.info("[{}] 真实管线 restoreWithAlign 完成: {}张人脸, 耗时={}ms", tag, results.size(), cost);
            for (int i = 0; i < results.size(); i++) {
                FaceRestoreResult r = results.get(i);
                PredictRectangle box = r.box();
                log.info("[{}] #{} box=({:.0f},{:.0f}) {:.0f}x{:.0f} conf={:.2f}",
                        tag, i, box.x(), box.y(), box.width(), box.height(), box.confidence());
            }
        }
    }
}

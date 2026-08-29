package com.chua.example.onnx;

import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineCallback;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import com.chua.deeplearning.support.face.FaceRestoreResult;
import com.chua.deeplearning.support.model.PredictRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 人脸修复 A/B 对比（真实管线 + 回调落盘）。
 *
 * <p>检测模型固定为 onnx scrfd-face-detector，保证两侧对齐输入完全一致；
 * 仅修复模型在 pytorch-gfpgan 与 onnx-gfpgan 间切换，通过
 * {@link FacePipeline#restoreWithAlign(byte[], FacePipelineCallback)} 统一入口跑真实管线，
 * 回调将每组对齐图与修复图分目录落盘供对比。</p>
 *
 * <pre>{@code
 *   FaceRestorePipelineCompareTest D:\images\3peoplebeauty.jpg
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class FaceRestorePipelineCompareTest {

    private static final Logger log = LoggerFactory.getLogger(FaceRestorePipelineCompareTest.class);

    /** 输出根目录 */
    private static final String OUT_ROOT = "D:\\images\\output\\face_restore_compare";

    /** 检测模型（固定，保证对齐输入一致） */
    private static final String DETECTOR_ID = "scrfd-face-detector";

    /** 待对比的修复模型：pt 与 onnx */
    private static final String[][] RESTORE_MODELS = {
            {"pt", "pytorch-gfpgan"},
            {"onnx", "onnx-gfpgan"}
    };

    private FaceRestorePipelineCompareTest() {
    }

    /**
     * 入口。
     *
     * @param args args[0] 输入图片路径，默认 D:\images\3peoplebeauty.jpg
     * @throws Exception 运行异常
     */
    public static void main(String[] args) throws Exception {
        String imagePath = args.length > 0 ? args[0] : "D:\\images\\3peoplebeauty.jpg";
        byte[] img = Files.readAllBytes(Path.of(imagePath));

        // 独立运行（非 Spring 容器）时 SPI 可能未触发注册器，显式加载
        Class.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");
        Class.forName("com.chua.deeplearning.support.pytorch.PytorchModelRegistrar");

        // 调试：打印 pt/onnx 模型解析路径
        log.info("[debug] root-dir = {}", System.getProperty("deeplearning.model.root-dir"));
        log.info("[debug] ModelRegistry.root = {}", com.chua.deeplearning.support.engine.ModelRegistry.getModelRootDir());
        for (String mid : new String[]{"pytorch-gfpgan", "onnx-gfpgan", "scrfd-face-detector"}) {
            var entry = com.chua.deeplearning.support.engine.ModelRegistry.get(mid);
            log.info("[debug] {} entry={}", mid, entry);
            try {
                log.info("[debug] {} -> {}", mid, com.chua.deeplearning.support.engine.ModelRegistry.resolveModelPath(mid));
            } catch (Throwable t) {
                log.info("[debug] {} -> ERROR {}", mid, t.getMessage());
            }
        }

        Path root = Path.of(OUT_ROOT);
        Files.createDirectories(root);

        for (String[] m : RESTORE_MODELS) {
            String tag = m[0];
            String modelId = m[1];
            Path outDir = root.resolve(tag);
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
                log.info("[{}] #{} box=({:.0f},{:.0f}) {:.0f}x{:.0f} conf={:.2f} 输出已落盘到 {}",
                        tag, i, box.x(), box.y(), box.width(), box.height(), box.confidence(), outDir);
            }
            log.info("");
        }
        log.info("[done] A/B 对比完成，输出目录: {}", root);
    }
}

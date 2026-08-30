package com.chua.example.face;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import com.chua.deeplearning.support.face.FaceRestoreResult;
import com.chua.deeplearning.support.model.PredictRectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 人脸修复管线端到端测试：检测→5点对齐→修复，回调落盘中间图片。
 *
 * <p>修复模型分别使用 ONNX(GFPGANv1.3) 和 PT 各跑一次，对比效果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class FacePipelineExample {

    /** 测试图片路径，可通过 args[0] 覆盖 */
    private static final String TEST_IMAGE = "D:\\images\\3peoplebeauty.jpg";
    /** 输出目录 */
    private static final String OUT_DIR = "D:\\images\\output\\face_pipeline_test";
    /** 人脸检测模型 */
    private static final String DETECTOR = "scrfd-face-detector";

    private FacePipelineExample() {
    }

    public static void main(String[] args) throws Exception {
        String testImage = args.length > 0 ? args[0] : TEST_IMAGE;
        ReflectUtils.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");

        byte[] img = Files.readAllBytes(Paths.get(testImage));
        Files.createDirectories(Path.of(OUT_DIR));

        // ── 1. ONNX GFPGAN ──
        System.out.println("===== ONNX GFPGAN =====");
        runPipeline(img, "onnx-gfpgan", "onnx");

        // ── 2. PT GFPGAN ──
        System.out.println("===== PT GFPGAN =====");
        runPipeline(img, "pytorch-gfpgan", "pt");
    }

    private static void runPipeline(byte[] img, String restoreModel, String tag) throws Exception {
        FacePipeline pipeline = FacePipeline.builder()
                .detector(DETECTOR)
                .restore(restoreModel)
                .build();
        pipeline.setCallback(new FacePipelineDiskCallback(Path.of(OUT_DIR, tag)));

        long t0 = System.currentTimeMillis();
        List<FaceRestoreResult> results = pipeline.restoreWithAlign(img);
        long cost = System.currentTimeMillis() - t0;

        System.out.printf("[%s] 检测人脸=%d, 修复人脸=%d, 耗时=%dms%n",
                tag, pipeline.detect(img).size(), results.size(), cost);

        for (int i = 0; i < results.size(); i++) {
            FaceRestoreResult r = results.get(i);
            PredictRectangle box = r.box();
            System.out.printf("  face[%d] box=(%.0f,%.0f) %.0fx%.0f conf=%.2f kps=%d%n",
                    i, box.x(), box.y(), box.width(), box.height(), box.confidence(),
                    box.keypoints() == null ? 0 : box.keypoints().size());
            System.out.printf("    aligned=%dB restored=%dB%n",
                    r.alignedFace().length, r.restoredFace().length);
        }
    }
}
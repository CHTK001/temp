package com.chua.example.onnx;

import com.chua.deeplearning.support.face.*;
import com.chua.deeplearning.support.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 人脸模型综合实测：检测 → 对齐 → 修复 → 超分，统一使用 FacePipelineDiskCallback 落盘各阶段图片。
 *
 * <p>对 {@code G:\images} 人脸图逐张执行完整管线，中间结果自动落盘到输出目录。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FaceDetectDrawExample extends BaseExample {

    /**
     * 输入图片目录
     */
    private static final String INPUT_DIR = "G:\\images";

    /**
     * 输出目录
     */
    private static final String OUTPUT_DIR = "G:\\images\\output\\face_detect_draw";

    /**
     * 测试图片
     */
    private static final String[] TEST_IMAGES = {
            "三个人.jpg",
            "黑白人物.jpg"
    };

    /**
     * 仅检测的图
     */
    private static final String[] DETECT_ONLY_IMAGES = {
            "很多人小脸.jpg"
    };

    /** 创建 FaceDetectDrawExample 实例 */
    private FaceDetectDrawExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        Path outDir = Path.of(OUTPUT_DIR);
        Files.createDirectories(outDir);

        // 所有管线统一使用回调落盘中间图片
        FacePipeline pipeline = FacePipeline.builder()
                .detector("scrfd-face-detector")
                .restore("codeformer")
                .superResolution("gfpgan-face-super-resolution")
                .build();
        pipeline.setCallback(new FacePipelineDiskCallback(outDir));

        // 只检测的图
        for (String name : DETECT_ONLY_IMAGES) {
            Path in = Path.of(INPUT_DIR, name);
            if (!Files.exists(in)) {
                continue;
            }
            byte[] img = Files.readAllBytes(in);
            String base = name.substring(0, name.lastIndexOf('.'));
            log.info("===== " + name + " (仅检测) =====");
            detectAndDraw(pipeline, base, img);
            log.info("");
        }

        // 完整链路：检测 → 对齐 → 修复(CodeFormer) → 超分(GFPGAN)，使用 restoreWithAlign 统一入口
        for (String name : TEST_IMAGES) {
            Path in = Path.of(INPUT_DIR, name);
            if (!Files.exists(in)) {
                log.info("[face-ops] 跳过（不存在）: " + name);
                continue;
            }
            byte[] img = Files.readAllBytes(in);
            String base = name.substring(0, name.lastIndexOf('.'));
            log.info("===== " + name + " =====");

            // 检测 + 画框
            detectAndDraw(pipeline, base, img);

            // 完整修复管线（对齐 → 修复 → 贴回），回调自动落盘中间图片
            long t1 = System.currentTimeMillis();
            List<FaceRestoreResult> results = pipeline.restoreWithAlign(img);
            byte[] result = results.get(0).restoredFace();
            long tTotal = System.currentTimeMillis() - t1;
            Path resultOut = Path.of(OUTPUT_DIR, base + "_result.png");
            Files.write(resultOut, result);
            log.info("[管线] restoreWithAlign 完成 耗时=" + tTotal + "ms 结果=" + resultOut);
            log.info("");
        }
        printResult("face-ops", "onnx", "scrfd+codeformer+gfpgan", 0);
    }

    /**
     * 检测并画框输出。
     */
    private static List<FaceDetectionHit> detectAndDraw(FacePipeline pipeline, String base, byte[] img) throws Exception {
        long t0 = System.currentTimeMillis();
        List<FaceDetectionHit> hits = pipeline.detectPipeline(img);
        long tDetect = System.currentTimeMillis() - t0;
        log.info("[检测] 人脸数=" + hits.size() + " 耗时=" + tDetect + "ms");
        for (int i = 0; i < hits.size(); i++) {
            var box = hits.get(i).box();
            log.info(String.format("[检测]   #%d box=(%.0f,%.0f) %.0fx%.0f conf=%.2f",
                    i, box.x(), box.y(), box.width(), box.height(), box.confidence()));
        }
        Mat src = ImageUtils.decode(img);
        if (src != null) {
            for (int i = 0; i < hits.size(); i++) {
                var box = hits.get(i).box();
                Imgproc.rectangle(src, new Point(box.x(), box.y()),
                        new Point(box.x() + box.width(), box.y() + box.height()),
                        new Scalar(0, 255, 0), 2);
                Imgproc.putText(src, "face-" + i + " " + String.format("%.2f", box.confidence()),
                        new Point(box.x(), box.y() - 6),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 255, 0), 2);
            }
            MatOfByte mob = new MatOfByte();
            Imgcodecs.imencode(".jpg", src, mob);
            Path detectOut = Path.of(OUTPUT_DIR, base + "_detect.jpg");
            Files.write(detectOut, mob.toArray());
            src.release();
            mob.release();
            log.info("[检测] 已输出: " + detectOut);
        }
        return hits;
    }
}

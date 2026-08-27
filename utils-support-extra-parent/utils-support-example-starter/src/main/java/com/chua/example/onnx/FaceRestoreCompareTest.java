package com.chua.example.onnx;

import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.utils.ImageUtils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A/B 对比：检测/裁剪/对齐/分割模型固定（pytorch-retinaface + pytorch-parsenet），
 * 仅"修复"在 pytorch-gfpgan 与 onnx-gfpgan 间切换，输出两组可直比对修复图。
 */
public class FaceRestoreCompareTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FaceRestoreCompareTest.class);
    private static final String OUT_DIR = "D:\\images\\output";

    public static void main(String[] args) {
        try {
        String imagePath = args.length > 0 ? args[0] : "D:\\images\\3peoplebeauty.jpg";
        byte[] img = Files.readAllBytes(Path.of(imagePath));

        Class.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");
        Class.forName("com.chua.deeplearning.support.pytorch.PytorchModelRegistrar");

        // 固定检测模型
        FaceDetector detector = FaceDetector.create("pytorch-retinaface");
        log.info("检测模型=pytorch-retinaface 开始 detect...");
        List<PredictRectangle> boxes = detector.detect(img);
        log.info("检测模型=pytorch-retinaface 人脸数=" + boxes.size());
        if (boxes.isEmpty()) {
            log.info("未检测到人脸");
            return;
        }

        Mat src = ImageUtils.decode(img);
        int iw = src.cols(), ih = src.rows();

        // 固定分割模型
        ImageEnhancer parsenet = ImageEnhancer.create("pytorch-parsenet");

        String[] restoreIds = {"pytorch-gfpgan", "onnx-gfpgan"};
        for (String rid : restoreIds) {
            ImageEnhancer gfpgan = ImageEnhancer.create(rid);
            for (int i = 0; i < boxes.size(); i++) {
                PredictRectangle box = boxes.get(i);
                int x1 = (int) box.x(), y1 = (int) box.y();
                int x2 = x1 + (int) box.width(), y2 = y1 + (int) box.height();
                int newX1 = Math.max((int) (x1 + x1 * 0.5f - x2 * 0.5f), 0);
                int newX2 = Math.min((int) (x2 + x2 * 0.5f - x1 * 0.5f), iw - 1);
                int newY1 = Math.max((int) (y1 + y1 * 0.5f - y2 * 0.5f), 0);
                int newY2 = Math.min((int) (y2 + y2 * 0.5f - y1 * 0.5f), ih - 1);
                int cw = newX2 - newX1, ch = newY2 - newY1;
                if (cw <= 0 || ch <= 0) continue;
                Mat sub = new Mat(src, new Rect(newX1, newY1, cw, ch));
                List<float[]> kps = new ArrayList<>();
                if (box.keypoints() != null) {
                    for (float[] p : box.keypoints()) kps.add(new float[]{p[0] - newX1, p[1] - newY1});
                }
                if (kps.size() < 5) { sub.release(); continue; }

                Mat affine = ImageUtils.estimateFaceAffine512(kps);
                Mat aligned = new Mat();
                org.opencv.imgproc.Imgproc.warpAffine(sub, aligned, affine,
                        new org.opencv.core.Size(512, 512),
                        org.opencv.imgproc.Imgproc.INTER_CUBIC, 0, new Scalar(135, 133, 132));
                byte[] face = ImageUtils.encode(aligned);

                long t1 = System.currentTimeMillis();
                byte[] restored = gfpgan.enhance(face);
                long cost = System.currentTimeMillis() - t1;

                String tag = rid.equals("onnx-gfpgan") ? "onnx" : "pt";
                Path restoreOut = Path.of(OUT_DIR, tag + "_face" + i + "_restore.png");
                Files.write(restoreOut, restored);
                log.info("[" + tag + "-gfpgan] #" + i + " 耗时=" + cost + "ms -> " + restoreOut);

                // 贴回
                Mat softMask = ImageUtils.decode(parsenet.enhance(restored));
                if (softMask.channels() > 1) {
                    Mat g = new Mat();
                    org.opencv.imgproc.Imgproc.cvtColor(softMask, g, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
                    softMask.release(); softMask = g;
                }
                Mat restoredMat = ImageUtils.decode(restored);
                Mat pasted = ImageUtils.pasteFace(src, restoredMat, softMask, affine);
                Path pasteOut = Path.of(OUT_DIR, tag + "_face" + i + "_pasted.png");
                Files.write(pasteOut, ImageUtils.encode(pasted));
                log.info("[" + tag + "-paste] #" + i + " -> " + pasteOut);

                pasted.release(); restoredMat.release(); softMask.release();
                aligned.release(); sub.release(); affine.release();
            }
            log.info("[" + rid + "] 完成");
        }
        src.release();
        log.info("[done] A/B 对比完成");
        } catch (Throwable t) {
            log.error("FAILED: " + t, t);
            t.printStackTrace();
            System.exit(2);
        }
    }
}

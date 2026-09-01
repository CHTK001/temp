package com.chua.example.onnx;

import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.deeplearning.support.translator.ITranslator;
import org.opencv.core.Mat;

import static com.chua.deeplearning.support.engine.AbstractIdentificationEngine.getInstance;
import static com.chua.deeplearning.support.utils.ImageUtils.decode;
import static java.util.Arrays.equals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * OCR 鏃嬭浆璇婃柇锛氶獙璇佽溅绁ㄥ悇鏃嬭浆瑙掑害鐨勬柟鍚戝垎绫?鈫?鏁村浘鐭 鈫?妫€娴嬫瑙掑害 鈫?璇嗗埆銆?
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class OcrAngleExample {

    /** 鍒涘缓 OcrAngleDiag 瀹炰緥 */
    private OcrAngleExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String dir = "G:\\images";
        String[] files = {"杞︾エ.png", "杞︾エticket_90.png", "杞︾エticket_180.png", "杞︾エticket_270.png"};

        String directionModel = args.length > 0 ? args[0] : "doc-orientation";
        log.info("[diag] 鏂瑰悜妯″瀷=" + directionModel);

        OcrPipeline ocr = OcrPipeline.builder()
                .detector("paddleocrv6-medium-det")
                .recognizer("paddleocrv6-medium-rec")
                .direction(directionModel)
                .build();

        @SuppressWarnings("unchecked")
        ITranslator<Object, Object> dirT = (ITranslator<Object, Object>)
                getInstance()
                        .get(directionModel, ITranslator.class);

        for (String name : files) {
            byte[] img = Files.readAllBytes(Path.of(dir, name));
            log.info("===== " + name + " =====");

            Object dr = dirT.translate(img);
            log.info("  鏂瑰悜鍒嗙被: " + describeDirection(dr));

            byte[] corrected = ocr.correct(img);
            boolean rotated = !equals(img, corrected);
            log.info("  鏁村浘鐭: " + (rotated ? "宸叉棆杞? : "鏈棆杞?璺宠繃鎴?掳)"));
            Mat correctedMat = decode(corrected);
            log.info("  鐭鍚庡昂瀵? " + correctedMat.cols() + "x" + correctedMat.rows());
            correctedMat.release();

            ImageDetector det = ocr.detector();
            List<DetectionInfo> boxes = det.detect(corrected);
            log.info("  妫€娴嬫鏁? " + boxes.size());
            int shown = 0;
            for (DetectionInfo b : boxes) {
                if (shown++ >= 3) {
                    break;
                }
                System.out.printf("    box: (%.0f,%.0f) %.0fx%.0f angle=%.1f conf=%.2f%n",
                        b.x(), b.y(), b.width(), b.height(), b.angle(), b.confidence());
            }

            List<OcrResult> results = ocr.recognizeDetail(img);
            log.info("  璇嗗埆缁撴灉(鍓?): ");
            int n = 0;
            for (OcrResult r : results) {
                if (n++ >= 3) {
                    break;
                }
                System.out.printf("    text='%s' conf=%.2f%n", r.text(), r.confidence());
            }
            log.info("");
        }
    }

    /** DescribeDirection */
    private static String describeDirection(Object dr) {
        try {
            Object name = ReflectUtils.invoke(dr, "getName", String.class);
            Object prob = ReflectUtils.invoke(dr, "getProbability", double.class);
            return name + " prob=" + String.format("%.3f", prob);
        } catch (Exception e) {
            return String.valueOf(dr);
        }
    }
}

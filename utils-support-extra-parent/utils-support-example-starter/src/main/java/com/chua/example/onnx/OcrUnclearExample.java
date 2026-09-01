package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.deeplearning.support.translator.ITranslator;
import com.chua.deeplearning.support.utils.ImageUtils;
import org.opencv.core.Mat;

import static com.chua.deeplearning.support.engine.AbstractIdentificationEngine.getInstance;
import static java.util.Arrays.equals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 璇婃柇涓夊紶"寰堜笉娓呮鐨勬枃瀛楀浘鐗?锛氭柟鍚戞ā鍨嬪垎绫?鈫?correct 鏄惁鏃嬭浆 鈫?璇嗗埆銆?
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class OcrUnclearExample {

    /** 鍒涘缓 OcrUnclearDiag 瀹炰緥 */
    private OcrUnclearExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String[] files = {
                "寰堜笉娓呮鐨勬枃瀛楀浘鐗囩敤浜庢祴璇曟枃瀛楅珮娓呬慨澶嶆ā鍨?png",
                "寰堜笉娓呮鐨勬枃瀛楀浘鐗囩敤浜庢祴璇曟枃瀛楅珮娓呬慨澶嶆ā鍨?.png",
                "寰堜笉娓呮鐨勬枃瀛楀浘鐗囩敤浜庢祴璇曟枃瀛楅珮娓呬慨澶嶆ā鍨?.png"
        };
        @SuppressWarnings("unchecked")
        ITranslator<Object, Object> dirT = (ITranslator<Object, Object>)
                getInstance()
                        .get("doc-orientation", ITranslator.class);

        OcrPipeline ocr = OcrPipeline.builder()
                .detector("paddleocrv6-medium-det")
                .recognizer("paddleocrv6-medium-rec")
                .direction("doc-orientation")
                .build();

        for (String name : files) {
            byte[] img = Files.readAllBytes(Path.of("G:\\images", name));
            log.info("===== " + name + " =====");
            Mat m0 = ImageUtils.decode(img);
            log.info("  鍘熷浘灏哄=" + m0.cols() + "x" + m0.rows());

            Object dr = dirT.translate(img);
            String cls = String.valueOf(ReflectUtils.invoke(dr, "getName", Object.class));
            double prob = (double) ReflectUtils.invoke(dr, "getProbability", double.class);
            log.info("  鏂瑰悜鍒嗙被=" + cls + " prob=" + String.format("%.3f", prob));

            byte[] corrected = ocr.correct(img);
            boolean rotated = !equals(img, corrected);
            Mat m1 = ImageUtils.decode(corrected);
            log.info("  鐭鍚?" + (rotated ? "宸叉棆杞? : "鏈棆杞?) + " 灏哄=" + m1.cols() + "x" + m1.rows());
            m0.release();
            m1.release();

            List<OcrResult> results = ocr.recognizeDetail(img);
            log.info("  璇嗗埆鍧楁暟=" + results.size());
            for (OcrResult r : results) {
                System.out.printf("    [%.2f] '%s'%n", r.confidence(), r.text());
            }
        }
    }
}

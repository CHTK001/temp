package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.deeplearning.support.ocr.OcrResult;
import com.chua.deeplearning.support.utils.ImageUtils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import static java.util.Arrays.equals;
import static org.opencv.core.Core.rotate;
import static org.opencv.core.Core.ROTATE_180;
import static org.opencv.core.Core.ROTATE_90_CLOCKWISE;
import static org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE;
import static org.opencv.imgproc.Imgproc.INTER_LINEAR;
import static org.opencv.imgproc.Imgproc.INTER_NEAREST;
import static org.opencv.imgproc.Imgproc.resize;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static org.opencv.core.Core.absdiff;
import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.meanStdDev;
import static org.opencv.core.CvType.CV_32F;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 瀵规瘮 rotate 瀹炵幇锛氬綋鍓?SPI 鐗?vs 绾?OpenCV 鐗堬紝瀵规棆杞溅绁ㄨ瘑鍒エ鍙枫€?
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class OcrRotateCompareExample {

    /** 鍒涘缓 OcrRotateCompareExample 瀹炰緥 */
    private OcrRotateCompareExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        ImageUtils.load();
        byte[] img0 = Files.readAllBytes(Path.of("G:\\images\\杞︾エ.png"));
        byte[] img90 = Files.readAllBytes(Path.of("G:\\images\\杞︾エticket_90.png"));
        byte[] img270 = Files.readAllBytes(Path.of("G:\\images\\杞︾エticket_270.png"));

        // 姝ｅ悜鍘熷浘鐩存帴璇嗗埆
        recognize("姝ｅ悜鍘熷浘", img0);

        // 鐢ㄦ鍚戝師鍥剧敓鎴?90掳 鏃嬭浆锛堟棤鎹燂級锛屽啀鏃嬭浆鍥炴鍚戯紝楠岃瘉"鏃嬭浆寰€杩?鏄惁涓?Z
        byte[] gen90 = ImageUtils.rotate(img0, 90);
        byte[] gen90back = ImageUtils.rotate(gen90, 270);
        recognize("姝ｅ悜鍥?>杞?0->杞洖", gen90back);

        // 鐜版湁 ticket_90 鍥炬棆杞洖姝ｅ悜
        byte[] rot90spi = ImageUtils.rotate(img90, 270);
        recognize("ticket_90鍥?>杞洖", rot90spi);

        // 淇濈暀鏃嬭浆鍥炴鍚戠殑鍥惧拰鐢熸垚鍥撅紝鍋氱エ鍙峰尯鍩熷儚绱犲姣?
        byte[] genback = ImageUtils.rotate(gen90, 270);
        java.nio.file.Files.write(java.nio.file.Path.of("G:\\images\\output\\diag_ticket90_back.png"), rot90spi);
        java.nio.file.Files.write(java.nio.file.Path.of("G:\\images\\output\\diag_orig.png"), img0);
        // 灏?ticket_90 杞洖鍥剧缉鏀惧榻愬埌姝ｅ悜灏哄鍚庤鍓斁澶э紝渚涜倝鐪兼牳瀵?Z 瀛楀舰
        saveZoomedAligned(img0, rot90spi, 103, 65, 253, 20,
                "G:\\images\\output\\diag_ticketno_orig.png",
                "G:\\images\\output\\diag_ticketno_from90.png");
        log.info("[cmp] 宸蹭繚瀛? diag_ticket90_back.png, diag_orig.png, diag_ticketno_orig.png, diag_ticketno_from90.png");
        log.info("[cmp] 绁ㄥ彿鍖哄煙鍍忕礌瀵规瘮(姝ｅ悜鍘熷浘 vs ticket_90杞洖):");
    }

    /**
     * 淇濆瓨ZoomedAligned
     * @param ref ref
     * @param src src
     * @param x x
     * @param y y
     * @param w w
     * @param h h
     * @param outRef outRef
     * @param outSrc outSrc
     */
    private static void saveZoomedAligned(byte[] ref, byte[] src, int x, int y, int w, int h,
                                          String outRef, String outSrc) {
        try {
            ImageUtils.load();
            Mat mr = ImageUtils.decode(ref);
            Mat ms = ImageUtils.decode(src);
            // 灏?src 缂╂斁鍒?ref 灏哄
            Mat msScaled = new Mat();
            resize(ms, msScaled, new Size(mr.cols(), mr.rows()),
                    0, 0, INTER_LINEAR);
            saveZoomedHelper(mr, x, y, w, h, outRef);
            saveZoomedHelper(msScaled, x, y, w, h, outSrc);
            msScaled.release();
            mr.release();
            ms.release();
        } catch (Exception e) {
            log.info("[cmp] saveZoomedAligned 澶辫触: " + e.getMessage());
        }
    }

    /** 淇濆瓨ZoomedHelper */
    private static void saveZoomedHelper(Mat src, int x, int y, int w, int h, String out) {
        Mat crop = new Mat(src, new org.opencv.core.Rect(x, y, w, h));
        Mat big = new Mat();
        resize(crop, big, new Size(w * 8, h * 8),
                0, 0, INTER_NEAREST);
        imwrite(out, big);
        crop.release();
        big.release();
    }

    /** RegionMae */
    private static double regionMae(byte[] a, byte[] b, int x, int y, int w, int h) {
        try {
            ImageUtils.load();
            Mat ma = ImageUtils.decode(a);
            Mat mb = ImageUtils.decode(b);
            if (ma == null || mb == null || x + w > ma.cols() || y + h > ma.rows()
                    || x + w > mb.cols() || y + h > mb.rows()) {
                return -1;
            }
            try {
                Mat ra = new Mat(ma, new org.opencv.core.Rect(x, y, w, h));
                Mat rb = new Mat(mb, new org.opencv.core.Rect(x, y, w, h));
                ra.convertTo(ra, CV_32F);
                rb.convertTo(rb, CV_32F);

                Mat diff = new Mat();
                absdiff(ra, rb, diff);
                meanStdDev(diff, new MatOfDouble(), new MatOfDouble());
                double m = mean(diff).val[0];
                ra.release();
                rb.release();
                diff.release();
                return m;
            } finally {
                ma.release();
                mb.release();
            }
        } catch (Exception e) {
            return -999;
        }
    }

    /** Recognize */
    private static void recognize(String label, byte[] img) {
        try {
            OcrPipeline ocr = OcrPipeline.builder()
                    .detector("paddleocrv6-medium-det")
                    .recognizer("paddleocrv6-medium-rec")
                    .direction("doc-orientation")
                    .build();
            List<OcrResult> results = ocr.recognizeDetail(img);
            log.info("[cmp] " + label + " 鍧楁暟=" + results.size());
            for (OcrResult r : results) {
                String t = r.text();
                log.info("      '" + t + "' conf=" + String.format("%.2f", r.confidence()));
            }
        } catch (Exception e) {
            log.info("[cmp] " + label + " 寮傚父: " + e.getMessage());
        }
    }

    /** Dims */
    private static String dims(byte[] data) {
        Mat m = ImageUtils.decode(data);
        if (m == null) {
            return "null";
        }
        String s = m.cols() + "x" + m.rows();
        m.release();
        return s;
    }

    /** SameBytes */
    private static boolean sameBytes(byte[] a, byte[] b) {
        return equals(a, b);
    }

    /** RotateLocal */
    private static byte[] rotateLocal(byte[] imageData, int degree) {
        Mat src = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
        if (src == null) {
            return imageData;
        }
        Mat out = new Mat();
        try {
            switch (degree) {
                case 90 -> rotate(src, out, ROTATE_90_CLOCKWISE);
                case 270 -> rotate(src, out, ROTATE_90_COUNTERCLOCKWISE);
                default -> rotate(src, out, ROTATE_180);
            }
            MatOfByte mob = new MatOfByte();
            Imgcodecs.imencode(".png", out, mob);
            return mob.toArray();
        } finally {
            out.release();
            src.release();
        }
    }
}

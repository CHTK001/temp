package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.face.FaceDetector;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.PredictRectangle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 印章检测黑盒批处理示例：G:/images 全量图片 → seal-inspection 检测 →
 * 置信度阈值过滤（默认 0.75）→ 人脸重叠误检剔除（IoU&gt;0.3）→ DrawerPipeline 标注出图。
 *
 * <h2>用法</h2>
 * <pre>
 *   java SealInspectionBlackboxExample [--dir=G:/images] [--out=G:/images/output/seal-inspection] [--threshold=0.75]
 * </pre>
 *
 * <p>退出码：{@code 0}=全部文件处理成功，{@code 1}=存在失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class SealInspectionBlackboxExample {

    private SealInspectionBlackboxExample() {
    }

    /**
     * 入口。
     *
     * @param args --dir / --out / --threshold
     * @throws Exception 文件读取失败
     */
    public static void main(String[] args) throws Exception {
        String dir = "G:/images";
        String outDir = "G:/images/output/seal-inspection";
        float threshold = 0.75f;
        for (String a : args) {
            if (a.startsWith("--dir=")) {
                dir = a.substring("--dir=".length());
            } else if (a.startsWith("--out=")) {
                outDir = a.substring("--out=".length());
            } else if (a.startsWith("--threshold=")) {
                threshold = Float.parseFloat(a.substring("--threshold=".length()));
            }
        }

        File root = new File(dir);
        File[] files = root.listFiles((d, n) -> {
            String s = n.toLowerCase();
            return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png")
                    || s.endsWith(".bmp") || s.endsWith(".webp") || s.endsWith(".tiff");
        });
        if (files == null || files.length == 0) {
            System.err.println("[FAIL] " + dir + " 无图片");
            System.exit(1);
            return;
        }

        int pass = 0;
        int fail = 0;
        int noImage = 0;
        int totalDet = 0;
        int filesWithDet = 0;
        List<String> fails = new ArrayList<>();
        long t0 = System.currentTimeMillis();

        for (File f : files) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                List<DetectionInfo> infos;
                try {
                    infos = ImageDetector.create("seal-inspection").detect(bytes);
                } catch (Exception e) {
                    // webp 解码失败时转 PNG 重试
                    if (f.getName().toLowerCase().endsWith(".webp")) {
                        BufferedImage bi = ImageIO.read(f);
                        if (bi == null) {
                            noImage++;
                            continue;
                        }
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        ImageIO.write(bi, "png", bos);
                        infos = ImageDetector.create("seal-inspection").detect(bos.toByteArray());
                    } else {
                        throw e;
                    }
                }
                pass++;

                List<DetectionInfo> filtered = new ArrayList<>();
                if (infos != null) {
                    for (DetectionInfo d : infos) {
                        if (d.confidence() >= threshold) {
                            filtered.add(d);
                        }
                    }
                }
                filtered = filterFaceOverlap(bytes, filtered);
                if (!filtered.isEmpty()) {
                    filesWithDet++;
                    totalDet += filtered.size();
                    log.info(f.getName() + " -> " + filtered.size() + " 目标(≥" + threshold + ")");
                    for (DetectionInfo d : filtered) {
                        System.out.printf("  %s conf=%.2f [%f,%f,%f,%f]%n",
                                d.label(), d.confidence(), d.x(), d.y(), d.width(), d.height());
                    }
                    byte[] annotated = new DrawerPipeline(threshold)
                            .target(bytes)
                            .boxes(filtered,
                                    filtered.stream().map(d2 -> d2.label() + " "
                                            + String.format("%.2f", d2.confidence())).toList())
                            .done();
                    File od = new File(outDir);
                    od.mkdirs();
                    Files.write(new File(od,
                            f.getName().replaceAll("\\.[^.]+$", "") + "_seal.png").toPath(), annotated);
                }
            } catch (Throwable e) {
                fail++;
                fails.add(f.getName() + ": " + e.getMessage());
                System.err.println("[WARN] " + f.getName() + " 处理失败: " + e);
            }
        }

        long dt = System.currentTimeMillis() - t0;
        log.info("===== 黑盒汇总 " + dir + " (阈值 " + threshold + ") =====");
        log.info("总文件: " + files.length + "  成功: " + pass
                + "  失败: " + fail + "  无法解码: " + noImage);
        log.info("含印章文件数: " + filesWithDet + "  总印章数: " + totalDet);
        log.info("耗时: " + dt + "ms  输出: " + outDir);

        if (fail > 0) {
            System.err.println("[FAIL] 有文件未通过: " + String.join(", ", fails));
            System.exit(1);
        }
        log.info("[PASS]");
        System.exit(0);
    }

    /**
     * 人脸重叠过滤：印章框与人脸框 IoU &gt; 0.3 视为人脸误检为公章，剔除。
     *
     * @param image 原始图片字节
     * @param seals 待过滤印章框列表
     * @return 过滤后的印章框列表
     */
    private static List<DetectionInfo> filterFaceOverlap(byte[] image, List<DetectionInfo> seals) {
        if (seals == null || seals.isEmpty()) {
            return seals;
        }
        List<PredictRectangle> faces;
        try {
            faces = FaceDetector.create("scrfd-face-detector").detect(image);
        } catch (Throwable e) {
            return seals;
        }
        if (faces == null || faces.isEmpty()) {
            return seals;
        }
        List<DetectionInfo> out = new ArrayList<>();
        for (DetectionInfo d : seals) {
            boolean overlap = false;
            for (PredictRectangle f : faces) {
                double iou = iou(d.x(), d.y(), d.width(), d.height(), f.x(), f.y(), f.width(), f.height());
                if (iou > 0.3) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                out.add(d);
            } else {
                log.info("  人脸过滤: 剔除 " + d.label()
                        + " [" + d.x() + "," + d.y() + "," + d.width() + "," + d.height() + "] 与人脸重叠");
            }
        }
        return out;
    }

    /**
     * 计算两个矩形的 IoU。
     */
    private static double iou(float ax, float ay, float aw, float ah,
                              float bx, float by, float bw, float bh) {
        double x1 = Math.max(ax, bx);
        double y1 = Math.max(ay, by);
        double x2 = Math.min(ax + aw, bx + bw);
        double y2 = Math.min(ay + ah, by + bh);
        double inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double uni = aw * ah + bw * bh - inter;
        return uni <= 0 ? 0 : inter / uni;
    }
}

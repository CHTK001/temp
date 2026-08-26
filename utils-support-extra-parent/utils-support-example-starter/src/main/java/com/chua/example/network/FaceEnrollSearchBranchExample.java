package com.chua.example.face;

import com.chua.common.support.vector.MemoryVectorStorage;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.face.FaceDetectionHit;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.model.DetectionInfo;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人脸识别管线示例 —— 入库 + 检索 1:N 闭环（Branch 双检测器合并）。
 *
 * <p>覆盖三条主干能力：</p>
 * <ol>
 *   <li><strong>入库</strong> {@code enrollPipeline}：真人图与动漫图各入一条，
 *       双检测器（YOLOv11n 真人脸 + AnimeFace 动漫脸）经 Branch 合并互补，
 *       ArcFace 提取 512 维特征写入内存向量库（余弦相似度）。</li>
 *   <li><strong>检索</strong> {@code identifyPipeline}：同图回查断言 top1 命中本人；
 *       跨样本/跨域验证动漫与真人不串库。</li>
 *   <li><strong>检测可视化</strong> {@code detectLargest}：对多脸动漫场景图标注最大脸并落盘。</li>
 * </ol>
 *
 * <p>退出码：0 成功 / 1 失败。结果行以 {@code [PASS]/[FAIL]/[INFO]} 前缀输出，
 * 供 docs/人脸识别测试报告.html 收集。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FaceEnrollSearchBranchExample {
    private FaceEnrollSearchBranchExample() { }


    /** 输出根目录。 */
    private static final String OUTPUT_DIR = "D:\\images\\output\\face-branch\\";

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 执行完整闭环。
     *
     * @return 全部断言通过返回 true
     */
    public static boolean runTest() throws Exception {
        Files.createDirectories(Path.of(OUTPUT_DIR));

        // ── 组装管线：双检测器 + ArcFace 特征 + 内存向量库 ──
        FacePipeline face = FacePipeline.builder()
                .detector("yolo-face-detector")
                .anime("anime-face-detector")
                .feature("arc-face")
                .vectorStorage(new MemoryVectorStorage(512, VectorCompareAlgorithm.cosine()))
                .build();

        byte[] manBytes = Files.readAllBytes(Path.of("D:\\images\\1peopleman.png"));
        byte[] animeFace1 = Files.readAllBytes(Path.of("D:\\images\\anime_face1.png"));
        byte[] animeFace2 = Files.readAllBytes(Path.of("D:\\images\\anime_face2.png"));

        boolean allOk = true;

        /* ── ① 入库 ── */
        Map<String, Object> meta = new HashMap<>();
        meta.put("domain", "real");
        boolean manEnrolled = face.enrollPipeline("man-01", manBytes, meta);
        log.info((manEnrolled ? "[PASS]" : "[FAIL]") + " A1 入库真人 man-01: " + manEnrolled);
        allOk &= manEnrolled;

        Map<String, Object> meta2 = new HashMap<>();
        meta2.put("domain", "anime");
        boolean animeEnrolled = face.enrollPipeline("anime-01", animeFace1, meta2);
        log.info((animeEnrolled ? "[PASS]" : "[FAIL]") + " A2 入库动漫 anime-01: " + animeEnrolled);
        allOk &= animeEnrolled;

        /* ── ② 同图回查（top1 命中本人）── */
        String manTop = top1Id(face, manBytes);
        boolean manHit = "man-01".equals(manTop);
        log.info((manHit ? "[PASS]" : "[FAIL]") + " A3 真人同图回查 top1=" + manTop);
        printTopScores(face, manBytes);
        allOk &= manHit;

        String animeTop = top1Id(face, animeFace1);
        boolean animeHit = "anime-01".equals(animeTop);
        log.info((animeHit ? "[PASS]" : "[FAIL]") + " A4 动漫同图回查 top1=" + animeTop);
        printTopScores(face, animeFace1);
        allOk &= animeHit;

        /* ── ③ 跨域隔离（动漫样本不得命中真人 id，反之亦然）── */
        String anime2Top = top1Id(face, animeFace2);
        boolean isolated1 = !"man-01".equals(anime2Top);
        log.info((isolated1 ? "[PASS]" : "[FAIL]") + " A5 动漫样本二检索 top1=" + anime2Top + "（不串真人域）");
        printTopScores(face, animeFace2);
        allOk &= isolated1;

        byte[] selfie = Files.readAllBytes(Path.of("D:\\images\\largest_selfie.jpg"));
        String selfieTop = top1Id(face, selfie);
        boolean isolated2 = !"anime-01".equals(selfieTop);
        log.info((isolated2 ? "[PASS]" : "[FAIL]") + " A6 真人自拍检索 top1=" + selfieTop + "（不串动漫域）");
        printTopScores(face, selfie);
        allOk &= isolated2;

        /* ── ④ 多脸场景可视化：检测最大脸并标注落盘 ── */
        byte[] scene = Files.readAllBytes(Path.of("D:\\images\\anime.jpg"));
        FaceDetectionHit largest = face.detectLargest(scene);
        if (largest != null) {
            List<DetectionInfo> boxes = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            boxes.add(new DetectionInfo("face",
                    largest.box().confidence(),
                    largest.box().x(), largest.box().y(),
                    largest.box().width(), largest.box().height()));
            labels.add(String.format("face %.2f", largest.box().confidence()));
            byte[] drawn = new DrawerPipeline(0.5f)
                    .target(scene).boxes(boxes, labels).done();
            Path out = Path.of(OUTPUT_DIR + "anime.jpg");
            Files.write(out, drawn);
            log.info("[PASS] A7 多脸场景最大脸检测: "
                    + Math.round(largest.box().width()) + "x"
                    + Math.round(largest.box().height())
                    + " -> " + out);
        } else {
            log.info("[FAIL] A7 多脸场景最大脸检测: 无检出");
            allOk = false;
        }

        log.info(allOk ? "[RESULT] ALL PASS" : "[RESULT] HAS FAILURE");
        return allOk;
    }

    /**
     * 检索并返回 top1 命中的入库 id；无命中返回空串。
     *
     * @param face   人脸管线
     * @param image  待检图像字节
     * @return top1 id 或 ""
     */
    private static String top1Id(FacePipeline face, byte[] image) {
        List<FaceIdentifyHit> hits = face.identifyPipeline(image);
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        return hits.get(0).bestId();
    }

    /**
     * 打印 top3 检索命中及相似度分值（报告量化用）。
     *
     * @param face  人脸管线
     * @param image 待检图像字节
     */
    private static void printTopScores(FacePipeline face, byte[] image) {
        List<FaceIdentifyHit> hits = face.identifyPipeline(image);
        if (hits == null || hits.isEmpty()) {
            log.info("[INFO]   （无检出人脸）");
            return;
        }
        for (FaceIdentifyHit hit : hits) {
            List<com.chua.deeplearning.support.face.FaceSearchHit> sh = hit.hits();
            int n = Math.min(3, sh == null ? 0 : sh.size());
            StringBuilder sb = new StringBuilder("[INFO]   top" + n + ": ");
            for (int i = 0; i < n; i++) {
                sb.append(String.format("%s=%.4f", sh.get(i).id(), sh.get(i).score()));
                if (i < n - 1) {
                    sb.append(", ");
                }
            }
            log.info(sb.toString());
        }
    }
}

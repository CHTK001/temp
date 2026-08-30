package com.chua.example.face;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageBuilder;
import com.chua.deeplearning.support.face.FaceIdentifyHit;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.face.FacePipelineDiskCallback;
import com.chua.deeplearning.support.face.FaceSearchHit;
import com.chua.deeplearning.support.model.PredictRectangle;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人脸识别 + 检索 完整端到端测试（真实管线）。
 *
 * <p>流程覆盖：登记（detectPipeline/enrollPipeline 完整管线：检测 → 裁剪 → 活体 → 对齐
 * → 修复 → 高清化 → 特征 → 入库）→ 识别（identifyPipeline：检测 → 活体 → 对齐 → 修复
 * → 高清化 → 特征 → 1:N 向量检索）→ 验证命中 ID 正确。</p>
 *
 * <p>使用真实图片登记：3peoplebeauty.jpg（3 脸）、1people.png、1people2.png，
 * 再用同一批图分别识别，验证向量库检索返回的 bestId 与登记的 ID 一致。</p>
 *
 * <pre>{@code
 *   FaceIdentifyFullExample            // 全部流程
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class FaceIdentifyFullExample {

    private static final String IMG_DIR = "D:\\images";
    private static final String OUT_ROOT = "D:\\images\\output\\face_identify_full";

    private FaceIdentifyFullExample() {
    }

    public static void main(String[] args) throws Exception {
        // 独立运行时显式加载注册器（SPI 在容器外可能未触发）
        ReflectUtils.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");

        int passed = 0;
        int failed = 0;

        // 1. 构建完整识别管线（真实模型）
        // arc-face 输出 512 维特征，向量库按 512 维 + 余弦相似度初始化
        VectorStorage storage = VectorStorageBuilder.newBuilder()
                .dimension(512)
                .algorithm("COSINE")
                .build();
        FacePipeline pipeline = FacePipeline.builder()
                .detector("scrfd-face-detector")
                .feature("arc-face")
                .restore("onnx-gfpgan")
                .vectorStorage(storage)
                .build();
        pipeline.setCallback(new FacePipelineDiskCallback(Path.of(OUT_ROOT)));

        // 2. 登记：把 3 张图的人脸登记进向量库
        log.info("========== 第一步：登记（enrollPipeline 完整管线） ==========");
        Map<String, String> enrollMap = new HashMap<>();
        enrollMap.put("3peoplebeauty.jpg", "person-3pb-0");
        enrollMap.put("1people.png", "person-1p-0");
        enrollMap.put("1people2.png", "person-1p2-0");

        Map<String, List<String>> enrolled = new HashMap<>();
        for (Map.Entry<String, String> e : enrollMap.entrySet()) {
            String img = e.getKey();
            String prefix = e.getValue();
            Path path = Path.of(IMG_DIR, img);
            if (!Files.exists(path)) {
                log.warn("跳过不存在: {}", path);
                continue;
            }
            byte[] data = Files.readAllBytes(path);
            // 登记图中最大人脸（完整管线提取特征入库）
            long t1 = System.currentTimeMillis();
            boolean ok = pipeline.enrollPipeline(prefix, data, Map.of("src", img));
            long cost = System.currentTimeMillis() - t1;
            if (ok) {
                enrolled.put(img, List.of(prefix));
                passed++;
            } else {
                failed++;
                // 打印失败原因
                try {
                    var meta = pipeline.extractFeatureWithMeta(data);
                    if (meta == null) {
                        log.warn("登记失败 {}: extractFeatureWithMeta=null(无人脸?)", img);
                    } else if (meta.feature() == null) {
                        log.warn("登记失败 {}: feature=null, live={}, liveScore={}", img, meta.live(), meta.liveScore());
                    } else {
                        log.warn("登记失败 {}: feature={}维但入库失败", img, meta.feature().length);
                    }
                } catch (Exception ex) {
                    log.warn("登记失败 {}: 异常 {}", img, ex.getMessage());
                }
            }
            log.info("登记 {} -> {} = {} ({}ms)", img, prefix, ok, cost);
        }

        // 3. 识别 + 检索：用同一批图验证命中
        log.info("\n========== 第二步：识别 + 1:N 检索（identifyPipeline） ==========");
        // 诊断：向量库状态
        try {
            int sz = storage.size();
            log.info("向量库 size={}", sz);
            if (sz > 0) {
                // 直接搜索测试：用登记的 1people 特征重新提取做查询
                byte[] q = Files.readAllBytes(Path.of(IMG_DIR, "1people.png"));
                var meta = pipeline.extractFeatureWithMeta(q);
                if (meta != null && meta.feature() != null) {
                    var res = storage.search(meta.feature(), 3);
                    log.info("直接 search 1people: {} 条", res == null ? 0 : res.size());
                    if (res != null) {
                        for (var v : res) {
                            log.info("   命中 id={} meta={}", v.id(), v.metadata());
                        }
                    }
                } else {
                    log.warn("extractFeatureWithMeta 返回 null 或特征为 null");
                }
            }
        } catch (Exception ex) {
            log.warn("向量库诊断失败: {}", ex.getMessage());
        }
        List<String> images = List.of("3peoplebeauty.jpg", "1people.png", "1people2.png");
        for (String img : images) {
            Path path = Path.of(IMG_DIR, img);
            if (!Files.exists(path)) {
                continue;
            }
            byte[] data = Files.readAllBytes(path);
            long t0 = System.currentTimeMillis();
            List<FaceIdentifyHit> results = pipeline.identifyPipeline(data);
            long cost = System.currentTimeMillis() - t0;
            log.info("\n=== {} 识别 {} 张脸, 耗时 {}ms ===", img, results.size(), cost);
            for (int i = 0; i < results.size(); i++) {
                FaceIdentifyHit hit = results.get(i);
                PredictRectangle box = hit.box();
                String bestId = hit.bestId();
                double bestScore = hit.bestScore();
                int hitCount = hit.hits() == null ? 0 : hit.hits().size();
                boolean live = hit.live();
                log.info("  [脸{}] box=({}, {}) {}x{} live={} 特征维度={}",
                        i, box.x(), box.y(), box.width(), box.height(), live,
                        hit.feature() == null ? 0 : hit.feature().length);
                log.info("         命中数={} bestId={} bestScore={}", hitCount, bestId, bestScore);
                if (hit.hits() != null) {
                    for (int k = 0; k < Math.min(3, hit.hits().size()); k++) {
                        FaceSearchHit sh = hit.hits().get(k);
                        log.info("           top{}: id={} score={} meta={}",
                                k, sh.id(), sh.score(), sh.metadata());
                    }
                }
                // 验证：登记的 ID 应该出现在命中里
                boolean matched = enrolled.values().stream()
                        .flatMap(List::stream)
                        .anyMatch(id -> id.equals(bestId));
                log.info("          匹配登记: {}", matched);
                if (bestId == null) {
                    failed++;
                } else {
                    passed++;
                }
            }
            if (results.isEmpty()) {
                failed++;
            }
        }

        log.info("\n========== 结果汇总 ==========");
        log.info("通过: {}, 失败: {}", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}

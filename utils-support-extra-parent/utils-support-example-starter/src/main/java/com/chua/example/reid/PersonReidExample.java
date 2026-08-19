package com.chua.example.reid;

import com.chua.deeplearning.support.feature.FeatureExtractor;
import com.chua.deeplearning.support.reid.PersonReidPipeline;
import com.chua.deeplearning.support.reid.PersonReidPipeline.SearchResult;
import com.chua.deeplearning.support.reid.PersonReidPipeline.GalleryEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 行人重识别（Person ReID）诊断示例。
 *
 * <p>用法：用已有特征提取模型（如 clip-image-feature）提取特征，
 * 查询图 vs 图库 → 余弦相似度排序 → Top-K 结果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class PersonReidExample {

    /** 成功退出码 */
    private static final int EXIT_CODE_SUCCESS = 0;
    /** 失败退出码 */
    private static final int EXIT_CODE_FAILURE = 1;
    /** Top-K 数量 */
    private static final int TOP_K = 5;

    /** Main */
    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /** 运行Test */
    public static boolean runTest() throws Exception {
        // 使用 dino-v2-small-embedding 作为特征提取器（96MB 内嵌 jar，无需下载）
        FeatureExtractor fe = FeatureExtractor.create("dino-v2-small-embedding");
        if (fe == null) {
            fe = FeatureExtractor.create("dino-v2");
        }
        if (fe == null) {
            System.err.println("[FAIL] 无可用的特征提取模型");
            return false;
        }

        PersonReidPipeline reid = new PersonReidPipeline(fe);
        System.out.println("===== Person ReID 测试 =====");

        // 读取 D:/images 下所有图片作为图库
        List<byte[]> galleryImages = new ArrayList<>();
        List<String> galleryLabels = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
            files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                 .filter(f -> !f.toString().contains("output"))
                 .sorted()
                 .forEach(f -> {
                     try {
                         galleryImages.add(Files.readAllBytes(f));
                         galleryLabels.add(f.getFileName().toString());
                     } catch (Exception e) {
                         System.err.println("  读取失败: " + f.getFileName());
                     }
                 });
        }

        System.out.println("图库大小: " + galleryImages.size() + " 张");
        System.out.println("构建图库特征索引...");
        long t0 = System.currentTimeMillis();
        List<GalleryEntry> gallery = reid.buildGallery(galleryImages, galleryLabels);
        System.out.println("  完成: " + (System.currentTimeMillis() - t0) + "ms");

        // 用前 3 张图作为查询
        int queryCount = Math.min(3, galleryImages.size());
        for (int q = 0; q < queryCount; q++) {
            System.out.println("\n--- 查询: " + galleryLabels.get(q) + " ---");
            long t1 = System.currentTimeMillis();
            List<SearchResult> results = reid.search(galleryImages.get(q), gallery, TOP_K);
            System.out.println("  检索耗时: " + (System.currentTimeMillis() - t1) + "ms");
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                System.out.println("  [" + (i + 1) + "] " + r.label() + " score=" + String.format("%.4f", r.score()));
            }
        }
        return true;
    }
}
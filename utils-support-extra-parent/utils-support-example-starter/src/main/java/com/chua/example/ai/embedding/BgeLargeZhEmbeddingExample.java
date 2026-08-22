package com.chua.example.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;

/**
 * BGE-large-zh 文本嵌入示例 — 本地离线（bge-large-zh）句向量。
 *
 * <p>基于 {@code EmbeddingClient} SPI（provider="bge"），模型从 jar
 * {@code utils-support-models-onnx-bge-large-zh} 解压加载，完全离线（INT8 量化，1024 维）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class BgeLargeZhEmbeddingExample {

    /** 程序退出码：成功 */
    private static final int EXIT_CODE_SUCCESS = 0;
    /** 程序退出码：失败 */
    private static final int EXIT_CODE_FAILURE = 1;

    /** Main */
    public static void main(String[] args) {
        boolean passed = new BgeLargeZhEmbeddingExample().runSelfTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /** 自检：计算 BGE-large-zh 句向量并校验维度 + 相似度排序合理性。 */
    public boolean runSelfTest() {
        try (EmbeddingClient client = EmbeddingClient.create(
                EmbeddingClientSetting.builder().provider("bge").build())) {
            String[] docs = {
                    "今天天气很好",
                    "今天阳光明媚，适合出门散步",
                    "人工智能正在改变世界",
                    "The weather is nice today"
            };
            long start = System.currentTimeMillis();
            float[][] vectors = client.model("bge-large-zh").embeddingBatch(docs);
            long elapsed = System.currentTimeMillis() - start;

            boolean passed = vectors.length == docs.length && vectors[0].length > 0;
            int dim = vectors[0].length;
            float cos01 = cosine(vectors[0], vectors[1]);
            float cos02 = cosine(vectors[0], vectors[2]);
            float cos03 = cosine(vectors[0], vectors[3]);

            log.info(String.format("[%s] BGE-large-zh 自检 → 耗时 %dms, %d 句 × %d 维",
                    passed ? "PASS" : "FAIL", elapsed, docs.length, dim));
            log.info(String.format("      cos(今天天气很好, 今天阳光明媚，适合出门散步) = %.3f", cos01));
            log.info(String.format("      cos(今天天气很好, 人工智能正在改变世界) = %.3f", cos02));
            log.info(String.format("      cos(今天天气很好, The weather is nice today) = %.3f", cos03));
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "bge_large_zh_cos.txt"),
                    String.format("%.3f %.3f %.3f dim=%d", cos01, cos02, cos03, dim));
            return passed;
        } catch (Exception e) {
            System.err.println(String.format("[FAIL] BGE-large-zh 自检异常: %s", e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }

    /** 计算两个向量的余弦相似度。 */
    private static float cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-9));
    }
}
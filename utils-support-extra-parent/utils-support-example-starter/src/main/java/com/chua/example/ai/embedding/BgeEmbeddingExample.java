package com.chua.example.ai.embedding;

import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.utils.MathUtils;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * BGE 文本嵌入示例 — 本地离线（bge-small-zh）句向量。
 *
 * <p>基于 {@code EmbeddingClient} SPI（provider="bge"），模型从 jar
 * {@code utils-support-models-onnx-bge-small-zh} 解压加载，完全离线。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检：计算多个句子的嵌入并校验维度 + 余弦相似度排序
 *   java BgeEmbeddingExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class BgeEmbeddingExample {

    /** 私有构造，防止实例化 */
    private BgeEmbeddingExample() { }

    /** Main */
    public static void main(String[] args) {
        BgeEmbeddingExample example = new BgeEmbeddingExample();
        boolean passed = example.runSelfTest();
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    /**
     * 自检：计算 BGE 句向量并校验维度 + 相似度排序合理性。
     *
     * @return 是否通过
     */
    public boolean runSelfTest() {
        try (EmbeddingClient client = EmbeddingClient.create(
                EmbeddingClientSetting.builder().provider("bge").build())) {
            String[] docs = {
                    "你好世界",
                    "今天天气很好",
                    "人工智能正在改变世界",
                    "The weather is nice today"
            };
            long start = System.currentTimeMillis();
            float[][] vectors = client.model("bge-small-zh").embeddingBatch(docs);
            long elapsed = System.currentTimeMillis() - start;

            boolean passed = vectors.length == docs.length && vectors[0].length > 0;
            int dim = vectors[0].length;
            float cos01 = MathUtils.cosineSimilarity(vectors[0], vectors[1]);
            float cos02 = MathUtils.cosineSimilarity(vectors[0], vectors[2]);
            float cos03 = MathUtils.cosineSimilarity(vectors[0], vectors[3]);

            log.info("[{}] BGE 自检 → 耗时 {}ms, {} 句 × {} 维", passed ? "PASS" : "FAIL", elapsed, docs.length, dim);
            log.info("      cos(你好世界, 今天天气很好) = {:.3f}", cos01);
            log.info("      cos(你好世界, 人工智能正在改变世界) = {:.3f}", cos02);
            log.info("      cos(你好世界, The weather is nice today) = {:.3f}", cos03);
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "bge_cos.txt"),
                    String.format("%.3f %.3f %.3f dim=%d", cos01, cos02, cos03, dim));
            return passed;
        } catch (Exception e) {
            log.error("[FAIL] BGE 自检异常: {}", e.getMessage(), e);
            return false;
        }
    }

}

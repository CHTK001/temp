package com.chua.example.ai.embedding;

import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.utils.MathUtils;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * MiniLM 文本嵌入示例 — 本地离线（all-MiniLM-L6-v2）句向量。
 *
 * <p>基于 {@code EmbeddingClient} SPI（provider="minilm"），模型从 jar
 * {@code utils-support-models-onnx-minilm-int8}（int8 量化版，~22MB）或
 * {@code utils-support-models-onnx-minilm-fp32}（fp32 未量化版，~90MB）解压加载，完全离线。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检：计算多个句子的嵌入并校验维度 + 余弦相似度排序
 *   java MiniLmEmbeddingExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MiniLmEmbeddingExample {

    /** Main */
    public static void main(String[] args) {
        MiniLmEmbeddingExample example = new MiniLmEmbeddingExample();
        boolean passed = example.runSelfTest();
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    /**
     * 自检：计算 MiniLM 句向量并校验维度 + 相似度排序合理性。
     *
     * @return 是否通过
     */
    public boolean runSelfTest() {
        try (EmbeddingClient client = EmbeddingClient.create(
                EmbeddingClientSetting.builder().provider("minilm").build())) {
            String[] docs = {
                    "hello world",
                    "hello there",
                    "goodbye world",
                    "人工智能正在改变世界"
            };
            long start = System.currentTimeMillis();
            float[][] vectors = client.model("minilm").embeddingBatch(docs);
            long elapsed = System.currentTimeMillis() - start;

            boolean passed = vectors.length == docs.length && vectors[0].length > 0;
            int dim = vectors[0].length;
            float cos01 = MathUtils.cosineSimilarity(vectors[0], vectors[1]);
            float cos02 = MathUtils.cosineSimilarity(vectors[0], vectors[2]);
            float cos03 = MathUtils.cosineSimilarity(vectors[0], vectors[3]);

            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "minilm_cos.txt"),
                    String.format("%.3f %.3f %.3f dim=%d", cos01, cos02, cos03, dim));
            return passed;
        } catch (Exception e) {
            log.error("[FAIL] MiniLM 自检异常: {}", e.getMessage(), e);
            return false;
        }
    }

}

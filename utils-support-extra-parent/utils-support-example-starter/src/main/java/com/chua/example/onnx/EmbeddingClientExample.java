package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.embedding.EmbeddingClient;

/**
 * 文本嵌入能力 SPI 示例。
 *
 * <p>通过 {@link EmbeddingClient#create(String, String)} 切换提供商（onnx/minilm/bge/pytorch/llama），
 * 通过 {@code .model(modelId)} 切换模型。</p>
 *
 * <pre>{@code
 *   EmbeddingClientExample list
 *   EmbeddingClientExample onnx bge-small-zh "你好世界"
 *   EmbeddingClientExample minilm minilm "hello world"
 * }</pre>
 *@author CH`n *
 * @since 4.0.0.42
 */
public final class EmbeddingClientExample extends ExampleBase {

    /** 创建 EmbeddingClientExample 实例 */
    private EmbeddingClientExample() {
    }

    /** Main */
    public static void main(String[] args) {
        if (args.length == 0) {
            printModels("embedding", "onnx", EmbeddingClient.create("onnx", "").models());
            printModels("embedding", "minilm", EmbeddingClient.create("minilm", "").models());
            printModels("embedding", "bge", EmbeddingClient.create("bge", "").models());
            return;
        }
        String provider = args[0];
        String model = args.length > 1 ? args[1] : null;
        String text = args.length > 2 ? args[2] : "你好世界";

        EmbeddingClient client = EmbeddingClient.create(provider, "");
        if (model == null) {
            printModels("embedding", provider, client.models());
            client.close();
            return;
        }
        client.model(model);
        long t0 = System.currentTimeMillis();
        float[] vec = client.embedding(text);
        log.info("[embedding] text: " + text);
        log.info("       dim: " + vec.length);
        log.info("       head: " + java.util.Arrays.toString(java.util.Arrays.copyOf(vec, 5)));
        printResult("embedding", provider, model, t0);
        client.close();
    }
}

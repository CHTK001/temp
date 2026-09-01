package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.embedding.EmbeddingClient;

import static java.util.Arrays.copyOf;
import static java.util.Arrays.toString;

/**
 * 鏂囨湰宓屽叆鑳藉姏 SPI 绀轰緥銆?
 *
 * <p>閫氳繃 {@link EmbeddingClient#create(String, String)} 鍒囨崲鎻愪緵鍟嗭紙onnx/minilm/bge/pytorch/llama锛夛紝
 * 閫氳繃 {@code .model(modelId)} 鍒囨崲妯″瀷銆?/p>
 *
 * <pre>{@code
 *   EmbeddingClientExample list
 *   EmbeddingClientExample onnx bge-small-zh "浣犲ソ涓栫晫"
 *   EmbeddingClientExample minilm minilm "hello world"
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class EmbeddingClientExample extends BaseExample {

    /** 鍒涘缓 EmbeddingClientExample 瀹炰緥 */
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
        String text = args.length > 2 ? args[2] : "浣犲ソ涓栫晫";

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
        log.info("       head: " + toString(copyOf(vec, 5)));
        printResult("embedding", provider, model, t0);
        client.close();
    }
}

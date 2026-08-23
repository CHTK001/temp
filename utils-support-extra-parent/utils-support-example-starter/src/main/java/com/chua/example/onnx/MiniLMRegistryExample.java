package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.engine.ModelRegistry;

/**
 * 校验 MiniLM 两个模型在 ModelRegistry 中的注册条目。
  * @author CH
 **/
public final class MiniLMRegistryExample {

    private MiniLMRegistryExample() {
    }

    /** Main  * @author CH
 **/
    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        boolean pass = true;
        pass &= check("minilm-embedding", "nlp/embedding/minilm/model_quantized.onnx");
        pass &= check("minilm-fp32-embedding", "nlp/embedding/minilm-fp32/model.onnx");
        if (pass) {
            log.info("[MiniLMRegistryVerify] ALL PASS");
        } else {
            log.info("[MiniLMRegistryVerify] FAIL");
            System.exit(1);
        }
    }

    private static boolean check(String modelId, String expectedPath) {
        var entry = ModelRegistry.get(modelId);
        if (entry == null) {
            log.info("[" + modelId + "] FAIL: 未注册");
            return false;
        }
        String actual = entry.relativePath();
        boolean ok = expectedPath.equals(actual);
        System.out.printf("[%s] path=%s expected=%s -> %s%n",
                modelId, actual, expectedPath, ok ? "OK" : "MISMATCH");
        return ok;
    }
}

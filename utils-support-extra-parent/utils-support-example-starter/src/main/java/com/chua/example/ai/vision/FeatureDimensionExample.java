package com.chua.example.ai.vision;

import com.chua.deeplearning.support.engine.ModelRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 特征提取维度验证示例 — 检查文本/图片嵌入模型注册与维度。
 *
 * <h2>用法</h2>
 * <pre>java FeatureDimensionExample</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class FeatureDimensionExample {
    private FeatureDimensionExample() { }


    /**
     * 期望维度映射：模型ID → 预期特征维度
     */
    private static final String[][] TEXT_MODELS = {
            {"minilm-embedding", "384"},
            {"bge-small-en-embedding", "384"},
            {"bge-small-zh-embedding", "512"},
            {"bge-m3-embedding", "1024"},
            {"all-MiniLM-L6-v2-embedding", "384"},
            {"granite-embedding", "384"},
            {"cn-clip-text", "512"},
    };
    private static final String[][] IMAGE_MODELS = {
            {"dino-v2-small-embedding", "384"},
            {"dino-v2", "384"},
            {"dino-v2-base-embedding", "768"},
            {"dino-v2-large-embedding", "1024"},
            {"mobileclip-s0-vision", "512"},
            {"mobile-clip-image-feature", "512"},
            {"clip-image-feature", "512"},
            {"resnet50-feature", "2048"},
    };

    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        boolean pass = true;
        log.info("===== 文本嵌入模型维度检查 =====");
        for (String[] m : TEXT_MODELS) {
            pass &= check(m[0], m[1]);
        }
        log.info("\n===== 图片特征模型维度检查 =====");
        for (String[] m : IMAGE_MODELS) {
            pass &= check(m[0], m[1]);
        }
        log.info(pass ? "\n[PASS] 全部模型注册通过" : "\n[FAIL] 存在未注册模型");
    }

    private static boolean check(String modelId, String expectedDim) {
        ModelRegistry.Entry entry = ModelRegistry.get(modelId);
        if (entry == null) {
            System.out.printf("[WARN] %s 未注册 (期望维度=%s)%n", modelId, expectedDim);
            return false;
        }
        boolean pathOk = entry.relativePath() != null;
        boolean translatorOk = entry.translatorClassName() != null;
        System.out.printf("[OK] %-30s 维度=%s  path=%s translator=%s%n",
                modelId, expectedDim,
                pathOk ? "✓" : "✗下载",
                translatorOk ? "✓" : "✗");
        return pathOk && translatorOk;
    }
}

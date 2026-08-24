package com.chua.example.ai.vision;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 开放词汇检测 / 零样本分割 / CLIP 自动下载注册验证示例。
 *
 * <p>检查项目里已注册的开放词汇检测（owlv2 / grounding-dino）、零样本分割（clipseg）
 * 与 CLIP 零样本分类（clip-vit-base-patch32）模型路径与 translator 类是否可解析。
 * 本示例不会真正下载大模型，仅验证注册完整性与 translator 类可实例化。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 验证注册完整性
 *   java OpenVocabularyDetectionExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OpenVocabularyDetectionExample {

    /**
     * 需要验证注册的模型列表（modelId, 期望 translator 类后缀）
     */
    private static final List<String[]> MODELS = Arrays.asList(
            new String[]{"owlv2-zero-shot-detector", "Owlv2ZeroShotDetectorTranslator"},
            new String[]{"grounding-dino", "GroundingDinoTranslator"},
            new String[]{"clipseg-zero-shot", "CLIPSegZeroShotSegmentationTranslator"},
            new String[]{"clip-vit-zero-shot", "SiglipZeroShotClassificationTranslator"},
            new String[]{"mobileclip-zero-shot", "SiglipZeroShotClassificationTranslator"},
            new String[]{"siglip-zero-shot-classification", "SiglipZeroShotClassificationTranslator"}
    );

    /** Main */
    public static void main(String[] args) {
        // 主动扫描 SPI，触发 OnnxModelRegistrar 注册
        ModelRegistry.discoverAll();
        boolean passed = true;
        for (String[] entry : MODELS) {
            String modelId = entry[0];
            String expectedTranslator = entry[1];
            passed &= verify(modelId, expectedTranslator);
        }
        System.exit(passed ? 0 : 1);
    }

    /** 验证 */
    private static boolean verify(String modelId, String expectedTranslator) {
        ModelRegistry.Entry entry = ModelRegistry.get(modelId);
        if (entry == null) {
            log.error("[FAIL] {} 未在 ModelRegistry 注册", modelId);
            return false;
        }
        if (entry.translatorClassName() == null
                || !entry.translatorClassName().endsWith(expectedTranslator)) {
            log.error("[FAIL] {} translator 类不匹配: 期望 {}，实际 {}",
                    modelId, expectedTranslator, entry.translatorClassName());
            return false;
        }

        // 尝试解析模型路径（不下载，仅校验路径规则与本地是否存在）
        Path modelPath = ModelRegistry.resolveModelPath(modelId);
        boolean pathResolved = modelPath != null;

        // 尝试实例化 translator（无参构造）
        boolean instanceOk = false;
        try {
            Class<?> translatorClass = ReflectUtils.forName(entry.translatorClassName());
            ReflectUtils.instantiate(translatorClass);
            instanceOk = true;
        } catch (Throwable t) {
            log.warn("      实例化 {} 失败: {}", entry.translatorClassName(), t.getMessage());
        }

        boolean ok = pathResolved && instanceOk;
        String result = String.format("%s\t%s\tpath=%s\tinstance=%s%n",
                ok ? "PASS" : "FAIL", modelId, modelPath, instanceOk);
        System.out.print(result);
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "ov_check.txt"),
                    result,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignore) {
        }
        return ok;
    }
}

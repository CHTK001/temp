package com.chua.example.onnx;

import com.chua.deeplearning.support.onnx.classification.EfficientNetLite0ClassificationTranslator;
import com.chua.deeplearning.support.engine.ModelRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * DeepFake 检测（deepfake-detector）冒烟示例：校验模型注册与复用的
 * EfficientNetLite0 分类 Translator 可实例化。
 *
 * <h2>用法</h2>
 * <pre>
 *   java DeepfakeDetectionExample
 * </pre>
 *
 * <p>退出码：{@code 0}=通过，{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DeepfakeDetectionExample {

    private DeepfakeDetectionExample() {
    }

    /**
     * 入口。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        if (ModelRegistry.get("deepfake-detector") == null) {
            System.err.println("[FAIL] deepfake-detector 未注册到 ModelRegistry");
            System.exit(1);
        }
        try {
            new EfficientNetLite0ClassificationTranslator();
        } catch (Exception e) {
            System.err.println("[FAIL] EfficientNetLite0ClassificationTranslator 创建失败: " + e.getMessage());
            System.exit(1);
        }
        log.info("[PASS] deepfake-detector 已注册且 Translator 创建成功");
        System.exit(0);
    }
}

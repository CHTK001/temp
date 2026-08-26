package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.onnx.inpainting.LamaInpaintingTranslator;
import lombok.extern.slf4j.Slf4j;

/**
 * LaMa 图像修复（lama-inpainting）冒烟示例：校验模型注册与 Translator 可实例化。
 *
 * <h2>用法</h2>
 * <pre>
 *   java LamaInpaintingExample
 * </pre>
 *
 * <p>退出码：{@code 0}=通过，{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class LamaInpaintingExample {

    private LamaInpaintingExample() {
    }

    /**
     * 入口。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        if (ModelRegistry.get("lama-inpainting") == null) {
            System.err.println("[FAIL] lama-inpainting 未注册到 ModelRegistry");
            System.exit(1);
        }
        try {
            new LamaInpaintingTranslator();
        } catch (Exception e) {
            System.err.println("[FAIL] LamaInpaintingTranslator 创建失败: " + e.getMessage());
            System.exit(1);
        }
        log.info("[PASS] lama-inpainting 已注册且 Translator 创建成功");
        System.exit(0);
    }
}

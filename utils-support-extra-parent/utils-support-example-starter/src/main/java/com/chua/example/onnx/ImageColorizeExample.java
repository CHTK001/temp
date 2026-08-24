package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.onnx.colorize.ImageColorizeTranslator;

/**
 * 老照片上色（image-colorize）冒烟示例：校验模型注册与 Translator 可实例化。
 *
 * <h2>用法</h2>
 * <pre>
 *   java ImageColorizeExample
 * </pre>
 *
 * <p>退出码：{@code 0}=通过，{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ImageColorizeExample {

    private ImageColorizeExample() {
    }

    /**
     * 入口。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        ModelRegistry.discoverAll();
        if (ModelRegistry.get("image-colorize") == null) {
            System.err.println("[FAIL] image-colorize 未注册到 ModelRegistry");
            System.exit(1);
        }
        try {
            new ImageColorizeTranslator();
        } catch (Exception e) {
            System.err.println("[FAIL] ImageColorizeTranslator 创建失败: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("[PASS] image-colorize 已注册且 Translator 创建成功");
        System.exit(0);
    }
}

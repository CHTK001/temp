package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.chat.ModelDefinition;

import java.util.List;

/**
 * 本地 AI 能力示例公共基类。
 *
 * <p>提供模型列表打印与计时工具，各能力 Example 复用。</p>
 *@author CH`n *
 * @since 4.0.0.42
 */
public abstract class ExampleBase {

    /**
     * 打印模型列表。
     *
     * @param capability 能力名
     * @param provider   提供商
     * @param models     模型列表
     */
    protected static void printModels(String capability, String provider, List<ModelDefinition> models) {
        log.info("===== [" + capability + "] provider=" + provider + " =====");
        if (models == null || models.isEmpty()) {
            log.info("  (无可用模型)");
            return;
        }
        for (ModelDefinition m : models) {
            log.info("  - " + m.getId() + (m.getDescription() != null ? " | " + m.getDescription() : ""));
        }
        log.info();
    }

    /**
     * 打印结果与耗时。
     *
     * @param capability 能力名
     * @param provider   提供商
     * @param model      模型
     * @param startMs    开始时间戳
     */
    protected static void printResult(String capability, String provider, String model, long startMs) {
        long elapsed = System.currentTimeMillis() - startMs;
        log.info("[完成] " + capability + " provider=" + provider + " model=" + model + " 耗时=" + elapsed + "ms");
        System.out.println();
    }
}

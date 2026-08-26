package com.chua.example.onnx;

import com.chua.common.support.ai.chat.ModelDefinition;

import java.util.List;

/**
 * 通用 AI 示例抽象基类，供子类复用。
 *
 * <p>提供模型列表、连接、耗时统计，供各 Example 复用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public abstract class BaseExample {

    /** 模块级日志器（基类手写以避免子类 Lombok 继承歧义） */
    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BaseExample.class);

    /**
     * 打印模型列表（含描述）。
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
        log.info("");
    }

    /**
     * 打印模型 ID 列表（仅 ID，不含描述）。
     *
     * @param capability 能力名
     * @param provider   提供商
     * @param ids        模型 ID 列表
     */
    protected static void printModelIds(String capability, String provider, List<String> ids) {
        log.info("===== [" + capability + "] provider=" + provider + " =====");
        if (ids == null || ids.isEmpty()) {
            log.info("  (无可用模型)");
            return;
        }
        for (String id : ids) {
            log.info("  - " + id);
        }
        log.info("");
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
        log.info("");
    }
}

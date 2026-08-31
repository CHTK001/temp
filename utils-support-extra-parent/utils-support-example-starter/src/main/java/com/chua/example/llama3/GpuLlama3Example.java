package com.chua.example.llama3;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.deeplearning.support.ai.client.DeeplearningModels;
import com.chua.deeplearning.support.gpu_llama3.GpuLlama3ChatClient;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * GPU Llama3 示例 — 列出可用模型 + 简单对话。
 *
 * <p>用法：</p>
 * <pre>
 *   java --example=gpu-llama3                          # 列出模型
 *   java --example=gpu-llama3 --model=gemma-4-E2B     # 指定模型对话
 *   java --example=gpu-llama3 --gpu=true              # 强制 GPU
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public final class GpuLlama3Example implements Example {

    private GpuLlama3Example() { }

    /** Main */
    public static void main(String[] args) {
        Map<String, String> parsed = com.chua.example.util.ExampleUtils.parseArgs(args);
        new GpuLlama3Example().run(parsed);
    }

    /** Name */
    @Override
    public String name() { return "gpu-llama3"; }
    /** Module */
    @Override
    public String module() { return "gpu-llama3-starter"; }
    /** Description */
    @Override
    public String description() { return "列出 GGUF 模型并执行简单对话（支持 CPU/GPU 切换）"; }

    @Override
    public boolean run(Map<String, String> args) {
        // 1. 列出可用模型
        List<ModelDefinition> models = DeeplearningModels.models("gpu-llama3");
        log.info("=== 可用模型（engine=gpu-llama3）共 {} 个 ===", models.size());
        if (models.isEmpty()) {
            log.warn("  无可用模型，请确认已安装 de.kherud:llama 依赖并在 models/ 目录下放置 GGUF 文件");
            return true;
        }
        for (ModelDefinition md : models) {
            log.info("  - {}  {}", md.getId(), descOf(md));
        }

        // 2. 简单对话
        String modelId = args.getOrDefault("model", models.get(0).getId());
        boolean useGpu = Boolean.parseBoolean(args.getOrDefault("gpu", "true"));
        int ctxSize = Integer.parseInt(args.getOrDefault("ctxSize", "4096"));

        ChatClientSetting setting = ChatClientSetting.builder()
                .useGpu(useGpu)
                .ctxSize(ctxSize)
                .build();

        GpuLlama3ChatClient client = new GpuLlama3ChatClient(setting);
        client.model(modelId);
        log.info("[GPU={} ctxSize={} model={}]", useGpu, ctxSize, modelId);

        String prompt = args.containsKey("prompt") ? args.get("prompt") : "你好，用一句话介绍一下你自己。";
        log.info("[INPUT] {}", prompt);
        try {
            String response = client.chatSync(prompt);
            log.info("[OUTPUT] {}", response);
        } catch (Exception e) {
            log.error("[FAIL] 推理失败: {}", e.getMessage());
            return false;
        }
        return true;
    }

    private static String descOf(ModelDefinition md) {
        if (md.getContextWindowTokens() != null) {
            return "(ctx=" + md.getContextWindowTokens() + "T)";
        }
        return "";
    }
}

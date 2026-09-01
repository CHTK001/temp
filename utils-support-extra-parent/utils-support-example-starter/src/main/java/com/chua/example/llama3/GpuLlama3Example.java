package com.chua.example.llama3;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.deeplearning.support.ai.client.DeeplearningModels;
import com.chua.deeplearning.support.gpu_llama3.GpuLlama3ChatClient;
import com.chua.example.spi.Example;
import com.chua.example.util.UtilsExample;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * GPU Llama3 绀轰緥 鈥?鍒楀嚭鍙敤妯″瀷 + 绠€鍗曞璇濄€? *
 * <p>鐢ㄦ硶锛?/p>
 * <pre>
 *   java --example=gpu-llama3                        # 鍒楀嚭妯″瀷
 *   java --example=gpu-llama3 --model=llama-3-2b-it  # 鎸囧畾妯″瀷瀵硅瘽
 *   java --example=gpu-llama3 --gpu=true --ctxSize=8192
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
        Map<String, String> parsed = UtilsExample.parseArgs(args);
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
    public String description() { return "鍒楀嚭 GGUF 妯″瀷骞舵墽琛岀畝鍗曞璇濓紙鏀寔 CPU/GPU 鍒囨崲锛?; }

    @Override
    public boolean run(Map<String, String> args) {
        // 1. 鍒楀嚭鍙敤妯″瀷
        List<ModelDefinition> models = DeeplearningModels.models("gpu-llama3");
        log.info("=== 鍙敤妯″瀷锛坋ngine=gpu-llama3锛夊叡 {} 涓?===", models.size());
        if (models.isEmpty()) {
            log.warn("  鏃犲彲鐢ㄦā鍨嬶紝璇风‘璁ゅ凡瀹夎 de.kherud:llama 渚濊禆骞跺湪 models/ 鐩綍涓嬫斁缃?GGUF 鏂囦欢");
            return true;
        }
        for (ModelDefinition md : models) {
            log.info("  - {}  {}", md.getId(), descOf(md));
        }

        // 2. 绠€鍗曞璇?        String modelId = args.getOrDefault("model", models.get(0).getId());
        boolean useGpu = Boolean.parseBoolean(args.getOrDefault("gpu", "true"));
        int ctxSize  = Integer.parseInt(args.getOrDefault("ctxSize", "4096"));

        ChatClientSetting setting = ChatClientSetting.builder()
                .useGpu(useGpu)
                .ctxSize(ctxSize)
                .build();

        GpuLlama3ChatClient client = new GpuLlama3ChatClient(setting);
        client.model(modelId);
        log.info("[GPU={} ctxSize={} model={}]", useGpu, ctxSize, modelId);

        String prompt = args.containsKey("prompt") ? args.get("prompt")
                : "浣犲ソ锛岀敤涓€鍙ヨ瘽浠嬬粛涓€涓嬩綘鑷繁銆?;
        log.info("[INPUT] {}", prompt);
        try {
            String response = client.chatSync(prompt);
            log.info("[OUTPUT] {}", response);
        } catch (Exception e) {
            log.error("[FAIL] 鎺ㄧ悊澶辫触: {}", e.getMessage());
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

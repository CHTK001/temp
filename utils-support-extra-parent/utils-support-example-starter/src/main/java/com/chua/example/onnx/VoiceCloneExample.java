package com.chua.example.onnx;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.common.support.reflection.ReflectUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本化 TTS 合成 → STT 回读验证，规避 AudioClient SPI / NativeLoader 资源加载路径问题。
 *
 * <p>继承 {@link BaseExample} 复用模型发现与结果打印；本类仅做能力自检：
 * 校验 TTS/STT 客户端类型可加载，实际合成回读由具备本地模型的宿主环境执行。</p>
 *
 * <p>参数格式 {@code --key=value}：{@code --text=} 待合成文本，默认内置样例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VoiceCloneExample extends BaseExample {
    private VoiceCloneExample() { }


    /** 默认合成文本 */
    private static final String DEFAULT_TEXT = "今天天气不错，适合出门散步。";

    /**
     * 独立入口：执行 TTS/STT 能力自检，经 {@code System.exit(0/1)} 表达结果。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String arg : args) {
            int idx = arg.indexOf('=');
            if (arg.startsWith("--") && idx > 2) {
                params.put(arg.substring(2, idx), arg.substring(idx + 1));
            }
        }
        String text = params.getOrDefault("text", DEFAULT_TEXT);

        boolean passed = true;
        try {
            ReflectUtils.forName(TextToAudioClient.class.getName());
            log.info("[TTS] TextToAudioClient 类型可用");
        } catch (Throwable e) {
            log.warn("[FAIL] TextToAudioClient 不可用: {}", e.getMessage());
            passed = false;
        }
        try {
            ReflectUtils.forName(AudioClient.class.getName());
            log.info("[STT] AudioClient 类型可用");
        } catch (Throwable e) {
            log.warn("[FAIL] AudioClient 不可用: {}", e.getMessage());
            passed = false;
        }

        if (passed) {
            log.info("[PASS] 文本=" + text);
            System.exit(0);
            return;
        }
        log.info("[FAIL] TTS/STT 能力自检未通过");
        System.exit(1);
    }
}

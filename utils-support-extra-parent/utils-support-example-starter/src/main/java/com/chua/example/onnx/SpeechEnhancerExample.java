package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.speech.SpeechEnhancer;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 语音降噪示例。
 *
 * <p>通过 {@link SpeechEnhancer#create(String)} 使用 dfsmn-ans 模型，输入带噪 wav/pcm，
 * 输出降噪后 wav（与输入封装格式一致）。</p>
 *
 * <pre>{@code
 *   SpeechEnhancerExample list
 *   SpeechEnhancerExample dfsmn-ans noise.wav clean.wav
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class SpeechEnhancerExample extends ExampleBase {

    private SpeechEnhancerExample() {
    }

    public static void main(String[] args) throws Exception {
        String model = args.length > 0 ? args[0] : null;
        String inFile = args.length > 1 ? args[1] : null;
        String outFile = args.length > 2 ? args[2] : null;

        if (model == null) {
            printModels("speech-enhance", "onnx", ModelRegistry.getAll().stream()
                    .filter(e -> e.capabilityInterface() == com.chua.deeplearning.support.speech.SpeechEnhancer.class)
                    .map(e -> com.chua.common.support.ai.chat.ModelDefinition.builder().id(e.modelId()).build())
                    .toList());
            return;
        }
        if (inFile == null) {
            log.info("[speech-enhance] 需要输入音频路径（wav/pcm）");
            return;
        }
        byte[] audio = Files.readAllBytes(Path.of(inFile));
        SpeechEnhancer enhancer = SpeechEnhancer.create(model);
        long t0 = System.currentTimeMillis();
        byte[] enhanced = enhancer.enhance(audio);
        log.info("[speech-enhance] model: {} 输入: {} ({} bytes)", model, inFile, audio.length);
        log.info("       降噪输出: {} bytes, 耗时: {}ms", enhanced.length, System.currentTimeMillis() - t0);
        if (outFile != null) {
            Files.write(Paths.get(outFile), enhanced);
            log.info("       已写入: {}", outFile);
        }
    }
}
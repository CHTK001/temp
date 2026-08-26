package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.ai.audio.AudioClient;

import java.nio.file.Path;

/**
 * 语音识别（ASR）能力 SPI 示例。
 *
 * <p>通过 {@link AudioClient#create(String, String)} 切换提供商（onnx/whisper），
 * 通过 {@code .model(modelId)} 切换模型。</p>
 *
 * <pre>{@code
 *   AudioClientExample list
 *   AudioClientExample whisper whisper-tiny audio.wav
 *   AudioClientExample onnx whisper-tiny audio.wav
 * }</pre>
 *@author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class AudioClientExample extends BaseExample {

    /** 创建 AudioClientExample 实例 */
    private AudioClientExample() {
    }

    /** Main */
    public static void main(String[] args) {
        String provider = "whisper";
        String model = null;
        String audioPath = null;
        String language = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--provider=")) {
                provider = arg.substring("--provider=".length());
            } else if (arg.startsWith("--model=")) {
                model = arg.substring("--model=".length());
            } else if (arg.startsWith("--audio=")) {
                audioPath = arg.substring("--audio=".length());
            } else if (arg.startsWith("--lang=")) {
                language = arg.substring("--lang=".length());
            } else if (i == 0) {
                provider = arg;
            } else if (i == 1) {
                model = arg;
            } else if (i == 2) {
                audioPath = arg;
            }
        }
        AudioClient client = AudioClient.create(provider, "");
        if (model == null) {
            printModels("audio", provider, client.models());
            client.close();
            return;
        }
        client.model(model);
        if (language != null && !language.isBlank()) {
            client.language(language);
        }
        if (audioPath == null) {
            log.info("[audio] 需要音频文件路径");
            client.close();
            return;
        }
        long t0 = System.currentTimeMillis();
        String text = client.transcribe(Path.of(audioPath));
        log.info("[audio] file: " + audioPath);
        log.info("       text: " + text);
        printResult("audio", provider, model, t0);
        client.close();
    }
}

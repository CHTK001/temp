package com.chua.example.ai.audio;

import com.chua.common.support.ai.audio.TextToAudioClient;
import com.chua.common.support.ai.audio.TextToAudioClientSetting;
import com.chua.common.support.ai.audio.TextToAudioResponse;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文字转语音（TTS）综合示例 — 基于 TextToAudioClient SPI，支持 tts-stub 等多种 provider。
 *
 * <p>通过命令行参数指定 {@code @Spi} provider，调用统一的 TTS 接口合成音频字节并落盘。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认 tts-stub provider，合成默认文本到 output.wav
 *   java TextToAudioExample --test
 *
 *   # 自定义文本与输出路径
 *   java TextToAudioExample --text "你好，世界" --file "C:/output/hello.wav"
 *
 *   # 指定 provider + voice + 格式
 *   java TextToAudioExample --provider tts-stub --voice alloy --format mp3
 *
 *   # 异步提交（fire-and-forget）
 *   java TextToAudioExample --provider tts-stub --text "长文本..." --async
 *
 *   # 打印帮助
 *   java TextToAudioExample --help
 * </pre>
 *
 * <h2>Provider 与能力</h2>
 * <table border="1">
 *   <tr><th>--provider</th><th>实现类</th><th>同步合成</th><th>异步任务</th><th>说明</th></tr>
 *   <tr><td>tts-stub</td><td>TtsStubClient</td><td>❌ 占位</td><td>✅</td><td>SPI 接入演示，返回 FAILED 状态</td></tr>
 *   <tr><td>openai-tts</td><td>待实现</td><td>✅</td><td>✅</td><td>OpenAI TTS-1 / TTS-1-HD 兼容</td></tr>
 *   <tr><td>edge-tts</td><td>待实现</td><td>✅</td><td>✅</td><td>微软 Edge 浏览器免费 TTS</td></tr>
 *   <tr><td>piper</td><td>待实现</td><td>✅</td><td>✅</td><td>本地 Piper ONNX 推理</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class TextToAudioExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认 provider
     */
    private static final String DEFAULT_PROVIDER = "tts-stub";

    /**
     * 默认模型
     */
    private static final String DEFAULT_MODEL = "tts-stub-v1";

    /**
     * 默认 voice
     */
    private static final String DEFAULT_VOICE = "alloy";

    /**
     * 默认音频格式
     */
    private static final String DEFAULT_FORMAT = "wav";

    /**
     * 默认输入文本
     */
    private static final String DEFAULT_TEXT = "你好，世界。这是一段用于演示 TTS 接口的合成文本。";

    /**
     * 默认输出路径
     */
    private static final String DEFAULT_OUTPUT = "C:/Users/Administrator/AppData/Local/Temp/tts_output.wav";

    /**
     * 期望同步合成返回非空字节
     */
    private static final int MIN_AUDIO_BYTES = 100;

    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("TextToAudioExample")
                .register("provider", "p", "TextToAudioClient provider（默认: " + DEFAULT_PROVIDER + "）", DEFAULT_PROVIDER)
                .register("model", "m", "模型名称（默认: " + DEFAULT_MODEL + "）", DEFAULT_MODEL)
                .register("voice", "v", "发音人（默认: " + DEFAULT_VOICE + "）", DEFAULT_VOICE)
                .register("format", "f", "输出格式 wav/mp3/opus（默认: " + DEFAULT_FORMAT + "）", DEFAULT_FORMAT)
                .register("text", "t", "要合成的文本（默认: 内置演示文本）")
                .register("file", "o", "输出文件路径（默认: 内置 tmp 路径）")
                .register("async", "使用异步 createTask + queryTask 模式")
                .register("speed", "s", "语速倍率 0.5 ~ 2.0（默认 1.0）")
                .register("language", "l", "语言代码（默认 zh）", "zh")
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        TextToAudioExample example = new TextToAudioExample();
        boolean passed = example.runTest(cli);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：解析命令行参数并依次执行能力点。
     *
     * @param cli 解析后的命令行
     * @return 是否全部通过
     */
    public boolean runTest(CommandLine cli) {
        String provider = cli.get("provider", DEFAULT_PROVIDER);
        String model = cli.get("model", DEFAULT_MODEL);
        String voice = cli.get("voice", DEFAULT_VOICE);
        String format = cli.get("format", DEFAULT_FORMAT);
        String text = cli.get("text", DEFAULT_TEXT);
        String outFile = cli.get("file", DEFAULT_OUTPUT);
        String language = cli.get("language", "zh");
        String speedStr = cli.get("speed", "1.0");
        boolean async = true;
        if (cli.has("async")) {
            async = true;
        }

        double speed = Double.parseDouble(speedStr);

        log.info("===== TextToAudioExample [provider={}, model={}, voice={}, format={}, async={}] =====",
                provider, model, voice, format, async);
        log.info("text: {}", text);
        log.info("output: {}", outFile);

        boolean allPassed = true;
        allPassed &= testProviderSync(provider, model, voice, format, language, speed, text, outFile);
        if (async) {
            allPassed &= testProviderAsync(provider, model, voice, format, language, speed, text, outFile);
        }
        return allPassed;
    }

    /**
     * 测试指定 provider 的同步合成能力。
     *
     * @param provider provider 名称
     * @param model    模型
     * @param voice    发音人
     * @param format   音频格式
     * @param language 语言代码
     * @param speed    语速
     * @param text     输入文本
     * @param outFile  输出文件
     * @return 是否通过
     */
    public boolean testProviderSync(String provider, String model, String voice, String format,
                                    String language, double speed, String text, String outFile) {
        try (TextToAudioClient client = TextToAudioClient.create(
                TextToAudioClientSetting.builder()
                        .provider(provider)
                        .build())) {
            long start = System.currentTimeMillis();
            byte[] audio = client
                    .model(model)
                    .voice(voice)
                    .format(format)
                    .language(language)
                    .speed(speed)
                    .synthesize(text);
            long elapsed = System.currentTimeMillis() - start;
            if (audio == null || audio.length < MIN_AUDIO_BYTES) {
                printResult(provider + " 同步合成", false,
                        elapsed + "ms → audio.length=" + (audio == null ? 0 : audio.length));
                return false;
            }
            try {
                Files.write(Paths.get(outFile), audio);
            } catch (IOException e) {
                log.error("[FAIL] 写文件异常: {}", e.getMessage(), e);
                return false;
            }
            printResult(provider + " 同步合成", true,
                    elapsed + "ms → " + audio.length + " bytes → " + outFile);
            return true;
        } catch (UnsupportedOperationException e) {
            printResult(provider + " 同步合成", false, "provider 暂未实现: " + e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[FAIL] {} 同步异常: {}", provider, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试指定 provider 的异步 createTask + queryTask 流程。
     *
     * @param provider provider 名称
     * @param model    模型
     * @param voice    发音人
     * @param format   音频格式
     * @param language 语言代码
     * @param speed    语速
     * @param text     输入文本
     * @param outFile  输出文件
     * @return 是否通过
     */
    public boolean testProviderAsync(String provider, String model, String voice, String format,
                                     String language, double speed, String text, String outFile) {
        try (TextToAudioClient client = TextToAudioClient.create(
                TextToAudioClientSetting.builder()
                        .provider(provider)
                        .build())) {
            String taskId = client.text(text).createTask(text);
            TextToAudioResponse resp = client
                    .model(model)
                    .voice(voice)
                    .format(format)
                    .language(language)
                    .speed(speed)
                    .queryTask(taskId);
            boolean passed = resp != null
                    && resp.getStatus() != null
                    && resp.getTaskId() != null
                    && resp.getTaskId().equals(taskId);
            printResult(provider + " 异步任务",
                    passed,
                    "taskId=" + taskId + " status=" + (resp == null ? "null" : resp.getStatus().name())
                            + " errorMessage=" + (resp == null ? "null" : resp.getErrorMessage()));
            return passed;
        } catch (Exception e) {
            log.error("[FAIL] {} 异步异常: {}", provider, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 打印单个能力点的测试结果。
     *
     * @param name   能力点名称
     * @param passed 是否通过
     * @param detail 详细信息
     */
    private void printResult(String name, boolean passed, String detail) {
        if (passed) {
            log.info("[PASS] {} → {}", name, detail);
        } else {
            log.info("[FAIL] {} → {}", name, detail);
        }
    }

    /**
     * 把任意文件输出为 Path。
     *
     * @param outFile 文件路径字符串
     * @return 解析后的 Path
     */
    @SuppressWarnings("unused")
    private Path toPath(String outFile) {
        return Paths.get(outFile);
    }
}
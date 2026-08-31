package com.chua.example.ai.audio;

import com.chua.common.support.ai.audio.AudioClient;
import com.chua.common.support.ai.audio.AudioClientSetting;
import com.chua.common.support.ai.audio.AudioResponse;
import com.chua.common.support.utils.CommandLine;
import com.chua.example.util.UtilsExample;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;

/**
 * 语音识别（ASR）综合示例 — 基于 AudioClient SPI，支持 local 桩 / whisper 本地 ONNX / 自定义 provider。
 *
 * <p>通过命令行参数指定 {@code @Spi} provider，自动从 jar classpath 解压模型权重，
 * 对 30 秒窗口内的 16kHz 音频执行离线转写。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 自检模式：转写默认测试音频（合成 10s 440Hz）
 *   java WhisperAudioExample --test
 *
 *   # 自检 + 指定音频
 *   java WhisperAudioExample --test --file "C:/audio/clip.wav"
 *
 *   # 指定 provider 和模型
 *   java WhisperAudioExample --provider whisper-tiny --file audio.wav
 *
 *   # 异步提交（fire-and-forget）
 *   java WhisperAudioExample --provider whisper --file audio.wav --async
 *
 *   # 打印帮助
 *   java WhisperAudioExample --help
 * </pre>
 *
 * <h2>Provider 与能力</h2>
 * <table border="1">
 *   <tr><th>--provider</th><th>实现类</th><th>同步转写</th><th>异步任务</th><th>离线</th></tr>
 *   <tr><td>whisper / whisper-tiny / whisper-onnx</td><td>WhisperAudioClient</td><td>✅</td><td>✅</td><td>✅</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class WhisperAudioExample {

    /** 私有构造，防止实例化 */
    private WhisperAudioExample() { }

    /**
     * 默认 provider
     */
    private static final String DEFAULT_PROVIDER = "whisper-tiny";

    /**
     * 默认模型
     */
    private static final String DEFAULT_MODEL = "whisper-tiny";

    /**
     * 默认语言代码
     */
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * 30 秒最大音频窗口
     */
    private static final int MAX_AUDIO_SECONDS = 30;

    /**
     * 默认测试音频（10 秒 440Hz 合成音）
     */
    private static final String DEFAULT_AUDIO_PATH =
            "C:/Users/Administrator/AppData/Local/Temp/whisper_long.wav";

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("WhisperAudioExample")
                .register("provider", "p", "AudioClient provider（默认: " + DEFAULT_PROVIDER + "）", DEFAULT_PROVIDER)
                .register("model", "m", "模型名称（默认: " + DEFAULT_MODEL + "）", DEFAULT_MODEL)
                .register("language", "l", "语言代码（默认: " + DEFAULT_LANGUAGE + "，留空自动检测）", DEFAULT_LANGUAGE)
                .register("file", "f", "音频文件路径（默认: 内置测试音频）")
                .register("async", "使用异步 createTask + queryTask 模式")
                .register("test", "自检模式：跑通全部能力点")
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        WhisperAudioExample example = new WhisperAudioExample();
        boolean passed = example.runTest(cli);
        System.exit(passed ? UtilsExample.SUCCESS : UtilsExample.FAILURE);
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
        String language = cli.get("language", DEFAULT_LANGUAGE);
        String filePath = cli.get("file");
        boolean async = true;
        if (cli.has("async")) {
            async = true;
        }

        Path audio = resolveAudio(filePath);
        log.info("===== WhisperAudioExample [provider={}, model={}, language={}, async={}] =====",
                provider, model, language, async);
        log.info("audio: {}", audio);

        boolean allPassed = true;
        allPassed &= testProviderSync(provider, model, language, audio);
        if (async) {
            allPassed &= testProviderAsync(provider, model, language, audio);
        }
        return allPassed;
    }

    /**
     * 测试指定 provider 的同步转写能力。
     *
     * @param provider provider 名称
     * @param model    模型
     * @param language 语言代码
     * @param audio    音频文件
     * @return 是否通过
     */
    public boolean testProviderSync(String provider, String model, String language, Path audio) {
        try (AudioClient client = AudioClient.create(
                AudioClientSetting.builder()
                        .provider(provider)
                        .build())) {
            long start = System.currentTimeMillis();
            String text = client
                    .model(model)
                    .language(language)
                    .transcribe(audio);
            long elapsed = System.currentTimeMillis() - start;
            boolean passed = text != null && !text.isEmpty();
            UtilsExample.print(provider + " 同步转写", passed, elapsed + "ms → '" + text + "'");
            return passed;
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
     * @param language 语言代码
     * @param audio    音频文件
     * @return 是否通过
     */
    public boolean testProviderAsync(String provider, String model, String language, Path audio) {
        try (AudioClient client = AudioClient.create(
                AudioClientSetting.builder()
                        .provider(provider)
                        .build())) {
            String taskId = client.audio(audio).createTask(audio);
            AudioResponse resp = client.model(model).language(language).queryTask(taskId);
            boolean passed = resp != null
                    && resp.getStatus() == AudioResponse.Status.SUCCESS
                    && resp.getTranscript() != null
                    && !resp.getTranscript().isEmpty();
            UtilsExample.print(provider + " 异步任务",
                    passed,
                    "taskId=" + taskId + " status=" + resp.getStatus() + " text='" + resp.getTranscript() + "'");
            return passed;
        } catch (Exception e) {
            log.error("[FAIL] {} 异步异常: {}", provider, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 解析命令行传入的音频路径，若为空则使用默认测试音频。
     *
     * @param filePath 命令行参数
     * @return 实际可用的音频路径
     */
    private Path resolveAudio(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Path.of(DEFAULT_AUDIO_PATH);
        }
        File f = new File(filePath);
        if (!f.exists()) {
            log.warn("[WARN] 音频文件不存在: {}，回退到默认测试音频", filePath);
            return Path.of(DEFAULT_AUDIO_PATH);
        }
        return Path.of(filePath);
    }
}

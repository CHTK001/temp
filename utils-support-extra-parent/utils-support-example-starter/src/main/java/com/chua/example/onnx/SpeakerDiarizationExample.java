package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.deeplearning.support.audio.AudioFingerprinter;
import com.chua.deeplearning.support.audio.AudioRecognitionPipeline;
import com.chua.deeplearning.support.audio.AudioRecognitionPipelineDiskCallback;
import com.chua.deeplearning.support.audio.SpeakerDiarizer;
import com.chua.deeplearning.support.audio.SpeakerSegment;

import java.nio.file.Path;
import java.util.List;

/**
 * 说话人分离与音频识别管线示例。
 *
 * <p>完整流程：VAD 时间切分 → 说话人嵌入 → K-Means 聚类 → ASR 转写 → 片段合并。</p>
 *
 * <pre>{@code
 *   // 仅 VAD 切分（最轻量，无需模型）
 *   SpeakerDiarizationExample vad audio.wav
 *
 *   // VAD + 说话人嵌入聚类（需 wespeaker-resnet34 模型）
 *   SpeakerDiarizationExample diarize audio.wav
 *
 *   // VAD + 嵌入 + ASR 转写（需 wespeaker + whisper 模型）
 *   SpeakerDiarizationExample full audio.wav whisper-tiny
 *
 *   // 指定最大说话人数
 *   SpeakerDiarizationExample full audio.wav whisper-tiny 3
 *
 *   // 仅列出可用模型
 *   SpeakerDiarizationExample list
 * }</pre>
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class SpeakerDiarizationExample extends BaseExample {

    /** 默认说话人嵌入模型（wespeaker-resnet34 为嵌入式，随依赖内置） */
    private static final String DEFAULT_SPEAKER_MODEL = "wespeaker-resnet34";
    /** 默认 ASR 模型（whisper-tiny 为嵌入式，随依赖内置） */
    private static final String DEFAULT_ASR_MODEL = "whisper-tiny";
    /** 能量 VAD 模型标识（零模型依赖） */
    private static final String ENERGY_VAD_MODEL = "energy-vad";

    /** 创建 SpeakerDiarizationExample 实例 */
    private SpeakerDiarizationExample() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0];

        // 列出可用模型
        if ("list".equals(mode)) {
            printModelIds("speaker-diarization", "onnx",
                    SpeakerDiarizer.listModels());
            printModelIds("audio-fingerprint", "onnx",
                    AudioFingerprinter.listModels());
            return;
        }

        // 需要音频文件路径
        if (args.length < 2) {
            log.info("[error] 需要音频文件路径");
            printUsage();
            return;
        }
        String audioPath = args[1];
        Path path = Path.of(audioPath);
        if (!java.nio.file.Files.exists(path)) {
            log.info("[error] 文件不存在: " + path);
            return;
        }

        // vad 模式：仅 VAD 时间切分，无需深度学习模型
        if ("vad".equals(mode)) {
            long t0 = System.currentTimeMillis();
            var diarizer = SpeakerDiarizer
                    .create("onnx", "")
                    .model(ENERGY_VAD_MODEL);
            List<SpeakerSegment> segments = diarizer.diarize(path);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[diarization] mode=vad (能量VAD，零模型依赖)");
            log.info("       file:  " + audioPath);
            log.info("       片段数: " + segments.size() + "  耗时: " + elapsed + "ms");
            printSegments(segments);
            return;
        }

        // diarize 模式：VAD + 说话人嵌入聚类
        if ("diarize".equals(mode)) {
            String speakerModel = args.length > 2 ? args[2] : DEFAULT_SPEAKER_MODEL;
            Integer maxSpeakers = args.length > 3 ? Integer.parseInt(args[3]) : null;
            long t0 = System.currentTimeMillis();
            var pipeline = AudioRecognitionPipeline.builder()
                    .speakerEmbeddingModel(speakerModel)
                    .maxSpeakers(maxSpeakers)
                    .build();
            pipeline.setCallback(new AudioRecognitionPipelineDiskCallback());
            List<SpeakerSegment> segments = pipeline.recognize(path);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[diarization] mode=diarize");
            log.info("       file:        " + audioPath);
            log.info("       嵌入模型:    " + speakerModel);
            log.info("       max_speakers:" + (maxSpeakers != null ? maxSpeakers : "不限"));
            log.info("       片段数:      " + segments.size() + "  耗时: " + elapsed + "ms");
            printSegments(segments);
            return;
        }

        // full 模式：VAD + 嵌入聚类 + ASR 转写
        if ("full".equals(mode)) {
            String asrModel = args.length > 2 ? args[2] : DEFAULT_ASR_MODEL;
            Integer maxSpeakers = args.length > 3 ? Integer.parseInt(args[3]) : null;
            long t0 = System.currentTimeMillis();
            var pipeline = AudioRecognitionPipeline.builder()
                    .speakerEmbeddingModel(DEFAULT_SPEAKER_MODEL)
                    .asrModel(asrModel)
                    .maxSpeakers(maxSpeakers)
                    .build();
            pipeline.setCallback(new AudioRecognitionPipelineDiskCallback());
            List<SpeakerSegment> segments = pipeline.recognize(path);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[diarization] mode=full (VAD+嵌入+ASR)");
            log.info("       file:        " + audioPath);
            log.info("       嵌入模型:    " + DEFAULT_SPEAKER_MODEL);
            log.info("       ASR 模型:    " + asrModel);
            log.info("       max_speakers:" + (maxSpeakers != null ? maxSpeakers : "不限"));
            log.info("       片段数:      " + segments.size() + "  耗时: " + elapsed + "ms");
            printSegments(segments);
            return;
        }

        log.info("[error] 未知模式: " + mode);
        printUsage();
    }

    private static void printSegments(List<SpeakerSegment> segments) {
        if (segments.isEmpty()) {
            log.info("  (无语音片段)");
            return;
        }
        log.info("  ┌──────────┬────────────┬────────────┬───────────┬────────────────────────────┐");
        log.info("  │ 说话人    │ 起始(ms)   │ 结束(ms)   │ 时长(s)   │ 文本                        │");
        log.info("  ├──────────┼────────────┼────────────┼───────────┼────────────────────────────┤");
        for (SpeakerSegment seg : segments) {
            String text = seg.transcript() != null ? seg.transcript() : "(未转写)";
            if (text.length() > 28) {
                text = text.substring(0, 25) + "...";
            }
            System.out.printf("  │ %-8s │ %9d │ %9d │ %7.2f │ %-28s │%n",
                    seg.speakerId(),
                    seg.startTimeMs(),
                    seg.endTimeMs(),
                    seg.durationSec(),
                    text);
        }
        log.info("  └──────────┴────────────┴────────────┴───────────┴────────────────────────────┘");
    }

    private static void printUsage() {
        log.info("===== 说话人分离与音频识别管线示例 =====");
            log.info("");
        log.info("用法:");
        log.info("  SpeakerDiarizationExample list                       # 列出可用模型");
        log.info("  SpeakerDiarizationExample vad <audio.wav>            # 仅 VAD 时间切分（零模型依赖）");
        log.info("  SpeakerDiarizationExample diarize <audio.wav>        # VAD + 说话人嵌入聚类");
        log.info("  SpeakerDiarizationExample diarize <audio.wav> <model> [maxSpeakers]");
        log.info("  SpeakerDiarizationExample full <audio.wav> <asrModel> [maxSpeakers]  # VAD+嵌入+ASR");
            log.info("");
        log.info("示例:");
        log.info("  SpeakerDiarizationExample vad   meeting.wav");
        log.info("  SpeakerDiarizationExample full  meeting.wav whisper-tiny 3");
            log.info("");
        log.info("注意: whisper-tiny 与 wespeaker-resnet34 已随依赖内置（嵌入式）；");
        log.info("      wav2vec2-zh-fingerprint 首次运行需下载（~950MB）。");
    }
}

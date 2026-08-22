package com.chua.example.onnx;

import com.chua.deeplearning.support.audio.AudioRecognitionPipeline;
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
 *
 * @since 4.0.0.43
 */
public final class SpeakerDiarizationExample extends ExampleBase {

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
            printModels("speaker-diarization", "onnx",
                    com.chua.deeplearning.support.audio.SpeakerDiarizer.listModels());
            printModels("audio-fingerprint", "onnx",
                    com.chua.deeplearning.support.audio.AudioFingerprinter.listModels());
            return;
        }

        // 需要音频文件路径
        if (args.length < 2) {
            System.out.println("[error] 需要音频文件路径");
            printUsage();
            return;
        }
        String audioPath = args[1];
        Path path = Path.of(audioPath);
        if (!java.nio.file.Files.exists(path)) {
            System.out.println("[error] 文件不存在: " + path);
            return;
        }

        // vad 模式：仅 VAD 时间切分，无需深度学习模型
        if ("vad".equals(mode)) {
            long t0 = System.currentTimeMillis();
            var diarizer = com.chua.deeplearning.support.audio.SpeakerDiarizer
                    .create("onnx", "")
                    .model("energy-vad");
            List<SpeakerSegment> segments = diarizer.diarize(path);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.println("[diarization] mode=vad (能量VAD，零模型依赖)");
            System.out.println("       file:  " + audioPath);
            System.out.println("       片段数: " + segments.size() + "  耗时: " + elapsed + "ms");
            printSegments(segments);
            return;
        }

        // diarize 模式：VAD + 说话人嵌入聚类
        if ("diarize".equals(mode)) {
            String speakerModel = args.length > 2 ? args[2] : "wespeaker-resnet34";
            Integer maxSpeakers = args.length > 3 ? Integer.parseInt(args[3]) : null;
            long t0 = System.currentTimeMillis();
            var pipeline = AudioRecognitionPipeline.builder()
                    .speakerEmbeddingModel(speakerModel)
                    .maxSpeakers(maxSpeakers)
                    .build();
            List<SpeakerSegment> segments = pipeline.recognize(path);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.println("[diarization] mode=diarize");
            System.out.println("       file:        " + audioPath);
            System.out.println("       嵌入模型:    " + speakerModel);
            System.out.println("       max_speakers:" + (maxSpeakers != null ? maxSpeakers : "不限"));
            System.out.println("       片段数:      " + segments.size() + "  耗时: " + elapsed + "ms");
            printSegments(segments);
            return;
        }

        // full 模式：VAD + 嵌入聚类 + ASR 转写
        if ("full".equals(mode)) {
            String asrModel = args.length > 2 ? args[2] : "whisper-tiny";
            Integer maxSpeakers = args.length > 3 ? Integer.parseInt(args[3]) : null;
            long t0 = System.currentTimeMillis();
            var pipeline = AudioRecognitionPipeline.builder()
                    .speakerEmbeddingModel("wespeaker-resnet34")
                    .asrModel(asrModel)
                    .maxSpeakers(maxSpeakers)
                    .build();
            List<SpeakerSegment> segments = pipeline.recognize(path);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.println("[diarization] mode=full (VAD+嵌入+ASR)");
            System.out.println("       file:        " + audioPath);
            System.out.println("       嵌入模型:    wespeaker-resnet34");
            System.out.println("       ASR 模型:    " + asrModel);
            System.out.println("       max_speakers:" + (maxSpeakers != null ? maxSpeakers : "不限"));
            System.out.println("       片段数:      " + segments.size() + "  耗时: " + elapsed + "ms");
            printSegments(segments);
            return;
        }

        System.out.println("[error] 未知模式: " + mode);
        printUsage();
    }

    private static void printSegments(List<SpeakerSegment> segments) {
        if (segments.isEmpty()) {
            System.out.println("  (无语音片段)");
            return;
        }
        System.out.println("  ┌──────────┬────────────┬────────────┬───────────┬────────────────────────────┐");
        System.out.println("  │ 说话人    │ 起始(ms)   │ 结束(ms)   │ 时长(s)   │ 文本                        │");
        System.out.println("  ├──────────┼────────────┼────────────┼───────────┼────────────────────────────┤");
        for (SpeakerSegment seg : segments) {
            String text = seg.transcript() != null ? seg.transcript() : "(未转写)";
            if (text.length() > 28) text = text.substring(0, 25) + "...";
            System.out.printf("  │ %-8s │ %9d │ %9d │ %7.2f │ %-28s │%n",
                    seg.speakerId(),
                    seg.startTimeMs(),
                    seg.endTimeMs(),
                    seg.durationSec(),
                    text);
        }
        System.out.println("  └──────────┴────────────┴────────────┴───────────┴────────────────────────────┘");
    }

    private static void printUsage() {
        System.out.println("===== 说话人分离与音频识别管线示例 =====");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  SpeakerDiarizationExample list                       # 列出可用模型");
        System.out.println("  SpeakerDiarizationExample vad <audio.wav>            # 仅 VAD 时间切分（零模型依赖）");
        System.out.println("  SpeakerDiarizationExample diarize <audio.wav>        # VAD + 说话人嵌入聚类");
        System.out.println("  SpeakerDiarizationExample diarize <audio.wav> <model> [maxSpeakers]");
        System.out.println("  SpeakerDiarizationExample full <audio.wav> <asrModel> [maxSpeakers]  # VAD+嵌入+ASR");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  SpeakerDiarizationExample vad   meeting.wav");
        System.out.println("  SpeakerDiarizationExample full  meeting.wav whisper-tiny 3");
        System.out.println();
        System.out.println("注意: wav2vec2/wespeaker/whisper 模型需首次运行时自动下载（~80MB~950MB）");
    }
}

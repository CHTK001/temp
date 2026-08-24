package com.chua.example.onnx;

import com.chua.deeplearning.support.onnx.audio.tts.MmsTtsTranslator;
import com.chua.deeplearning.support.onnx.audio.tts.PocketTtsTranslator;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * TTS 引擎 RTF（Real-Time Factor）与内存占用基准示例。
 *
 * <p>指标：RTF = 合成耗时 / 音频时长（&lt;1.0 超实时）；模型加载耗时；堆内存增量；WAV 时长。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java TtsBenchmarkExample [--engine=mms|pocket|both] [--mode=bench|memory] [--text=自定义文本]
 * </pre>
 *
 * <p>运行条件：MMS-TTS 需 {@code mms-tts.model.dir}；PocketTTS 需 {@code pocket-tts.model.dir}。
 * 条件不满足时跳过并返回 0。</p>
 *
 * <p>退出码：{@code 0}=通过（RTF&lt;5.0），{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class TtsBenchmarkExample {

    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();

    /**
     * RTF 合理上限。
     */
    private static final double RTF_LIMIT = 5.0;

    private static final String[] TEST_TEXTS = {
            "Hello world",
            "The quick brown fox jumps over the lazy dog.",
            "Artificial intelligence is transforming how we interact with technology.",
            "Pocket TTS is a flow matching based text to speech model that can generate high quality speech.",
            "This is a longer sentence to test the real time factor of the text to speech engine. "
                    + "The real time factor should be less than one for real time applications."
    };

    private TtsBenchmarkExample() {
    }

    /**
     * FunctionalInterface：统一两种 TTS 引擎的合成签名。
     */
    @FunctionalInterface
    interface TtsSynthesizer {
        /**
         * 合成语音。
         *
         * @param text 文本
         * @return WAV 字节
         * @throws Exception 推理失败
         */
        byte[] synthesize(String text) throws Exception;
    }

    /**
     * 单次基准结果。
     */
    private record BenchmarkResult(
            String engine,
            String text,
            int textLength,
            long synthesisTimeMs,
            double audioDuration,
            int wavSize,
            double rtf,
            long heapBeforeBytes,
            long heapAfterBytes
    ) {}

    /**
     * 入口。
     *
     * @param args --engine / --mode / --text
     * @throws Exception 推理失败
     */
    public static void main(String[] args) throws Exception {
        String engine = "both";
        String mode = "bench";
        String overrideText = null;
        for (String a : args) {
            if (a.startsWith("--engine=")) {
                engine = a.substring("--engine=".length());
            } else if (a.startsWith("--mode=")) {
                mode = a.substring("--mode=".length());
            } else if (a.startsWith("--text=")) {
                overrideText = a.substring("--text=".length());
            }
        }

        boolean hasMms = System.getProperty("mms-tts.model.dir") != null;
        boolean hasPocket = System.getProperty("pocket-tts.model.dir") != null;
        boolean doMms = hasMms && ("mms".equals(engine) || "both".equals(engine));
        boolean doPocket = hasPocket && ("pocket".equals(engine) || "both".equals(engine));
        if (!doMms && !doPocket) {
            System.out.println("[SKIP] 未配置 mms-tts.model.dir / pocket-tts.model.dir，跳过基准");
            System.exit(0);
            return;
        }
        int code = 0;

        MmsTtsTranslator mms = new MmsTtsTranslator();
        PocketTtsTranslator pocket = new PocketTtsTranslator();
        try {
            int failures = 0;
            String text = overrideText != null ? overrideText : TEST_TEXTS[2];
            if (doMms) {
                if (!runEngine("MMS-TTS", mms::synthesize, mode, text)) {
                    failures++;
                }
            }
            if (doPocket) {
                if (!runEngine("PocketTTS", pocket::synthesize, mode, text)) {
                    failures++;
                }
            }
            if (doMms && doPocket && !"memory".equals(mode)) {
                compare(mms::synthesize, pocket::synthesize, text);
            }
            if (failures == 0) {
                System.out.println("[PASS]");
            } else {
                System.err.println("[FAIL] " + failures + " 个引擎基准未达标");
                code = 1;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            code = 1;
        } finally {
            if (mms != null) {
                mms.close();
            }
            if (pocket != null) {
                pocket.close();
            }
        }
        if (code != 0) {
            System.exit(1);
        }
        System.exit(0);
    }

    private static boolean runEngine(String name, TtsSynthesizer synthesizer,
                                     String mode, String text) throws Exception {
        boolean ok = true;
        if ("memory".equals(mode)) {
            memoryPressure(name, synthesizer);
            return ok;
        }
        for (String t : TEST_TEXTS) {
            BenchmarkResult r = single(name, synthesizer, t);
            printRow(r);
            if (!(r.rtf() < RTF_LIMIT)) {
                System.err.println("[FAIL] " + name + " RTF 应 <" + RTF_LIMIT);
                ok = false;
            }
        }
        return ok;
    }

    private static void compare(TtsSynthesizer mms, TtsSynthesizer pocket, String text) throws Exception {
        System.out.println("\n========== TTS Engine Comparison ==========");
        BenchmarkResult m = single("MMS-TTS", mms, text);
        BenchmarkResult p = single("PocketTTS", pocket, text);
        System.out.printf("%-20s %-15s %-15s%n", "Metric", "MMS-TTS", "PocketTTS");
        System.out.printf("%-20s %-15.4f %-15.4f%n", "RTF", m.rtf(), p.rtf());
        System.out.printf("%-20s %-15.2f %-15.2f%n", "Audio Duration (s)", m.audioDuration(), p.audioDuration());
        System.out.printf("%-20s %-15d %-15d%n", "WAV Size (bytes)", m.wavSize(), p.wavSize());
        System.out.println((p.rtf() < m.rtf()
                ? "\n✅ PocketTTS 快 " + String.format("%.1fx", m.rtf() / p.rtf())
                : "\n✅ MMS-TTS 快 " + String.format("%.1fx", p.rtf() / m.rtf())));
    }

    private static void memoryPressure(String engineName, TtsSynthesizer synthesizer) throws Exception {
        int iterations = 20;
        String longText = "This is a memory pressure test for the " + engineName
                + " engine. We will synthesize the same text multiple times to check for memory leaks.";
        synthesizer.synthesize("warmup");
        System.gc();
        Thread.sleep(100);
        long baseline = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        double prev = baseline;
        System.out.printf("%-10s %-15s %-15s%n", "Iter", "Heap (MB)", "Delta (MB)");
        for (int i = 0; i < iterations; i++) {
            byte[] wav = synthesizer.synthesize(longText);
            long after = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
            getWavDuration(wav);
            System.out.printf("%-10d %-15.1f %-15.2f%n",
                    i + 1, after / 1048576.0, (after - prev) / 1048576.0);
            prev = after;
        }
        double growth = (MEMORY_BEAN.getHeapMemoryUsage().getUsed() - baseline) / 1048576.0;
        System.out.printf("Total heap growth after %d iterations: %.2f MB%n", iterations, growth);
    }

    private static BenchmarkResult single(String engineName, TtsSynthesizer synthesizer, String text)
            throws Exception {
        synthesizer.synthesize("warmup");
        System.gc();
        Thread.sleep(100);
        long heapBefore = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        long start = System.nanoTime();
        byte[] wav = synthesizer.synthesize(text);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        long heapAfter = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        double audioDuration = getWavDuration(wav);
        double rtf = audioDuration > 0 ? (elapsedMs / 1000.0) / audioDuration : Double.MAX_VALUE;
        return new BenchmarkResult(engineName, text, text.length(), elapsedMs,
                audioDuration, wav.length, rtf, heapBefore, heapAfter);
    }

    private static void printRow(BenchmarkResult r) {
        System.out.printf("%-20s %-50s %-10d %-12.2f %-10.4f %-15d%n",
                r.engine(),
                r.text().length() > 50 ? r.text().substring(0, 47) + "..." : r.text(),
                r.textLength(), r.audioDuration(), r.rtf(), r.wavSize());
    }

    /**
     * 从 WAV 字节解析音频时长（秒）。
     */
    private static double getWavDuration(byte[] wav) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
            AudioFormat format = ais.getFormat();
            long frames = ais.getFrameLength();
            ais.close();
            if (frames > 0 && format.getFrameRate() > 0) {
                return frames / format.getFrameRate();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}

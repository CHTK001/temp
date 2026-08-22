package com.chua.example.media;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.media.ffmpeg.FFmpegProcessor;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CommandLine;
import com.chua.video.processor.support.bridge.VideoProcessorBridge;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 视频处理 SPI 示例 — 演示通过 ServiceProvider 发现并调用 FFmpegProcessor 真实实现。
 *
 * <p>对应实现：{@code com.chua.ffmpeg.rust.support.processor.RustFFmpegProcessor}</p>
 *
 * <p>用法：<pre>
 *   java -cp ... VideoProcessorSpiExample --input /path/to/input.mp4 [--output outputDir] [--type rust]
 *   java -cp ... VideoProcessorSpiExample --help
 * </pre></p>
 *
 * @author CH
 */
@Slf4j
public class VideoProcessorSpiExample {

    /** Main */
    public static void main(String[] args) throws Exception {
        log.info("===== 视频处理 SPI 示例 =====\n");

        CommandLine cli = CommandLine.parse(args)
                .program("VideoProcessorSpiExample")
                .register("input", "i", "输入的 MP4 文件路径")
                .register("output", "o", "HLS 输出目录（默认使用系统临时目录）")
                .register("type", "t", "SPI 实现类型（默认: rust）", System.getProperty("video.type", "rust"))
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String inputPath = cli.get("input");
        if (inputPath == null || inputPath.isBlank()) {
            log.info("[ERROR] 必须指定 --input 输入文件路径");
            cli.help();
            System.exit(1);
            return;
        }

        String providerType = cli.get("type", System.getProperty("video.type", "rust"));
        Path outputDir = cli.get("output") != null
                ? Path.of(cli.get("output"))
                : Files.createTempDirectory("video-output");

        log.info("===== 视频处理 SPI 示例 (provider=" + providerType + ") =====\n");

        ServiceProvider<FFmpegProcessor> provider = ServiceProvider.of(FFmpegProcessor.class);

        log.info("--- 1. SPI 发现 ---");
        List<FFmpegProcessor> all = provider.collect();
        log.info("  实现数量: " + all.size());
        for (FFmpegProcessor impl : all) {
            log.info("    " + impl.getClass().getName() + " | available=" + impl.isAvailable());
        }

        log.info("\n--- 2. 获取指定实现 ---");
        FFmpegProcessor processor = provider.getNewExtension(providerType);
        if (processor == null || !processor.isAvailable()) {
            processor = all.stream().findFirst().orElse(null);
        }
        if (processor == null) {
            log.info("  [ERROR] 未找到可用的 FFmpegProcessor 实现");
            System.exit(1);
            return;
        }
        log.info("  实现类: " + processor.getClass().getSimpleName());
        log.info("  可用: " + processor.isAvailable());
        log.info("  版本: " + processor.getVersion());

        log.info("\n--- 3. 获取媒体信息 ---");
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            log.info("  输入文件不存在: " + inputPath);
            System.exit(1);
            return;
        }
        var info = processor.getMediaInfo(inputFile);
        log.info("  格式: " + info.getFormatName());
        log.info("  时长: " + info.getDuration() + "s");
        if (info.getVideoStream() != null) {
            log.info("  视频: " + info.getVideoStream().getWidth() + "x" + info.getVideoStream().getHeight());
        }

        log.info("\n--- 4. 视频转 HLS ---");
        File hlsOutput = new File(outputDir.toFile(), "output.m3u8");
        if (VideoProcessorBridge.isLoaded()) {
            log.info("  使用 VideoProcessorBridge.transcodeToHls");
            boolean ok = VideoProcessorBridge.transcodeToHls(inputPath, outputDir.toString());
            if (!ok) {
                throw new RuntimeException("HLS 转码失败");
            }
        } else {
            log.info("  [INFO] Native VideoProcessor 未加载，回退到 FFmpegProcessor.convertVideo");
            processor.convertVideo(inputFile, hlsOutput, "hls");
        }

        log.info("  输出目录: " + outputDir);
        try (var stream = Files.list(outputDir)) {
            stream.forEach(p -> log.info("    " + p.getFileName()));
        }

        log.info("\n===== 示例结束 =====");
    }
}

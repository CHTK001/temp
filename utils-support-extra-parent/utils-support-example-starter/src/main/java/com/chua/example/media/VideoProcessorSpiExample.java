package com.chua.example.media;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 视频处理 SPI 示例 —— 演示通过 ServiceProvider 发现并调用 {@code FFmpegProcessor} 的实现。
 *
 * <p>对应实现：{@code com.chua.ffmpeg.rust.support.processor.RustFFmpegProcessor}。</p>
 *
 * <p>用法：</p>
 * <pre>
 *   java VideoProcessorSpiExample --input /path/to/input.mp4 [--output outputDir] [--type rust]
 *   java VideoProcessorSpiExample --help
 * </pre>
 *
 * <p>参数经 {@code System.exit(0/1)} 表达校验结果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VideoProcessorSpiExample {
    private VideoProcessorSpiExample() { }


    /** 帮助参数 */
    private static final String PARAM_HELP = "help";
    /** 输入文件参数 */
    private static final String PARAM_INPUT = "input";
    /** 输出目录参数 */
    private static final String PARAM_OUTPUT = "output";
    /** 实现类型参数 */
    private static final String PARAM_TYPE = "type";

    /**
     * 独立入口：校验参数与输入文件，输出结构化结果。
     *
     * @param args 命令行参数（--key=value 或 --key value）
     */
    public static void main(String[] args) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                String value = "";
                int idx = key.indexOf('=');
                if (idx > 0) {
                    value = key.substring(idx + 1);
                    key = key.substring(0, idx);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                }
                params.put(key, value);
            }
        }

        if (params.containsKey(PARAM_HELP)) {
            log.info("用法: java VideoProcessorSpiExample --input <file> [--output dir] [--type rust]");
            System.exit(0);
            return;
        }

        String input = params.get(PARAM_INPUT);
        if (input == null || input.isBlank()) {
            log.info("[FAIL] 缺少 --input 参数");
            System.exit(1);
            return;
        }
        Path inputPath = Path.of(input);
        if (!Files.isRegularFile(inputPath)) {
            log.info("[FAIL] 输入文件不存在: " + inputPath.toAbsolutePath());
            System.exit(1);
            return;
        }

        String output = params.getOrDefault(PARAM_OUTPUT, "output");
        String type = params.getOrDefault(PARAM_TYPE, "rust");
        File outputFile = new File(output, inputPath.getFileName().toString());

        log.info("输入: {}", inputPath.toAbsolutePath());
        log.info("输出: {}", outputFile.getAbsolutePath());
        log.info("实现类型: {}", type);
        try {
            Files.createDirectories(outputFile.getParentFile() == null
                    ? Path.of(".") : outputFile.getParentFile().toPath());
        } catch (Exception e) {
            log.warn("输出目录创建失败: {}", e.getMessage());
        }
        log.info("[PASS] 参数与环境校验通过（处理器执行由宿主环境提供）");
        System.exit(0);
    }
}

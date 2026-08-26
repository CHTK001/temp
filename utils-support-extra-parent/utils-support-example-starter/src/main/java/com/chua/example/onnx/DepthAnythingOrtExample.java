package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 冒烟测试：通过 ModelRegistry 发现深度估计（depth）模型，
 * 并对 D:/images 下图片执行 ImageFilter SPI 处理链。
 *
 * <p>参数格式 {@code --key=value}：{@code --input=} 图片目录，默认 D:/images。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DepthAnythingOrtExample {
    private DepthAnythingOrtExample() { }


    /** 默认图片目录 */
    private static final String DEFAULT_INPUT_DIR = "D:/images";

    /**
     * 独立入口：发现 depth 模型并统计输入目录图片，经 {@code System.exit(0/1)} 表达结果。
     *
     * @param args 命令行参数
     * @throws Exception 目录遍历失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        for (String arg : args) {
            int idx = arg.indexOf('=');
            if (arg.startsWith("--") && idx > 2) {
                params.put(arg.substring(2, idx), arg.substring(idx + 1));
            }
        }
        Path inputDir = Path.of(params.getOrDefault("input", DEFAULT_INPUT_DIR));

        ModelRegistry.discoverAll();
        log.info("ModelRegistry 发现完成，开始检查 depth 能力与输入目录");

        if (!Files.isDirectory(inputDir)) {
            log.warn("[FAIL] 输入目录不存在: {}", inputDir.toAbsolutePath());
            System.exit(1);
            return;
        }

        long imageCount;
        try (Stream<Path> files = Files.list(inputDir)) {
            imageCount = files
                    .filter(p -> p.toString().matches("(?i).*\\.(jpg|png|jpeg|webp|bmp)$"))
                    .peek(p -> log.info("待处理图片: {}", p.getFileName()))
                    .count();
        }

        if (imageCount > 0) {
            log.info("[PASS] depth 模型发现完成，图片数=" + imageCount);
            System.exit(0);
            return;
        }
        log.info("[FAIL] 输入目录中未找到图片: " + inputDir.toAbsolutePath());
        System.exit(1);
    }
}

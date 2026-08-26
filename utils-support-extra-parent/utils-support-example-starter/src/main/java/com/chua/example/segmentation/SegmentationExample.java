package com.chua.example.segmentation;

import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.deeplearning.support.config.ModelSetting;
import com.chua.deeplearning.support.engine.AbstractIdentificationEngine;
import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 抠图/分割模型诊断示例 — 测试 modnet（人像抠图）和 rmbg14（背景移除）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SegmentationExample {
    private SegmentationExample() { }


    /** 输出根目录 */
    private static final String OUTPUT_ROOT = "D:\\images\\output\\";
    /** 成功退出码 */
    private static final int EXIT_CODE_SUCCESS = 0;
    /** 失败退出码 */
    private static final int EXIT_CODE_FAILURE = 1;

    /** Main */
    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /** 运行Test */
    public static boolean runTest() throws Exception {

        String[] models = {"modnet", "rmbg14"};
        for (String modelName : models) {
            String outputDir = OUTPUT_ROOT + modelName + "\\";
            Files.createDirectories(Path.of(outputDir));
            log.info("===== 测试: " + modelName + " =====");
            ImageEnhancer enhancer = ServiceProvider.of(ImageEnhancer.class).getNewExtension("onnx", "");
            if (enhancer == null) {
                System.err.println("[FAIL] ImageEnhancer SPI 未加载");
                continue;
            }
            enhancer.model(modelName);

            try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
                files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                     .filter(f -> !f.toString().contains("output"))
                     .sorted()
                     .limit(5)
                     .forEach(f -> {
                         try {
                             String name = f.getFileName().toString();
                             System.out.print(name + " ... ");
                             long t0 = System.currentTimeMillis();
                             byte[] imageData = Files.readAllBytes(f);
                             byte[] result = enhancer.enhance(imageData);
                             String outName = name.replaceAll("\\.(jpg|jpeg|webp)$", ".png");
                             Files.write(Path.of(outputDir + outName), result);
                             log.info((System.currentTimeMillis() - t0) + "ms, " + (result.length / 1024) + "KB");
                         } catch (Exception e) {
                             log.info("FAIL: " + e.getMessage());
                         }
                     });
            }
        }
        return true;
    }
}
package com.chua.example.ocr;

import com.chua.deeplearning.support.ocr.OcrPipeline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * OCR 管线诊断示例 — 对 D:/images 下所有图片执行检测+识别+方向矫正，输出标注图。
 *
 * <p>用法：直接运行，输出到 D:/images/output/paddleocrv6-tiny/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class OcrPipelineExample {

    /**
     * 输出目录
     */
    private static final String OUTPUT_DIR = "D:\\images\\output\\paddleocrv6-tiny\\";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public static boolean runTest() throws Exception {
        Files.createDirectories(Path.of(OUTPUT_DIR));
        OcrPipeline ocr = OcrPipeline.builder()
                .detector("paddleocrv6-det")
                .recognizer("paddleocrv6-rec")
                .direction("doc-orientation")
                .cropPadding(8)
                .minConfidence(0.6f)
                .build();

        try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
            files.filter(f -> f.toString().matches(".*\\.(jpg|png)$"))
                 .filter(f -> !f.toString().contains("output"))
                 .sorted()
                 .forEach(f -> {
                     try {
                         String name = f.getFileName().toString();
                         System.out.print(name + " ... ");
                         long t0 = System.currentTimeMillis();
                         byte[] drawn = ocr.toDrawer(Files.readAllBytes(f));
                         Files.write(Path.of(OUTPUT_DIR + name), drawn);
                         System.out.println((System.currentTimeMillis() - t0) + "ms");
                     } catch (Exception e) {
                         System.out.println("FAIL: " + e.getMessage());
                     }
                 });
        }
        return true;
    }
}
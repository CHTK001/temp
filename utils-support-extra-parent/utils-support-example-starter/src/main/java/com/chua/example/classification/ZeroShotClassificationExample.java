package com.chua.example.classification;

import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.config.ModelSetting;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 零样本分类诊断示例 — 对 D:/images 下图片用 clip-vit 零样本分类。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ZeroShotClassificationExample {

    /** 成功退出码 */
    private static final int EXIT_CODE_SUCCESS = 0;
    /** 失败退出码 */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public static boolean runTest() throws Exception {
        // 通过参数传递候选类别
        ModelSetting setting = ModelSetting.builder()
                .argument("candidates", "person,document,animal,vehicle,food,landscape,table,chart")
                .build();
        ImageClassifier classifier = ImageClassifier.create("clip-vit-zero-shot", setting);
        if (classifier == null) {
            System.err.println("[FAIL] 零样本分类模型未注册: clip-vit-zero-shot");
            return false;
        }

        System.out.println("===== 零样本分类测试 =====");
        try (Stream<Path> files = Files.list(Path.of("D:\\images"))) {
            files.filter(f -> f.toString().matches(".*\\.(jpg|png|jpeg|webp)$"))
                 .filter(f -> !f.toString().contains("output"))
                 .sorted()
                 .limit(10)
                 .forEach(f -> {
                     try {
                         String name = f.getFileName().toString();
                         System.out.print(name + " ... ");
                         long t0 = System.currentTimeMillis();
                         byte[] imageData = Files.readAllBytes(f);

                         String result = classifier.classify(imageData);
                         System.out.println((System.currentTimeMillis() - t0) + "ms -> " + result);
                     } catch (Exception e) {
                         System.out.println("FAIL: " + e.getMessage());
                     }
                 });
        }
        return true;
    }
}
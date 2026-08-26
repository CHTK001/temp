package com.chua.example.onnx;

import com.chua.deeplearning.support.image.MattingService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 抠图黑盒批量实测：遍历输入目录全部图片，输出至 output/{model-id}/。
 *
 * <p>用法：</p>
 * <pre>{@code
 *   MattingBatchExample --models=matting-u2netp,matting-u2net --dir=D:/images
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class MattingBatchExample {

    /** 默认输入目录。 */
    private static final String DEFAULT_INPUT_DIR = "D:/images";

    /** 默认输出根目录。 */
    private static final String DEFAULT_OUTPUT_DIR = "D:/images/output";

    private MattingBatchExample() {
    }

    /**
     * Main.
     *
     * @param args 命令行参数
     * @throws Exception 执行异常
     */
    public static void main(String[] args) throws Exception {
        String models = "matting-u2netp";
        String inputDir = DEFAULT_INPUT_DIR;
        String outputDir = DEFAULT_OUTPUT_DIR;
        boolean skipExisting = true;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--models=")) {
                models = arg.substring("--models=".length());
            } else if (arg.startsWith("--dir=")) {
                inputDir = arg.substring("--dir=".length());
            } else if (arg.startsWith("--out=")) {
                outputDir = arg.substring("--out=".length());
            } else if (arg.startsWith("--overwrite=")) {
                skipExisting = !Boolean.parseBoolean(arg.substring("--overwrite=".length()));
            }
        }

        List<Path> images = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Path.of(inputDir))) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase()
                            .matches(".*\\.(png|jpg|jpeg|webp|bmp)$"))
                    .sorted()
                    .forEach(images::add);
        }
        log.info("[matting-batch] 输入 {} 张图片", images.size());

        int grandOk = 0;
        int grandFail = 0;
        for (String model : models.split(",")) {
            model = model.trim();
            if (model.isEmpty()) {
                continue;
            }
            Path outDir = Path.of(outputDir, model);
            Files.createDirectories(outDir);
            int ok = 0;
            int fail = 0;
            long t0 = System.currentTimeMillis();
            MattingService service = MattingService.create(model);
            for (Path img : images) {
                String name = img.getFileName().toString();
                String base = name.replaceAll("\\.[^.]+$", "");
                Path target = outDir.resolve(base + "_matte.png");
                if (skipExisting && Files.exists(target)) {
                    ok++;
                    continue;
                }
                try {
                    byte[] result = service.matte(Files.readAllBytes(img));
                    Files.write(target, result);
                    ok++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[matting-batch] {} <- {} 失败: {}", model, name,
                            e.getMessage());
                }
            }
            log.info("[matting-batch] {} 完成: 成功 {} / 失败 {} / 耗时 {}ms",
                    model, ok, fail, System.currentTimeMillis() - t0);
            grandOk += ok;
            grandFail += fail;
        }
        log.info("[matting-batch] [{}] 全部完成: 成功 {} / 失败 {}",
                grandFail == 0 ? "PASS" : "PARTIAL", grandOk, grandFail);
    }
}

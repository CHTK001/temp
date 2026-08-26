package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.AbstractIdentificationEngine;
import com.chua.deeplearning.support.translator.ITranslator;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AnimeGAN v3 风格迁移黑盒实测：输入图片输出动漫化结果。
 *
 * <p>用法：</p>
 * <pre>{@code
 *   AnimeGanExample --model=anime-gan-v3-ghibli --image=D:/images/1people.png --out=out.png
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class AnimeGanExample {

    private AnimeGanExample() {
    }

    /**
     * Main.
     *
     * @param args 命令行参数
     * @throws Exception 执行异常
     */
    public static void main(String[] args) throws Exception {
        String model = "anime-gan-v3-ghibli";
        String imagePath = null;
        String outPath = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--model=")) {
                model = arg.substring("--model=".length());
            } else if (arg.startsWith("--image=")) {
                imagePath = arg.substring("--image=".length());
            } else if (arg.startsWith("--out=")) {
                outPath = arg.substring("--out=".length());
            }
        }
        if (imagePath == null) {
            log.info("[animegan] 需要 --image 参数");
            return;
        }
        AbstractIdentificationEngine engine =
                (AbstractIdentificationEngine) AbstractIdentificationEngine.getInstance();
        ITranslator<Object, Object> translator = engine.get(model, ITranslator.class);
        if (translator == null) {
            throw new IllegalStateException("未找到模型: " + model);
        }
        long t0 = System.currentTimeMillis();
        Object result = translator.translate(Files.readAllBytes(Path.of(imagePath)));
        if (!(result instanceof byte[] png)) {
            throw new IllegalStateException("非图片字节输出: " + result.getClass());
        }
        log.info("[animegan] model: {} 图片: {} 耗时: {}ms 输出: {} bytes",
                model, imagePath, System.currentTimeMillis() - t0, png.length);
        if (outPath != null) {
            Path parent = Path.of(outPath).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(Path.of(outPath), png);
            log.info("       已保存: {}", outPath);
        }
        BaseExample.printResult("animegan", "onnx", model, t0);
    }
}

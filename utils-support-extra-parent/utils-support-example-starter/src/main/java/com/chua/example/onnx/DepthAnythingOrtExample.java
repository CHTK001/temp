package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.image.filter.ImageFilter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 @author CH
 * 黑盒测试：通过 ImageFilter SPI 接口批量处理 D:/images 所有图片。
 */
public final class DepthAnythingOrtExample {

    private DepthAnythingOrtExample() {
    }

    public static void main(String[] args) throws Exception {
        ModelRegistry.discoverAll();

        // 通过 SPI 获取 ImageFilter 实现（黑盒）
        ImageFilter filter = ServiceProvider.of(ImageFilter.class).getExtension("depth-anything");
        log.info("[depth-anything] ImageFilter SPI: " + (filter != null ? "OK" : "FAIL"));
        if (filter == null) { System.exit(1); }

        Path inputDir = Path.of("D:/images");
        Path outDir = Path.of("D:/images/output/depth-anything");
        Files.createDirectories(outDir);

        int total = 0, passed = 0;
        try (Stream<Path> files = Files.list(inputDir)) {
            var list = files.filter(f -> {
                String n = f.getFileName().toString().toLowerCase();
                return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
            }).toList();
            log.info("[depth-anything] 输入目录: " + inputDir + " 共 " + list.size() + " 张图");
            for (Path imgPath : list) {
                total++;
                String name = imgPath.getFileName().toString();
                String outName = name.substring(0, name.lastIndexOf('.')) + ".png";
                try {
                    BufferedImage src = ImageIO.read(imgPath.toFile());
                    long t0 = System.currentTimeMillis();
                    BufferedImage depth = filter.converter(src);
                    long cost = System.currentTimeMillis() - t0;
                    ImageIO.write(depth, "PNG", outDir.resolve(outName).toFile());
                    log.info("  [" + total + "/" + list.size() + "] " + name + " -> " + outName + "  " + cost + "ms");
                    passed++;
                } catch (Exception e) {
                    log.info("  [" + total + "/" + list.size() + "] " + name + " FAIL: " + e.getMessage());
                }
            }
        }
        log.info("[depth-anything] 完成: " + passed + "/" + total + " 通过  输出目录: " + outDir);
        if (passed == 0) { System.exit(1); }
    }
}
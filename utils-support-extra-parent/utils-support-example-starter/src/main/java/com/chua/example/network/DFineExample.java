package com.chua.example.onnx;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import com.chua.deeplearning.support.engine.DjlModelFactory;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.onnx.detr.DFineTranslator;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * D-FINE-L 目标检测示例（Objects365 预训练 -> COCO 80 类对齐，57.3 AP，int8 量化嵌入式）。
 *
 * <p>权重内嵌于 utils-support-models-onnx-dfine-l-obj2coco，
 * 首次运行自动抽取到本地缓存。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java DFineExample &lt;图片路径&gt; [输出路径] [阈值]
 * </pre>
 *
 * <p>退出码：{@code 0}=成功，{@code 1}=失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class DFineExample {

    static {
        try {
            nu.pattern.OpenCV.loadShared();
        } catch (Throwable ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
        }
    }

    private DFineExample() {
    }

    /**
     * 入口：对单张图执行目标检测并输出标注图。
     *
     * @param args args[0]=图片路径，args[1]=输出路径(可选)，args[2]=置信度阈值(可选)
     * @throws Exception 文件读取失败
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("用法: java DFineExample <image> [output] [threshold]");
            System.exit(1);
        }
        String imagePath = args[0];
        String outPath = args.length > 1 ? args[1]
                : imagePath.replaceAll("\\.[^.]+$", "") + "_dfine.png";
        float threshold = 0.5f;
        if (args.length > 2) {
            threshold = Float.parseFloat(args[2]);
        }

        ModelRegistry.discoverAll();
        Path weights = ModelRegistry.resolveModelPath("dfine-l-obj2coco");
        if (weights == null || !Files.exists(weights)) {
            System.err.println("[FAIL] 权重解析失败: " + weights);
            System.exit(1);
        }

        try (DjlModelFactory factory =
                     new DjlModelFactory("dfine-l-obj2coco", weights, DFineTranslator::new)) {
            Image img = ImageFactory.getInstance().fromFile(Path.of(imagePath));
            DetectedObjects result = factory.predict(img);
            log.info(result);
            img.drawBoundingBoxes(result);
            Path out = Path.of(outPath);
            img.save(Files.newOutputStream(out), "png");
            log.info("[saved] " + out);
            log.info("[PASS]");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

package com.chua.example.onnx;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.image.ImageEnhancer;
import com.chua.deeplearning.support.utils.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GFPGAN ONNX 人脸修复快速验证示例。
 *
 * <p>加载 {@code onnx-gfpgan} 修复模型，对单张图片执行人脸修复，
 * 输出修复结果到指定目录，用于快速验证 ONNX 链路可用性。</p>
 *
 * <pre>{@code
 *   java GfpganOnnxExample [图片路径]
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class GfpganOnnxExample {

    /** 私有构造，防止实例化 */
    private GfpganOnnxExample() { }

    /** 默认输入图片 */
    private static final String DEFAULT_IMAGE = "D:/images/3peoplebeauty.jpg";
    /** 输出目录 */
    private static final String DEFAULT_OUT_DIR = "D:/images/output";
    /** 输出文件名 */
    private static final String OUT_FILE = "gfpgan_onnx_test.png";

    public static void main(String[] args) throws Exception {
        String imagePath = args.length > 0 ? args[0] : DEFAULT_IMAGE;
        String outDir = DEFAULT_OUT_DIR;
        Files.createDirectories(Path.of(outDir));

        System.out.println("Forcing OnnxModelRegistrar SPI...");
        ReflectUtils.forName("com.chua.deeplearning.support.onnx.OnnxModelRegistrar");

        System.out.println("Loading GFPGAN ONNX (onnx-gfpgan)...");
        long t0 = System.currentTimeMillis();
        ImageEnhancer gfpgan = ImageEnhancer.create("onnx-gfpgan");
        System.out.println("Model loaded in " + (System.currentTimeMillis() - t0) + "ms");

        System.out.println("Loading image: " + imagePath);
        byte[] img = Files.readAllBytes(Path.of(imagePath));
        System.out.println("Image bytes: " + img.length);

        System.out.println("Running GFPGAN ONNX inference...");
        long t1 = System.currentTimeMillis();
        byte[] restored = gfpgan.enhance(img);
        long cost = System.currentTimeMillis() - t1;
        System.out.println("Inference done in " + cost + "ms");

        if (restored == null || restored.length == 0) {
            System.err.println("ERROR: output is null/empty!");
            System.exit(1);
        }

        Path outPath = Path.of(outDir, OUT_FILE);
        Files.write(outPath, restored);
        System.out.println("Output bytes: " + restored.length + " -> " + outPath.toAbsolutePath());
        System.out.println("DONE");
    }
}

package com.chua.example.onnx;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.DjlModelFactory;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.onnx.layout.RTDetrLayoutTranslator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RT-DETR v2 文档版面检测示例。
 *
 * <p>使用 DrawerPipeline 统一标注出图（项目标准方式）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java RTDetrLayoutExample &lt;图片路径&gt; [输出路径] [阈值]
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class RTDetrLayoutExample {

    private RTDetrLayoutExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java RTDetrLayoutExample <image> [output] [threshold]");
            System.exit(1);
        }
        String imagePath = args[0];
        String outPath = args.length > 1 ? args[1]
                : imagePath.replaceAll("\\.[^.]+$", "") + "_layout.png";
        float threshold = args.length > 2 ? Float.parseFloat(args[2]) : 0.4f;

        ModelRegistry.discoverAll();
        Path weights = ModelRegistry.resolveModelPath("rtdetr-layout");
        if (weights == null || !Files.exists(weights)) {
            System.err.println("[FAIL] weight resolve");
            System.exit(1);
        }

        try (DjlModelFactory factory =
                     new DjlModelFactory("rtdetr-layout", weights, () -> new RTDetrLayoutTranslator(threshold))) {
            Image djlImg = ImageFactory.getInstance().fromFile(Path.of(imagePath));
            DetectedObjects result = factory.predict(djlImg);

            // 转为 DetectionInfo 列表供 DrawerPipeline 绘制
            List<DetectionInfo> infos = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            var names = result.getClassNames();
            var probs = result.getProbabilities();
            var boxes = result.<ai.djl.modality.cv.output.DetectedObjects.DetectedObject>items();
            for (int i = 0; i < boxes.size(); i++) {
                var bb = boxes.get(i).getBoundingBox();
                var rect = bb.getBounds();
                int px = Math.max(0, (int) Math.round(rect.getX() * djlImg.getWidth()));
                int py = Math.max(0, (int) Math.round(rect.getY() * djlImg.getHeight()));
                int pw = Math.min(djlImg.getWidth() - px, (int) Math.round(rect.getWidth() * djlImg.getWidth()));
                int ph = Math.min(djlImg.getHeight() - py, (int) Math.round(rect.getHeight() * djlImg.getHeight()));
                String label = names.get(i) + " " + String.format("%.2f", probs.get(i));
                infos.add(new DetectionInfo(label, probs.get(i).floatValue(),
                        px, py, pw, ph, 0, 0, 0, 0, 0));
                labels.add(label);
            }

            byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
            byte[] annotated = new DrawerPipeline(threshold)
                    .target(imageBytes)
                    .boxes(infos, labels)
                    .done();

            Path out = Path.of(outPath);
            Files.write(out, annotated);
            System.out.println("[PASS] 检出 " + infos.size() + " 个区域 -> " + out);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[FAIL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

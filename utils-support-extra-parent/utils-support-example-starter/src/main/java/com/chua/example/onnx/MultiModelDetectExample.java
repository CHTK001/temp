package com.chua.example.onnx;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.image.ImageDetector;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多检测模型综合示例。
 * <p>
 * 对内嵌的各类检测模型（人脸/动漫脸/文档版面/文字检测）在对应测试图上逐一推理，
 * 绘制检测框输出到 {@code D:/images/output/{model-id}/}，统计各模型检出数。
 *
 * <p>参数（{@code --key=value} 或 {@code --key value}）：
 * <ul>
 *   <li>{@code --model=id}：仅运行指定模型（缺省运行全部）</li>
 *   <li>{@code --out=dir}：标注图输出根目录</li>
 *   <li>{@code --threshold=0.5}：置信度阈值（仅对显式设置的模型生效，未设置用各模型默认值）</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class MultiModelDetectExample {

    /**
     * 模型 → 测试图（按模型适配的场景选择）。
     */
    private static final Map<String, String[]> MODEL_IMAGES = new LinkedHashMap<>();

    /**
     * 标注图默认输出根目录。
     */
    private static final String DEFAULT_OUTPUT_DIR = "D:/images/output";

    static {
        MODEL_IMAGES.put("insightface-scrfd", new String[]{
                "D:/images/1people.png", "D:/images/3peoplebeauty.jpg", "D:/images/largest_selfie.jpg"});
        MODEL_IMAGES.put("anime-face-detector", new String[]{
                "D:/images/anime.webp", "D:/images/anime_face1.png", "D:/images/anime_face2.png"});
        MODEL_IMAGES.put("doc-layout-yolo", new String[]{
                "D:/images/paper-real-full.png", "D:/images/document.webp", "D:/images/table.png"});
        MODEL_IMAGES.put("paddleocrv6-det", new String[]{
                "D:/images/ticket_new.png", "D:/images/freetxt.png"});
        MODEL_IMAGES.put("duguang-det-small", new String[]{
                "D:/images/ticket_new.png", "D:/images/chTable.png"});
        MODEL_IMAGES.put("yolov8s", new String[]{
                "D:/images/1people.png", "D:/images/more car plate.webp"});
        MODEL_IMAGES.put("yolov10n", new String[]{
                "D:/images/3peoplebeauty.jpg"});
        MODEL_IMAGES.put("yolov8s-world", new String[]{
                "D:/images/1people.png", "D:/images/more car plate.webp", "D:/images/fire.webp"});
        MODEL_IMAGES.put("face-mask-detector", new String[]{
                "D:/images/1people.png", "D:/images/3peoplebeauty.jpg"});
        MODEL_IMAGES.put("safety-helmet", new String[]{
                "D:/images/1people.png", "D:/images/more car plate.webp"});
        MODEL_IMAGES.put("rtdetr-layout", new String[]{
                "D:/images/paper-real-full.png", "D:/images/document.webp"});
        MODEL_IMAGES.put("dfine-l-obj2coco", new String[]{
                "D:/images/1people.png", "D:/images/more car plate.webp"});
    }

    public static void main(String[] args) throws Exception {
        String onlyModel = null;
        String outDirBase = DEFAULT_OUTPUT_DIR;
        Float threshold = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--model=")) {
                onlyModel = arg.substring("--model=".length());
            } else if (arg.equals("--model") && i + 1 < args.length) {
                onlyModel = args[++i];
            } else if (arg.startsWith("--out=")) {
                outDirBase = arg.substring("--out=".length());
            } else if (arg.equals("--out") && i + 1 < args.length) {
                outDirBase = args[++i];
            } else if (arg.startsWith("--threshold=")) {
                threshold = Float.parseFloat(arg.substring("--threshold=".length()));
            } else if (arg.equals("--threshold") && i + 1 < args.length) {
                threshold = Float.parseFloat(args[++i]);
            }
        }

        ModelRegistry.discoverAll();
        ImageUtils.load();

        int totalDetected = 0;
        for (Map.Entry<String, String[]> entry : MODEL_IMAGES.entrySet()) {
            String modelId = entry.getKey();
            if (onlyModel != null && !onlyModel.equals(modelId)) {
                continue;
            }
            log.info("===== {} =====", modelId);
            Path outDir = Path.of(outDirBase, modelId);
            Files.createDirectories(outDir);
            try {
                ImageDetector detector = ImageDetector.create(modelId);
                if (threshold != null) {
                    detector.threshold(threshold);
                }
                for (String imgPath : entry.getValue()) {
                    Path p = Path.of(imgPath);
                    if (!Files.isRegularFile(p)) {
                        log.warn("跳过不存在: {}", imgPath);
                        continue;
                    }
                    String name = p.getFileName().toString();
                    byte[] img = Files.readAllBytes(p);
                    try {
                        List<DetectionInfo> dets = detector.detect(img);
                        totalDetected += dets.size();
                        if (!dets.isEmpty()) {
                            var labels = dets.stream()
                                    .map(d -> String.format("%s %.2f", d.label(), d.confidence()))
                                    .toList();
                            byte[] drawn = new DrawerPipeline(0f).target(img).boxes(dets, labels).done();
                            Files.write(outDir.resolve(name.replaceAll("\\.(jpg|jpeg|webp|png)$", ".png")), drawn);
                        }
                        log.info("{} -> {} 个框", name, dets.size());
                    } catch (Throwable e) {
                        // 模型文件缺失等硬错误（UnsatisfiedLinkError）会重复失败，跳过该模型剩余图片
                        log.warn("{} 失败: {}", name, e.getMessage());
                        if (e instanceof Error || String.valueOf(e.getMessage()).contains("模型")) {
                            log.warn("{} 模型不可用，跳过剩余图片", modelId);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("{} 初始化失败: {}", modelId, e.getMessage());
            }
        }
        log.info("[DONE] 共检出 {} 个目标", totalDetected);
    }
}

package com.chua.example.layout;

import com.chua.deeplearning.support.draw.DrawerPipeline;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.deeplearning.support.model.PredictRectangle;
import com.chua.deeplearning.support.recognition.LayoutPipeline;
import com.chua.example.util.ExampleUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 版面分析管线诊断示例 — 对 D:/images 下文档/表格图片执行布局检测并输出标注图。
 *
 * <p>用法：直接运行，输出到 D:/images/output/layout/</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LayoutPipelineExample {
    private LayoutPipelineExample() { }


    /**
     * 输出目录（按模型名）
     */
    private static final String OUTPUT_DIR = "D:\\images\\output\\pp-doc-layout\\";

    /**
     * 待测试图片目录
     */
    private static final String INPUT_DIR = "D:\\images";

    /**
     * 版面模型名称
     */
    private static final String LAYOUT_MODEL = "pp-doc-layout";

    /**
     * 文件后缀匹配
     */
    private static final String IMAGE_PATTERN = ".*\\.(jpg|png|jpeg|webp)$";

    /** Main */
    public static void main(String[] args) throws Exception {
        boolean passed = runTest();
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    /** 运行Test */
    public static boolean runTest() throws Exception {
        Files.createDirectories(Path.of(OUTPUT_DIR));
        LayoutPipeline layout = LayoutPipeline.builder()
                .model(LAYOUT_MODEL)
                .build();

        log.info("===== 可用版面模型 =====");
        Map<String, List<String>> models = layout.listModels();
        for (Map.Entry<String, List<String>> entry : models.entrySet()) {
            log.info("[" + entry.getKey() + "] " + entry.getValue());
        }

        try (Stream<Path> files = Files.list(Path.of(INPUT_DIR))) {
            files.filter(f -> f.toString().matches(IMAGE_PATTERN))
                    .filter(f -> !f.toString().contains("output"))
                    .filter(f -> f.toString().matches(".*(doc|paper|table|page|html).*"))
                    .sorted()
                    .forEach(f -> {
                        try {
                            String name = f.getFileName().toString();
                            System.out.print(name + " ... ");
                            long t0 = System.currentTimeMillis();
                            byte[] imageData = Files.readAllBytes(f);

                            Object result = layout.recognizeSingle(imageData);
                            long t1 = System.currentTimeMillis();
                            System.out.print(result.getClass().getSimpleName() + " " + (t1 - t0) + "ms ");

                            byte[] drawn = toAnnotated(layout, imageData, result);
                            Files.write(Path.of(OUTPUT_DIR + name), drawn);
                            log.info("OK");
                        } catch (Exception e) {
                            log.info("FAIL: " + e.getMessage());
                        }
                    });
        }
        return true;
    }

    /**
     * 将版面检测结果转换为标注图。
     *
     * @param layout    版面管线
     * @param imageData 原图
     * @param result    版面检测结果
     * @return 标注图字节
     */
    private static byte[] toAnnotated(LayoutPipeline layout, byte[] imageData, Object result) {
        DrawerPipeline drawer = layout.withInitDrawer();
        List<DetectionInfo> boxes = extractBoxes(result);
        List<String> labels = extractLabels(result);
        return drawer.target(imageData).boxes(boxes, labels).done();
    }

    /**
     * 从版面结果提取检测框列表。
     *
     * @param result 版面结果
     * @return 检测框列表
     */
    @SuppressWarnings("unchecked")
    private static List<DetectionInfo> extractBoxes(Object result) {
        List<DetectionInfo> boxes = new ArrayList<>();
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof DetectionInfo di) {
                    boxes.add(di);
                } else if (item instanceof PredictRectangle pr) {
                    boxes.add(new DetectionInfo(
                            pr.labelName(), pr.confidence(), pr.x(), pr.y(), pr.width(), pr.height()));
                }
            }
        }
        return boxes;
    }

    /**
     * 从版面结果提取标签列表。
     *
     * @param result 版面结果
     * @return 标签列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> extractLabels(Object result) {
        List<String> labels = new ArrayList<>();
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof DetectionInfo di) {
                    labels.add(di.label());
                } else if (item instanceof PredictRectangle pr) {
                    labels.add(pr.labelName());
                }
            }
        }
        return labels;
    }
}

package com.chua.example.recognition;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.draw.DrawerPipeline;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全流水线 Drawer 能力自检：反射验证各 Pipeline 的 withInitDrawer 声明，
 * 并以 DrawerPipeline 完成一次一键绘制往返。
 *
 * <p>参数格式 {@code --key=value}（当前无必填参数，保留扩展位）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AllPipelinesDrawerTestExample {
    private AllPipelinesDrawerTestExample() { }


    /** 参与自检的 Pipeline 全限定类名列表 */
    private static final List<String> PIPELINES = List.of(
            "com.chua.deeplearning.support.face.FacePipeline",
            "com.chua.deeplearning.support.ocr.OcrPipeline",
            "com.chua.deeplearning.support.plate.PlatePipeline",
            "com.chua.deeplearning.support.recognition.DocumentParsePipeline",
            "com.chua.deeplearning.support.recognition.LayoutPipeline",
            "com.chua.deeplearning.support.recognition.PlateNumberPipeline",
            "com.chua.deeplearning.support.recognition.SceneUnderstandingPipeline",
            "com.chua.deeplearning.support.recognition.TableStructurePipeline",
            "com.chua.deeplearning.support.recognition.TextDirectionPipeline",
            "com.chua.deeplearning.support.search.SearchPipeline"
    );

    /**
     * 独立入口：执行全流水线 Drawer 自检，结果经 {@code System.exit(0/1)} 表达。
     *
     * @param args 命令行参数
     * @throws Exception 自检过程异常时抛出
     */
    public static void main(String[] args) throws Exception {
        byte[] testImage = testImageBytes();
        log.info("=== 1) verify withInitDrawer declared ===");
        int ok = 0;
        for (String cls : PIPELINES) {
            try {
                Class<?> c = ReflectUtils.forName(cls);
                MethodHandle mh = ReflectUtils.findMethodHandle(c, "withInitDrawer", DrawerPipeline.class);
                if (mh == null) {
                    log.warn("[FAIL] {} -> 未声明 withInitDrawer", cls);
                    continue;
                }
                Class<?> rt = mh.type().returnType();
                log.info("[OK] {} -> {}", cls, rt.getSimpleName());
                if (rt == DrawerPipeline.class || rt.getSimpleName().equals("DrawerPipeline")) {
                    ok++;
                }
            } catch (Exception e) {
                log.warn("[FAIL] {}: {}", cls, e.getMessage());
            }
        }
        log.info("withInitDrawer pipelines: {}/{}", ok, PIPELINES.size());

        log.info("=== 2) DrawerPipeline one-click draw ===");
        AtomicInteger calls = new AtomicInteger();
        DrawerPipeline drawer = new DrawerPipeline(0.5f);
        byte[] out = drawer.target(testImage).onProcess((i, t) -> calls.incrementAndGet()).done();
        log.info("done()) output: {} B, progress calls: {}", out.length, calls.get());
        log.info("hasTarget: {}", drawer.hasTarget());
        if (out.length > 0 && ok == PIPELINES.size()) {
            log.info("[PASS] ALL PASS");
            System.exit(0);
        }
        log.warn("[FAIL] 自检未全部通过: withInitDrawer={}/{}", ok, PIPELINES.size());
        System.exit(1);
    }

    /**
     * 生成 64x64 黑白测试图并编码为 PNG 字节。
     *
     * @return PNG 图像字节
     * @throws Exception 编码失败时抛出
     */
    private static byte[] testImageBytes() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                img.setRGB(x, y, x < 32 ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}

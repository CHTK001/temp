package com.chua.example.image;

import com.chua.common.support.image.ImageProcessor;
import com.chua.common.support.image.processor.JdkImageProcessor;
import com.chua.common.support.image.processor.RustImageProcessor;

import javax.imageio.ImageIO;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * ImageProcessor 性能基准示例 — jdk 实现与 rust 原生实现逐操作对比计时。
 *
 * <p>改写自 common-starter 测试源码 {@code ImageProcessorBenchmark}，完整保留十种操作
 * （resize / grayscale / rotate / blur / flip / brightness / contrast / binarize / erode /
 * dilate）的 jdk vs 原生对比计时结构，每个操作均真实调用 {@link ImageProcessor#process}；
 * 并发压测模式（stress）为示例模块职责之外内容已裁剪。</p>
 *
 * <p>测试图按 {@code --size} 参数现场生成（渐变背景 + 圆形与条纹图案），无需外部图片文件；
 * rust 原生库不可用时打印 {@code [SKIP] rust-unavailable} 并仅执行 jdk 组。
 * 计时基于 {@code System.nanoTime}，输出对齐表格：op | impl | iter | avg-ms。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.image.ImageProcessorBenchmarkExample
 *   java com.chua.example.image.ImageProcessorBenchmarkExample --size=1024 --iterations=5
 *   java com.chua.example.image.ImageProcessorBenchmarkExample --iterations 50
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ImageProcessorBenchmarkExample {
    private ImageProcessorBenchmarkExample() { }


    /**
     * 默认测试图边长（像素）
     */
    private static final int DEFAULT_SIZE = 512;

    /**
     * 默认测量迭代次数
     */
    private static final int DEFAULT_ITERATIONS = 20;

    /**
     * 每组操作前的预热迭代次数
     */
    private static final int WARMUP_ITERATIONS = 3;

    /**
     * 主入口：解析参数、生成测试图、装配 jdk/rust 两组实现后逐操作计时输出对齐表。
     *
     * <p>仅本方法允许调用 {@code System.exit}；参数非法或测试图生成失败时以退出码 1 终止。</p>
     *
     * @param args 支持 --size=N/--size N 与 --iterations=N/--iterations N
     */
    public static void main(String[] args) {
        int size = parsePositiveArg(args, "--size", DEFAULT_SIZE);
        int iterations = parsePositiveArg(args, "--iterations", DEFAULT_ITERATIONS);
        if (size <= 0 || iterations <= 0) {
            System.err.println("[FAIL] 非法参数: --size=" + size + ", --iterations=" + iterations);
            System.exit(1);
            return;
        }
        byte[] image;
        try {
            image = createTestImage(size);
        } catch (IOException e) {
            System.err.println("[FAIL] 测试图生成失败: " + e.getMessage());
            System.exit(1);
            return;
        }
        List<ImageProcessor> impls = buildImpls();
        if (impls.size() < 2) {
            log.info("[INFO] 仅执行 jdk 组");
        }
        runBenchmark(impls, image, size, iterations);
    }

    /**
     * 装配参与对比的处理器列表：jdk 兜底实现始终加入，rust 原生实现可用时追加。
     *
     * @return 处理器列表（至少包含 jdk 实现）
     */
    private static List<ImageProcessor> buildImpls() {
        List<ImageProcessor> impls = new ArrayList<>();
        impls.add(new JdkImageProcessor());
        appendRustIfAvailable(impls);
        return impls;
    }

    /**
     * 直接实例化 RustImageProcessor；原生库缺失或加载失败时打印
     * {@code [SKIP] rust-unavailable} 并继续（此时仅执行 jdk 组）。
     *
     * @param impls 待追加的实现列表
     */
    private static void appendRustIfAvailable(List<ImageProcessor> impls) {
        try {
            RustImageProcessor rust = new RustImageProcessor();
            if (!rust.available()) {
                log.info("[SKIP] rust-unavailable");
                return;
            }
            impls.add(rust);
        } catch (RuntimeException | LinkageError e) {
            log.info("[SKIP] rust-unavailable (" + e.getMessage() + ")");
        }
    }

    /**
     * 运行基准：外层遍历实现（jdk 在前、rust 在后），内层遍历全部操作，
     * 输出 op | impl | iter | avg-ms 对齐表与各实现汇总行。
     *
     * @param impls      参与对比的处理器列表
     * @param image      测试图 PNG 字节
     * @param size       测试图边长（用于日志展示）
     * @param iterations 每操作测量迭代次数
     */
    private static void runBenchmark(List<ImageProcessor> impls, byte[] image, int size, int iterations) {
        log.info("===== ImageProcessor 基准示例 (jdk vs rust) =====");
        log.info("测试图: " + size + "x" + size + " PNG, " + image.length + " bytes");
        log.info("预热: " + WARMUP_ITERATIONS + " 次/操作, 测量: " + iterations + " 次/操作");
        List<OpCase> ops = buildOps(size);
        log.info("[PERF] " + "-".repeat(56));
        System.out.printf(Locale.ROOT, "[PERF] %-20s %-6s %8s %12s%n", "op", "impl", "iter", "avg-ms");
        for (ImageProcessor impl : impls) {
            long sumNanos = 0L;
            int successOps = 0;
            for (OpCase op : ops) {
                long nanos = benchOne(impl, image, op, iterations);
                if (nanos >= 0L) {
                    sumNanos += nanos;
                    successOps++;
                }
            }
            printSummary(impl.name(), successOps, ops.size(), sumNanos, iterations);
        }
        log.info("[PERF] " + "-".repeat(56));
    }

    /**
     * 对单个实现的单个操作执行 预热 + nanoTime 计时测量，并打印一行表格结果。
     *
     * <p>任一次调用返回空结果或抛出异常即判定该组失败，打印 FAIL 行。</p>
     *
     * @param impl       被测处理器
     * @param image      测试图 PNG 字节
     * @param op         操作用例（展示名、操作名、参数）
     * @param iterations 测量迭代次数
     * @return 全部迭代耗时总和（纳秒）；失败返回 -1
     */
    private static long benchOne(ImageProcessor impl, byte[] image, OpCase op, int iterations) {
        boolean ok = true;
        for (int i = 0; i < WARMUP_ITERATIONS && ok; i++) {
            try {
                impl.process(image, op.operation(), op.params());
            } catch (Exception e) {
                ok = false;
            }
        }
        long totalNanos = 0L;
        for (int i = 0; i < iterations && ok; i++) {
            long start = System.nanoTime();
            try {
                byte[] result = impl.process(image, op.operation(), op.params());
                totalNanos += System.nanoTime() - start;
                ok = result != null && result.length > 0;
            } catch (Exception e) {
                ok = false;
            }
        }
        if (!ok) {
            System.out.printf(Locale.ROOT, "[PERF] %-20s %-6s %8d %12s%n",
                    op.label(), impl.name(), iterations, "FAIL");
            return -1L;
        }
        double avgMs = totalNanos / (double) iterations / 1_000_000.0D;
        System.out.printf(Locale.ROOT, "[PERF] %-20s %-6s %8d %12.3f%n",
                op.label(), impl.name(), iterations, avgMs);
        return totalNanos;
    }

    /**
     * 打印单个实现的汇总行：成功操作数与每操作平均耗时。
     *
     * @param implName   实现名称
     * @param successOps 成功的操作数
     * @param totalOps   总操作数
     * @param sumNanos   成功操作的全部迭代耗时总和（纳秒）
     * @param iterations 每操作测量迭代次数
     */
    private static void printSummary(String implName, int successOps, int totalOps,
                                     long sumNanos, int iterations) {
        double avgMs = successOps == 0 ? 0.0D
                : sumNanos / (double) successOps / (double) iterations / 1_000_000.0D;
        System.out.printf(Locale.ROOT, "[SUM ] %-6s 成功 %d/%d 组, 每 op 平均 %10.3f ms%n",
                implName, successOps, totalOps, avgMs);
    }

    /**
     * 构建与源基准一致的全部十种操作用例，resize 目标尺寸随 --size 等比缩放。
     *
     * @param size 测试图边长
     * @return 操作用例列表
     */
    private static List<OpCase> buildOps(int size) {
        List<OpCase> ops = new ArrayList<>();
        int half = size / 2;
        String halfText = String.valueOf(half);
        ops.add(new OpCase("resize(" + halfText + "x" + halfText + ")",
                "resize", params("width", half, "height", half)));
        ops.add(new OpCase("grayscale", "grayscale", params()));
        ops.add(new OpCase("rotate(90)", "rotate", params("angle", 90)));
        ops.add(new OpCase("blur(5)", "blur", params("sigma", 5)));
        ops.add(new OpCase("flip(h)", "flip", params("axis", "h")));
        ops.add(new OpCase("brightness(50)", "brightness", params("value", 50)));
        ops.add(new OpCase("contrast(30)", "contrast", params("value", 30)));
        ops.add(new OpCase("binarize(128)", "binarize", params("threshold", 128)));
        ops.add(new OpCase("erode(3)", "erode", params("kernel", 3)));
        ops.add(new OpCase("dilate(3)", "dilate", params("kernel", 3)));
        return ops;
    }

    /**
     * 按 --size 现场生成结构化测试图（渐变背景 + 白圆 + 黑色竖条纹与边框）并编码为 PNG 字节。
     *
     * @param size 图像边长（像素）
     * @return PNG 编码字节
     * @throws IOException PNG 编码失败时抛出
     */
    private static byte[] createTestImage(int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        paintPattern(graphics, size);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * 在画布上绘制渐变、圆形与条纹组合图案，保证二值化/形态学等操作有可辨识的结构。
     *
     * @param graphics 画布
     * @param size     图像边长（像素）
     */
    private static void paintPattern(Graphics2D graphics, int size) {
        graphics.setPaint(new GradientPaint(0, 0, Color.ORANGE, size, size, Color.BLUE));
        graphics.fillRect(0, 0, size, size);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(size / 4, size / 4, size / 2, size / 2);
        graphics.setColor(Color.BLACK);
        for (int i = 1; i < 8; i++) {
            graphics.drawLine(i * size / 8, 0, i * size / 8, size);
        }
        graphics.drawRect(size / 8, size / 8, size * 3 / 4, size * 3 / 4);
    }

    /**
     * 解析正整数命令行参数，支持 {@code --flag=value} 与 {@code --flag value} 两种形式。
     *
     * @param args         命令行参数
     * @param flag         参数名（含前导 --）
     * @param defaultValue 缺省值
     * @return 解析值；格式非法返回 -1；未出现返回缺省值
     */
    private static int parsePositiveArg(String[] args, String flag, int defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(flag + "=")) {
                return parseStrictInt(args[i].substring(flag.length() + 1));
            }
            if (args[i].equals(flag) && i + 1 < args.length) {
                return parseStrictInt(args[i + 1]);
            }
        }
        return defaultValue;
    }

    /**
     * 严格解析整数字符串，非法输入统一返回 -1 供上层判错。
     *
     * @param raw 整数字符串
     * @return 解析结果；非法返回 -1
     */
    private static int parseStrictInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 以键值对序列构造参数表。
     *
     * @param keyValues 依次为 key, value, key, value...
     * @return 参数表
     */
    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }

    /**
     * 单个基准操作用例。
     *
     * @param label     表格展示名（如 erode(3)）
     * @param operation SPI 操作名（如 erode）
     * @param params    操作参数表
     */
    private record OpCase(String label, String operation, Map<String, Object> params) { }
}

package com.chua.example.tui;

import com.chua.common.support.lang.process.MultiProgressBar;
import com.chua.common.support.lang.process.ProgressSimulator;
import com.chua.common.support.lang.process.ProgressBar;
import com.chua.common.support.lang.process.ProgressBarStyle;
import com.chua.common.support.lang.process.ProgressUnitType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ProgressBar 功能测试示例。
 * <p>
 * 覆盖以下场景：
 * <ul>
 *   <li>基本步进操作（step/stepBy/stepTo）</li>
 *   <li>Builder 自定义配置（样式、单位、速度、ETA）</li>
 *   <li>不确定模式（maxHint(-1)）</li>
 *   <li>暂停/恢复/重置</li>
 *   <li>包装迭代器/集合/数组/Stream</li>
 *   <li>多进度条并排显示</li>
 *   <li>进度模拟器曲线</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class ProgressBarExample {

    private ProgressBarExample() {
    }

    public static void main(String[] args) {
        System.out.println("[INFO] ProgressBarExample 开始");
        System.out.println("[INFO] 注意：每个场景之间有短暂间隔，请按提示操作");
        System.out.println();

        testBasicStep();
        sleep(1500);

        testBuilderCustom();
        sleep(1500);

        testIndefiniteMode();
        sleep(1500);

        testPauseResumeReset();
        sleep(1500);

        testWrapIterable();
        sleep(1500);

        testWrapStreamAndArray();
        sleep(1500);

        testMultiProgressBar();
        sleep(1500);

        testProgressSimulator();
        sleep(1000);

        System.out.println("[INFO] ProgressBarExample 全部完成");
        System.exit(0);
    }

    private static void testBasicStep() {
        System.out.println(">>> 场景 1：基本步进操作");
        try (ProgressBar pb = new ProgressBar("下载文件", 100)) {
            for (int i = 0; i <= 100; i += 10) {
                pb.stepTo(i);
                sleep(120);
            }
            pb.setExtraMessage("完成");
            sleep(300);
        }
        System.out.println("[PASS] 基本步进");
    }

    private static void testBuilderCustom() {
        System.out.println(">>> 场景 2：Builder 自定义（ASCII 风格 + 字节单位）");
        try (ProgressBar pb = ProgressBar.builder()
                .setTaskName("大文件传输")
                .setInitialMax(50_000_000L)
                .setUnit(ProgressUnitType.BYTE)
                .setUnit("KB", 1024L)
                .setStyle(ProgressBarStyle.ASCII)
                .showSpeed()
                .continuousUpdate()
                .clearDisplayOnFinish()
                .build()) {
            for (long i = 0; i <= 50_000_000L; i += 5_000_000L) {
                pb.stepBy(5_000_000L);
                sleep(150);
            }
        }
        System.out.println("[PASS] Builder 自定义");
    }

    private static void testIndefiniteMode() {
        System.out.println(">>> 场景 3：不确定模式（maxHint(-1)）");
        try (ProgressBar pb = new ProgressBar("处理中...", -1)) {
            pb.maxHint(-1);
            for (int i = 0; i < 15; i++) {
                pb.stepBy(1);
                pb.setExtraMessage("思考中 " + (i + 1) + "/15");
                sleep(200);
            }
            pb.setExtraMessage("完成");
            sleep(300);
        }
        System.out.println("[PASS] 不确定模式");
    }

    private static void testPauseResumeReset() {
        System.out.println(">>> 场景 4：暂停 / 恢复 / 重置");
        try (ProgressBar pb = new ProgressBar("暂停演示", 100)) {
            pb.stepBy(30);
            sleep(400);
            pb.pause();
            pb.setExtraMessage("已暂停");
            sleep(800);
            pb.resume();
            pb.setExtraMessage("已恢复");
            sleep(400);
            pb.reset();
            pb.setExtraMessage("已重置");
            sleep(300);
            pb.stepBy(100);
            sleep(300);
        }
        System.out.println("[PASS] 暂停/恢复/重置");
    }

    private static void testWrapIterable() {
        System.out.println(">>> 场景 5：包装 Iterable（List<Integer>）");
        List<Integer> items = IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toList());
        long total = 0;
        try (ProgressBar pb = new ProgressBar("遍历集合", items.size())) {
            for (int item : ProgressBar.wrap(items, "处理中")) {
                total += item;
                sleep(80);
            }
        }
        System.out.println("[PASS] Iterable 包装，元素和 = " + total);
    }

    private static void testWrapStreamAndArray() {
        System.out.println(">>> 场景 6：包装 Stream + 数组");
        try (ProgressBar pb = new ProgressBar("Stream 处理", 10)) {
            long sum = ProgressBar.wrap(
                    IntStream.rangeClosed(1, 10).mapToObj(i -> (long) i * i),
                    "平方计算"
            ).mapToLong(Long::longValue).sum();
            System.out.println("      Stream 平方和 = " + sum);
        }
        String[] words = {"hello", "world", "foo", "bar", "baz"};
        long[] counter = {0};
        try (ProgressBar pb = new ProgressBar("数组遍历", words.length)) {
            ProgressBar.wrap(words, "扫描").forEach(w -> {
                counter[0]++;
                sleep(100);
            });
        }
        System.out.println("[PASS] 数组遍历完成，元素数 = " + counter[0]);
    }

    private static void testMultiProgressBar() {
        System.out.println(">>> 场景 7：多进度条并排显示");
        try (MultiProgressBar mpb = MultiProgressBar.builder()
                .addTask("下载数据", 100)
                .addTask("解析结构", 80)
                .addTask("写入结果", 60)
                .build()) {
            for (int i = 0; i <= 100; i += 5) {
                mpb.stepBy(0, 5);
                if (i % 5 == 0) mpb.stepBy(1, 4);
                if (i % 10 == 0) mpb.stepBy(2, 3);
                sleep(80);
            }
        }
        System.out.println("[PASS] 多进度条");
    }

    private static void testProgressSimulator() {
        System.out.println(">>> 场景 8：ProgressSimulator 曲线演示");
        try (ProgressBar pb = new ProgressBar("S型曲线", 100)) {
            ProgressSimulator sim = new ProgressSimulator(ProgressSimulator.Type.SLOW_FAST_SLOW, 100);
            while (!sim.isFinish()) {
                double val = sim.next();
                pb.stepTo((long) val);
                sleep(60);
            }
        }
        System.out.println("[PASS] 进度模拟器 S 曲线");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.chua.example.utils;

import com.chua.common.support.utils.MathUtils;
import com.chua.common.support.utils.MathUtils.LinearRegression;
import com.chua.common.support.utils.MathUtils.SamplePoint;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * MathUtils 数学工具类示例，演示高斯分布、线性回归、移动平均的使用。
 *
 * <h2>用法</h2>
 * <pre>
 *   java MathUtilsExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MathUtilsExample {
    private MathUtilsExample() { }


    public static void main(String[] args) {
        // 1) 钟状图采样：N(0,1) 默认 100 点
        List<SamplePoint> samples = MathUtils.gaussianSample(0, 1);
        SamplePoint peak = samples.stream().reduce((a, b) -> a.y() > b.y() ? a : b).orElse(null);
        log.info("钟状图: 采样 {} 点, 峰值 x={}, y={}", samples.size(),
                String.format("%.3f", peak.x()), String.format("%.5f", peak.y()));

        // 2) 高斯概率密度
        double pdf = MathUtils.gaussianPdf(0, 0, 1);
        log.info("PDF: f(0|0,1) = " + String.format("%.6f", pdf));

        // 3) 高斯累积分布
        double cdf = MathUtils.gaussianCdf(1.96, 0, 1);
        log.info("CDF: Φ(1.96) = " + String.format("%.6f", cdf));

        // 4) 线性回归
        double[] xs = {1, 2, 3, 4, 5};
        double[] ys = {2.1, 4.0, 5.9, 8.2, 9.8};
        LinearRegression lr = MathUtils.linearRegression(xs, ys);
        log.info("回归: y = " + String.format("%.3f", lr.slope()) + "x + " + String.format("%.3f", lr.intercept())
                + "  R²=" + String.format("%.4f", lr.rSquared())
                + "  r=" + String.format("%.4f", lr.pearson()));

        // 5) 预测
        double[] pred = MathUtils.linearPredict(xs, ys, new double[]{6, 7, 8});
        log.info("预测: x=6→" + String.format("%.2f", pred[0])
                + "  x=7→" + String.format("%.2f", pred[1])
                + "  x=8→" + String.format("%.2f", pred[2]));

        // 6) 移动平均
        double[] noisy = {10, 90, 15, 85, 12, 88};
        double[] smoothed = MathUtils.simpleMovingAverage(noisy, 3);
        log.info("SMA 原始: {},{},{},{},{},{}", noisy[0], noisy[1], noisy[2], noisy[3], noisy[4], noisy[5]);
        log.info("SMA 平滑: " + String.format("%.1f,%.1f,%.1f,%.1f,%.1f,%.1f",
                smoothed[0], smoothed[1], smoothed[2], smoothed[3], smoothed[4], smoothed[5]));

        log.info("完成");
    }
}

package com.chua.example.utils;

import com.chua.common.support.utils.MathUtils;
import com.chua.common.support.utils.MathUtils.LinearRegression;
import com.chua.common.support.utils.MathUtils.SamplePoint;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * MathUtils 进阶示例，在 {@link MathUtilsExample} 基础用法之上补充边界与统计特性自检。
 *
 * <p>改写自 common-starter 单元测试 MathUtilsTest，覆盖高斯 PDF/CDF 参考值与对称性、
 * 采样参数校验、线性回归常量序列与非法输入、移动平均窗口边界等场景。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.utils.MathUtilsAdvancedExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MathUtilsAdvancedExample {
    private MathUtilsAdvancedExample() { }


    /**
     * 数值断言允许的默认误差
     */
    private static final double DELTA = 1.0E-6D;

    /**
     * 主入口，依次执行六组自检，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean ok = true;
        ok &= exampleGaussianPdf();
        ok &= exampleGaussianSample();
        ok &= exampleGaussianCdf();
        ok &= exampleLinearRegression();
        ok &= exampleLinearPredict();
        ok &= exampleSimpleMovingAverage();
        log.info("===== MathUtilsAdvancedExample: {} =====", ok ? "全部通过" : "存在失败项");
        if (!ok) {
            System.exit(1);
        }
    }

    /**
     * 自检高斯 PDF：均值处峰值、非法标准差归零、关于均值对称。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleGaussianPdf() {
        log.info("===== 高斯 PDF 自检 =====");
        double expectedPeak = 1.0D / Math.sqrt(2 * Math.PI);
        boolean ok = true;
        ok &= check("标准正态在均值处密度为 1/sqrt(2*PI)", () ->
                near(MathUtils.gaussianPdf(0, 0, 1), expectedPeak, DELTA));
        ok &= check("标准差为 0 或负数时返回 0", () ->
                near(MathUtils.gaussianPdf(0, 0, 0), 0, DELTA) && near(MathUtils.gaussianPdf(0, 0, -1), 0, DELTA));
        ok &= check("PDF 关于均值对称", () ->
                near(MathUtils.gaussianPdf(2, 0, 3), MathUtils.gaussianPdf(-2, 0, 3), DELTA));
        return ok;
    }

    /**
     * 自检高斯采样：默认点数与 ±4σ 范围、峰值贴近均值、非法参数返回空列表。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleGaussianSample() {
        log.info("===== 高斯采样自检 =====");
        List<SamplePoint> defaults = MathUtils.gaussianSample(0, 1);
        boolean ok = true;
        ok &= check("默认采样 100 点且横坐标覆盖 ±4σ", () ->
                defaults.size() == 100
                        && near(defaults.get(0).x(), -4.0D, DELTA)
                        && near(defaults.get(defaults.size() - 1).x(), 4.0D, DELTA));
        ok &= check("自定义采样峰值出现在均值附近（μ=5 ± 0.5）", MathUtilsAdvancedExample::peakNearMean);
        ok &= check("非法参数返回空列表", () ->
                MathUtils.gaussianSample(0, 1, 1, 3).isEmpty()
                        && MathUtils.gaussianSample(0, 0, 50, 3).isEmpty()
                        && MathUtils.gaussianSample(0, 1, 50, 0).isEmpty());
        return ok;
    }

    /**
     * 自检高斯 CDF：均值处 0.5、已知参考值 Φ(±1)、对称性 Φ(-x) = 1 - Φ(x)。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleGaussianCdf() {
        log.info("===== 高斯 CDF 自检 =====");
        boolean ok = true;
        ok &= check("CDF 在均值处为 0.5", () -> near(MathUtils.gaussianCdf(0, 0, 1), 0.5D, DELTA));
        ok &= check("已知参考值 Φ(1)=0.8413447 Φ(-1)=0.1586552", () ->
                near(MathUtils.gaussianCdf(1, 0, 1), 0.8413447D, 1.0E-4D)
                        && near(MathUtils.gaussianCdf(-1, 0, 1), 0.1586552D, 1.0E-4D));
        ok &= check("对称性 Φ(-x) + Φ(x) = 1", () ->
                near(MathUtils.gaussianCdf(2, 0, 1) + MathUtils.gaussianCdf(-2, 0, 1), 1.0D, DELTA));
        return ok;
    }

    /**
     * 自检线性回归：正常拟合 y=2x+1、常量 Y/X、完全负相关、null 与长度不一致输入。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleLinearRegression() {
        log.info("===== 线性回归自检 =====");
        double[] xs = {1, 2, 3, 4, 5};
        double[] ys = {3.01D, 4.98D, 7.02D, 8.99D, 11.0D};
        LinearRegression normal = MathUtils.linearRegression(xs, ys);
        LinearRegression constantY = MathUtils.linearRegression(xs, new double[]{10, 10, 10, 10, 10});
        double[] oneToFive = {1, 2, 3, 4, 5};
        LinearRegression constantX = MathUtils.linearRegression(new double[]{5, 5, 5, 5, 5}, oneToFive);
        LinearRegression negative = MathUtils.linearRegression(xs, new double[]{5, 4, 3, 2, 1});
        LinearRegression blank = MathUtils.linearRegression(null, null);
        LinearRegression mismatch = MathUtils.linearRegression(new double[]{1, 2}, new double[]{1});
        boolean ok = true;
        ok &= check("正常数据回归 slope=2 intercept=1 R²=1 r=1", () ->
                near(normal.slope(), 2.0D, 0.05D)
                        && near(normal.intercept(), 1.0D, 0.05D)
                        && near(normal.rSquared(), 1.0D, 0.01D)
                        && near(normal.pearson(), 1.0D, 0.01D));
        ok &= check("常量 Y 时 slope=0 截距=10 且 r/R² 归零非 NaN", () ->
                near(constantY.slope(), 0, DELTA)
                        && near(constantY.intercept(), 10.0D, DELTA)
                        && near(constantY.rSquared(), 0, DELTA)
                        && near(constantY.pearson(), 0, DELTA)
                        && !Double.isNaN(constantY.pearson()));
        ok &= check("常量 X 时 slope=0 截距=Y 均值 3", () ->
                near(constantX.slope(), 0, DELTA) && near(constantX.intercept(), 3.0D, DELTA));
        ok &= check("完全负相关 slope=-1 pearson=-1 R²=1", () ->
                near(negative.slope(), -1.0D, DELTA)
                        && near(negative.pearson(), -1.0D, DELTA)
                        && near(negative.rSquared(), 1.0D, DELTA));
        ok &= check("null 输入与长度不一致均返回全零结果", () ->
                allZero(blank) && near(mismatch.slope(), 0, DELTA));
        return ok;
    }

    /**
     * 自检线性预测：基于回归的预测值、null 输入返回全 0 数组、空 predictX 返回空数组。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleLinearPredict() {
        log.info("===== 线性预测自检 =====");
        double[] xs = {1, 2, 3, 4, 5};
        double[] ys = {3.01D, 4.98D, 7.02D, 8.99D, 11.0D};
        double[] predicted = MathUtils.linearPredict(xs, ys, new double[]{6.0D, 10.0D});
        double[] blank = MathUtils.linearPredict(null, null, new double[]{1, 2, 3});
        double[] empty = MathUtils.linearPredict(new double[]{1, 2}, new double[]{1, 2}, new double[0]);
        boolean ok = true;
        ok &= check("预测 x=6→13 x=10→21", () ->
                near(predicted[0], 13.0D, 0.05D) && near(predicted[1], 21.0D, 0.05D));
        ok &= check("null 输入返回与 predictX 等长全 0 数组", () -> blank.length == 3 && allZeroValues(blank));
        ok &= check("空 predictX 返回空数组", () -> empty.length == 0);
        return ok;
    }

    /**
     * 自检移动平均：常量序列不变、震荡平滑、窗口为 1 恒等、空输入与超界窗口。
     *
     * @return 全部通过返回 true
     */
    private static boolean exampleSimpleMovingAverage() {
        log.info("===== 移动平均自检 =====");
        double[] constants = {5, 5, 5, 5, 5, 5, 5};
        double[] denoiseSource = {1, 9, 1, 9, 1, 9};
        double[] denoised = MathUtils.simpleMovingAverage(denoiseSource, 2);
        double[] identitySource = {1, 2, 3, 4};
        double[] identity = MathUtils.simpleMovingAverage(identitySource, 1);
        double[] untouched = {1, 2, 3};
        boolean ok = true;
        ok &= check("常量序列移动平均仍为常量", () -> allEqual(MathUtils.simpleMovingAverage(constants, 3), 5.0D));
        ok &= check("震荡序列窗口满后稳定为 5", () ->
                near(denoised[0], 1.0D, DELTA) && near(denoised[1], 5.0D, DELTA)
                        && tailEquals(denoised, 2, 5.0D));
        ok &= check("窗口为 1 时结果等于输入", () -> Arrays.equals(identity, identitySource));
        ok &= check("空输入/null/超界窗口不修改原数组", () ->
                MathUtils.simpleMovingAverage(new double[0], 3).length == 0
                        && MathUtils.simpleMovingAverage((double[]) null, 3).length == 0
                        && Arrays.equals(MathUtils.simpleMovingAverage(untouched, 10), untouched)
                        && MathUtils.simpleMovingAverage(List.of(1, 2, 3, 4, 5), 2).length == 5);
        return ok;
    }

    /**
     * 执行单条自检并打印 PASS / FAIL 结果。
     *
     * @param name      自检项名称
     * @param condition 断言条件
     * @return 通过返回 true
     */
    private static boolean check(String name, BooleanSupplier condition) {
        boolean result = condition.getAsBoolean();
        log.info("[{}] {}", result ? "PASS" : "FAIL", name);
        return result;
    }

    /**
     * 判断实际值与期望值之差是否在允许误差内。
     *
     * @param actual   实际值
     * @param expected 期望值
     * @param delta    允许误差
     * @return 在误差内返回 true
     */
    private static boolean near(double actual, double expected, double delta) {
        return Math.abs(actual - expected) <= delta;
    }

    /**
     * 校验采样峰值点的横坐标是否落在均值附近（μ ± 0.5）。
     *
     * @return 峰值贴近均值返回 true
     */
    private static boolean peakNearMean() {
        List<SamplePoint> points = MathUtils.gaussianSample(5, 2, 50, 4);
        double maxY = points.stream().mapToDouble(SamplePoint::y).max().orElse(0);
        for (SamplePoint point : points) {
            if (near(point.y(), maxY, DELTA)) {
                return Math.abs(point.x() - 5.0D) <= 0.5D;
            }
        }
        return false;
    }

    /**
     * 判断回归结果四个统计量是否均为 0。
     *
     * @param lr 回归结果
     * @return 全零返回 true
     */
    private static boolean allZero(LinearRegression lr) {
        return near(lr.slope(), 0, DELTA) && near(lr.intercept(), 0, DELTA)
                && near(lr.rSquared(), 0, DELTA) && near(lr.pearson(), 0, DELTA);
    }

    /**
     * 判断数组所有元素是否均为 0。
     *
     * @param values 数组
     * @return 全零返回 true
     */
    private static boolean allZeroValues(double[] values) {
        return Arrays.stream(values).allMatch(value -> near(value, 0, DELTA));
    }

    /**
     * 判断数组所有元素是否等于指定值。
     *
     * @param values   数组
     * @param expected 期望值
     * @return 全部相等返回 true
     */
    private static boolean allEqual(double[] values, double expected) {
        return Arrays.stream(values).allMatch(value -> near(value, expected, DELTA));
    }

    /**
     * 判断数组从指定下标起的尾部元素是否都等于指定值。
     *
     * @param values 数组
     * @param from   起始下标
     * @param expected 期望值
     * @return 尾部全相等返回 true
     */
    private static boolean tailEquals(double[] values, int from, double expected) {
        for (int i = from; i < values.length; i++) {
            if (!near(values[i], expected, DELTA)) {
                return false;
            }
        }
        return true;
    }
}

package com.chua.example.vector;

import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorMath;
import lombok.extern.slf4j.Slf4j;

import static java.lang.Math.random;

/**
 * VectorMath 标量与向量化性能对比基准示例。
 *
 * <p>对多种维度（64/256/384/512/1024）的浮点向量执行欧氏距离与点积计算，
 * 对比 {@link VectorCompareAlgorithm}（构造算法对象）与 {@link VectorMath}
 * （静态方法直调）两种调用方式的耗时，输出性能倍率。</p>
 *
 * <pre>{@code
 *   java VectorMathBenchExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VectorMathBenchExample {

    /** 私有构造，防止实例化 */
    private VectorMathBenchExample() { }

    /** 基准测试维度集 */
    private static final int[] DIMS = {64, 256, 384, 512, 1024};
    /** 小维度的迭代次数 */
    private static final int SMALL_COUNT = 5_000_000;
    /** 大维度的迭代次数 */
    private static final int LARGE_COUNT = 500_000;
    /** 小维度阈值（<= 256 视为小维度） */
    private static final int SMALL_DIM = 256;

    /**
     * 主入口。
     *
     * @param args 无参数
     */
    public static void main(String[] args) {
        log.info("Vector API: {}, lanes={}", VectorMath.isVectorized(), VectorMath.lanes());
        for (int dim : DIMS) {
            benchmark(dim);
        }
    }

    /**
     * 对指定维度执行基准测试。
     *
     * @param dim 向量维度
     */
    private static void benchmark(int dim) {
        float[] a = randomVector(dim);
        float[] b = randomVector(dim);
        int count = dim <= SMALL_DIM ? SMALL_COUNT : LARGE_COUNT;

        // 预热：构造算法对象 + 首次调用
        var algo = VectorCompareAlgorithm.cosine();
        for (int i = 0; i < count / 5; i++) {
            algo.compare(a, b);
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            algo.compare(a, b);
        }
        long t1 = System.nanoTime();
        double algoMs = (t1 - t0) / 1e6;

        t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            VectorMath.cosine(a, b);
        }
        t1 = System.nanoTime();
        double vecMs = (t1 - t0) / 1e6;

        log.info("dim=%5d algo=%.1fms vectorMath=%.1fms speedup=%.2fx",
                dim, algoMs, vecMs, algoMs / vecMs);
    }

    /**
     * 生成指定维度的随机浮点向量。
     *
     * @param dim 维度
     * @return 随机向量
     */
    private static float[] randomVector(int dim) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = (float) random();
        }
        return v;
    }
}

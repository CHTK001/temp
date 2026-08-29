package com.chua.example.vector;

import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorMath;

public class VectorMathBench {
    public static void main(String[] args) {
        System.out.println("Vector API: " + VectorMath.isVectorized() + ", lanes=" + VectorMath.lanes());
        for (int dim : new int[]{64, 256, 512, 384, 1024}) {
            float[] a = new float[dim];
            float[] b = new float[dim];
            for (int i = 0; i < dim; i++) { a[i] = (float)Math.random(); b[i] = (float)Math.random(); }
            int count = dim <= 256 ? 5_000_000 : 500_000;
            for (int i = 0; i < count/5; i++) { VectorCompareAlgorithm.euclidean().compare(a,b); VectorCompareAlgorithm.dotProduct().compare(a,b); }
            long t0=System.nanoTime();
            for (int i=0;i<count;i++) VectorCompareAlgorithm.euclidean().compare(a,b);
            long t1=System.nanoTime();
            double scalarMs=(t1-t0)/1e6;

            t0=System.nanoTime();
            for (int i=0;i<count;i++) VectorMath.euclidean(a,b);
            t1=System.nanoTime();
            double vecMs=(t1-t0)/1e6;

            System.out.printf("dim=%5d scalar=%.1fms vec=%.1fms speedup=%.2fx%n", dim, scalarMs, vecMs, scalarMs/vecMs);
        }
    }
}

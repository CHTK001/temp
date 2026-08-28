package com.chua.example.tree;

import com.chua.common.support.tree.*;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) throws Exception {
        // B+ tree 100K
        long t0 = System.nanoTime();
        BPlusTree<Integer, String> plus100k = new BPlusTree<>(200);
        for (int i = 0; i < 100000; i++) plus100k.put(i, "v" + i);
        long t1 = System.nanoTime();
        int miss = 0;
        for (int j = 0; j < 100000; j++) if (!plus100k.containsKey(j)) miss++;
        long t2 = System.nanoTime();
        System.out.println("B+ 100K: size=" + plus100k.size() + " missing=" + miss
                + " put=" + (t1 - t0) / 1_000_000 + "ms get=" + (t2 - t1) / 1_000_000 + "ms");

        // B+ tree 1M
        t0 = System.nanoTime();
        BPlusTree<Integer, String> plus1m = new BPlusTree<>(200);
        for (int i = 0; i < 1000000; i++) plus1m.put(i, "v" + i);
        t1 = System.nanoTime();
        miss = 0;
        for (int j = 0; j < 1000000; j++) if (!plus1m.containsKey(j)) miss++;
        t2 = System.nanoTime();
        System.out.println("B+ 1M: size=" + plus1m.size() + " missing=" + miss
                + " put=" + (t1 - t0) / 1_000_000 + "ms get=" + (t2 - t1) / 1_000_000 + "ms");

        // BTree 100K
        t0 = System.nanoTime();
        BTree<Integer, String> bt100k = new BTree<>(200);
        for (int i = 0; i < 100000; i++) bt100k.put(i, "v" + i);
        t1 = System.nanoTime();
        miss = 0;
        for (int j = 0; j < 100000; j++) if (!bt100k.containsKey(j)) miss++;
        t2 = System.nanoTime();
        System.out.println("B 100K: size=" + bt100k.size() + " missing=" + miss
                + " put=" + (t1 - t0) / 1_000_000 + "ms get=" + (t2 - t1) / 1_000_000 + "ms");

        // BTree 1M
        t0 = System.nanoTime();
        BTree<Integer, String> bt1m = new BTree<>(200);
        for (int i = 0; i < 1000000; i++) bt1m.put(i, "v" + i);
        t1 = System.nanoTime();
        miss = 0;
        for (int j = 0; j < 1000000; j++) if (!bt1m.containsKey(j)) miss++;
        t2 = System.nanoTime();
        System.out.println("B 1M: size=" + bt1m.size() + " missing=" + miss
                + " put=" + (t1 - t0) / 1_000_000 + "ms get=" + (t2 - t1) / 1_000_000 + "ms");

        // Range tests
        long r0 = System.nanoTime();
        List<Map.Entry<Integer, String>> r1 = plus1m.range(0, 1000000);
        long r1t = System.nanoTime() - r0;
        System.out.println("B+ range[0,1M): " + r1.size() + " entries in " + r1t / 1_000_000 + "ms");

        r0 = System.nanoTime();
        List<Map.Entry<Integer, String>> r2 = plus1m.range(490000, 500000);
        r1t = System.nanoTime() - r0;
        System.out.println("B+ range[490k,500k): " + r2.size() + " entries in " + r1t / 1_000_000 + "ms");

        r0 = System.nanoTime();
        List<Map.Entry<Integer, String>> r3 = bt1m.range(0, 1000000);
        r1t = System.nanoTime() - r0;
        System.out.println("B range[0,1M): " + r3.size() + " entries in " + r1t / 1_000_000 + "ms");

        r0 = System.nanoTime();
        List<Map.Entry<Integer, String>> r4 = bt1m.range(490000, 500000);
        r1t = System.nanoTime() - r0;
        System.out.println("B range[490k,500k): " + r4.size() + " entries in " + r1t / 1_000_000 + "ms");
    }
}

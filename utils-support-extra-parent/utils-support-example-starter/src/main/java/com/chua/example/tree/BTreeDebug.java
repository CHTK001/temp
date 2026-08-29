package com.chua.example.tree;

import com.chua.common.support.tree.*;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) throws Exception {
        // Test different orders
        for (int order = 3; order <= 10; order++) {
            BTree<Integer, String> bt = new BTree<>(order);
            for (int i = 0; i < 10000; i++) bt.put(i, "v" + i);
            int miss = 0;
            for (int j = 0; j < 10000; j++) {
                if (!bt.containsKey(j)) miss++;
            }
            System.out.println("BTree order=" + order + " 10K: missing=" + miss);
        }

        // Test specific failing case
        BTree<Integer, String> bt = new BTree<>(4);
        for (int i = 0; i < 100; i++) bt.put(i, "v" + i);
        int miss = 0;
        List<Integer> misses = new ArrayList<>();
        for (int j = 0; j < 100; j++) {
            if (!bt.containsKey(j)) { miss++; misses.add(j); }
        }
        System.out.println("\nBTree order=4 100: missing=" + miss);
        System.out.println("First 10 missing: " + misses.subList(0, Math.min(10, misses.size())));
    }
}

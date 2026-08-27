package com.chua.example.tree;

import com.chua.common.support.tree.BTree;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) {
        BTree<Integer, String> tree = new BTree<>(4);
        for (int i = 0; i <= 7; i++) {
            tree.put(i, "v" + i);
            System.out.println("After insert " + i + " (size=" + tree.size() + ")");
            int miss = 0;
            for (int j = 0; j <= i; j++) {
                if (!tree.containsKey(j)) miss++;
            }
            if (miss > 0) {
                System.out.println("  MISSING: " + miss);
                for (int j = 0; j <= i; j++) {
                    if (!tree.containsKey(j)) System.out.println("    missing: " + j);
                }
            }
        }
        System.out.println("Final size=" + tree.size());
        for (int j = 0; j <= 7; j++) {
            System.out.println("  containsKey(" + j + ")=" + tree.containsKey(j));
        }
    }
}

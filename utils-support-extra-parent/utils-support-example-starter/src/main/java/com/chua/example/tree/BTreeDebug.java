package com.chua.example.tree;

import com.chua.common.support.tree.BPlusTree;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) {
        BPlusTree<Integer, String> plus = new BPlusTree<>(4);
        for (int i = 0; i < 10; i++) {
            System.out.println("Inserting " + i);
            try {
                plus.put(i, "v" + i);
                System.out.println("  OK, size=" + plus.size());
            } catch (Exception e) {
                System.out.println("  ERROR: " + e);
                e.printStackTrace();
                break;
            }
        }
        System.out.println("Done. size=" + plus.size());
        int miss = 0;
        for (int j = 0; j < 10; j++) {
            if (!plus.containsKey(j)) miss++;
        }
        System.out.println("missing=" + miss);
    }
}

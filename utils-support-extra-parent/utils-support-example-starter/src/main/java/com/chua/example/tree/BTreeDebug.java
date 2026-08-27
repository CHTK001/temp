package com.chua.example.tree;

import com.chua.common.support.tree.BTree;
import com.chua.common.support.tree.BPlusTree;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) {
        // Test B+ tree
        BPlusTree<Integer, String> plus = new BPlusTree<>(4);
        for (int i = 0; i < 100; i++) {
            plus.put(i, "v" + i);
        }
        int miss = 0;
        for (int j = 0; j < 100; j++) {
            if (!plus.containsKey(j)) miss++;
        }
        System.out.println("B+ 100: size=" + plus.size() + " missing=" + miss);

        // Test B tree
        BTree<Integer, String> bt = new BTree<>(4);
        for (int i = 0; i < 100; i++) {
            bt.put(i, "v" + i);
        }
        miss = 0;
        for (int j = 0; j < 100; j++) {
            if (!bt.containsKey(j)) miss++;
        }
        System.out.println("B 100: size=" + bt.size() + " missing=" + miss);

        // Test B+ 1M
        BPlusTree<Integer, String> plus1m = new BPlusTree<>(200);
        for (int i = 0; i < 100000; i++) {
            plus1m.put(i, "v" + i);
        }
        miss = 0;
        for (int j = 0; j < 100000; j++) {
            if (!plus1m.containsKey(j)) miss++;
        }
        System.out.println("B+ 100K: size=" + plus1m.size() + " missing=" + miss);

        // Test B 100K
        BTree<Integer, String> bt1m = new BTree<>(200);
        for (int i = 0; i < 100000; i++) {
            bt1m.put(i, "v" + i);
        }
        miss = 0;
        for (int j = 0; j < 100000; j++) {
            if (!bt1m.containsKey(j)) miss++;
        }
        System.out.println("B 100K: size=" + bt1m.size() + " missing=" + miss);
    }
}

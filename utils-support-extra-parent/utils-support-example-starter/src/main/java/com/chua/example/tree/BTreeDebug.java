package com.chua.example.tree;

import com.chua.common.support.tree.*;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) throws Exception {
        BTree<Integer, String> bt = new BTree<>(4);
        for (int i = 0; i < 20; i++) {
            bt.put(i, "v" + i);
        }
        System.out.println("size=" + bt.size());
        for (int j = 0; j < 20; j++) {
            if (!bt.containsKey(j)) {
                System.out.println("MISS: " + j);
            } else {
                System.out.println("OK: " + j + "=" + bt.get(j).get());
            }
        }
    }
}

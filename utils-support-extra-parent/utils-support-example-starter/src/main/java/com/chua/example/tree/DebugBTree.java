package com.chua.example.tree;

import com.chua.common.support.tree.BTree;
import java.util.*;

public class DebugBTree {
    public static void main(String[] args) throws Exception {
        BTree<Integer, String> tree = new BTree<>(200);
        for (int i = 0; i < 1_000_000; i++) tree.put(i, "v" + i);

        // Find first missing key
        List<Map.Entry<Integer, String>> all = tree.range(0, 1_000_000);
        Set<Integer> inRange = new HashSet<>();
        for (var e : all) inRange.add(e.getKey());

        int firstMissing = -1;
        for (int i = 0; i < 1_000_000; i++) {
            if (!inRange.contains(i)) {
                firstMissing = i;
                break;
            }
        }
        System.out.println("first missing key: " + firstMissing);

        // Check which keys around firstMissing are present/missing
        if (firstMissing >= 0) {
            for (int i = Math.max(0, firstMissing - 10); i <= Math.min(999999, firstMissing + 10); i++) {
                System.out.println("key " + i + ": get=" + tree.get(i).isPresent()
                    + " inRange=" + inRange.contains(i));
            }
        }

        // Check get vs range consistency
        int getMiss = 0;
        int rangeMiss = 0;
        for (int i = 0; i < 1_000_000; i++) {
            if (!tree.get(i).isPresent()) getMiss++;
            if (!inRange.contains(i)) rangeMiss++;
        }
        System.out.println("get misses: " + getMiss);
        System.out.println("range misses: " + rangeMiss);
        System.out.println("range size: " + all.size());
    }
}

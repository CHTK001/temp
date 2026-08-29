package com.chua.example.tree;

import com.chua.common.support.tree.*;
import java.lang.reflect.*;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) throws Exception {
        BTree<Integer, String> tree = new BTree<>(200);
        for (int i = 0; i < 1000000; i++) tree.put(i, "v" + i);

        // Get root
        Field rootField = BTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        Object root = rootField.get(tree);
        Field leafField = root.getClass().getDeclaredField("leaf");
        Field keysField = root.getClass().getDeclaredField("keys");
        Field childrenField = root.getClass().getDeclaredField("children");
        leafField.setAccessible(true); keysField.setAccessible(true); childrenField.setAccessible(true);
        boolean isLeaf = (boolean) leafField.get(root);
        List<?> rKeys = (List<?>) keysField.get(root);
        List<?> children = (List<?>) childrenField.get(root);
        System.out.println("Root: isLeaf=" + isLeaf + " keys=" + rKeys.size() + " children=" + children.size());
        System.out.println("Root firstKey=" + rKeys.get(0) + " lastKey=" + rKeys.get(rKeys.size()-1));

        // Count leaves at each depth
        int[] leafCountByDepth = new int[10];
        int[] internalCountByDepth = new int[10];
        countNodesByDepth(root, 0, leafCountByDepth, internalCountByDepth);
        System.out.println("\nNode counts by depth:");
        for (int d = 0; d < 10; d++) {
            if (leafCountByDepth[d] > 0 || internalCountByDepth[d] > 0) {
                System.out.println("  Depth " + d + ": internals=" + internalCountByDepth[d] + " leaves=" + leafCountByDepth[d]);
            }
        }

        // Count total entries per depth
        int[] entriesByDepth = new int[10];
        countEntriesByDepth(root, 0, entriesByDepth);
        System.out.println("\nEntries per depth:");
        for (int d = 0; d < 10; d++) {
            if (entriesByDepth[d] > 0) {
                System.out.println("  Depth " + d + ": " + entriesByDepth[d] + " entries");
            }
        }

        // Run range query and trace
        System.out.println("\n=== Range [490000, 500000) ===");
        int from = 490000, to = 500000;
        List<Map.Entry<Integer, String>> result = new ArrayList<>();
        traceCollectRange(root, from, to, result, 0);
        System.out.println("Found: " + result.size() + " entries");

        // Check missing
        Set<Integer> found = new HashSet<>();
        for (Map.Entry<Integer, String> e : result) found.add(e.getKey());
        List<Integer> missing = new ArrayList<>();
        for (int j = from; j < to; j++) {
            if (!found.contains(j)) missing.add(j);
        }
        System.out.println("Missing: " + missing.size());
        if (!missing.isEmpty()) {
            System.out.println("First 5: " + missing.subList(0, Math.min(5, missing.size())));
            System.out.println("Last 5: " + missing.subList(Math.max(0, missing.size()-5), missing.size()));
        }
    }

    static void traceCollectRange(Object node, int from, int to, List<Map.Entry<Integer, String>> result, int depth) throws Exception {
        Field leafField = node.getClass().getDeclaredField("leaf");
        Field keysField = node.getClass().getDeclaredField("keys");
        Field childrenField = node.getClass().getDeclaredField("children");
        leafField.setAccessible(true); keysField.setAccessible(true); childrenField.setAccessible(true);
        boolean isLeaf = (boolean) leafField.get(node);
        List<?> keys = (List<?>) keysField.get(node);
        List<?> children = (List<?>) childrenField.get(node);

        // Collect in-range keys
        for (int i = 0; i < keys.size(); i++) {
            int k = (Integer) keys.get(i);
            if (k >= from && k < to) result.add(new AbstractMap.SimpleEntry<>(k, "v" + k));
        }

        if (isLeaf) return;

        int start = 0;
        while (start < children.size() && (keys.isEmpty() || ((Integer)keys.get(Math.min(start, keys.size()-1))) < from)) {
            start++;
        }
        int end = children.size();
        while (end > start && (!keys.isEmpty() && ((Integer)keys.get(Math.min(end-1, keys.size()-1))) < to)) {
            end--;
        }

        if (depth <= 2) {
            System.out.println("  ".repeat(depth) + "INT keys=" + keys.size() + " children=" + children.size()
                    + " start=" + start + " end=" + end
                    + " key[start-1]=" + (start > 0 ? keys.get(start-1) : "N/A")
                    + " key[end-1]=" + (end > 0 ? keys.get(Math.min(end-1, keys.size()-1)) : "N/A"));
        }

        for (int j = start; j < end; j++) {
            traceCollectRange(children.get(j), from, to, result, depth + 1);
        }
    }

    static void countNodesByDepth(Object node, int depth, int[] leafCounts, int[] internalCounts) throws Exception {
        Field leafField = node.getClass().getDeclaredField("leaf");
        Field childrenField = node.getClass().getDeclaredField("children");
        leafField.setAccessible(true); childrenField.setAccessible(true);
        if ((boolean) leafField.get(node)) {
            if (depth < leafCounts.length) leafCounts[depth]++;
            return;
        }
        if (depth < internalCounts.length) internalCounts[depth]++;
        for (Object child : (List<?>) childrenField.get(node)) {
            countNodesByDepth(child, depth + 1, leafCounts, internalCounts);
        }
    }

    static void countEntriesByDepth(Object node, int depth, int[] entries) throws Exception {
        Field leafField = node.getClass().getDeclaredField("leaf");
        Field keysField = node.getClass().getDeclaredField("keys");
        Field childrenField = node.getClass().getDeclaredField("children");
        leafField.setAccessible(true); keysField.setAccessible(true); childrenField.setAccessible(true);
        int count = ((List<?>) keysField.get(node)).size();
        if (depth < entries.length) entries[depth] += count;
        if ((boolean) leafField.get(node)) return;
        for (Object child : (List<?>) childrenField.get(node)) {
            countEntriesByDepth(child, depth + 1, entries);
        }
    }
}

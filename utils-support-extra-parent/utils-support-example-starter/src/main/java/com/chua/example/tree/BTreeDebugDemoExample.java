package com.chua.example.tree;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.tree.BTree;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B-树内部结构调试示例：通过 ReflectUtils 反射读取 BTree 内部 root/leaf/keys/children 字段，
 * 统计各深度节点与条目数，演示 BTree 范围查询的内部遍历路径。
 *
 * <p>本示例仅用于诊断 BTree 内部结构与算法正确性，依赖 BTree 的私有字段布局，
 * 如 BTree 内部结构调整，本示例需要同步更新。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java BTreeDebugExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class BTreeDebugDemoExample {
    /**
     * 最大统计深度。
     */
    private static final int MAX_DEPTH = 10;

    /**
     * 示例入口：构造 1,000,000 条数据的 BTree，统计节点/条目分布并追踪一段范围查询路径。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        BTree<Integer, String> tree = new BTree<>(200);
        for (int i = 0; i < 1_000_000; i++) {
            tree.put(i, "v" + i);
        }

        Object root = ReflectUtils.getField(tree, "root");
        boolean isLeaf = (boolean) ReflectUtils.getField(root, "leaf");
        List<?> rKeys = (List<?>) ReflectUtils.getField(root, "keys");
        List<?> children = (List<?>) ReflectUtils.getField(root, "children");
        System.out.println("Root: isLeaf=" + isLeaf
                + " keys=" + rKeys.size()
                + " children=" + children.size());
        System.out.println("Root firstKey=" + rKeys.get(0)
                + " lastKey=" + rKeys.get(rKeys.size() - 1));

        int[] leafCountByDepth = new int[MAX_DEPTH];
        int[] internalCountByDepth = new int[MAX_DEPTH];
        countNodesByDepth(root, 0, leafCountByDepth, internalCountByDepth);
        System.out.println("\nNode counts by depth:");
        for (int d = 0; d < MAX_DEPTH; d++) {
            if (leafCountByDepth[d] > 0 || internalCountByDepth[d] > 0) {
                System.out.println("  Depth " + d
                        + ": internals=" + internalCountByDepth[d]
                        + " leaves=" + leafCountByDepth[d]);
            }
        }

        int[] entriesByDepth = new int[MAX_DEPTH];
        countEntriesByDepth(root, 0, entriesByDepth);
        System.out.println("\nEntries per depth:");
        for (int d = 0; d < MAX_DEPTH; d++) {
            if (entriesByDepth[d] > 0) {
                System.out.println("  Depth " + d + ": " + entriesByDepth[d] + " entries");
            }
        }

        System.out.println("\n=== Range [490000, 500000) ===");
        int from = 490_000;
        int to = 500_000;
        List<Map.Entry<Integer, String>> result = new ArrayList<>();
        traceCollectRange(root, from, to, result, 0);
        System.out.println("Found: " + result.size() + " entries");

        Set<Integer> found = new HashSet<>();
        for (Map.Entry<Integer, String> e : result) {
            found.add(e.getKey());
        }
        List<Integer> missing = new ArrayList<>();
        for (int j = from; j < to; j++) {
            if (!found.contains(j)) {
                missing.add(j);
            }
        }
        System.out.println("Missing: " + missing.size());
        if (!missing.isEmpty()) {
            System.out.println("First 5: " + missing.subList(0, Math.min(5, missing.size())));
            System.out.println("Last 5: " + missing.subList(Math.max(0, missing.size() - 5), missing.size()));
        }
    }

    /**
     * 递归遍历节点并收集 [from, to) 范围内的键值对。
     *
     * @param node  当前节点
     * @param from  范围下界（包含）
     * @param to    范围上界（不包含）
     * @param result 结果集
     * @param depth 当前深度
     */
    static void traceCollectRange(Object node, int from, int to,
                                  List<Map.Entry<Integer, String>> result, int depth) {
        List<?> keys = (List<?>) ReflectUtils.getField(node, "keys");
        List<?> children = (List<?>) ReflectUtils.getField(node, "children");
        boolean isLeaf = (boolean) ReflectUtils.getField(node, "leaf");

        for (int i = 0; i < keys.size(); i++) {
            int k = (Integer) keys.get(i);
            if (k >= from && k < to) {
                result.add(new AbstractMap.SimpleEntry<>(k, "v" + k));
            }
        }

        if (isLeaf) {
            return;
        }

        int start = 0;
        while (start < children.size()
                && (keys.isEmpty() || ((Integer) keys.get(Math.min(start, keys.size() - 1))) < from)) {
            start++;
        }
        int end = children.size();
        while (end > start
                && (!keys.isEmpty() && ((Integer) keys.get(Math.min(end - 1, keys.size() - 1))) < to)) {
            end--;
        }

        if (depth <= 2) {
            System.out.println("  ".repeat(depth)
                    + "INT keys=" + keys.size()
                    + " children=" + children.size()
                    + " start=" + start
                    + " end=" + end
                    + " key[start-1]=" + (start > 0 ? keys.get(start - 1) : "N/A")
                    + " key[end-1]=" + (end > 0 ? keys.get(Math.min(end - 1, keys.size() - 1)) : "N/A"));
        }

        for (int j = start; j < end; j++) {
            traceCollectRange(children.get(j), from, to, result, depth + 1);
        }
    }

    /**
     * 递归统计各深度叶子节点与内部节点数量。
     *
     * @param node           当前节点
     * @param depth          当前深度
     * @param leafCounts     叶子计数数组
     * @param internalCounts 内部节点计数数组
     */
    static void countNodesByDepth(Object node, int depth, int[] leafCounts, int[] internalCounts) {
        List<?> children = (List<?>) ReflectUtils.getField(node, "children");
        boolean isLeaf = (boolean) ReflectUtils.getField(node, "leaf");
        if (isLeaf) {
            if (depth < leafCounts.length) {
                leafCounts[depth]++;
            }
            return;
        }
        if (depth < internalCounts.length) {
            internalCounts[depth]++;
        }
        for (Object child : children) {
            countNodesByDepth(child, depth + 1, leafCounts, internalCounts);
        }
    }

    /**
     * 递归统计各深度条目数。
     *
     * @param node     当前节点
     * @param depth    当前深度
     * @param entries  条目数数组
     */
    static void countEntriesByDepth(Object node, int depth, int[] entries) {
        List<?> keys = (List<?>) ReflectUtils.getField(node, "keys");
        List<?> children = (List<?>) ReflectUtils.getField(node, "children");
        boolean isLeaf = (boolean) ReflectUtils.getField(node, "leaf");
        int count = keys.size();
        if (depth < entries.length) {
            entries[depth] += count;
        }
        if (isLeaf) {
            return;
        }
        for (Object child : children) {
            countEntriesByDepth(child, depth + 1, entries);
        }
    }
}

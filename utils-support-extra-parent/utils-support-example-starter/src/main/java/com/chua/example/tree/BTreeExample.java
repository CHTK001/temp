package com.chua.example.tree;

import static java.util.Objects.requireNonNull;

import com.chua.common.support.tree.BinaryTreeConverter;
import com.chua.common.support.tree.TreeEngine;
import com.chua.common.support.tree.TreeNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.UtilsExample;

/**
 * B+/B 树引擎全场景自检示例。
 *
 * <p>覆盖 B+ 树与 B 树的精确查找、范围查询、增删改、二叉树转换等核心能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class BTreeExample {

    private BTreeExample() {
    }

    public static void main(String[] args) {
        var passed = true;
        passed &= UtilsExample.timed("bplusPutAndGet", BTreeExample::bplusPutAndGet);
        passed &= UtilsExample.timed("bplusRangeQuery", BTreeExample::bplusRangeQuery);
        passed &= UtilsExample.timed("bplusDelete", BTreeExample::bplusDelete);
        passed &= UtilsExample.timed("bplusLargeScale", BTreeExample::bplusLargeScale);
        passed &= UtilsExample.timed("bplusPerf", BTreeExample::bplusPerf);
        passed &= UtilsExample.timed("btreePerf", BTreeExample::btreePerf);
        passed &= UtilsExample.timed("bplusToBinaryAndBack", BTreeExample::bplusToBinaryAndBack);
        passed &= UtilsExample.timed("btreePutAndGet", BTreeExample::btreePutAndGet);
        passed &= UtilsExample.timed("btreeRangeQuery", BTreeExample::btreeRangeQuery);
        passed &= UtilsExample.timed("btreeDelete", BTreeExample::btreeDelete);
        passed &= UtilsExample.timed("btreeToBinaryAndBack", BTreeExample::btreeToBinaryAndBack);
        if (!passed) {
            log.info("[FAIL] BTreeExample 存在失败场景");
            System.exit(UtilsExample.FAILURE);
        }
        log.info("[PASS] BTreeExample 全部场景通过");
        System.exit(UtilsExample.SUCCESS);
    }


    // ==================== B+ 树测试 ====================

    private static boolean bplusPutAndGet() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBPlusTree(100);
            tree.put(3, "three");
            tree.put(1, "one");
            tree.put(2, "two");
            tree.put(4, "four");

            assert tree.get(3).equals(Optional.of("three")) : "bplusPutAndGet get 3";
            assert tree.get(1).equals(Optional.of("one")) : "bplusPutAndGet get 1";
            assert tree.get(99).equals(Optional.empty()) : "bplusPutAndGet get miss";
            assert tree.size() == 4 : "bplusPutAndGet size";
            UtilsExample.print("bplusPutAndGet", true);
            return true;
        } catch (Exception e) {
            return UtilsExample.fail("bplusPutAndGet", e);
        }
    }

    private static boolean bplusRangeQuery() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBPlusTree(100);
            for (int i = 1; i <= 10; i++) {
                tree.put(i, "v" + i);
            }
            List<Map.Entry<Integer, String>> range = tree.range(3, 8);
            var ids = range.stream().map(Map.Entry::getKey).toList();
            boolean ok = ids.equals(List.of(3, 4, 5, 6, 7));
            UtilsExample.print("bplusRangeQuery", ok);
            return ok;
        } catch (Exception e) {
            return UtilsExample.fail("bplusRangeQuery", e);
        }
    }

    private static boolean bplusDelete() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBPlusTree(100);
            tree.put(1, "a");
            tree.put(2, "b");
            tree.put(3, "c");

            Optional<String> removed = tree.remove(2);
            assert removed.equals(Optional.of("b")) : "bplusDelete remove 2";
            assert !tree.containsKey(2) : "bplusDelete containsKey 2";
            assert tree.size() == 2 : "bplusDelete size";

            Optional<String> miss = tree.remove(99);
            assert miss.equals(Optional.empty()) : "bplusDelete remove miss";
            UtilsExample.print("bplusDelete", true);
            return true;
        } catch (Exception e) {
            return UtilsExample.fail("bplusDelete", e);
        }
    }

    private static boolean bplusLargeScale() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBPlusTree(200);
            int total = 1_000_000;
            for (int i = 0; i < total; i++) {
                tree.put(i, "value-" + i);
            }
            assert tree.size() == total : "bplusLargeScale size";

            long start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                Optional<String> v = tree.get(i);
                assert v.isPresent() : "bplusLargeScale get miss at " + i;
            }
            long elapsedNs = System.nanoTime() - start;
            log.info("[TIME] bplusLargeScale get {} ops in {}ms", total, elapsedNs / 1_000_000);

            List<Map.Entry<Integer, String>> range = tree.range(500_000, 500_100);
            boolean ok = range.size() == 100;
            UtilsExample.print("bplusLargeScale", ok);
            return ok;
        } catch (Exception e) {
            return UtilsExample.fail("bplusLargeScale", e);
        }
    }

    private static boolean bplusPerf() {
        try {
            int total = 1_000_000;
            TreeEngine<Integer, String> tree = TreeEngine.ofBPlusTree(200);
            long start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                tree.put(i, "v" + i);
            }
            long putNs = System.nanoTime() - start;
            log.info("[PERF] B+ put {} ops in {}ms", total, putNs / 1_000_000);

            start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                tree.get(i);
            }
            long getNs = System.nanoTime() - start;
            log.info("[PERF] B+ get {} ops in {}ms", total, getNs / 1_000_000);

            start = System.nanoTime();
            List<Map.Entry<Integer, String>> all = tree.range(0, total);
            long rangeNs = System.nanoTime() - start;
            log.info("[PERF] B+ range[0,1M) {} entries in {}ms", all.size(), rangeNs / 1_000_000);

            start = System.nanoTime();
            List<Map.Entry<Integer, String>> k10k = tree.range(490_000, 500_000);
            long range10kNs = System.nanoTime() - start;
            log.info("[PERF] B+ range[490k,500k) {} entries in {}ms", k10k.size(), range10kNs / 1_000_000);

            return all.size() == total && k10k.size() == 10_000;
        } catch (Exception e) {
            return UtilsExample.fail("bplusPerf", e);
        }
    }

    private static boolean btreePerf() {
        try {
            int total = 1_000_000;
            TreeEngine<Integer, String> tree = TreeEngine.ofBTree(200);
            long start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                tree.put(i, "v" + i);
            }
            long putNs = System.nanoTime() - start;
            log.info("[PERF] B  put {} ops in {}ms", total, putNs / 1_000_000);

            start = System.nanoTime();
            for (int i = 0; i < total; i++) {
                tree.get(i);
            }
            long getNs = System.nanoTime() - start;
            log.info("[PERF] B  get {} ops in {}ms", total, getNs / 1_000_000);

            start = System.nanoTime();
            List<Map.Entry<Integer, String>> all = tree.range(0, total);
            long rangeNs = System.nanoTime() - start;
            log.info("[PERF] B  range[0,1M) {} entries in {}ms", all.size(), rangeNs / 1_000_000);

            start = System.nanoTime();
            List<Map.Entry<Integer, String>> k10k = tree.range(490_000, 500_000);
            long range10kNs = System.nanoTime() - start;
            log.info("[PERF] B  range[490k,500k) {} entries in {}ms", k10k.size(), range10kNs / 1_000_000);

            return all.size() == total && k10k.size() == 10_000;
        } catch (Exception e) {
            return UtilsExample.fail("btreePerf", e);
        }
    }

    private static boolean bplusToBinaryAndBack() {
        try {
            TreeEngine<Integer, String> original = TreeEngine.ofBPlusTree(100);
            original.put(10, "ten");
            original.put(20, "twenty");
            original.put(30, "thirty");
            original.put(5, "five");

            TreeNode<Integer, String> binary = original.toBinaryTree();
            TreeEngine<Integer, String> restored = BinaryTreeConverter.binaryToBPlusTree(binary);

            assert restored.get(10).equals(Optional.of("ten")) : "bplusToBinaryAndBack 10";
            assert restored.get(20).equals(Optional.of("twenty")) : "bplusToBinaryAndBack 20";
            assert restored.get(30).equals(Optional.of("thirty")) : "bplusToBinaryAndBack 30";
            assert restored.get(5).equals(Optional.of("five")) : "bplusToBinaryAndBack 5";
            assert restored.size() == 4 : "bplusToBinaryAndBack size";
            UtilsExample.print("bplusToBinaryAndBack", true);
            return true;
        } catch (Exception e) {
            return UtilsExample.fail("bplusToBinaryAndBack", e);
        }
    }

    private static boolean btreePutAndGet() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBTree(100);
            tree.put(3, "three");
            tree.put(1, "one");
            tree.put(2, "two");

            assert tree.get(3).equals(Optional.of("three")) : "btreePutAndGet get 3";
            assert tree.get(1).equals(Optional.of("one")) : "btreePutAndGet get 1";
            assert !tree.containsKey(99) : "btreePutAndGet containsKey miss";
            assert tree.size() == 3 : "btreePutAndGet size";
            UtilsExample.print("btreePutAndGet", true);
            return true;
        } catch (Exception e) {
            return UtilsExample.fail("btreePutAndGet", e);
        }
    }

    private static boolean btreeRangeQuery() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBTree(100);
            for (int i = 1; i <= 10; i++) {
                tree.put(i, "v" + i);
            }
            List<Map.Entry<Integer, String>> range = tree.range(3, 8);
            var ids = range.stream().map(Map.Entry::getKey).toList();
            boolean ok = ids.equals(List.of(3, 4, 5, 6, 7));
            UtilsExample.print("btreeRangeQuery", ok);
            return ok;
        } catch (Exception e) {
            return UtilsExample.fail("btreeRangeQuery", e);
        }
    }

    private static boolean btreeDelete() {
        try {
            TreeEngine<Integer, String> tree = TreeEngine.ofBTree(100);
            tree.put(1, "a");
            tree.put(2, "b");
            tree.put(3, "c");

            Optional<String> removed = tree.remove(2);
            assert removed.equals(Optional.of("b")) : "btreeDelete remove 2";
            assert !tree.containsKey(2) : "btreeDelete containsKey 2";
            assert tree.size() == 2 : "btreeDelete size";
            UtilsExample.print("btreeDelete", true);
            return true;
        } catch (Exception e) {
            return UtilsExample.fail("btreeDelete", e);
        }
    }

    private static boolean btreeToBinaryAndBack() {
        try {
            TreeEngine<Integer, String> original = TreeEngine.ofBTree(100);
            original.put(10, "ten");
            original.put(20, "twenty");
            original.put(30, "thirty");
            original.put(5, "five");

            TreeNode<Integer, String> binary = original.toBinaryTree();
            TreeEngine<Integer, String> restored = BinaryTreeConverter.binaryToBTree(binary);

            assert restored.get(10).equals(Optional.of("ten")) : "btreeToBinaryAndBack 10";
            assert restored.get(20).equals(Optional.of("twenty")) : "btreeToBinaryAndBack 20";
            assert restored.get(30).equals(Optional.of("thirty")) : "btreeToBinaryAndBack 30";
            assert restored.get(5).equals(Optional.of("five")) : "btreeToBinaryAndBack 5";
            assert restored.size() == 4 : "btreeToBinaryAndBack size";
            UtilsExample.print("btreeToBinaryAndBack", true);
            return true;
        } catch (Exception e) {
            return UtilsExample.fail("btreeToBinaryAndBack", e);
        }
    }

    // ==================== 辅助 ====================
}

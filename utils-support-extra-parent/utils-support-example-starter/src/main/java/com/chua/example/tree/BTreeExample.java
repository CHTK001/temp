package com.chua.example.tree;

import static java.util.Objects.requireNonNull;

import com.chua.common.support.tree.BinaryTreeConverter;
import com.chua.common.support.tree.TreeEngine;
import com.chua.common.support.tree.TreeNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

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

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private BTreeExample() {
    }

    public static void main(String[] args) {
        var passed = true;
        passed &= timed("bplusPutAndGet", BTreeExample::bplusPutAndGet);
        passed &= timed("bplusRangeQuery", BTreeExample::bplusRangeQuery);
        passed &= timed("bplusDelete", BTreeExample::bplusDelete);
        passed &= timed("bplusLargeScale", BTreeExample::bplusLargeScale);
        passed &= timed("bplusPerf", BTreeExample::bplusPerf);
        passed &= timed("bplusToBinaryAndBack", BTreeExample::bplusToBinaryAndBack);
        passed &= timed("btreePutAndGet", BTreeExample::btreePutAndGet);
        passed &= timed("btreeRangeQuery", BTreeExample::btreeRangeQuery);
        passed &= timed("btreeDelete", BTreeExample::btreeDelete);
        passed &= timed("btreeToBinaryAndBack", BTreeExample::btreeToBinaryAndBack);
        if (!passed) {
            log.info("[FAIL] BTreeExample 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        log.info("[PASS] BTreeExample 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }

    /**
     * 计时执行测试场景并记录耗时。
     *
     * @param name     场景名称，用于日志与输出标记
     * @param scenario 测试场景，返回 true 表示通过
     * @return 场景是否通过
     */
    private static boolean timed(String name, BooleanSupplier scenario) {
        long start = System.currentTimeMillis();
        boolean ok = scenario.getAsBoolean();
        log.info("[TIME] " + name + " " + (System.currentTimeMillis() - start) + "ms");
        return ok;
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
            print("bplusPutAndGet", true);
            return true;
        } catch (Exception e) {
            return fail("bplusPutAndGet", e);
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
            print("bplusRangeQuery", ok);
            return ok;
        } catch (Exception e) {
            return fail("bplusRangeQuery", e);
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
            print("bplusDelete", true);
            return true;
        } catch (Exception e) {
            return fail("bplusDelete", e);
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
            print("bplusLargeScale", ok);
            return ok;
        } catch (Exception e) {
            return fail("bplusLargeScale", e);
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
            return fail("bplusPerf", e);
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
            return fail("btreePerf", e);
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
            print("bplusToBinaryAndBack", true);
            return true;
        } catch (Exception e) {
            return fail("bplusToBinaryAndBack", e);
        }
    }

    // ==================== B 树测试 ====================

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
            print("btreePutAndGet", true);
            return true;
        } catch (Exception e) {
            return fail("btreePutAndGet", e);
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
            print("btreeRangeQuery", ok);
            return ok;
        } catch (Exception e) {
            return fail("btreeRangeQuery", e);
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
            print("btreeDelete", true);
            return true;
        } catch (Exception e) {
            return fail("btreeDelete", e);
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
            print("btreeToBinaryAndBack", true);
            return true;
        } catch (Exception e) {
            return fail("btreeToBinaryAndBack", e);
        }
    }

    // ==================== 辅助 ====================

    private static void print(String name, boolean ok) {
        log.info("[{}] {}", ok ? "PASS" : "FAIL", name);
    }

    private static boolean fail(String name, Exception e) {
        log.info("[FAIL] " + name + " 异常: " + e);
        e.printStackTrace();
        return false;
    }
}

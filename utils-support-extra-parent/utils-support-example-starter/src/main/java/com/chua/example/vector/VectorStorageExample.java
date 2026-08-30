package com.chua.example.vector;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageBuilder;
import com.chua.example.util.ExampleUtils;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 向量存储基础功能示例（main 入口）— 由 {@code VectorStorageTest} 改写，
 * 与 SPI 形式的 {@link VectorStorageExampleSpi} 互补，演示 {@link VectorStorageBuilder}
 * 直接构建 memory 存储的增删查全链路。
 *
 * <h2>用法</h2>
 * <pre>
 *   java VectorStorageExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class VectorStorageExample {

    /**
     * 向量维度
     */
    private static final int DIMENSION = 3;

    /**
     * 防止实例化工具类。
     */
    private VectorStorageExample() {
    }

    // ==================== main ====================

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed;
        try (VectorStorage storage = newStorage()) {
            passed = testAddAndSearch(storage);
            passed &= testRemoveByIdPrefix(storage);
            passed &= testRemoveNonExistentPrefix(storage);
            passed &= testRemoveSingle(storage);
            passed &= testClear(storage);
        } catch (Exception e) {
            log.info("[FAIL] VectorStorage 示例异常: " + e.getMessage());
            System.exit(ExampleUtils.FAILURE);
            return;
        }
        if (!passed) {
            log.info("[FAIL] VectorStorage 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        } else {
            log.info("[PASS] VectorStorage 全部场景通过");
        }
        System.exit(ExampleUtils.SUCCESS);
    }

    // ==================== 场景 ====================

    /**
     * 场景 1：添加三条向量后按相似度检索 Top-2，最相似者排首。
     *
     * @param storage 向量存储
     * @return 通过返回 true
     */
    private static boolean testAddAndSearch(VectorStorage storage) {
        try {
            storage.add(new Vector("v1", new float[]{1.0f, 0.0f, 0.0f}));
            storage.add(new Vector("v2", new float[]{0.0f, 1.0f, 0.0f}));
            storage.add(new Vector("v3", new float[]{1.0f, 1.0f, 0.0f}));
            boolean ok = storage.size() == 3;
            List<Vector> results = storage.search(new float[]{1.0f, 0.1f, 0.0f}, 2);
            ok &= results.size() == 2;
            ok &= "v1".equals(results.get(0).id());
            ExampleUtils.print("add/search Top-K 最相似排首", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("add/search Top-K 最相似排首", e);
        }
    }

    /**
     * 场景 2：按前缀批量删除命中条目，其余保留。
     *
     * @param storage 向量存储
     * @return 通过返回 true
     */
    private static boolean testRemoveByIdPrefix(VectorStorage storage) {
        try {
            storage.clear();
            storage.add(new Vector("doc1_chunk0", new float[]{1.0f, 0.0f, 0.0f}));
            storage.add(new Vector("doc1_chunk1", new float[]{0.0f, 1.0f, 0.0f}));
            storage.add(new Vector("doc2_chunk0", new float[]{1.0f, 1.0f, 0.0f}));
            storage.add(new Vector("other_item", new float[]{0.5f, 0.5f, 0.5f}));
            boolean ok = storage.size() == 4;
            int removed = storage.removeByIdPrefix("doc1_");
            ok &= removed == 2;
            ok &= storage.size() == 2;
            List<Vector> remaining = storage.search(new float[]{1.0f, 1.0f, 0.0f}, 10);
            ok &= remaining.size() == 2;
            ExampleUtils.print("removeByIdPrefix 批量删除", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("removeByIdPrefix 批量删除", e);
        }
    }

    /**
     * 场景 3：删除不存在前缀返回 0 且不影响存量。
     *
     * @param storage 向量存储
     * @return 通过返回 true
     */
    private static boolean testRemoveNonExistentPrefix(VectorStorage storage) {
        try {
            storage.clear();
            storage.add(new Vector("doc1_chunk0", new float[]{1.0f, 0.0f, 0.0f}));
            int removed = storage.removeByIdPrefix("nonexistent_");
            boolean ok = removed == 0 && storage.size() == 1;
            ExampleUtils.print("removeByIdPrefix 无命中返回 0", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("removeByIdPrefix 无命中返回 0", e);
        }
    }

    /**
     * 场景 4：单条删除首次成功、二次失败，size 归零。
     *
     * @param storage 向量存储
     * @return 通过返回 true
     */
    private static boolean testRemoveSingle(VectorStorage storage) {
        try {
            storage.clear();
            storage.add(new Vector("only_one", new float[]{1.0f, 0.0f, 0.0f}));
            boolean first = storage.remove("only_one");
            boolean second = storage.remove("only_one");
            boolean ok = first && !second && storage.size() == 0;
            ExampleUtils.print("remove 单条幂等语义", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("remove 单条幂等语义", e);
        }
    }

    /**
     * 场景 5：clear 清空全部向量。
     *
     * @param storage 向量存储
     * @return 通过返回 true
     */
    private static boolean testClear(VectorStorage storage) {
        try {
            storage.clear();
            storage.add(new Vector("v1", new float[]{1.0f, 0.0f, 0.0f}));
            storage.add(new Vector("v2", new float[]{0.0f, 1.0f, 0.0f}));
            storage.clear();
            boolean ok = storage.size() == 0;
            ExampleUtils.print("clear 清空存储", ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail("clear 清空存储", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建基于 Builder 的 memory 向量存储。
     *
     * @return 向量存储实例
     */
    private static VectorStorage newStorage() {
        return VectorStorageBuilder.newBuilder()
                .dimension(DIMENSION)
                .algorithm(VectorCompareAlgorithm.cosine())
                .build();
    }
}

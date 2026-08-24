package com.chua.example.vector;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageBuilder;

import java.util.List;

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
public final class VectorStorageExample {

    /**
     * 向量维度
     */
    private static final int DIMENSION = 3;

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

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
            System.out.println("[FAIL] VectorStorage 示例异常: " + e.getMessage());
            System.exit(EXIT_CODE_FAILURE);
            return;
        }
        if (!passed) {
            System.out.println("[FAIL] VectorStorage 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        } else {
            System.out.println("[PASS] VectorStorage 全部场景通过");
        }
        System.exit(EXIT_CODE_SUCCESS);
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
            print("add/search Top-K 最相似排首", ok);
            return ok;
        } catch (Exception e) {
            return fail("add/search Top-K 最相似排首", e);
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
            print("removeByIdPrefix 批量删除", ok);
            return ok;
        } catch (Exception e) {
            return fail("removeByIdPrefix 批量删除", e);
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
            print("removeByIdPrefix 无命中返回 0", ok);
            return ok;
        } catch (Exception e) {
            return fail("removeByIdPrefix 无命中返回 0", e);
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
            print("remove 单条幂等语义", ok);
            return ok;
        } catch (Exception e) {
            return fail("remove 单条幂等语义", e);
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
            print("clear 清空存储", ok);
            return ok;
        } catch (Exception e) {
            return fail("clear 清空存储", e);
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

    /**
     * 输出单场景结果。
     *
     * @param name 场景名
     * @param ok   是否通过
     */
    private static void print(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    /**
     * 输出单场景异常失败结果。
     *
     * @param name 场景名
     * @param e    异常
     * @return 恒为 false
     */
    private static boolean fail(String name, Exception e) {
        System.out.println("[FAIL] " + name + ": " + e.getMessage());
        return false;
    }
}

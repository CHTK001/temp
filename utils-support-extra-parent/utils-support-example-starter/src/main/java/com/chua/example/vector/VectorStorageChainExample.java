package com.chua.example.vector;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.jvector.support.configuration.JVectorStorageProperties;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

/**
 * 链式 API 综合示例（可独立运行的 main 测试，非 JUnit）。
 *
 * <p>演示 {@link VectorStorageProvider#of(String)} 链式风格 API：</p>
 * <pre>{@code
 *   VectorStorage storage = VectorStorageProvider.of("memory")
 *       .dimension(4)
 *       .algorithm("cosine")
 *       .build();
 * }</pre>
 *
 * <p>覆盖以下场景：</p>
 * <ul>
 *   <li>memory 后端：默认构建 / 指定算法 / 名称字符串</li>
 *   <li>jvector 后端：MEMORY / ON_DISK / LARGER_THAN_MEMORY 三种模式</li>
 *   <li>等价性校验：of().build() 与 create() 结果一致</li>
 *   <li>错误处理：无效 SPI 名称 / 无效算法名 / 清空 / 大小</li>
 * </ul>
 *
 * @author CH
 */
public class VectorStorageChainExample {

    private static final int DIM = 4;
    private static final int N = 10;
    private static final int TOP_K = 3;
    private static final Random RND = new Random(42);

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   VectorStorage 链式 API  综合自测          ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        testMemoryChain();
        testMemoryChainWithAlgorithmInstance();
        testMemoryChainWithAlgorithmName();
        testChainCreatesSameAsCreate();
        testJVectorMemory();
        testJVectorLargerThanMemory();
        testJVectorOnDisk();
        testClear();
        testSize();
        testInvalidProvider();
        testInvalidAlgorithmName();

        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("结果: " + passed + " 通过, " + failed + " 失败");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ── memory 后端测试 ──────────────────────────────────────────

    private static void testMemoryChain() {
        System.out.println("── [memory] 默认链式构建 ──");
        try {
            VectorStorage s = VectorStorageProvider.of("memory")
                    .dimension(DIM)
                    .build();
            assertNotNull(s);
            assertEquals(DIM, s.dimension(), "dimension");
            seedAndSearch(s, "memory-default");
            pass();
        } catch (Exception e) {
            fail("memory 默认构建异常: " + e.getMessage());
        }
    }

    private static void testMemoryChainWithAlgorithmInstance() {
        System.out.println("── [memory] algorithm(实例) ──");
        try {
            VectorStorage s = VectorStorageProvider.of("memory")
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .build();
            assertNotNull(s);
            seedAndSearch(s, "memory-algo-instance");
            pass();
        } catch (Exception e) {
            fail("memory 算法实例异常: " + e.getMessage());
        }
    }

    private static void testMemoryChainWithAlgorithmName() {
        System.out.println("── [memory] algorithm(\"COSINE\") 名称字符串 ──");
        try {
            VectorStorage s = VectorStorageProvider.of("memory")
                    .dimension(DIM)
                    .algorithm("COSINE")
                    .build();
            assertNotNull(s);
            seedAndSearch(s, "memory-algo-name");
            pass();
        } catch (Exception e) {
            fail("memory 算法名称异常: " + e.getMessage());
        }
    }

    private static void testChainCreatesSameAsCreate() {
        System.out.println("── [等价比对] of().build() == create() ──");
        try {
            VectorStorage chain = VectorStorageProvider.of("memory")
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.euclidean())
                    .build();
            VectorStorage direct = VectorStorageProvider.create(
                    "memory", DIM, VectorCompareAlgorithm.euclidean());
            assertNotNull(chain);
            assertNotNull(direct);
            assertEquals(direct.dimension(), chain.dimension(), "dimension 一致");
            pass();
        } catch (Exception e) {
            fail("等价比对异常: " + e.getMessage());
        }
    }

    // ── jvector 三种模式 ─────────────────────────────────────────

    private static void testJVectorMemory() {
        runJVectorMode("jvector-memory", JVectorStorageProperties.Mode.MEMORY);
    }

    private static void testJVectorLargerThanMemory() {
        runJVectorMode("jvector-ltm", JVectorStorageProperties.Mode.LARGER_THAN_MEMORY);
    }

    private static void testJVectorOnDisk() {
        System.out.println("── [jvector] ON_DISK ──");
        try {
            var props = new JVectorStorageProperties();
            props.setMode(JVectorStorageProperties.Mode.ON_DISK);
            props.setIndexPath("./chain-test-on-disk");

            VectorStorage s = VectorStorageProvider.of("jvector")
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .properties(props)
                    .build();
            assertNotNull(s);
            try {
                seedAndSearch(s, "jvector-on-disk");
                var indexPath = Paths.get(props.getIndexPath());
                boolean exists = Files.exists(indexPath);
                System.out.println("    磁盘文件: " + indexPath.toAbsolutePath()
                        + " exists=" + exists);
                if (!exists) {
                    fail("ON_DISK 模式下索引文件未生成");
                } else {
                    pass();
                }
            } finally {
                s.close();
            }
        } catch (Exception e) {
            fail("jvector ON_DISK 异常: " + e.getMessage());
        }
    }

    private static void runJVectorMode(String label, JVectorStorageProperties.Mode mode) {
        System.out.println("── [jvector] " + mode + " ──");
        try {
            var props = new JVectorStorageProperties();
            props.setMode(mode);
            props.setIndexPath("./chain-test-" + mode.name().toLowerCase());

            VectorStorage s = VectorStorageProvider.of("jvector")
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .properties(props)
                    .build();
            assertNotNull(s);
            seedAndSearch(s, label);
            pass();
            s.close();
        } catch (Exception e) {
            fail("jvector " + mode + " 异常: " + e.getMessage());
        }
    }

    // ── 基础操作 ─────────────────────────────────────────────────

    private static void testClear() {
        System.out.println("── [memory] clear() ──");
        try {
            VectorStorage s = VectorStorageProvider.of("memory")
                    .dimension(DIM)
                    .build();
            s.add("x", randomVector());
            assertEquals(1, s.size(), "clear 前 size");
            s.clear();
            assertEquals(0, s.size(), "clear 后 size");
            pass();
        } catch (Exception e) {
            fail("clear 异常: " + e.getMessage());
        }
    }

    private static void testSize() {
        System.out.println("── [memory] size() ──");
        try {
            VectorStorage s = VectorStorageProvider.of("memory")
                    .dimension(DIM)
                    .build();
            assertEquals(0, s.size(), "初始 size");
            for (int i = 0; i < 5; i++) {
                s.add("size-" + i, randomVector());
            }
            assertEquals(5, s.size(), "添加后 size");
            pass();
        } catch (Exception e) {
            fail("size 异常: " + e.getMessage());
        }
    }

    // ── 错误处理 ─────────────────────────────────────────────────

    private static void testInvalidProvider() {
        System.out.println("── [异常] 不存在的 SPI 名称 ──");
        try {
            VectorStorageProvider.of("non_existent_spi").build();
            fail("不存在的 SPI 名称应抛 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass();
        } catch (Exception e) {
            fail("错误类型不对，应为 IllegalArgumentException，实际: "
                    + e.getClass().getSimpleName());
        }
    }

    private static void testInvalidAlgorithmName() {
        System.out.println("── [异常] 无效算法名称 ──");
        try {
            VectorStorageProvider.of("memory")
                    .algorithm("INVALID_ALGO")
                    .build();
            fail("无效算法名称应抛 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass();
        } catch (Exception e) {
            fail("错误类型不对，应为 IllegalArgumentException，实际: "
                    + e.getClass().getSimpleName());
        }
    }

    // ── 辅助方法 ─────────────────────────────────────────────────

    private static void seedAndSearch(VectorStorage storage, String label) {
        for (int i = 0; i < N; i++) {
            storage.add(label + "-" + i, randomVector());
        }
        List<Vector> results = storage.search(randomVector(), TOP_K);
        assertNotNull(results);
        System.out.println("    搜索 " + TOP_K + " 结果: "
                + (results.isEmpty() ? "空" : "id=" + results.get(0).id()));
    }

    private static float[] randomVector() {
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            v[i] = RND.nextFloat();
        }
        return v;
    }

    // ── 断言工具（纯 main 风格，不依赖 JUnit）────────────────────

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            fail("断言失败: " + msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertNotNull(Object obj) {
        if (obj == null) {
            fail("断言失败: 对象不应为 null");
        }
    }

    private static void pass() {
        passed++;
        System.out.println("  ✓ 通过");
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("  ✗ 失败: " + msg);
    }
}

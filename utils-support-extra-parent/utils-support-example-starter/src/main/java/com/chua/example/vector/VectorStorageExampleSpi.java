package com.chua.example.vector;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.example.spi.Example;
import com.chua.jvector.support.configuration.JVectorStorageProperties;
import com.chua.milvus.support.storage.MilvusVectorStorage;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 向量存储综合示例（SPI 形式）— 演示 {@link com.chua.example.spi.Example} 接口实现。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=vector-storage} 调用，
 * 内部基于 {@link VectorStorageProvider} SPI，支持 memory / jvector / milvus 三种实现。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 列出全部示例
 *   java ExampleRunner --list
 *
 *   # memory 实现
 *   java ExampleRunner --example=vector-storage --type=memory
 *
 *   # jvector 三种模式
 *   java ExampleRunner --example=vector-storage --type=jvector --mode=MEMORY
 *   java ExampleRunner --example=vector-storage --type=jvector --mode=ON_DISK
 *
 *   # milvus / Zilliz Cloud
 *   java ExampleRunner --example=vector-storage --type=milvus \
 *       --host=in03-xxx.serverless.gcp-us-west1.cloud.zilliz.com \
 *       --port=443 --collection=vector_store_v4 --token=xxx
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VectorStorageExampleSpi implements Example {

    /**
     * 向量维度
     */
    private static final int DIM = 4;

    /**
     * 测试向量条数
     */
    private static final int N = 10;

    /**
     * 搜索返回 Top-K
     */
    private static final int TOP_K = 3;

    @Override
    public String name() {
        return "vector-storage";
    }

    @Override
    public String module() {
        return "vector-storage";
    }

    @Override
    public String description() {
        return "向量存储综合自检（memory / jvector / milvus 三实现 SPI 切换）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "memory");
        JVectorStorageProperties.Mode mode = args.containsKey("mode")
                ? JVectorStorageProperties.Mode.valueOf(args.get("mode").toUpperCase())
                : JVectorStorageProperties.Mode.MEMORY;
        String host = args.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(args.getOrDefault("port", "19530"));
        String collection = args.getOrDefault("collection", "vector_store");
        String token = args.getOrDefault("token", null);

        log.info("===== vector-storage --test [type={}, mode={}] =====", type, mode);
        log.info("[1] 已注册的向量存储实现:");
        VectorStorageProvider.providers().forEach(p -> log.info("    - {}", p));

        return switch (type.toLowerCase()) {
            case "memory" -> testMemoryCapabilities();
            case "jvector" -> testJVectorCapabilities(mode);
            case "milvus" -> testMilvusCapabilities(host, port, collection, token);
            default -> {
                log.info("不支持的 SPI 类型: {}，可选: memory / jvector / milvus", type);
                yield false;
            }
        };
    }

    // ==================== memory 能力集 ====================

    private boolean testMemoryCapabilities() {
        log.info("\n[memory] 基础能力矩阵");
        boolean passed = true;
        passed &= testMemoryDefaultBuild();
        passed &= testMemoryAlgorithmBuild();
        passed &= testMemoryRemove();
        passed &= testMemoryUpdate();
        passed &= testMemoryClear();
        passed &= testMemorySize();
        return passed;
    }

    private boolean testMemoryDefaultBuild() {
        log.info("  [TC-01] memory 默认构建 + 搜索");
        try {
            VectorStorage s = VectorStorageProvider.of("memory").dimension(DIM).build();
            seedAndSearch(s, "memory-default");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("memory 默认构建异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testMemoryAlgorithmBuild() {
        log.info("  [TC-02] memory 指定算法(COSINE)构建 + 搜索");
        try {
            VectorStorage s = VectorStorageProvider.of("memory").dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine()).build();
            seedAndSearch(s, "memory-cosine");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("memory 算法构建异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testMemoryRemove() {
        log.info("  [TC-03] memory remove(id)");
        VectorStorage s = null;
        try {
            s = createMemoryStorage();
            s.add("keep", new float[]{1f, 0f, 0f, 0f});
            s.add("drop", new float[]{0f, 1f, 0f, 0f});
            assertEquals(2, s.size(), "删除前 size");
            assertEquals(true, s.remove("drop"), "删除存在的 id");
            assertEquals(1, s.size(), "删除后 size");
            assertEquals(false, s.remove("drop"), "删除不存在的 id");
            pass();
            return true;
        } catch (Exception e) {
            fail("remove 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    private boolean testMemoryUpdate() {
        log.info("  [TC-04] memory update(id, vector)");
        VectorStorage s = null;
        try {
            s = createMemoryStorage();
            s.add("a", new float[]{1f, 0f, 0f, 0f});
            s.add("b", new float[]{0f, 1f, 0f, 0f});
            s.add("c", new float[]{0f, 0f, 1f, 0f});
            assertEquals(3, s.size(), "更新前 size");
            assertEquals(true, s.update("a", new float[]{0f, 0f, 0f, 1f}), "更新存在的 id");
            assertEquals(3, s.size(), "更新不改 size");
            assertEquals(false, s.update("nope", new float[]{1f, 0f, 0f, 0f}), "更新不存在的 id");
            pass();
            return true;
        } catch (Exception e) {
            fail("update 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    private boolean testMemoryClear() {
        log.info("  [TC-05] memory clear()");
        try {
            VectorStorage s = createMemoryStorage();
            s.add("x", randomVector(new Random(1), DIM));
            assertEquals(1, s.size(), "clear 前 size");
            s.clear();
            assertEquals(0, s.size(), "clear 后 size");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("clear 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testMemorySize() {
        log.info("  [TC-06] memory size()");
        try {
            VectorStorage s = createMemoryStorage();
            assertEquals(0, s.size(), "初始 size");
            for (int i = 0; i < 5; i++) {
                s.add("size-" + i, randomVector(new Random(2), DIM));
            }
            assertEquals(5, s.size(), "添加后 size");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("size 异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== jvector 能力集 ====================

    private boolean testJVectorCapabilities(JVectorStorageProperties.Mode mode) {
        log.info("\n[jvector] 模式={} 能力矩阵", mode);
        boolean passed = true;
        passed &= testJVectorAddSearch(mode);
        passed &= testJVectorUpdate(mode);
        passed &= testJVectorRemove(mode);
        passed &= testJVectorClear(mode);
        passed &= testJVectorSize(mode);
        if (mode == JVectorStorageProperties.Mode.ON_DISK) {
            passed &= testJVectorDiskFile(mode);
            passed &= testJVectorPersistence(mode);
        }
        return passed;
    }

    private boolean testJVectorAddSearch(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-11] jvector {} add + search", mode);
        try {
            VectorStorage s = createJVectorStorage(mode);
            seedJVector(s, "jv-addsearch");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector add/search 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testJVectorUpdate(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-12] jvector {} update", mode);
        VectorStorage s = null;
        try {
            s = createJVectorStorage(mode);
            s.add("a", new float[]{1f, 0f, 0f, 0f});
            s.add("b", new float[]{0f, 1f, 0f, 0f});
            s.add("c", new float[]{0f, 0f, 1f, 0f});
            assertEquals(3, s.size(), "jvector 更新前 size");
            assertEquals(true, s.update("a", new float[]{0f, 0f, 0f, 1f}), "更新存在的 id");
            assertEquals(3, s.size(), "jvector 更新不改 size");
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector update 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    private boolean testJVectorRemove(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-13] jvector {} remove", mode);
        VectorStorage s = null;
        try {
            s = createJVectorStorage(mode);
            s.add("keep", new float[]{1f, 0f, 0f, 0f});
            s.add("drop", new float[]{0f, 1f, 0f, 0f});
            assertEquals(2, s.size(), "删除前 size");
            assertEquals(true, s.remove("drop"), "删除存在的 id");
            assertEquals(1, s.size(), "删除后 size");
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector remove 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    private boolean testJVectorClear(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-14] jvector {} clear", mode);
        try {
            VectorStorage s = createJVectorStorage(mode);
            s.add("x", randomVector(new Random(3), DIM));
            assertEquals(1, s.size(), "clear 前 size");
            s.clear();
            assertEquals(0, s.size(), "clear 后 size");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector clear 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testJVectorSize(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-15] jvector {} size", mode);
        try {
            VectorStorage s = createJVectorStorage(mode);
            assertEquals(0, s.size(), "初始 size");
            for (int i = 0; i < 5; i++) {
                s.add("jv-size-" + i, randomVector(new Random(4), DIM));
            }
            assertEquals(5, s.size(), "添加后 size");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector size 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testJVectorDiskFile(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-16] jvector ON_DISK 磁盘文件验证");
        try {
            var props = new JVectorStorageProperties();
            props.setMode(mode);
            java.nio.file.Path tmp = Files.createTempDirectory("jvector-disk-");
            props.setIndexPath(tmp.resolve("index").toString());
            VectorStorage s = VectorStorageProvider.of("jvector").dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine()).properties(props).build();
            seedJVector(s, "jv-disk");
            s.close();
            var graphPath = Paths.get(props.getIndexPath());
            boolean exists = Files.exists(graphPath);
            log.info("    磁盘图文件: {} exists={}", graphPath.toAbsolutePath(), exists);
            if (!exists) {
                fail("ON_DISK 模式下索引图文件未生成");
                return false;
            }
            pass();
            return true;
        } catch (Exception e) {
            fail("ON_DISK 磁盘文件验证异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testJVectorPersistence(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-17] jvector {} 持久化往返", mode);
        try {
            java.nio.file.Path tmp = Files.createTempDirectory("jvector-persist-");
            String indexPath = tmp.resolve("index").toString();

            var props1 = new JVectorStorageProperties();
            props1.setMode(mode);
            props1.setIndexPath(indexPath);
            VectorStorage s1 = VectorStorageProvider.of("jvector").dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine()).properties(props1).build();
            for (int i = 0; i < 5; i++) {
                s1.add("p-" + i, randomVector(new Random(11 + i), DIM));
            }
            assertEquals(5, s1.size(), "关闭前 size");
            s1.close();

            var props2 = new JVectorStorageProperties();
            props2.setMode(mode);
            props2.setIndexPath(indexPath);
            VectorStorage s2 = VectorStorageProvider.of("jvector").dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine()).properties(props2).build();
            assertEquals(5, s2.size(), "重启后 size 应为 5");
            s2.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector 持久化往返异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== milvus 能力集 ====================

    private boolean testMilvusCapabilities(String host, int port, String collection, String token) {
        log.info("\n[milvus] 基础能力矩阵 [host={}:{}, collection={}]", host, port, collection);
        boolean passed = true;
        passed &= testMilvusBuildAndSearch(host, port, collection, token);
        passed &= testMilvusDelete(host, port, collection, token);
        passed &= testMilvusUpsert(host, port, collection, token);
        return passed;
    }

    private boolean testMilvusBuildAndSearch(String host, int port, String collection, String token) {
        log.info("  [TC-21] milvus 构建 + 搜索");
        MilvusVectorStorage s = null;
        try {
            s = new MilvusVectorStorage(DIM, VectorCompareAlgorithm.cosine(), host, port, collection, token);
            seedAndSearch(s, "milvus");
            pass();
            return true;
        } catch (Exception e) {
            fail("milvus 构建异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    private boolean testMilvusDelete(String host, int port, String collection, String token) {
        log.info("  [TC-22] milvus 删除");
        MilvusVectorStorage s = null;
        try {
            s = new MilvusVectorStorage(DIM, VectorCompareAlgorithm.cosine(), host, port, collection, token);
            s.add("del_test", new float[]{1f, 0f, 0f, 0f});
            assertEquals(true, s.remove("del_test"), "milvus 删除存在的 id");
            pass();
            return true;
        } catch (Exception e) {
            fail("milvus 删除异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    private boolean testMilvusUpsert(String host, int port, String collection, String token) {
        log.info("  [TC-23] milvus upsert");
        MilvusVectorStorage s = null;
        try {
            s = new MilvusVectorStorage(DIM, VectorCompareAlgorithm.cosine(), host, port, collection, token);
            s.add("upsert_key", new float[]{1f, 0f, 0f, 0f});
            assertEquals(true, s.update("upsert_key", new float[]{0f, 1f, 0f, 0f}), "milvus 更新存在的 id");
            pass();
            return true;
        } catch (Exception e) {
            fail("milvus upsert 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    // ==================== 辅助方法 ====================

    private VectorStorage createMemoryStorage() {
        return VectorStorageProvider.of("memory").dimension(DIM)
                .algorithm(VectorCompareAlgorithm.cosine()).build();
    }

    private VectorStorage createJVectorStorage(JVectorStorageProperties.Mode mode) {
        var props = new JVectorStorageProperties();
        props.setMode(mode);
        String indexPath;
        if (mode == JVectorStorageProperties.Mode.ON_DISK
                || mode == JVectorStorageProperties.Mode.LARGER_THAN_MEMORY) {
            try {
                java.nio.file.Path tmp = Files.createTempDirectory("jvector-example-");
                indexPath = tmp.resolve("index").toString();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            indexPath = "./example-vector-" + mode.name().toLowerCase();
        }
        props.setIndexPath(indexPath);
        if (mode == JVectorStorageProperties.Mode.ON_DISK) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(indexPath));
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(indexPath + ".vec"));
            } catch (java.io.IOException ignored) {
            }
        }
        return VectorStorageProvider.of("jvector").dimension(DIM)
                .algorithm(VectorCompareAlgorithm.cosine()).properties(props).build();
    }

    private void seedAndSearch(VectorStorage storage, String label) {
        var rnd = new Random(42);
        for (int i = 0; i < N; i++) {
            storage.add(label + "-" + i, randomVector(rnd, DIM));
        }
        List<Vector> results = storage.search(randomVector(rnd, DIM), TOP_K);
        String firstId = results.isEmpty() ? "<empty>" : results.get(0).id();
        log.info("    搜索 Top-{} 结果: firstId={}", TOP_K, firstId);
    }

    private void seedJVector(VectorStorage storage, String label) {
        var rnd = new Random(42);
        for (int i = 0; i < N; i++) {
            storage.add(label + "-" + i, randomVector(rnd, DIM));
        }
    }

    private static float[] randomVector(Random rnd, int dim) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rnd.nextFloat();
        }
        return v;
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void pass() {
        log.info("  ✓ 通过");
    }

    private static void fail(String msg) {
        log.info("  ✗ 失败: {}", msg);
    }

    private static void closeQuietly(VectorStorage s) {
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }
}

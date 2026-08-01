package com.chua.example.vector;

import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.jvector.support.configuration.JVectorStorageProperties;
import com.chua.milvus.support.storage.MilvusVectorStorage;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

/**
 * 向量存储综合示例 — 基于 VectorStorage SPI，支持全部实现切换与自检。
 *
 * <p>通过命令行参数指定 {@link VectorStorageProvider} 的 SPI 名称，
 * 自检覆盖基础能力矩阵：添加、搜索、更新、删除、清空。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认 memory 实现
 *   java VectorStorageExample
 *
 *   # jvector MEMORY 模式
 *   java VectorStorageExample --type jvector --mode MEMORY
 *
 *   # jvector ON_DISK 模式
 *   java VectorStorageExample --type jvector --mode ON_DISK
 *
 *   # jvector LARGER_THAN_MEMORY 模式
 *   java VectorStorageExample --type jvector --mode LARGER_THAN_MEMORY
 *
 *   # 自检全部能力点
 *   java VectorStorageExample --type jvector --mode MEMORY --test
 * </pre>
 *
 * <h2>SPI 类型与能力</h2>
 * <table border="1">
 *   <tr><th>--type</th><th>实现类</th><th>add/search</th><th>update</th><th>remove</th><th>clear</th><th>持久化</th></tr>
 *   <tr><td>memory</td><td>MemoryVectorStorage</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>❌</td></tr>
 *   <tr><td>jvector</td><td>JVectorVectorStorage</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>ON_DISK 有</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VectorStorageExample {

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

    /**
     * 默认 SPI 类型
     */
    private static final String DEFAULT_TYPE = "memory";

    /**
     * SPI 类型：内存实现
     */
    private static final String TYPE_MEMORY = "memory";

    /**
     * SPI 类型：JVector 实现
     */
    private static final String TYPE_JVECTOR = "jvector";

    private static final String TYPE_MILVUS = "milvus";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    // ==================== main ====================

    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        String type = parsed.type() != null ? parsed.type() : DEFAULT_TYPE;
        JVectorStorageProperties.Mode mode = parsed.mode() != null
                ? parsed.mode() : JVectorStorageProperties.Mode.MEMORY;
        String host = parsed.host();
        int port = parsed.port();
        String collection = parsed.collection();
        String token = parsed.token();

        VectorStorageExample example = new VectorStorageExample();
        boolean passed = example.runTest(type, mode, host, port, collection, token);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    // ==================== 自检入口 ====================

    /**
     * 运行指定 SPI 类型的自检。
     *
     * @param type SPI 类型标识
     * @param mode JVector 存储模式（非 jvector 实现时忽略）
     * @return 全部测试通过返回 true
     */
    public boolean runTest(String type, JVectorStorageProperties.Mode mode,
                           String host, int port, String collection, String token) {
        log.info("===== VectorStorageExample --test [type={}, mode={}] =====", type, mode);

        // 打印已注册实现列表
        log.info("[1] 已注册的向量存储实现:");
        VectorStorageProvider.providers().forEach(p -> log.info("    - {}", p));

        // 根据 type 选择测试集
        if (TYPE_MEMORY.equalsIgnoreCase(type)) {
            return testMemoryCapabilities();
        } else if (TYPE_JVECTOR.equalsIgnoreCase(type)) {
            return testJVectorCapabilities(mode);
        } else if (TYPE_MILVUS.equalsIgnoreCase(type)) {
            return testMilvusCapabilities(host, port, collection, token);
        } else {
            log.info("不支持的 SPI 类型: {}，可选: {} / {} / {}", type, TYPE_MEMORY, TYPE_JVECTOR, TYPE_MILVUS);
            return false;
        }
    }

    // ==================== memory 能力集 ====================

    private boolean testMemoryCapabilities() {
        log.info("\n[memory] 基础能力矩阵");
        boolean passed = true;

        // 默认构建 + 搜索
        passed &= testMemoryDefaultBuild();
        // 指定算法构建 + 搜索
        passed &= testMemoryAlgorithmBuild();
        // 等价性校验
        passed &= testMemoryChainVsCreate();
        // remove / update / clear / size
        passed &= testMemoryRemove();
        passed &= testMemoryUpdate();
        passed &= testMemoryClear();
        passed &= testMemorySize();

        return passed;
    }

    private boolean testMemoryDefaultBuild() {
        log.info("  [TC-01] memory 默认构建 + 搜索");
        try {
            VectorStorage s = VectorStorageProvider.of(TYPE_MEMORY)
                    .dimension(DIM)
                    .build();
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
            VectorStorage s = VectorStorageProvider.of(TYPE_MEMORY)
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .build();
            seedAndSearch(s, "memory-cosine");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("memory 算法构建异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testMemoryChainVsCreate() {
        log.info("  [TC-03] 等价比对: of().build() == create()");
        try {
            VectorStorage chain = VectorStorageProvider.of(TYPE_MEMORY)
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.euclidean())
                    .build();
            VectorStorage direct = VectorStorageProvider.create(
                    TYPE_MEMORY, DIM, VectorCompareAlgorithm.euclidean());
            assertNotNull(chain);
            assertNotNull(direct);
            assertEquals(chain.dimension(), direct.dimension(), "dimension 一致");
            pass();
            return true;
        } catch (Exception e) {
            fail("等价比对异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testMemoryRemove() {
        log.info("  [TC-04] memory remove(id)");
        VectorStorage s = null;
        try {
            s = createMemoryStorage();
            s.add("keep", new float[]{1f, 0f, 0f, 0f});
            s.add("drop", new float[]{0f, 1f, 0f, 0f});
            assertEquals(2, s.size(), "删除前 size");

            boolean removed = s.remove("drop");
            assertEquals(true, removed, "删除存在的 id");
            assertEquals(1, s.size(), "删除后 size");

            boolean removedAgain = s.remove("drop");
            assertEquals(false, removedAgain, "删除不存在的 id");

            List<Vector> results = s.search(new float[]{1f, 0f, 0f, 0f}, TOP_K);
            boolean dropGone = results.stream().noneMatch(v -> "drop".equals(v.id()));
            if (!dropGone) {
                fail("删除后不应再搜到 drop");
                return false;
            }
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
        log.info("  [TC-05] memory update(id, vector)");
        VectorStorage s = null;
        try {
            s = createMemoryStorage();
            float[] v1 = new float[]{1f, 0f, 0f, 0f};
            float[] v2 = new float[]{0f, 1f, 0f, 0f};
            float[] v3 = new float[]{0f, 0f, 1f, 0f};
            float[] v4 = new float[]{0f, 0f, 0f, 1f};
            s.add("a", v1);
            s.add("b", v2);
            s.add("c", v3);
            assertEquals(3, s.size(), "更新前 size");

            boolean updated = s.update("a", v4);
            assertEquals(true, updated, "更新存在的 id");
            assertEquals(3, s.size(), "更新不改 size");

            List<Vector> results = s.search(v4, 1);
            boolean bestIsA = !results.isEmpty() && "a".equals(results.get(0).id());
            if (!bestIsA) {
                fail("更新后 a 应最接近 v4");
                return false;
            }

            boolean missing = s.update("nope", v1);
            assertEquals(false, missing, "更新不存在的 id");

            boolean dimThrown = false;
            try {
                s.update("a", new float[2]);
            } catch (IllegalArgumentException e) {
                dimThrown = true;
            }
            assertEquals(true, dimThrown, "维度不匹配应抛异常");

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
        log.info("  [TC-06] memory clear()");
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
        log.info("  [TC-07] memory size()");
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

        // 基础 add/search
        passed &= testJVectorAddSearch(mode);
        // update / remove / size
        passed &= testJVectorUpdate(mode);
        passed &= testJVectorRemove(mode);
        passed &= testJVectorClear(mode);
        passed &= testJVectorSize(mode);

        // ON_DISK 验证磁盘文件 + 持久化往返
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
            searchAndLogTop1(s);
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector add/search 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testJVectorUpdate(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-12] jvector {} update(id, vector)", mode);
        VectorStorage s = null;
        try {
            s = createJVectorStorage(mode);
            float[] v1 = new float[]{1f, 0f, 0f, 0f};
            float[] v2 = new float[]{0f, 1f, 0f, 0f};
            float[] v3 = new float[]{0f, 0f, 1f, 0f};
            float[] v4 = new float[]{0f, 0f, 0f, 1f};
            s.add("a", v1);
            s.add("b", v2);
            s.add("c", v3);
            assertEquals(3, s.size(), "jvector 更新前 size");

            boolean updated = s.update("a", v4);
            assertEquals(true, updated, "jvector 更新存在的 id");
            assertEquals(3, s.size(), "jvector 更新不改 size");

            List<Vector> results = s.search(v4, 1);
            boolean bestIsA = !results.isEmpty() && "a".equals(results.get(0).id());
            if (!bestIsA) {
                fail("jvector 更新后 a 应最接近 v4");
                return false;
            }

            boolean missing = s.update("nope", v1);
            assertEquals(false, missing, "jvector 更新不存在的 id");

            boolean dimThrown = false;
            try {
                s.update("a", new float[2]);
            } catch (IllegalArgumentException e) {
                dimThrown = true;
            }
            assertEquals(true, dimThrown, "jvector 维度不匹配应抛异常");

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
        log.info("  [TC-13] jvector {} remove(id)", mode);
        VectorStorage s = null;
        try {
            s = createJVectorStorage(mode);
            s.add("keep", new float[]{1f, 0f, 0f, 0f});
            s.add("drop", new float[]{0f, 1f, 0f, 0f});
            assertEquals(2, s.size(), "jvector 删除前 size");

            boolean removed = s.remove("drop");
            assertEquals(true, removed, "jvector 删除存在的 id");
            assertEquals(1, s.size(), "jvector 删除后 size");

            boolean removedAgain = s.remove("drop");
            assertEquals(false, removedAgain, "jvector 删除不存在的 id");

            List<Vector> results = s.search(new float[]{1f, 0f, 0f, 0f}, TOP_K);
            boolean dropGone = results.stream().noneMatch(v -> "drop".equals(v.id()));
            if (!dropGone) {
                fail("jvector 删除后不应再搜到 drop");
                return false;
            }
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
        log.info("  [TC-14] jvector {} clear()", mode);
        try {
            VectorStorage s = createJVectorStorage(mode);
            s.add("x", randomVector(new Random(3), DIM));
            assertEquals(1, s.size(), "jvector clear 前 size");
            s.clear();
            assertEquals(0, s.size(), "jvector clear 后 size");
            s.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector clear 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean testJVectorSize(JVectorStorageProperties.Mode mode) {
        log.info("  [TC-15] jvector {} size()", mode);
        try {
            VectorStorage s = createJVectorStorage(mode);
            assertEquals(0, s.size(), "jvector 初始 size");
            for (int i = 0; i < 5; i++) {
                s.add("jv-size-" + i, randomVector(new Random(4), DIM));
            }
            assertEquals(5, s.size(), "jvector 添加后 size");
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

            VectorStorage s = VectorStorageProvider.of(TYPE_JVECTOR)
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .properties(props)
                    .build();
            seedJVector(s, "jv-disk");
            s.close();

            // jvector 内部把图写到 indexPath
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
        log.info("  [TC-17] jvector {} 持久化往返 (close -> 重新打开)", mode);
        try {
            java.nio.file.Path tmp = Files.createTempDirectory("jvector-persist-");
            String indexPath = tmp.resolve("index").toString();

            // 第一阶段：写入 5 个向量后关闭
            var props1 = new JVectorStorageProperties();
            props1.setMode(mode);
            props1.setIndexPath(indexPath);
            VectorStorage s1 = VectorStorageProvider.of(TYPE_JVECTOR)
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .properties(props1)
                    .build();
            float[] target = new float[]{1f, 0f, 0f, 0f};
            for (int i = 0; i < 5; i++) {
                s1.add("p-" + i, randomVector(new Random(11 + i), DIM));
            }
            s1.add("target", target);
            assertEquals(6, s1.size(), "jvector 关闭前 size");
            s1.close();

            // 第二阶段：用同一路径重新打开，验证数据被回填
            var props2 = new JVectorStorageProperties();
            props2.setMode(mode);
            props2.setIndexPath(indexPath);
            VectorStorage s2 = VectorStorageProvider.of(TYPE_JVECTOR)
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .properties(props2)
                    .build();
            assertEquals(6, s2.size(), "jvector 重启后 size 应为 6");

            // 第三阶段：搜索 target，最相似的应是 target 自身
            List<Vector> results = s2.search(target, 1);
            boolean top1IsTarget = !results.isEmpty() && "target".equals(results.get(0).id());
            if (!top1IsTarget) {
                fail("jvector 重启后 target 应是最相似");
                s2.close();
                return false;
            }

            s2.close();
            pass();
            return true;
        } catch (Exception e) {
            fail("jvector 持久化往返异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建 memory 存储（每次新建，避免状态污染）。
     */
    private VectorStorage createMemoryStorage() {
        return VectorStorageProvider.of(TYPE_MEMORY)
                .dimension(DIM)
                .algorithm(VectorCompareAlgorithm.cosine())
                .build();
    }

    /**
     * 创建 jvector 存储（根据 mode）。
     */
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

        return VectorStorageProvider.of(TYPE_JVECTOR)
                .dimension(DIM)
                .algorithm(VectorCompareAlgorithm.cosine())
                .properties(props)
                .build();
    }

    /**
     * 向指定存储添加 N 个随机向量。
     */
    private void seedAndSearch(VectorStorage storage, String label) {
        var rnd = new Random(42);
        for (int i = 0; i < N; i++) {
            storage.add(label + "-" + i, randomVector(rnd, DIM));
        }
        List<Vector> results = storage.search(randomVector(rnd, DIM), TOP_K);
        String firstId = results.isEmpty() ? "<empty>" : results.get(0).id();
        log.info("    搜索 Top-{} 结果: firstId={}", TOP_K, firstId);
    }

    /**
     * 向 jvector 存储添加 N 个随机向量。
     */
    private void seedJVector(VectorStorage storage, String label) {
        var rnd = new Random(42);
        for (int i = 0; i < N; i++) {
            storage.add(label + "-" + i, randomVector(rnd, DIM));
        }
    }

    /**
     * 搜索并打印 Top-1 结果。
     */
    private void searchAndLogTop1(VectorStorage storage) {
        List<Vector> results = storage.search(randomVector(new Random(7), DIM), TOP_K);
        if (!results.isEmpty()) {
            Object rawScore = results.get(0).metadata().get("score");
            double score = rawScore instanceof Number ? ((Number) rawScore).doubleValue() : 0d;
            log.info("    Top-1 id={}, score={}", results.get(0).id(), score);
        }
    }

    /**
     * 生成指定维度的随机向量。
     */
    private static float[] randomVector(Random rnd, int dim) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rnd.nextFloat();
        }
        return v;
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
            boolean removed = s.remove("del_test");
            assertEquals(true, removed, "milvus 删除存在的 id");
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
            boolean updated = s.update("upsert_key", new float[]{0f, 1f, 0f, 0f});
            assertEquals(true, updated, "milvus 更新存在的 id");
            pass();
            return true;
        } catch (Exception e) {
            fail("milvus upsert 异常: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(s);
        }
    }

    // ==================== 断言工具 ====================

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

    private static void assertNotNull(Object obj) {
        if (obj == null) {
            throw new AssertionError("断言失败: 对象不应为 null");
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
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    // ==================== 参数解析 ====================

    /**
     * 解析命令行参数。
     *
     * @param args 命令行参数数组
     * @return 参数对象
     */
    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--type", "-t" -> {
                    if (index + 1 < args.length) {
                        result = result.withType(args[++index]);
                    }
                }
                case "--mode", "-m" -> {
                    if (index + 1 < args.length) {
                        result = result.withMode(
                                JVectorStorageProperties.Mode.valueOf(args[++index].toUpperCase()));
                    }
                }
                case "--host", "-H" -> {
                    if (index + 1 < args.length) {
                        result = result.withHost(args[++index]);
                    }
                }
                case "--port", "-P" -> {
                    if (index + 1 < args.length) {
                        result = result.withPort(Integer.parseInt(args[++index]));
                    }
                }
                case "--collection", "-c" -> {
                    if (index + 1 < args.length) {
                        result = result.withCollection(args[++index]);
                    }
                }
                case "--token" -> {
                    if (index + 1 < args.length) {
                        result = result.withToken(args[++index]);
                    }
                }
                case "--help", "-h" -> result = result.withHelp(true);
                default -> System.err.println("[WARN] 未知参数: " + args[index]);
            }
            index++;
        }
        return result;
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("向量存储综合示例 — 基于 VectorStorage SPI");
        System.out.println();
        System.out.println("用法: java VectorStorageExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --type, -t <key>    实现类型（默认: memory，可选: memory / jvector / milvus）");
        System.out.println("  --mode, -m <mode>    JVector 存储模式（默认: MEMORY，可选: MEMORY / ON_DISK / LARGER_THAN_MEMORY）");
        System.out.println("  --host, -H <host>    Milvus 服务地址（默认: 127.0.0.1）");
        System.out.println("  --port, -P <port>    Milvus 服务端口（默认: 19530）");
        System.out.println("  --collection, -c <name>  Milvus collection 名称（默认: vector_store）");
        System.out.println("  --token, -t <token>    Milvus 认证令牌（可选）");
        System.out.println("  --help, -h           显示此帮助");
    }

    // ==================== 参数容器 ====================

    /**
     * 命令行参数容器。
     *
     * @param type SPI 类型标识
     * @param mode JVector 存储模式
     * @param help 是否打印帮助
     * @author CH
     * @since 4.0.0.42
     */
    private record Args(
        String type,
        JVectorStorageProperties.Mode mode,
        boolean help,
        String host,
        int port,
        String collection,
        String token
    ) {
        /**
         * 带默认值的空参构造。
         */
        Args() {
            this(null, null, false, "127.0.0.1", 19530, "vector_store", null);
        }

        /**
         * 替换 type 字段，返回新实例。
         */
        public Args withType(String type) {
            return new Args(type, mode, help, host, port, collection, token);
        }

        /**
         * 替换 mode 字段，返回新实例。
         */
        public Args withMode(JVectorStorageProperties.Mode mode) {
            return new Args(type, mode, help, host, port, collection, token);
        }

        /**
         * 替换 help 字段，返回新实例。
         */
        public Args withHelp(boolean help) {
            return new Args(type, mode, help, host, port, collection, token);
        }

        /**
         * 替换 host 字段，返回新实例。
         */
        public Args withHost(String host) {
            return new Args(type, mode, help, host, port, collection, token);
        }

        /**
         * 替换 port 字段，返回新实例。
         */
        public Args withPort(int port) {
            return new Args(type, mode, help, host, port, collection, token);
        }

        /**
         * 替换 collection 字段，返回新实例。
         */
        public Args withCollection(String collection) {
            return new Args(type, mode, help, host, port, collection, token);
        }

        public Args withToken(String token) {
            return new Args(type, mode, help, host, port, collection, token);
        }
    }
}


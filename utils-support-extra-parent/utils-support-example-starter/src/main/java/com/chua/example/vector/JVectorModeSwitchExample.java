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
 * JVector 向量存储模式切换示例。
 *
 * <p>演示通过 {@link VectorStorageProvider} SPI 使用同一个 "jvector" 名称，
 * 切换三种内部存储策略：</p>
 * <ul>
 *   <li>MEMORY — 纯内存 HNSW 图，进程退出即丢失，不写磁盘</li>
 *   <li>ON_DISK — 构建内存图后写入磁盘({@code OnDiskGraphIndex.write})，
 *       下次启动从磁盘加载({@code OnDiskGraphIndex.load})</li>
 *   <li>LARGER_THAN_MEMORY — PQ 压缩向量常驻内存，全精度向量在磁盘，
 *       使用两阶段搜索（粗排+精排）</li>
 * </ul>
 *
 * @author CH
 */
public class JVectorModeSwitchExample {

    private static final int DIM = 8;
    private static final int N = 100;
    private static final int TOP_K = 5;

    public static void main(String[] args) {
        log("[1] 已注册的向量存储实现:");
        VectorStorageProvider.providers().forEach(p -> log("    - " + p));

        log("\n[2] 使用同一个 'jvector' SPI 名称，切换三种内部模式");
        for (var mode : JVectorStorageProperties.Mode.values()) {
            runOnce(mode.name().toLowerCase(), mode);
        }
        log("\n说明：");
        log("  - MEMORY: 纯内存，不写磁盘");
        log("  - ON_DISK: 写入 " + System.getProperty("user.dir", ".") + "/jvector-index-*");
        log("  - LARGER_THAN_MEMORY: PQ 在内存，不写文件");
    }

    private static void runOnce(String label, JVectorStorageProperties.Mode mode) {
        log("\n    --- " + label + " (mode=" + mode + ") ---");

        var props = new JVectorStorageProperties();
        props.setMode(mode);
        props.setIndexPath("./jvector-index-" + label);

        VectorStorage storage;
        try {
            // 链式风格：of("jvector") → dimension() → algorithm() → properties() → build()
            storage = VectorStorageProvider.of("jvector")
                    .dimension(DIM)
                    .algorithm(VectorCompareAlgorithm.cosine())
                    .properties(props)
                    .build();
        } catch (Exception e) {
            log("    ! 创建失败: " + e.getMessage());
            return;
        }

        seed(storage);
        search(storage);
        log("    当前数据量：size=" + storage.size());

        // ON_DISK 模式下验证磁盘文件已生成
        if (mode == JVectorStorageProperties.Mode.ON_DISK) {
            var indexPath = Paths.get(props.getIndexPath());
            log("    磁盘文件: " + indexPath.toAbsolutePath()
                    + " exists=" + Files.exists(indexPath));
        }

        storage.close();
    }

    private static void seed(VectorStorage storage) {
        var rnd = new Random(42);
        for (int i = 0; i < N; i++) {
            storage.add("id-" + i, randomVector(rnd, DIM));
        }
    }

    private static void search(VectorStorage storage) {
        var query = randomVector(new Random(7), DIM);
        List<Vector> results = storage.search(query, TOP_K);
        if (!results.isEmpty()) {
            log("    Top-1 id=" + results.get(0).id()
                    + ", score=" + results.get(0).metadata().get("score"));
        }
    }

    private static float[] randomVector(Random rnd, int dim) {
        var v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rnd.nextFloat();
        }
        return v;
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}

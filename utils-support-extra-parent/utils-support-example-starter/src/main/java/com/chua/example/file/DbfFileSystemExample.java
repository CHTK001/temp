package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DBF (dBASE) 文件系统独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 DBF（Map 列表）</li>
 *   <li>读 DBF（rows）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>POJO 写入 (write(Object))</li>
 *   <li>写过滤 (WriteBuilder.filter)</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.DbfFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DbfFileSystemExample {

    /**
     * 测试数据
     */
    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("code", "A001", "name", "原料甲", "qty", 100, "price", 12.5),
            Map.of("code", "A002", "name", "原料乙", "qty", 200, "price", 8.0),
            Map.of("code", "A003", "name", "原料丙", "qty", 50, "price", 25.0),
            Map.of("code", "A004", "name", "原料丁", "qty", 0, "price", 15.5)
    );

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 临时工作目录
     */
    private File workDir;

    /**
     * 全部测试是否通过
     */
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        DbfFileSystemExample example = new DbfFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        log.info("\n===== 注意 =====\nDBF 字段名最长 10 字符，自动截断。");

        testWriteAndRead();
        testFilter();
        testMapRows();
        testPojoWrite();
        testWriteFilter();
        cleanUp();

        log.info("========================================");
        log.info("[DbfFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("dbf-example-").toFile();
        log.info("[DBF] 工作目录: {}", workDir);
    }

    void testWriteAndRead() throws Exception {
        log.info("\n===== 1. 基础写入 + 读取 =====");
        File dbf = new File(workDir, "materials.dbf");
        FileSystem.create("dbf").write(dbf).write(TEST_DATA).finish();
        log.info("  [写入] {} bytes", dbf.length());

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        log.info("  [读取] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        log.info("\n===== 2. 行过滤 (qty>0) =====");
        File dbf = new File(workDir, "filter.dbf");
        FileSystem.create("dbf").write(dbf).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf)
                .filter(row -> ((Number) row.get("qty")).intValue() > 0)
                .rows();

        rows.forEach(row -> log.info("    {} qty={}", row.get("name"), row.get("qty")));
        check(rows.size() == 3, "期望 3 行");
    }

    void testMapRows() throws Exception {
        log.info("\n===== 3. 行数据转换 =====");
        File dbf = new File(workDir, "maprows.dbf");
        FileSystem.create("dbf").write(dbf).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    int qty = ((Number) m.get("qty")).intValue();
                    m.put("status", qty > 0 ? "有货" : "缺货");
                    return m;
                })
                .rows();

        rows.forEach(row -> log.info("    {} status={}", row.get("name"), row.get("status")));
        check(!rows.isEmpty() && rows.get(0).containsKey("status"), "缺少 status");
    }

    void testPojoWrite() throws Exception {
        log.info("\n===== 4. POJO 写入 =====");
        File dbf = new File(workDir, "pojo.dbf");
        List<Material> materials = List.of(
                new Material("B001", "钢材", 500, 3.5),
                new Material("B002", "木材", 300, 2.0)
        );
        FileSystem.create("dbf").write(dbf).write(materials).finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        log.info("  [POJO] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == materials.size(), "行数不匹配");
    }

    void testWriteFilter() throws Exception {
        log.info("\n===== 5. 写入行过滤 (qty>=100) =====");
        File dbf = new File(workDir, "write-filter.dbf");
        FileSystem.create("dbf").write(dbf)
                .filter(row -> ((Number) row.get("qty")).intValue() >= 100)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        log.info("  [写过滤] {} 行", rows.size());
        check(rows.size() == 2, "期望 2 行");
    }

    void cleanUp() {
        deleteDir(workDir);
    }

    private void check(boolean condition, String msg) {
        if (!condition) {
            log.info("  [FAIL] {}", msg);
            allPassed = false;
        } else {
            log.info("  [PASS]");
        }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * 测试物料实体。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Material {
        /**
         * 编码
         */
        private String code;

        /**
         * 名称
         */
        private String name;

        /**
         * 数量
         */
        private int qty;

        /**
         * 单价
         */
        private double price;

        /**
         * 全参构造。
         *
         * @param code  编码
         * @param name  名称
         * @param qty   数量
         * @param price 单价
         */
        public Material(String code, String name, int qty, double price) {
            this.code = code;
            this.name = name;
            this.qty = qty;
            this.price = price;
        }
    }
}

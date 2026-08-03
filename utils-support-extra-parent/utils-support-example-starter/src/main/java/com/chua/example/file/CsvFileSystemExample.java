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
 * CsvFileSystem 独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 CSV（Map 列表 / POJO 对象）</li>
 *   <li>读 CSV（rows / read）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>写过滤 (WriteBuilder.filter)</li>
 *   <li>POJO 写入 (write(Object))</li>
 * </ul>
 *
 * <pre>{@code
 * // 运行
 * java com.chua.example.file.CsvFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class CsvFileSystemExample {

    /**
     * 测试数据
     */
    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("id", 1, "name", "张三", "age", 25, "score", 88.5),
            Map.of("id", 2, "name", "李四", "age", 30, "score", 92.0),
            Map.of("id", 3, "name", "王五", "age", 18, "score", 75.5),
            Map.of("id", 4, "name", "赵六", "age", 22, "score", 67.0),
            Map.of("id", 5, "name", "钱七", "age", 28, "score", 95.0)
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
        CsvFileSystemExample example = new CsvFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testWriteAndRead();
        testFilter();
        testMapRows();
        testWriteFilter();
        testPojoWrite();
        cleanUp();

        log.info("========================================");
        log.info("[CsvFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("csv-example-").toFile();
        log.info("[CSV] 工作目录: {}", workDir);
    }

    void testWriteAndRead() throws Exception {
        log.info("\n===== 1. 基础写入 + 读取 =====");
        File csv = new File(workDir, "users.csv");
        FileSystem.create("csv").write(csv).write(TEST_DATA).finish();
        log.info("  [写入] {} bytes", csv.length());

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        log.info("  [读取] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        log.info("\n===== 2. 行过滤 (age>=25) =====");
        File csv = new File(workDir, "filter.csv");
        FileSystem.create("csv").write(csv).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv)
                .filter(row -> ((Number) row.get("age")).intValue() >= 25)
                .rows();

        log.info("  [过滤] {} 行", rows.size());
        rows.forEach(row -> log.info("    {} age={}", row.get("name"), row.get("age")));
        check(rows.size() == 3, "期望 3 行");
    }

    void testMapRows() throws Exception {
        log.info("\n===== 3. 行数据转换 (mapRows) =====");
        File csv = new File(workDir, "maprows.csv");
        FileSystem.create("csv").write(csv).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    int age = ((Number) m.get("age")).intValue();
                    m.put("age_group", age >= 18 ? "成年" : "未成年");
                    m.remove("score");
                    return m;
                })
                .rows();

        log.info("  [mapRows] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(!rows.isEmpty() && rows.get(0).containsKey("age_group"), "缺少 age_group");
    }

    void testWriteFilter() throws Exception {
        log.info("\n===== 4. 写入行过滤 (age>=20) =====");
        File csv = new File(workDir, "write-filter.csv");
        FileSystem.create("csv").write(csv)
                .filter(row -> ((Number) row.get("age")).intValue() >= 20)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        log.info("  [写过滤] {} 行", rows.size());
        rows.forEach(row -> log.info("    {} age={}", row.get("name"), row.get("age")));
        check(rows.size() == 4, "期望 4 行");
    }

    void testPojoWrite() throws Exception {
        log.info("\n===== 5. POJO 写入 =====");
        File csv = new File(workDir, "pojo.csv");
        List<User> users = List.of(
                new User(1, "Alice", 28, 88.5),
                new User(2, "Bob", 35, 92.0),
                new User(3, "Charlie", 22, 76.0)
        );
        FileSystem.create("csv").write(csv).write(users).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        log.info("  [POJO] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == users.size(), "行数不匹配");
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
     * 测试用户实体。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class User {
        /**
         * 主键
         */
        private int id;

        /**
         * 姓名
         */
        private String name;

        /**
         * 年龄
         */
        private int age;

        /**
         * 分数
         */
        private double score;

        /**
         * 无参构造。
         */
        public User() {
        }

        /**
         * 全参构造。
         *
         * @param id    主键
         * @param name  姓名
         * @param age   年龄
         * @param score 分数
         */
        public User(int id, String name, int age, double score) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.score = score;
        }
    }
}
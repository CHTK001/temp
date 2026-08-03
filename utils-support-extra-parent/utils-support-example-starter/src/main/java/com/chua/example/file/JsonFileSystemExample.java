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
 * JsonFileSystem 独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 JSON（表格格式 [[header],[val],...]）</li>
 *   <li>读 JSON（rows / toMap / toObject）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>POJO 写入 (write(Object))</li>
 *   <li>写过滤 (WriteBuilder.filter)</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.JsonFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class JsonFileSystemExample {

    /**
     * 测试数据
     */
    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("id", 1, "name", "张三", "dept", "技术部", "salary", 15000),
            Map.of("id", 2, "name", "李四", "dept", "市场部", "salary", 12000),
            Map.of("id", 3, "name", "王五", "dept", "技术部", "salary", 18000),
            Map.of("id", 4, "name", "赵六", "dept", "行政部", "salary", 9000)
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
        JsonFileSystemExample example = new JsonFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testWriteAndRead();
        testFilter();
        testMapRows();
        testPojoWrite();
        testWriteFilter();
        cleanUp();

        log.info("========================================");
        log.info("[JsonFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("json-example-").toFile();
        log.info("[JSON] 工作目录: {}", workDir);
    }

    void testWriteAndRead() throws Exception {
        log.info("\n===== 1. 基础写入 + 读取 =====");
        File json = new File(workDir, "users.json");
        FileSystem.create("json").write(json).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json).rows();
        log.info("  [读取] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        log.info("\n===== 2. 行过滤 (salary>=12000) =====");
        File json = new File(workDir, "filter.json");
        FileSystem.create("json").write(json).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json)
                .filter(row -> ((Number) row.get("salary")).intValue() >= 12000)
                .rows();

        rows.forEach(row -> log.info("    {} salary={}", row.get("name"), row.get("salary")));
        check(rows.size() == 3, "期望 3 行");
    }

    void testMapRows() throws Exception {
        log.info("\n===== 3. 行数据转换 (mapRows) =====");
        File json = new File(workDir, "maprows.json");
        FileSystem.create("json").write(json).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    int salary = ((Number) m.get("salary")).intValue();
                    m.put("level", salary >= 15000 ? "高级" : salary >= 10000 ? "中级" : "初级");
                    return m;
                })
                .rows();

        rows.forEach(row -> log.info("    {} level={}", row.get("name"), row.get("level")));
        check(!rows.isEmpty() && rows.get(0).containsKey("level"), "缺少 level");
    }

    void testPojoWrite() throws Exception {
        log.info("\n===== 4. POJO 写入 =====");
        File json = new File(workDir, "pojo.json");
        List<User> users = List.of(
                new User(1, "Alice", "Engineering"),
                new User(2, "Bob", "Marketing")
        );
        FileSystem.create("json").write(json).write(users).finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json).rows();
        log.info("  [POJO] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == users.size(), "行数不匹配");
    }

    void testWriteFilter() throws Exception {
        log.info("\n===== 5. 写入行过滤 (技术部) =====");
        File json = new File(workDir, "write-filter.json");
        FileSystem.create("json").write(json)
                .filter(row -> "技术部".equals(row.get("dept")))
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json).rows();
        log.info("  [写过滤] {} 行", rows.size());
        rows.forEach(row -> log.info("    {} dept={}", row.get("name"), row.get("dept")));
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
         * 部门
         */
        private String dept;

        /**
         * 全参构造。
         *
         * @param id   主键
         * @param name 姓名
         * @param dept 部门
         */
        public User(int id, String name, String dept) {
            this.id = id;
            this.name = name;
            this.dept = dept;
        }
    }
}

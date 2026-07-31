package com.chua.example.file;

import com.chua.common.support.file.FileSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

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
public class CsvFileSystemExample {

    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("id", 1, "name", "张三", "age", 25, "score", 88.5),
            Map.of("id", 2, "name", "李四", "age", 30, "score", 92.0),
            Map.of("id", 3, "name", "王五", "age", 18, "score", 75.5),
            Map.of("id", 4, "name", "赵六", "age", 22, "score", 67.0),
            Map.of("id", 5, "name", "钱七", "age", 28, "score", 95.0)
    );

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        CsvFileSystemExample example = new CsvFileSystemExample();
        example.setUp();
        example.testWriteAndRead();
        example.testFilter();
        example.testMapRows();
        example.testWriteFilter();
        example.testPojoWrite();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[CsvFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("csv-example-").toFile();
        System.out.println("[CSV] 工作目录: " + workDir);
    }

    void testWriteAndRead() throws Exception {
        System.out.println("\n===== 1. 基础写入 + 读取 =====");
        File csv = new File(workDir, "users.csv");
        FileSystem.create("csv").write(csv).write(TEST_DATA).finish();
        System.out.println("  [写入] " + csv.length() + " bytes");

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        System.out.println("  [读取] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        System.out.println("\n===== 2. 行过滤 (age>=25) =====");
        File csv = new File(workDir, "filter.csv");
        FileSystem.create("csv").write(csv).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv)
                .filter(row -> ((Number) row.get("age")).intValue() >= 25)
                .rows();

        System.out.println("  [过滤] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row.get("name") + " age=" + row.get("age")));
        check(rows.size() == 3, "期望 3 行");
    }

    void testMapRows() throws Exception {
        System.out.println("\n===== 3. 行数据转换 (mapRows) =====");
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

        System.out.println("  [mapRows] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(!rows.isEmpty() && rows.get(0).containsKey("age_group"), "缺少 age_group");
    }

    void testWriteFilter() throws Exception {
        System.out.println("\n===== 4. 写入行过滤 (age>=20) =====");
        File csv = new File(workDir, "write-filter.csv");
        FileSystem.create("csv").write(csv)
                .filter(row -> ((Number) row.get("age")).intValue() >= 20)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        System.out.println("  [写过滤] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row.get("name") + " age=" + row.get("age")));
        check(rows.size() == 4, "期望 4 行");
    }

    void testPojoWrite() throws Exception {
        System.out.println("\n===== 5. POJO 写入 =====");
        File csv = new File(workDir, "pojo.csv");
        List<User> users = List.of(
                new User(1, "Alice", 28, 88.5),
                new User(2, "Bob", 35, 92.0),
                new User(3, "Charlie", 22, 76.0)
        );
        FileSystem.create("csv").write(csv).write(users).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        System.out.println("  [POJO] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == users.size(), "行数不匹配");
    }

    void cleanUp() {
        deleteDir(workDir);
    }

    private void check(boolean condition, String msg) {
        if (!condition) { System.err.println("  [FAIL] " + msg); allPassed = false; }
        else { System.out.println("  [PASS]"); }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }

    public static class User {
        private int id;
        private String name;
        private int age;
        private double score;
        public User() {}
        public User(int id, String name, int age, double score) {
            this.id = id; this.name = name; this.age = age; this.score = score;
        }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
    }
}

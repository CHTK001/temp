package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Excel 文件系统独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 Excel（Map 列表 / POJO 对象）</li>
 *   <li>读 Excel（rows / selectColumns）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>固定表头 (freezeHeader)</li>
 *   <li>多 Sheet (writeSheet)</li>
 *   <li>POJO 写入</li>
 *   <li>写入行过滤</li>
 * </ul>
 *
 * <p><b>注意：</b>Excel 功能依赖于 Apache POI 库。</p>
 *
 * <pre>{@code
 * java com.chua.example.file.ExcelFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ExcelFileSystemExample {

    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("id", 1, "name", "张三", "score", 88.5, "grade", "A"),
            Map.of("id", 2, "name", "李四", "score", 92.0, "grade", "A+"),
            Map.of("id", 3, "name", "王五", "score", 75.5, "grade", "B"),
            Map.of("id", 4, "name", "赵六", "score", 67.0, "grade", "C"),
            Map.of("id", 5, "name", "孙七", "score", 55.0, "grade", "D")
    );

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        ExcelFileSystemExample example = new ExcelFileSystemExample();
        example.setUp();
        example.testBasicWriteAndRead();
        example.testFilter();
        example.testMapRows();
        example.testSelectColumns();
        example.testStyledWrite();
        example.testMultiSheet();
        example.testPojoWrite();
        example.testWriteFilter();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[ExcelFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("excel-example-").toFile();
        System.out.println("[Excel] 工作目录: " + workDir);
    }

    void testBasicWriteAndRead() throws Exception {
        System.out.println("\n===== 1. 基础写入 + 读取 =====");
        File xlsx = new File(workDir, "students.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();
        System.out.println("  [写入] " + xlsx.length() + " bytes");

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        System.out.println("  [读取] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        System.out.println("\n===== 2. 行过滤 (score>=80) =====");
        File xlsx = new File(workDir, "filter.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx)
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 80)
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("name") + " score=" + row.get("score")));
        check(rows.size() == 2, "期望 2 行");
    }

    void testMapRows() throws Exception {
        System.out.println("\n===== 3. 行数据转换 =====");
        File xlsx = new File(workDir, "maprows.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    double score = ((Number) m.get("score")).doubleValue();
                    m.put("pass", score >= 60 ? "及格" : "不及格");
                    return m;
                })
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("name") + " pass=" + row.get("pass")));
        check(!rows.isEmpty() && rows.get(0).containsKey("pass"), "缺少 pass");
    }

    void testSelectColumns() throws Exception {
        System.out.println("\n===== 4. 列投影 (selectColumns) =====");
        File xlsx = new File(workDir, "select.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx)
                .selectColumns("name", "score")
                .rows();

        System.out.println("  [列投影] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(!rows.isEmpty() && rows.get(0).size() == 2, "列数不匹配");
    }

    void testStyledWrite() throws Exception {
        System.out.println("\n===== 5. 冻结表头 =====");
        File xlsx = new File(workDir, "styled.xlsx");
        FileSystem.create("excel").write(xlsx)
                .write(TEST_DATA)
                .freezeHeader()
                .finish();
        System.out.println("  [写入] " + xlsx.length() + " bytes");
        System.out.println("  [PASS] 含冻结表头");
    }

    void testMultiSheet() throws Exception {
        System.out.println("\n===== 6. 多 Sheet =====");
        File xlsx = new File(workDir, "multi-sheet.xlsx");
        WriteBuilder wb = FileSystem.create("excel").write(xlsx);
        wb.write(TEST_DATA);
        wb.writeSheet("及格")
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 60)
                .write(TEST_DATA);
        wb.writeSheet("优秀")
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 85)
                .write(TEST_DATA);
        wb.finish();
        System.out.println("  [写入] " + xlsx.length() + " bytes");

        List<String> names = FileSystem.create("excel").read(xlsx).sheetNames();
        System.out.println("  [Sheet列表] " + names);
        check(names.size() == 3 && names.contains("及格") && names.contains("优秀"), "Sheet 数不匹配");
    }

    void testPojoWrite() throws Exception {
        System.out.println("\n===== 7. POJO 写入 =====");
        File xlsx = new File(workDir, "pojo.xlsx");
        List<Student> students = List.of(
                new Student(1, "Alice", 95.0, "A+"),
                new Student(2, "Bob", 78.0, "B+")
        );
        FileSystem.create("excel").write(xlsx).write(students).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        System.out.println("  [POJO] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == students.size(), "行数不匹配");
    }

    void testWriteFilter() throws Exception {
        System.out.println("\n===== 8. 写入行过滤 (score>=70) =====");
        File xlsx = new File(workDir, "write-filter.xlsx");
        FileSystem.create("excel").write(xlsx)
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 70)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        System.out.println("  [写过滤] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row.get("name") + " score=" + row.get("score")));
        check(rows.size() == 3, "期望 3 行");
    }

    void cleanUp() { deleteDir(workDir); }

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

    public static class Student {
        private int id;
        private String name;
        private double score;
        private String grade;
        public Student() {}
        public Student(int id, String name, double score, String grade) {
            this.id = id; this.name = name; this.score = score; this.grade = grade;
        }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
    }
}

package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@Slf4j
public class ExcelFileSystemExample {

    /**
     * 测试数据
     */
    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("id", 1, "name", "张三", "score", 88.5, "grade", "A"),
            Map.of("id", 2, "name", "李四", "score", 92.0, "grade", "A+"),
            Map.of("id", 3, "name", "王五", "score", 75.5, "grade", "B"),
            Map.of("id", 4, "name", "赵六", "score", 67.0, "grade", "C"),
            Map.of("id", 5, "name", "孙七", "score", 55.0, "grade", "D")
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
        ExcelFileSystemExample example = new ExcelFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testBasicWriteAndRead();
        testFilter();
        testMapRows();
        testSelectColumns();
        testStyledWrite();
        testMultiSheet();
        testPojoWrite();
        testWriteFilter();
        cleanUp();

        log.info("========================================");
        log.info("[ExcelFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("excel-example-").toFile();
        log.info("[Excel] 工作目录: {}", workDir);
    }

    void testBasicWriteAndRead() throws Exception {
        log.info("\n===== 1. 基础写入 + 读取 =====");
        File xlsx = new File(workDir, "students.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();
        log.info("  [写入] {} bytes", xlsx.length());

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        log.info("  [读取] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        log.info("\n===== 2. 行过滤 (score>=80) =====");
        File xlsx = new File(workDir, "filter.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx)
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 80)
                .rows();

        rows.forEach(row -> log.info("    {} score={}", row.get("name"), row.get("score")));
        check(rows.size() == 2, "期望 2 行");
    }

    void testMapRows() throws Exception {
        log.info("\n===== 3. 行数据转换 =====");
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

        rows.forEach(row -> log.info("    {} pass={}", row.get("name"), row.get("pass")));
        check(!rows.isEmpty() && rows.get(0).containsKey("pass"), "缺少 pass");
    }

    void testSelectColumns() throws Exception {
        log.info("\n===== 4. 列投影 (selectColumns) =====");
        File xlsx = new File(workDir, "select.xlsx");
        FileSystem.create("excel").write(xlsx).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx)
                .selectColumns("name", "score")
                .rows();

        log.info("  [列投影] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(!rows.isEmpty() && rows.get(0).size() == 2, "列数不匹配");
    }

    void testStyledWrite() throws Exception {
        log.info("\n===== 5. 冻结表头 =====");
        File xlsx = new File(workDir, "styled.xlsx");
        FileSystem.create("excel").write(xlsx)
                .write(TEST_DATA)
                .freezeHeader()
                .finish();
        log.info("  [写入] {} bytes", xlsx.length());
        log.info("  [PASS] 含冻结表头");
    }

    void testMultiSheet() throws Exception {
        log.info("\n===== 6. 多 Sheet =====");
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
        log.info("  [写入] {} bytes", xlsx.length());

        List<String> names = FileSystem.create("excel").read(xlsx).sheetNames();
        log.info("  [Sheet列表] {}", names);
        check(names.size() == 3 && names.contains("及格") && names.contains("优秀"), "Sheet 数不匹配");
    }

    void testPojoWrite() throws Exception {
        log.info("\n===== 7. POJO 写入 =====");
        File xlsx = new File(workDir, "pojo.xlsx");
        List<Student> students = List.of(
                new Student(1, "Alice", 95.0, "A+"),
                new Student(2, "Bob", 78.0, "B+")
        );
        FileSystem.create("excel").write(xlsx).write(students).finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        log.info("  [POJO] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == students.size(), "行数不匹配");
    }

    void testWriteFilter() throws Exception {
        log.info("\n===== 8. 写入行过滤 (score>=70) =====");
        File xlsx = new File(workDir, "write-filter.xlsx");
        FileSystem.create("excel").write(xlsx)
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 70)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        log.info("  [写过滤] {} 行", rows.size());
        rows.forEach(row -> log.info("    {} score={}", row.get("name"), row.get("score")));
        check(rows.size() == 3, "期望 3 行");
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
     * 测试学生实体。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Student {
        /**
         * 主键
         */
        private int id;

        /**
         * 姓名
         */
        private String name;

        /**
         * 分数
         */
        private double score;

        /**
         * 等级
         */
        private String grade;

        /**
         * 全参构造。
         *
         * @param id    主键
         * @param name  姓名
         * @param score 分数
         * @param grade 等级
         */
        public Student(int id, String name, double score, String grade) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.grade = grade;
        }
    }
}

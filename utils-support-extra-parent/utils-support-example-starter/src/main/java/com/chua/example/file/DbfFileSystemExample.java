package com.chua.example.file;

import com.chua.common.support.file.FileSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

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
public class DbfFileSystemExample {

    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("code", "A001", "name", "原料甲", "qty", 100, "price", 12.5),
            Map.of("code", "A002", "name", "原料乙", "qty", 200, "price", 8.0),
            Map.of("code", "A003", "name", "原料丙", "qty", 50, "price", 25.0),
            Map.of("code", "A004", "name", "原料丁", "qty", 0, "price", 15.5)
    );

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        DbfFileSystemExample example = new DbfFileSystemExample();
        example.setUp();
        System.out.println("\n===== 注意 =====\nDBF 字段名最长 10 字符，自动截断。");

        example.testWriteAndRead();
        example.testFilter();
        example.testMapRows();
        example.testPojoWrite();
        example.testWriteFilter();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[DbfFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("dbf-example-").toFile();
        System.out.println("[DBF] 工作目录: " + workDir);
    }

    void testWriteAndRead() throws Exception {
        System.out.println("\n===== 1. 基础写入 + 读取 =====");
        File dbf = new File(workDir, "materials.dbf");
        FileSystem.create("dbf").write(dbf).write(TEST_DATA).finish();
        System.out.println("  [写入] " + dbf.length() + " bytes");

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        System.out.println("  [读取] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        System.out.println("\n===== 2. 行过滤 (qty>0) =====");
        File dbf = new File(workDir, "filter.dbf");
        FileSystem.create("dbf").write(dbf).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf)
                .filter(row -> ((Number) row.get("qty")).intValue() > 0)
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("name") + " qty=" + row.get("qty")));
        check(rows.size() == 3, "期望 3 行");
    }

    void testMapRows() throws Exception {
        System.out.println("\n===== 3. 行数据转换 =====");
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

        rows.forEach(row -> System.out.println("    " + row.get("name") + " status=" + row.get("status")));
        check(!rows.isEmpty() && rows.get(0).containsKey("status"), "缺少 status");
    }

    void testPojoWrite() throws Exception {
        System.out.println("\n===== 4. POJO 写入 =====");
        File dbf = new File(workDir, "pojo.dbf");
        List<Material> materials = List.of(
                new Material("B001", "钢材", 500, 3.5),
                new Material("B002", "木材", 300, 2.0)
        );
        FileSystem.create("dbf").write(dbf).write(materials).finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        System.out.println("  [POJO] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == materials.size(), "行数不匹配");
    }

    void testWriteFilter() throws Exception {
        System.out.println("\n===== 5. 写入行过滤 (qty>=100) =====");
        File dbf = new File(workDir, "write-filter.dbf");
        FileSystem.create("dbf").write(dbf)
                .filter(row -> ((Number) row.get("qty")).intValue() >= 100)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        System.out.println("  [写过滤] " + rows.size() + " 行");
        check(rows.size() == 2, "期望 2 行");
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

    public static class Material {
        private String code;
        private String name;
        private int qty;
        private double price;
        public Material() {}
        public Material(String code, String name, int qty, double price) {
            this.code = code; this.name = name; this.qty = qty; this.price = price;
        }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getQty() { return qty; }
        public void setQty(int qty) { this.qty = qty; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
    }
}

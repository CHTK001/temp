package com.chua.example.file;

import com.chua.common.support.file.FileSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * XmlFileSystem 独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 XML（Map 列表）</li>
 *   <li>读 XML（rows / toMap）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>POJO 写入 (write(Object))</li>
 *   <li>写过滤 (WriteBuilder.filter)</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.XmlFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class XmlFileSystemExample {

    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("id", 1, "name", "商品A", "price", 29.9, "stock", 100),
            Map.of("id", 2, "name", "商品B", "price", 49.9, "stock", 50),
            Map.of("id", 3, "name", "商品C", "price", 9.9, "stock", 200),
            Map.of("id", 4, "name", "商品D", "price", 99.0, "stock", 10)
    );

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        XmlFileSystemExample example = new XmlFileSystemExample();
        example.setUp();
        example.testWriteAndRead();
        example.testFilter();
        example.testMapRows();
        example.testPojoWrite();
        example.testWriteFilter();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[XmlFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("xml-example-").toFile();
        System.out.println("[XML] 工作目录: " + workDir);
    }

    void testWriteAndRead() throws Exception {
        System.out.println("\n===== 1. 基础写入 + 读取 =====");
        File xml = new File(workDir, "products.xml");
        FileSystem.create("xml").write(xml).write(TEST_DATA).finish();

        String content = new String(Files.readAllBytes(xml.toPath()));
        System.out.println("  [写入内容]\n" + content);

        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml).rows();
        System.out.println("  [读取] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        System.out.println("\n===== 2. 行过滤 (price<50) =====");
        File xml = new File(workDir, "filter.xml");
        FileSystem.create("xml").write(xml).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml)
                .filter(row -> ((Number) row.get("price")).doubleValue() < 50)
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("name") + " price=" + row.get("price")));
        check(rows.size() == 3, "期望 3 行");
    }

    void testMapRows() throws Exception {
        System.out.println("\n===== 3. 行数据转换 (mapRows) =====");
        File xml = new File(workDir, "maprows.xml");
        FileSystem.create("xml").write(xml).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    double price = ((Number) m.get("price")).doubleValue();
                    m.put("price_level", price >= 50 ? "高价" : price >= 20 ? "中价" : "低价");
                    return m;
                })
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("name") + " price_level=" + row.get("price_level")));
        check(!rows.isEmpty() && rows.get(0).containsKey("price_level"), "缺少 price_level");
    }

    void testPojoWrite() throws Exception {
        System.out.println("\n===== 4. POJO 写入 =====");
        File xml = new File(workDir, "pojo.xml");
        List<Product> products = List.of(
                new Product(1, "Laptop", 5999.0, 30),
                new Product(2, "Mouse", 99.0, 200)
        );
        FileSystem.create("xml").write(xml).write(products).finish();

        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml).rows();
        System.out.println("  [POJO] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == products.size(), "行数不匹配");
    }

    void testWriteFilter() throws Exception {
        System.out.println("\n===== 5. 写入行过滤 (stock>=50) =====");
        File xml = new File(workDir, "write-filter.xml");
        FileSystem.create("xml").write(xml)
                .filter(row -> ((Number) row.get("stock")).intValue() >= 50)
                .write(TEST_DATA)
                .finish();

        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml).rows();
        System.out.println("  [写过滤] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row.get("name") + " stock=" + row.get("stock")));
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

    public static class Product {
        private int id;
        private String name;
        private double price;
        private int stock;
        public Product() {}
        public Product(int id, String name, double price, int stock) {
            this.id = id; this.name = name; this.price = price; this.stock = stock;
        }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public int getStock() { return stock; }
        public void setStock(int stock) { this.stock = stock; }
    }
}

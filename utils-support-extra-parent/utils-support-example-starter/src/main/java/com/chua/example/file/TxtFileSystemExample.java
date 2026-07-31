package com.chua.example.file;

import com.chua.common.support.file.FileSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * TxtFileSystem 独立完整测试示例（TAB 分隔文本）。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 TXT（Map 列表 → TAB 分隔）</li>
 *   <li>读 TXT（rows / asLines / asString）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>POJO 写入 (write(Object))</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.TxtFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class TxtFileSystemExample {

    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("city", "北京", "aqi", 55, "pm25", 35.2),
            Map.of("city", "上海", "aqi", 72, "pm25", 48.5),
            Map.of("city", "广州", "aqi", 48, "pm25", 28.1),
            Map.of("city", "深圳", "aqi", 38, "pm25", 22.3)
    );

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        TxtFileSystemExample example = new TxtFileSystemExample();
        example.setUp();
        example.testWriteAndRead();
        example.testFilter();
        example.testMapRows();
        example.testAsLines();
        example.testPojoWrite();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[TxtFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("txt-example-").toFile();
        System.out.println("[TXT] 工作目录: " + workDir);
    }

    void testWriteAndRead() throws Exception {
        System.out.println("\n===== 1. 基础写入 + 读取 =====");
        File txt = new File(workDir, "air.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        String content = new String(Files.readAllBytes(txt.toPath()));
        System.out.println("  [内容]\n" + content);

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt).rows();
        System.out.println("  [读取] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        System.out.println("\n===== 2. 行过滤 (aqi<50) =====");
        File txt = new File(workDir, "filter.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt)
                .filter(row -> ((Number) row.get("aqi")).intValue() < 50)
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("city") + " aqi=" + row.get("aqi")));
        check(rows.size() == 2, "期望 2 行");
    }

    void testMapRows() throws Exception {
        System.out.println("\n===== 3. 行数据转换 (mapRows) =====");
        File txt = new File(workDir, "maprows.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    int aqi = ((Number) m.get("aqi")).intValue();
                    m.put("level", aqi <= 50 ? "优" : aqi <= 100 ? "良" : "污染");
                    return m;
                })
                .rows();

        rows.forEach(row -> System.out.println("    " + row.get("city") + " level=" + row.get("level")));
        check(!rows.isEmpty() && rows.get(0).containsKey("level"), "缺少 level");
    }

    void testAsLines() throws Exception {
        System.out.println("\n===== 4. asLines =====");
        File txt = new File(workDir, "lines.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        List<String> lines = FileSystem.create("txt").read(txt).asLines();
        System.out.println("  [asLines] " + lines.size() + " 行");
        lines.forEach(line -> System.out.println("    " + line));
        check(lines.size() == TEST_DATA.size() + 1, "行数不匹配 (+1 header)");
    }

    void testPojoWrite() throws Exception {
        System.out.println("\n===== 5. POJO 写入 =====");
        File txt = new File(workDir, "pojo.txt");
        List<CityAir> cities = List.of(
                new CityAir("成都", 65, 42.1),
                new CityAir("杭州", 52, 33.5)
        );
        FileSystem.create("txt").write(txt).write(cities).finish();

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt).rows();
        System.out.println("  [POJO] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == cities.size(), "行数不匹配");
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

    public static class CityAir {
        private String city;
        private int aqi;
        private double pm25;
        public CityAir() {}
        public CityAir(String city, int aqi, double pm25) {
            this.city = city; this.aqi = aqi; this.pm25 = pm25;
        }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public int getAqi() { return aqi; }
        public void setAqi(int aqi) { this.aqi = aqi; }
        public double getPm25() { return pm25; }
        public void setPm25(double pm25) { this.pm25 = pm25; }
    }
}

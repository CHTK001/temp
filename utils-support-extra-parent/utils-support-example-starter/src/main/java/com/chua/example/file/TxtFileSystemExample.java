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
@Slf4j
public class TxtFileSystemExample {

    /**
     * 测试数据
     */
    private static final List<Map<String, Object>> TEST_DATA = List.of(
            Map.of("city", "北京", "aqi", 55, "pm25", 35.2),
            Map.of("city", "上海", "aqi", 72, "pm25", 48.5),
            Map.of("city", "广州", "aqi", 48, "pm25", 28.1),
            Map.of("city", "深圳", "aqi", 38, "pm25", 22.3)
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
        TxtFileSystemExample example = new TxtFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testWriteAndRead();
        testFilter();
        testMapRows();
        testAsLines();
        testPojoWrite();
        cleanUp();

        log.info("========================================");
        log.info("[TxtFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("txt-example-").toFile();
        log.info("[TXT] 工作目录: {}", workDir);
    }

    void testWriteAndRead() throws Exception {
        log.info("\n===== 1. 基础写入 + 读取 =====");
        File txt = new File(workDir, "air.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        String content = new String(Files.readAllBytes(txt.toPath()));
        log.info("  [内容]\n{}", content);

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt).rows();
        log.info("  [读取] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == TEST_DATA.size(), "行数不匹配");
    }

    void testFilter() throws Exception {
        log.info("\n===== 2. 行过滤 (aqi<50) =====");
        File txt = new File(workDir, "filter.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt)
                .filter(row -> ((Number) row.get("aqi")).intValue() < 50)
                .rows();

        rows.forEach(row -> log.info("    {} aqi={}", row.get("city"), row.get("aqi")));
        check(rows.size() == 2, "期望 2 行");
    }

    void testMapRows() throws Exception {
        log.info("\n===== 3. 行数据转换 (mapRows) =====");
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

        rows.forEach(row -> log.info("    {} level={}", row.get("city"), row.get("level")));
        check(!rows.isEmpty() && rows.get(0).containsKey("level"), "缺少 level");
    }

    void testAsLines() throws Exception {
        log.info("\n===== 4. asLines =====");
        File txt = new File(workDir, "lines.txt");
        FileSystem.create("txt").write(txt).write(TEST_DATA).finish();

        List<String> lines = FileSystem.create("txt").read(txt).asLines();
        log.info("  [asLines] {} 行", lines.size());
        lines.forEach(line -> log.info("    {}", line));
        check(lines.size() == TEST_DATA.size() + 1, "行数不匹配 (+1 header)");
    }

    void testPojoWrite() throws Exception {
        log.info("\n===== 5. POJO 写入 =====");
        File txt = new File(workDir, "pojo.txt");
        List<CityAir> cities = List.of(
                new CityAir("成都", 65, 42.1),
                new CityAir("杭州", 52, 33.5)
        );
        FileSystem.create("txt").write(txt).write(cities).finish();

        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt).rows();
        log.info("  [POJO] {} 行", rows.size());
        rows.forEach(row -> log.info("    {}", row));
        check(rows.size() == cities.size(), "行数不匹配");
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
     * 测试城市空气质量实体。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class CityAir {
        /**
         * 城市
         */
        private String city;

        /**
         * 空气质量指数
         */
        private int aqi;

        /**
         * PM2.5 浓度
         */
        private double pm25;

        /**
         * 全参构造。
         *
         * @param city 城市
         * @param aqi  空气质量指数
         * @param pm25 PM2.5 浓度
         */
        public CityAir(String city, int aqi, double pm25) {
            this.city = city;
            this.aqi = aqi;
            this.pm25 = pm25;
        }
    }
}

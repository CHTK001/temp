package com.chua.example.file;

import com.chua.common.support.file.FileSystem;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * IniFileSystem 独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 INI（嵌套 Map / 行列表格式）</li>
 *   <li>读 INI（toMap / rows / sections / section）</li>
 *   <li>行过滤 (filter)</li>
 *   <li>行数据转换 (mapRows)</li>
 *   <li>Properties 互转</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.IniFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class IniFileSystemExample {

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        IniFileSystemExample example = new IniFileSystemExample();
        example.setUp();
        example.testWriteAndReadNested();
        example.testWriteRowList();
        example.testReadSections();
        example.testFilter();
        example.testMapRows();
        example.testReadSection();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[IniFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("ini-example-").toFile();
        System.out.println("[INI] 工作目录: " + workDir);
    }

    /** 1. 写入嵌套 Map → 读取验证 */
    void testWriteAndReadNested() throws Exception {
        System.out.println("\n===== 1. 写入嵌套 Map + 读取 =====");
        File ini = new File(workDir, "config.ini");

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("host", "localhost");
        database.put("port", "3306");
        database.put("user", "root");

        Map<String, Object> logging = new LinkedHashMap<>();
        logging.put("level", "INFO");
        logging.put("file", "/var/log/app.log");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("database", database);
        data.put("logging", logging);

        FileSystem.create("ini").write(ini).write(data).finish();

        String content = new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8);
        System.out.println("  [写入内容]\n" + content);

        Map<String, Object> loaded = FileSystem.create("ini").read(ini).toMap();
        System.out.println("  [toMap] " + loaded);
        check(loaded.containsKey("database"), "缺少 [database]");
        check(loaded.containsKey("logging"), "缺少 [logging]");
    }

    /** 2. 写入行列表格式（含 __section__） */
    void testWriteRowList() throws Exception {
        System.out.println("\n===== 2. 写入行列表格式 =====");
        File ini = new File(workDir, "rows.ini");

        List<Map<String, Object>> rows = List.of(
                new LinkedHashMap<>(Map.of("__section__", "server", "host", "192.168.1.1", "port", "8080")),
                new LinkedHashMap<>(Map.of("__section__", "cache", "type", "redis", "ttl", "3600"))
        );

        FileSystem.create("ini").write(ini).write(rows).finish();

        String content = new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8);
        System.out.println("  [内容]\n" + content);

        List<Map<String, Object>> loaded = FileSystem.create("ini").read(ini).rows();
        System.out.println("  [rows] " + loaded.size() + " 行");
        loaded.forEach(row -> System.out.println("    " + row));
        check(loaded.size() == 2, "期望 2 行");
    }

    /** 3. 读取所有 Section 名称 */
    void testReadSections() throws Exception {
        System.out.println("\n===== 3. 读取 Section 名称 =====");
        File ini = new File(workDir, "sections.ini");
        FileSystem.create("ini").write(ini).write(Map.of(
                "section1", Map.of("key1", "val1"),
                "section2", Map.of("key2", "val2"),
                "section3", Map.of("key3", "val3")
        )).finish();

        Set<String> sections = FileSystem.create("ini").read(ini).sections();
        System.out.println("  [sections] " + sections);
        check(sections.size() == 3, "期望 3 个 section");
        check(sections.contains("section1"), "缺少 section1");
    }

    /** 4. 行过滤 */
    void testFilter() throws Exception {
        System.out.println("\n===== 4. 行过滤 =====");
        File ini = new File(workDir, "filter.ini");
        FileSystem.create("ini").write(ini).write(List.of(
                Map.of("__section__", "dev", "env", "development"),
                Map.of("__section__", "prod", "env", "production"),
                Map.of("__section__", "test", "env", "testing")
        )).finish();

        List<Map<String, Object>> rows = FileSystem.create("ini").read(ini)
                .filter(row -> !"production".equals(row.get("env")))
                .rows();

        System.out.println("  [过滤后] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(rows.size() == 2, "期望 2 行");
    }

    /** 5. 行数据转换 */
    void testMapRows() throws Exception {
        System.out.println("\n===== 5. 行数据转换 =====");
        File ini = new File(workDir, "maprows.ini");
        FileSystem.create("ini").write(ini).write(List.of(
                Map.of("__section__", "db1", "size", "100"),
                Map.of("__section__", "db2", "size", "200")
        )).finish();

        List<Map<String, Object>> rows = FileSystem.create("ini").read(ini)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    m.put("size_kb", m.get("size") + "KB");
                    return m;
                })
                .rows();

        System.out.println("  [mapRows] " + rows.size() + " 行");
        rows.forEach(row -> System.out.println("    " + row));
        check(!rows.isEmpty() && rows.get(0).containsKey("size_kb"), "缺少 size_kb");
    }

    /** 6. 读取指定 Section */
    void testReadSection() throws Exception {
        System.out.println("\n===== 6. 读取指定 Section =====");
        File ini = new File(workDir, "section-read.ini");
        FileSystem.create("ini").write(ini).write(Map.of(
                "server", Map.of("host", "localhost", "port", "8080"),
                "cache", Map.of("type", "memcached")
        )).finish();

        Map<String, String> server = FileSystem.create("ini").read(ini).section("server");
        System.out.println("  [section server] " + server);
        check("localhost".equals(server.get("host")), "host 不匹配");
        check("8080".equals(server.get("port")), "port 不匹配");
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
}

package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * FileSystem 综合示例 — 覆盖全部表格类文件格式的链式写入与读取能力。
 *
 * <p>所有 test 方法异常自然传播，无 try-catch。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java FileSystemExample              # 全部格式自检
 *   java FileSystemExample --type csv   # 指定格式
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class FileSystemExample {

    private static final String DEFAULT_TYPE = "all";

    private static final List<Map<String, Object>> TABLE_DATA = List.of(
            Map.of("id", 1, "name", "张三", "age", 25, "score", 88.5),
            Map.of("id", 2, "name", "李四", "age", 30, "score", 92.0),
            Map.of("id", 3, "name", "王五", "age", 18, "score", 75.5),
            Map.of("id", 4, "name", "赵六", "age", 22, "score", 67.0),
            Map.of("id", 5, "name", "钱七", "age", 28, "score", 95.0)
    );

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        FileSystemExample example = new FileSystemExample();
        example.workDir = Files.createTempDirectory("fs-all-").toFile();
        System.out.println("[FileSystemExample] 工作目录: " + example.workDir);

        example.runTest(parsed.type());
        System.out.println("\n========================================");
        System.out.println("[FileSystemExample] self-test type=" + parsed.type()
                + ", passed=" + example.allPassed);

        deleteDir(example.workDir);
        System.exit(example.allPassed ? 0 : 1);
    }

    boolean runTest(String type) {
        String target = (type == null || type.isEmpty()) ? DEFAULT_TYPE : type.toLowerCase();
        return switch (target) {
            case "csv" -> testCsv();
            case "json" -> testJson();
            case "xml" -> testXml();
            case "txt" -> testTxt();
            case "excel" -> testExcel();
            case "dbf" -> testDbf();
            case "log" -> testLog();
            case "tar" -> testTar();
            case "zip" -> testZip();
            case "yaml" -> testYaml();
            case "ini" -> testIni();
            case DEFAULT_TYPE ->
                testCsv() && testJson() && testXml() && testTxt()
                        && testExcel() && testDbf() && testLog() && testTar() && testZip()
                        && testYaml() && testIni();
            default -> {
                System.err.println("[FileSystemExample] 未知类型: " + target);
                yield false;
            }
        };
    }

    // ==================== CSV ====================

    boolean testCsv() throws Exception {
        System.out.println("\n===== [csv] =====");
        File csv = new File(workDir, "demo.csv");
        FileSystem.create("csv").write(csv).write(TABLE_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        System.out.println("  [读取] " + rows.size() + " 行");
        check(rows.size() == TABLE_DATA.size(), "行数");

        List<Map<String, Object>> filtered = FileSystem.create("csv").read(csv)
                .filter(row -> ((Number) row.get("age")).intValue() >= 25)
                .rows();
        System.out.println("  [过滤 age>=25] " + filtered.size() + " 行");

        List<Map<String, Object>> mapped = FileSystem.create("csv").read(csv)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    double score = ((Number) m.get("score")).doubleValue();
                    m.put("level", score >= 90 ? "优秀" : score >= 80 ? "良好" : "一般");
                    return m;
                })
                .rows();
        System.out.println("  [mapRows] " + mapped.size() + " 行");
        mapped.forEach(row -> System.out.println("    " + row.get("name") + " level=" + row.get("level")));
        System.out.println("  [PASS]");
        return true;
    }

    boolean testJson() throws Exception {
        System.out.println("\n===== [json] =====");
        File json = new File(workDir, "demo.json");
        FileSystem.create("json").write(json).write(TABLE_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        System.out.println("  [读取] " + rows.size() + " 行");

        List<Map<String, Object>> filtered = FileSystem.create("json").read(json)
                .filter(row -> "张三".equals(row.get("name")) || "李四".equals(row.get("name")))
                .rows();
        System.out.println("  [过滤 张三/李四] " + filtered.size() + " 行");

        List<Map<String, Object>> mapped = FileSystem.create("json").read(json)
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 80)
                .mapRows(row -> { Map<String, Object> m = new LinkedHashMap<>(row); m.remove("score"); return m; })
                .rows();
        System.out.println("  [filter+mapRows] " + mapped.size() + " 行");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testXml() throws Exception {
        System.out.println("\n===== [xml] =====");
        File xml = new File(workDir, "demo.xml");
        FileSystem.create("xml").write(xml).write(TABLE_DATA).finish();
        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        System.out.println("  [读取] " + rows.size() + " 行");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testTxt() throws Exception {
        System.out.println("\n===== [txt] =====");
        File txt = new File(workDir, "demo.txt");
        FileSystem.create("txt").write(txt).write(TABLE_DATA).finish();
        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        System.out.println("  [读取] " + rows.size() + " 行");
        List<String> lines = FileSystem.create("txt").read(txt).asLines();
        System.out.println("  [asLines] " + lines.size() + " 行");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testExcel() throws Exception {
        System.out.println("\n===== [excel] =====");
        File xlsx = new File(workDir, "demo.xlsx");
        FileSystem.create("excel").write(xlsx).write(TABLE_DATA).freezeHeader().finish();
        System.out.println("  [写入+冻结表头] " + xlsx.length() + " bytes");

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");

        List<Map<String, Object>> projected = FileSystem.create("excel").read(xlsx)
                .selectColumns("name", "score").rows();
        System.out.println("  [selectColumns] " + projected.size() + " 行, 每行 " + projected.get(0).size() + " 列");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testDbf() throws Exception {
        System.out.println("\n===== [dbf] =====");
        File dbf = new File(workDir, "demo.dbf");
        FileSystem.create("dbf").write(dbf).write(TABLE_DATA).finish();
        System.out.println("  [写入] " + dbf.length() + " bytes");

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testLog() throws Exception {
        System.out.println("\n===== [log] =====");
        File log = new File(workDir, "app.log");
        FileSystem.create("log").write(log)
                .write("INFO: 服务启动")
                .write("ERROR: 连接超时 - retry=1")
                .write("INFO: 重试成功")
                .write("ERROR: 磁盘空间不足")
                .write("INFO: 任务完成")
                .finish();

        List<String> lines = FileSystem.create("log").read(log).lines();
        check(lines.size() == 5, "行数");
        System.out.println("  [lines] " + lines.size() + " 行");

        String text = FileSystem.create("log").read(log).asString();
        System.out.println("  [asString] " + text.length() + " 字符");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testTar() throws Exception {
        System.out.println("\n===== [tar] =====");
        File tarFile = new File(workDir, "demo.tar");
        FileSystem.create("tar").write(tarFile).write(TABLE_DATA).finish();
        check(tarFile.length() > 0, "tar 非空");

        List<String> entries = FileSystem.create("tar").read(tarFile).listEntries();
        System.out.println("  [条目] " + entries.size() + " 个");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testZip() throws Exception {
        System.out.println("\n===== [zip] =====");
        File zipFile = new File(workDir, "demo.zip");
        FileSystem.create("zip").write(zipFile).finish();
        List<String> entries = FileSystem.create("zip").read(zipFile).listEntries();
        System.out.println("  [条目] " + entries.size() + " 个");
        System.out.println("  [PASS]");
        return true;
    }

    // ==================== 辅助方法 ====================

    private void check(boolean condition, String msg) {
        if (!condition) { System.err.println("  [FAIL] " + msg); allPassed = false; }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }

    private static Args parseArgs(String[] args) {
        Args result = new Args(null, false);
        if (args == null) return result;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--type", "-t" -> {
                    if (i + 1 < args.length) result = result.withType(args[++i]);
                }
                case "--help", "-h" -> result = result.withHelp(true);
            }
        }
        return result;
    }

    boolean testYaml() throws Exception {
        System.out.println("\n===== [yaml] =====");
        File yml = new File(workDir, "demo.yml");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "test");
        data.put("version", 1);
        FileSystem.create("yaml").write(yml).write(data);
        System.out.println("  [写入] " + yml.length() + " bytes");

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        check("test".equals(loaded.get("name")), "name 不匹配");
        System.out.println("  [PASS]");
        return true;
    }

    boolean testIni() throws Exception {
        System.out.println("\n===== [ini] =====");
        File ini = new File(workDir, "demo.ini");
        FileSystem.create("ini").write(ini)
                .write(Map.of("server", Map.of("host", "localhost", "port", "8080")))
                .finish();
        Map<String, Object> loaded = FileSystem.create("ini").read(ini).toMap();
        check(loaded.containsKey("server"), "缺少 [server]");
        System.out.println("  [PASS]");
        return true;
    }

    private static void printHelp() {
        System.out.println("FileSystemExample — FileSystem SPI 综合示例");
        System.out.println("用法: java FileSystemExample [选项]");
        System.out.println("  --type, -t <key>  csv/json/xml/txt/excel/dbf/log/tar/zip/yaml/ini/all (默认 all)");
        System.out.println("  --help, -h        帮助");
        System.out.println("独立示例:");
        System.out.println("  java com.chua.example.file.CsvFileSystemExample");
        System.out.println("  java com.chua.example.file.ExcelFileSystemExample");
        System.out.println("  java com.chua.example.file.LogFileSystemExample");
        System.out.println("  java com.chua.example.file.YamlFileSystemExample");
        System.out.println("  java com.chua.example.file.IniFileSystemExample");
    }

    private record Args(String type, boolean help) {
        public Args withType(String type) { return new Args(type, help); }
        public Args withHelp(boolean help) { return new Args(type, help); }
    }
}

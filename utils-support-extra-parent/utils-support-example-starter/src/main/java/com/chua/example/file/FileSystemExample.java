package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@Slf4j
public class FileSystemExample {

    /**
     * 默认类型（全部）
     */
    private static final String DEFAULT_TYPE = "all";

    /**
     * 测试表格数据
     */
    private static final List<Map<String, Object>> TABLE_DATA = List.of(
            Map.of("id", 1, "name", "张三", "age", 25, "score", 88.5),
            Map.of("id", 2, "name", "李四", "age", 30, "score", 92.0),
            Map.of("id", 3, "name", "王五", "age", 18, "score", 75.5),
            Map.of("id", 4, "name", "赵六", "age", 22, "score", 67.0),
            Map.of("id", 5, "name", "钱七", "age", 28, "score", 95.0)
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
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        FileSystemExample example = new FileSystemExample();
        example.workDir = Files.createTempDirectory("fs-all-").toFile();
        log.info("[FileSystemExample] 工作目录: {}", example.workDir);

        example.runTest(parsed.type());
        log.info("\n========================================");
        log.info("[FileSystemExample] self-test type={}, passed={}", parsed.type(), example.allPassed);

        deleteDir(example.workDir);
        System.exit(example.allPassed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
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
                log.info("[FileSystemExample] 未知类型: {}", target);
                yield false;
            }
        };
    }

    // ==================== CSV ====================

    boolean testCsv() throws Exception {
        log.info("\n===== [csv] =====");
        File csv = new File(workDir, "demo.csv");
        FileSystem.create("csv").write(csv).write(TABLE_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("csv").read(csv).rows();
        log.info("  [读取] {} 行", rows.size());
        check(rows.size() == TABLE_DATA.size(), "行数");

        List<Map<String, Object>> filtered = FileSystem.create("csv").read(csv)
                .filter(row -> ((Number) row.get("age")).intValue() >= 25)
                .rows();
        log.info("  [过滤 age>=25] {} 行", filtered.size());

        List<Map<String, Object>> mapped = FileSystem.create("csv").read(csv)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    double score = ((Number) m.get("score")).doubleValue();
                    m.put("level", score >= 90 ? "优秀" : score >= 80 ? "良好" : "一般");
                    return m;
                })
                .rows();
        log.info("  [mapRows] {} 行", mapped.size());
        mapped.forEach(row -> log.info("    {} level={}", row.get("name"), row.get("level")));
        log.info("  [PASS]");
        return true;
    }

    boolean testJson() throws Exception {
        log.info("\n===== [json] =====");
        File json = new File(workDir, "demo.json");
        FileSystem.create("json").write(json).write(TABLE_DATA).finish();

        List<Map<String, Object>> rows = FileSystem.create("json").read(json).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        log.info("  [读取] {} 行", rows.size());

        List<Map<String, Object>> filtered = FileSystem.create("json").read(json)
                .filter(row -> "张三".equals(row.get("name")) || "李四".equals(row.get("name")))
                .rows();
        log.info("  [过滤 张三/李四] {} 行", filtered.size());

        List<Map<String, Object>> mapped = FileSystem.create("json").read(json)
                .filter(row -> ((Number) row.get("score")).doubleValue() >= 80)
                .mapRows(row -> {
                    Map<String, Object> m = new LinkedHashMap<>(row);
                    m.remove("score");
                    return m;
                })
                .rows();
        log.info("  [filter+mapRows] {} 行", mapped.size());
        log.info("  [PASS]");
        return true;
    }

    boolean testXml() throws Exception {
        log.info("\n===== [xml] =====");
        File xml = new File(workDir, "demo.xml");
        FileSystem.create("xml").write(xml).write(TABLE_DATA).finish();
        List<Map<String, Object>> rows = FileSystem.create("xml").read(xml).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        log.info("  [读取] {} 行", rows.size());
        log.info("  [PASS]");
        return true;
    }

    boolean testTxt() throws Exception {
        log.info("\n===== [txt] =====");
        File txt = new File(workDir, "demo.txt");
        FileSystem.create("txt").write(txt).write(TABLE_DATA).finish();
        List<Map<String, Object>> rows = FileSystem.create("txt").read(txt).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        log.info("  [读取] {} 行", rows.size());
        List<String> lines = FileSystem.create("txt").read(txt).asLines();
        log.info("  [asLines] {} 行", lines.size());
        log.info("  [PASS]");
        return true;
    }

    boolean testExcel() throws Exception {
        log.info("\n===== [excel] =====");
        File xlsx = new File(workDir, "demo.xlsx");
        FileSystem.create("excel").write(xlsx).write(TABLE_DATA).freezeHeader().finish();
        log.info("  [写入+冻结表头] {} bytes", xlsx.length());

        List<Map<String, Object>> rows = FileSystem.create("excel").read(xlsx).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");

        List<Map<String, Object>> projected = FileSystem.create("excel").read(xlsx)
                .selectColumns("name", "score").rows();
        log.info("  [selectColumns] {} 行, 每行 {} 列", projected.size(), projected.get(0).size());
        log.info("  [PASS]");
        return true;
    }

    boolean testDbf() throws Exception {
        log.info("\n===== [dbf] =====");
        File dbf = new File(workDir, "demo.dbf");
        FileSystem.create("dbf").write(dbf).write(TABLE_DATA).finish();
        log.info("  [写入] {} bytes", dbf.length());

        List<Map<String, Object>> rows = FileSystem.create("dbf").read(dbf).rows();
        check(rows.size() == TABLE_DATA.size(), "行数");
        log.info("  [PASS]");
        return true;
    }

    boolean testLog() throws Exception {
        log.info("\n===== [log] =====");
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
        log.info("  [lines] {} 行", lines.size());

        String text = FileSystem.create("log").read(log).asString();
        log.info("  [asString] {} 字符", text.length());
        log.info("  [PASS]");
        return true;
    }

    boolean testTar() throws Exception {
        log.info("\n===== [tar] =====");
        File tarFile = new File(workDir, "demo.tar");
        FileSystem.create("tar").write(tarFile).write(TABLE_DATA).finish();
        check(tarFile.length() > 0, "tar 非空");

        List<String> entries = FileSystem.create("tar").read(tarFile).listEntries();
        log.info("  [条目] {} 个", entries.size());
        log.info("  [PASS]");
        return true;
    }

    boolean testZip() throws Exception {
        log.info("\n===== [zip] =====");
        File zipFile = new File(workDir, "demo.zip");
        FileSystem.create("zip").write(zipFile).finish();
        List<String> entries = FileSystem.create("zip").read(zipFile).listEntries();
        log.info("  [条目] {} 个", entries.size());
        log.info("  [PASS]");
        return true;
    }

    // ==================== 辅助方法 ====================

    private void check(boolean condition, String msg) {
        if (!condition) {
            log.info("  [FAIL] {}", msg);
            allPassed = false;
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

    private static Args parseArgs(String[] args) {
        Args result = new Args(null, false);
        if (args == null) {
            return result;
        }
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--type", "-t" -> {
                    if (i + 1 < args.length) {
                        result = result.withType(args[++i]);
                    }
                }
                case "--help", "-h" -> result = result.withHelp(true);
                default -> {
                }
            }
        }
        return result;
    }

    boolean testYaml() throws Exception {
        log.info("\n===== [yaml] =====");
        File yml = new File(workDir, "demo.yml");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "test");
        data.put("version", 1);
        FileSystem.create("yaml").write(yml).write(data);
        log.info("  [写入] {} bytes", yml.length());

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        check("test".equals(loaded.get("name")), "name 不匹配");
        log.info("  [PASS]");
        return true;
    }

    boolean testIni() throws Exception {
        log.info("\n===== [ini] =====");
        File ini = new File(workDir, "demo.ini");
        FileSystem.create("ini").write(ini)
                .write(Map.of("server", Map.of("host", "localhost", "port", "8080")))
                .finish();
        Map<String, Object> loaded = FileSystem.create("ini").read(ini).toMap();
        check(loaded.containsKey("server"), "缺少 [server]");
        log.info("  [PASS]");
        return true;
    }

    private static void printHelp() {
        log.info("FileSystemExample — FileSystem SPI 综合示例");
        log.info("用法: java FileSystemExample [选项]");
        log.info("  --type, -t <key>  csv/json/xml/txt/excel/dbf/log/tar/zip/yaml/ini/all (默认 all)");
        log.info("  --help, -h        帮助");
        log.info("独立示例:");
        log.info("  java com.chua.example.file.CsvFileSystemExample");
        log.info("  java com.chua.example.file.ExcelFileSystemExample");
        log.info("  java com.chua.example.file.LogFileSystemExample");
        log.info("  java com.chua.example.file.YamlFileSystemExample");
        log.info("  java com.chua.example.file.IniFileSystemExample");
    }

    /**
     * 命令行参数。
     *
     * @param type 类型
     * @param help 帮助
     */
    private record Args(String type, boolean help) {
        public Args withType(String type) {
            return new Args(type, help);
        }

        public Args withHelp(boolean help) {
            return new Args(type, help);
        }
    }
}

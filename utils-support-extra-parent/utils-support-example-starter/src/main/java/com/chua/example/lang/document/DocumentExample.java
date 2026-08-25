package com.chua.example.lang.document;

import com.chua.common.support.lang.document.*;
import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 数据库文档导出示例：从 JDBC 抓取 schema 并按 html/markdown/word/pdf 格式输出。
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认导出到 ./out
 *   java DocumentExample
 *
 *   # 指定数据库主机、schema 与输出目录
 *   java DocumentExample --host 172.16.0.40 --schema report --out out
 *
 *   # 打印帮助
 *   java DocumentExample --help
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class DocumentExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 输出目录
     */
    private static final String DEFAULT_OUTPUT_DIR = "out";

    /**
     * 数据库目标主机
     */
    private static final String DEFAULT_DB_HOST = "172.16.0.40";

    /**
     * 数据库 schema
     */
    private static final String DEFAULT_DB_SCHEMA = "report";

    /**
     * 数据库版本号
     */
    private static final String DEFAULT_DB_VERSION = "1.0.0";

    /**
     * JDBC 端口
     */
    private static final int DEFAULT_DB_PORT = 3306;

    /**
     * JDBC 用户名
     */
    private static final String DEFAULT_DB_USER = "root";

    /**
     * JDBC 密码（通过命令行参数 --password 覆盖）
     */
    private static final String DEFAULT_DB_PASSWORD = "";

    /**
     * MySQL JDBC 驱动
     */
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("DocumentExample")
                .register("host", "H", "数据库主机", DEFAULT_DB_HOST)
                .register("port", "P", "数据库端口", String.valueOf(DEFAULT_DB_PORT))
                .register("schema", "s", "数据库 schema", DEFAULT_DB_SCHEMA)
                .register("version", "v", "数据库版本号", DEFAULT_DB_VERSION)
                .register("user", "u", "数据库用户名", DEFAULT_DB_USER)
                .register("password", "p", "数据库密码", DEFAULT_DB_PASSWORD)
                .register("out", "o", "输出目录", DEFAULT_OUTPUT_DIR)
                .register("help", "h", "显示帮助信息");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String host = cli.get("host", DEFAULT_DB_HOST);
        int port = cli.getInt("port", DEFAULT_DB_PORT);
        String schema = cli.get("schema", DEFAULT_DB_SCHEMA);
        String version = cli.get("version", DEFAULT_DB_VERSION);
        String user = cli.get("user", DEFAULT_DB_USER);
        String password = cli.get("password", DEFAULT_DB_PASSWORD);
        String outDir = cli.get("out", DEFAULT_OUTPUT_DIR);

        DocumentExample example = new DocumentExample();
        boolean passed = example.runTest(host, port, schema, version, user, password, outDir);
        log.info("[DocumentExample] 自检结果: passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 执行全部自检测试（无参版本，保留兼容 SPI 调用）。
     *
     * @return 全部测试通过返回 true
     */
    public boolean runTest() {
        return runTest(
                DEFAULT_DB_HOST,
                DEFAULT_DB_PORT,
                DEFAULT_DB_SCHEMA,
                DEFAULT_DB_VERSION,
                DEFAULT_DB_USER,
                DEFAULT_DB_PASSWORD,
                DEFAULT_OUTPUT_DIR
        );
    }

    /**
     * 执行全部自检测试。
     *
     * @param host     数据库主机
     * @param port     数据库端口
     * @param schema   数据库 schema
     * @param version  数据库版本号
     * @param user     数据库用户名
     * @param password 数据库密码
     * @param outDir   输出目录
     * @return 全部测试通过返回 true
     */
    public boolean runTest(String host, int port, String schema, String version,
                           String user, String password, String outDir) {
        boolean passed = true;
        try {
            DocumentParser parser = DocumentParser.create("database");

            DocumentConfig config = DocumentConfig.builder()
                    .url("jdbc:mysql://" + host + ":" + port + "/" + schema)
                    .username(user)
                    .password(password)
                    .driverClass(MYSQL_DRIVER)
                    .options(Map.of("schemas", schema, "version", version))
                    .build();

            DocumentData data = parser.parse(config);
            passed &= data != null && data.getTables() != null;

            String customHtml = readResource("document/templates/default/index.html");
            passed &= customHtml != null && !customHtml.isEmpty();

            DocumentExporter.of(data)
                    .template(DocumentTemplateType.DEFAULT)
                    .customHtmlTemplate(customHtml)
                    .format("html")
                    .output(new File(outDir + "/" + host + "-database.html"))
                    .export()
                    .format("markdown")
                    .output(new File(outDir + "/" + host + "-database.md"))
                    .export()
                    .format("word")
                    .output(new File(outDir + "/" + host + "-database.docx"))
                    .export()
                    .format("pdf")
                    .output(new File(outDir + "/" + host + "-database.pdf"))
                    .export();

            log.info("导出完成，共 {} 张表", data.getTables().size());
        } catch (Exception e) {
            log.error("[DocumentExample] 自检异常: {}", e.getMessage(), e);
            passed = false;
        }
        log.info("[DocumentExample] 自检完成: {}", (passed ? "✅ PASS" : "❌ FAIL"));
        return passed;
    }

    /** 读取Resource */
    private static String readResource(String path) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = DocumentExample.class.getClassLoader();
        }
        try (InputStream is = cl.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("资源不存在: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

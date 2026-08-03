package com.chua.example.lang.document;

import com.chua.common.support.lang.document.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 数据库文档导出示例：从 JDBC 抓取 schema 并按 html/markdown/word/pdf 格式输出。
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
    private static final String OUTPUT_DIR = "out";

    /**
     * 数据库目标主机
     */
    private static final String DB_HOST = "172.16.0.40";

    /**
     * 数据库 schema
     */
    private static final String DB_SCHEMA = "report";

    /**
     * 数据库版本号
     */
    private static final String DB_VERSION = "1.0.0";

    public static void main(String[] args) {
        DocumentExample example = new DocumentExample();
        boolean passed = example.runTest();
        log.info("[DocumentExample] 自检结果: passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 执行全部自检测试。
     *
     * @return 全部测试通过返回 true
     */
    public boolean runTest() {
        boolean passed = true;
        try {
            DocumentParser parser = DocumentParser.create("database");

            DocumentConfig config = DocumentConfig.builder()
                    .url("jdbc:mysql://" + DB_HOST + ":3306/" + DB_SCHEMA)
                    .username("root")
                    .password("root@")
                    .driverClass("com.mysql.cj.jdbc.Driver")
                    .options(Map.of("schemas", DB_SCHEMA, "version", DB_VERSION))
                    .build();

            DocumentData data = parser.parse(config);
            passed &= data != null && data.getTables() != null;

            String customHtml = readResource("document/templates/default/index.html");
            passed &= customHtml != null && !customHtml.isEmpty();

            DocumentExporter.of(data)
                    .template(DocumentTemplateType.DEFAULT)
                    .customHtmlTemplate(customHtml)
                    .format("html")
                    .output(new File(OUTPUT_DIR + "/" + DB_HOST + "-database.html"))
                    .export()
                    .format("markdown")
                    .output(new File(OUTPUT_DIR + "/" + DB_HOST + "-database.md"))
                    .export()
                    .format("word")
                    .output(new File(OUTPUT_DIR + "/" + DB_HOST + "-database.docx"))
                    .export()
                    .format("pdf")
                    .output(new File(OUTPUT_DIR + "/" + DB_HOST + "-database.pdf"))
                    .export();

            log.info("导出完成，共 {} 张表", data.getTables().size());
        } catch (Exception e) {
            log.error("[DocumentExample] 自检异常: {}", e.getMessage(), e);
            passed = false;
        }
        log.info("[DocumentExample] 自检完成: {}", (passed ? "✅ PASS" : "❌ FAIL"));
        return passed;
    }

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

package com.chua.example.lang.document;

import com.chua.common.support.lang.document.*;

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
public class DocumentExample {

    public static void main(String[] args) throws Exception {
        DocumentParser parser = DocumentParser.create("database");

        DocumentConfig config = DocumentConfig.builder()
                .url("jdbc:mysql://172.16.0.40:3306/report")
                .username("root")
                .password("root@")
                .driverClass("com.mysql.cj.jdbc.Driver")
                .options(Map.of("schemas", "report", "version", "1.0.0"))
                .build();

        DocumentData data = parser.parse(config);

        String customHtml = readResource("document/templates/default/index.html");

        DocumentExporter.of(data)
                .template(DocumentTemplateType.DEFAULT)
                .customHtmlTemplate(customHtml)
                .format("html")
                .output(new File("out/172.16.0.40-database.html"))
                .export()
                .format("markdown")
                .output(new File("out/172.16.0.40-database.md"))
                .export()
                .format("word")
                .output(new File("out/172.16.0.40-database.docx"))
                .export()
                .format("pdf")
                .output(new File("out/172.16.0.40-database.pdf"))
                .export();

        System.out.println("导出完成，共 " + data.getTables().size() + " 张表");
    }

    private static String readResource(String path) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = DocumentExample.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("资源不存在: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

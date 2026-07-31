package com.chua.example.filesearch;

import com.chua.filesystem.support.filesearch.model.FileInfo;
import com.chua.filesystem.support.filesearch.model.FileSearchCriteria;
import com.chua.filesearch.support.service.FileSearchService;

import java.io.File;
import java.util.List;

/**
 * FileSearchHome 示例
 * <p>演示如何在 {@code /home} 目录下搜索所有文件，最多返回 20 条结果。</p>
 *
 * @author CH
 * @since 2026-07-27
 */
public class FileSearchHomeExample {

    /**
     * 默认最大结果数
     */
    private static final int DEFAULT_MAX_RESULTS = 20;

    public static void main(String[] args) {
        String root = "/home";
        String customRoot = System.getProperty("filesearch.root", "");
        if (!customRoot.isEmpty()) {
            root = customRoot;
        }
        printStep("root", root);

        File rootFile = new File(root);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            System.err.println("[FileSearchHomeExample] root not exists or not a directory: " + root);
            System.exit(1);
        }

        FileSearchService service = FileSearchService.getInstance();
        if (!service.isAvailable()) {
            System.err.println("[FileSearchHomeExample] service not available");
            System.exit(2);
        }

        FileSearchCriteria criteria = FileSearchCriteria.builder()
                .rootPath(root)
                .maxResults(DEFAULT_MAX_RESULTS)
                .sortBy(FileSearchCriteria.SORT_BY_NAME)
                .order(FileSearchCriteria.ORDER_ASC)
                .build();
        printStep("criteria", "maxResults=" + DEFAULT_MAX_RESULTS);

        List<FileInfo> results = service.search(criteria);
        printStep("result", "found=" + results.size());

        for (FileInfo fileInfo : results) {
            System.out.printf("  %s  %s%n", padRight(fileInfo.sizeFormatted(), 10), fileInfo.path());
        }

        System.out.println("========================================");
        System.out.println("  ALL DONE");
        System.out.println("========================================");
    }

    private static String padRight(String str, int width) {
        if (str == null) {
            str = "";
        }
        if (str.length() >= width) {
            return str;
        }
        return str + " ".repeat(width - str.length());
    }

    private static void printStep(String label, String value) {
        System.out.println("[FileSearchHomeExample] " + label + "=" + value);
    }
}

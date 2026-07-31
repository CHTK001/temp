package com.chua.example.filesearch;

import com.chua.filesystem.support.filesearch.model.FileInfo;
import com.chua.filesystem.support.filesearch.model.FileSearchCriteria;
import com.chua.filesearch.support.service.FileSearchService;

import java.io.File;
import java.util.List;

/**
 * FileSearch 示例
 * <p>演示如何使用 {@link FileSearchService} 按名称模式搜索文件，默认在当前工作目录下查找 Java 文件。</p>
 *
 * @author CH
 * @since 2026-07-27
 */
public class FileSearchExample {

    /**
     * 默认名称模式
     */
    private static final String DEFAULT_NAME_PATTERN = "*.java";

    /**
     * 默认最大结果数
     */
    private static final int DEFAULT_MAX_RESULTS = 20;

    public static void main(String[] args) {
        String root = System.getProperty("user.dir");
        if (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty()) {
            root = args[0];
        }
        String customRoot = System.getProperty("filesearch.root", "");
        if (!customRoot.isEmpty()) {
            root = customRoot;
        }
        printStep("root", root);

        File rootFile = new File(root);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            System.err.println("[FileSearchExample] root not exists or not a directory: " + root);
            System.exit(1);
            return;
        }

        FileSearchService service = FileSearchService.getInstance();
        if (!service.isAvailable()) {
            System.err.println("[FileSearchExample] service not available");
            System.exit(2);
            return;
        }

        FileSearchCriteria criteria = FileSearchCriteria.builder()
                .rootPath(root)
                .namePattern(DEFAULT_NAME_PATTERN)
                .maxResults(DEFAULT_MAX_RESULTS)
                .sortBy(FileSearchCriteria.SORT_BY_NAME)
                .order(FileSearchCriteria.ORDER_ASC)
                .build();
        printStep("criteria", "namePattern=" + DEFAULT_NAME_PATTERN + ", maxResults=" + DEFAULT_MAX_RESULTS);

        List<FileInfo> results = service.search(criteria);
        printStep("result", "found=" + (results == null ? 0 : results.size()));

        if (results != null) {
            for (FileInfo fileInfo : results) {
                System.out.printf("  %s  %s%n", padRight(fileInfo.sizeFormatted(), 10), fileInfo.path());
            }
        }

        System.out.println("========================================");
        System.out.println("  ALL DONE");
        System.out.println("========================================");
    }

    /**
     * 字符串左对齐填充至指定宽度
     *
     * @param str   原字符串
     * @param width 目标宽度
     * @return 填充后的字符串
     */
    private static String padRight(String str, int width) {
        if (str == null) {
            str = "";
        }
        if (str.length() >= width) {
            return str;
        }
        return str + " ".repeat(width - str.length());
    }

    /**
     * 打印步骤标识
     *
     * @param label 步骤名
     * @param value 步骤值
     */
    private static void printStep(String label, String value) {
        System.out.println("[FileSearchExample] " + label + "=" + value);
    }
}

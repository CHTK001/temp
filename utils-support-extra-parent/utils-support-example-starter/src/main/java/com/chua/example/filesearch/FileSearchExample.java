package com.chua.example.filesearch;

import com.chua.filesystem.support.filesearch.model.FileInfo;
import com.chua.filesystem.support.filesearch.model.FileSearchCriteria;
import com.chua.filesearch.support.service.FileSearchService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

/**
 * FileSearch 示例
 * <p>演示如何使用 {@link FileSearchService} 按名称模式搜索文件，默认在当前工作目录下查找 Java 文件。</p>
 *
 * @author CH
 * @since 2026-07-27
 */
@Slf4j
public class FileSearchExample {

    /**
     * 默认名称模式
     */
    private static final String DEFAULT_NAME_PATTERN = "*.java";

    /**
     * 默认最大结果数
     */
    private static final int DEFAULT_MAX_RESULTS = 20;

    /**
     * 打印宽度
     */
    private static final int PAD_WIDTH = 10;

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) {
        FileSearchExample example = new FileSearchExample();
        boolean passed = example.runTest(args);
        log.info("[FileSearchExample] 自检结果: passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：在指定根目录下按名称模式搜索文件。
     *
     * @param args 命令行参数，args[0] 可指定搜索根目录
     * @return true 表示文件搜索服务可用
     */
    public boolean runTest(String[] args) {
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
            log.error("[FileSearchExample] root not exists or not a directory: {}", root);
            return false;
        }

        FileSearchService service = FileSearchService.getInstance();
        if (!service.isAvailable()) {
            log.error("[FileSearchExample] service not available");
            return false;
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
                log.info("  {}  {}", padRight(fileInfo.sizeFormatted(), PAD_WIDTH), fileInfo.path());
            }
        }

        log.info("========================================");
        log.info("  ALL DONE");
        log.info("========================================");
        return true;
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
        log.info("[FileSearchExample] {}={}", label, value);
    }
}

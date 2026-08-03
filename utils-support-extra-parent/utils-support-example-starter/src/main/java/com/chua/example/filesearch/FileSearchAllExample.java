package com.chua.example.filesearch;

import com.chua.filesystem.support.filesearch.model.FileInfo;
import com.chua.filesystem.support.filesearch.model.FileSearchCriteria;
import com.chua.filesearch.support.service.FileSearchService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

/**
 * FileSearchAll 示例
 * <p>演示如何在 {@code C:\\} 根目录下搜索所有文件，最多返回 20 条结果。</p>
 *
 * @author CH
 * @since 2026-07-27
 */
@Slf4j
public class FileSearchAllExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 默认根目录
     */
    private static final String DEFAULT_ROOT = "C:\\";

    /**
     * 打印宽度
     */
    private static final int PAD_WIDTH = 10;

    public static void main(String[] args) {
        FileSearchAllExample example = new FileSearchAllExample();
        boolean passed = example.runTest(args);
        log.info("[FileSearchAllExample] 自检结果: passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：在指定根目录下搜索文件并打印结果。
     *
     * @param args 命令行参数（本示例未使用）
     * @return true 表示文件搜索服务可用
     */
    public boolean runTest(String[] args) {
        String root = System.getProperty("filesearch.root", DEFAULT_ROOT);
        int maxResults = Integer.parseInt(System.getProperty("filesearch.maxResults", "-1"));
        printStep("root", root);

        File rootFile = new File(root);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            log.error("[FileSearchAllExample] root not exists or not a directory: {}", root);
            return false;
        }

        FileSearchService service = FileSearchService.getInstance();
        if (!service.isAvailable()) {
            log.error("[FileSearchAllExample] service not available");
            return false;
        }

        FileSearchCriteria.Builder builder = FileSearchCriteria.builder()
                .rootPath(root)
                .sortBy(FileSearchCriteria.SORT_BY_NAME)
                .order(FileSearchCriteria.ORDER_ASC);
        if (maxResults >= 0) {
            builder.maxResults(maxResults);
        }
        FileSearchCriteria criteria = builder.build();
        printStep("criteria", maxResults >= 0 ? "maxResults=" + maxResults : "unlimited(default=1000)");

        List<FileInfo> results = service.search(criteria);
        printStep("result", "found=" + results.size());

        for (FileInfo fileInfo : results) {
            log.info("  {}  {}", padRight(fileInfo.sizeFormatted(), PAD_WIDTH), fileInfo.path());
        }

        log.info("========================================");
        log.info("  ALL DONE");
        log.info("========================================");
        return true;
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
        log.info("[FileSearchAllExample] {}={}", label, value);
    }
}

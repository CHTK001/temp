package com.chua.example.filesearch;

import com.chua.filesystem.support.filesearch.model.FileInfo;
import com.chua.filesystem.support.filesearch.model.FileSearchCriteria;
import com.chua.filesearch.support.service.FileSearchService;

import java.io.File;
import java.util.List;

/**
 * 用例2：检索 C:/ 所有文件/目录
 *
 * @author CH
 * @since 2026-07-27
 */
public class FileSearchExampleCAll {

    public static void main(String[] args) {
        String root = "C:/";
        printStep("root", root);

        File rootFile = new File(root);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            System.err.println("[FileSearchExampleCAll] root not exists or not a directory: " + root);
            System.exit(1);
        }

        FileSearchService service = FileSearchService.getInstance();
        if (!service.isAvailable()) {
            System.err.println("[FileSearchExampleCAll] service not available");
            System.exit(2);
        }

        FileSearchCriteria criteria = FileSearchCriteria.builder()
                .rootPath(root)
                .maxResults(10)
                .sortBy(FileSearchCriteria.SORT_BY_NAME)
                .order(FileSearchCriteria.ORDER_ASC)
                .build();
        printStep("criteria", "maxResults=10");

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
        System.out.println("[FileSearchExampleCAll] " + label + "=" + value);
    }
}

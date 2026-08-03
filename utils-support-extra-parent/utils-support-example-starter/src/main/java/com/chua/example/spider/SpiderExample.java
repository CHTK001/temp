package com.chua.example.spider;

import com.chua.spider.support.Spider;
import com.chua.spider.support.SpiderLinkExtractor;
import com.chua.spider.support.SpiderUrlFilter;
import com.chua.spider.support.annotation.SpiderField;
import com.chua.spider.support.extractor.HtmlLinkExtractor;
import com.chua.spider.support.model.SpiderResult;
import com.chua.spider.support.model.SpiderSite;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 爬虫综合示例 — 演示 {@code utils-support-spider-starter} 的能力矩阵。
 *
 * <p>本示例覆盖单页面抓取、多线程并发、Lambda Pipeline、POJO 映射、
 * 深度控制 + 链接追踪等核心能力，提供自检流程用于验证爬虫模块可用性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行模式：执行全部能力点自检
 *   java SpiderExample
 *
 *   # 指定能力点测试（basic / threads / pojo / depth / all）
 *   java SpiderExample --type threads
 *
 *   # 打印帮助
 *   java SpiderExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>基础单页抓取</td><td>{@link #testBasic()}</td><td>默认 HttpFetcher + HtmlParser + ConsolePipeline</td></tr>
 *   <tr><td>多线程并发</td><td>{@link #testThreads()}</td><td>5 线程并发 + Lambda Pipeline</td></tr>
 *   <tr><td>POJO 映射</td><td>{@link #testPojo()}</td><td>@SpiderField 注解 + CSS 选择器提取</td></tr>
 *   <tr><td>深度控制 + 链接追踪</td><td>{@link #testDepth()}</td><td>maxDepth=1 + 域名过滤 + HtmlLinkExtractor</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SpiderExample {

    /**
     * 默认测试目标 URL（静态 HTML 页面，稳定可访问）
     */
    private static final String DEFAULT_URL = "https://gitee.com";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 多线程并发数
     */
    private static final int THREAD_COUNT = 5;

    /**
     * 最大爬取深度
     */
    private static final int MAX_DEPTH = 1;

    /**
     * 最大爬取页面数（深度爬取场景限制）
     */
    private static final int MAX_PAGES = 3;

    /**
     * 主入口：根据命令行参数运行指定能力点自检。
     *
     * @param args 命令行参数，args[0]=能力点类型（basic / threads / pojo / depth / all），默认 all
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
                ? args[0].toLowerCase()
                : "all";

        if ("--help".equals(type) || "-h".equals(type)) {
            printHelp();
            return;
        }

        SpiderExample example = new SpiderExample();
        boolean passed = example.runTest(type);
        log.info("[SpiderExample] self-test type={}, passed={}", type, passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 启动自检流程：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = "all";
        }
        switch (type.toLowerCase()) {
            case "basic" -> {
                return testBasic();
            }
            case "threads" -> {
                return testThreads();
            }
            case "pojo" -> {
                return testPojo();
            }
            case "depth" -> {
                return testDepth();
            }
            case "all" -> {
                return testBasic() && testThreads() && testPojo() && testDepth();
            }
            default -> {
                log.error("[SpiderExample] 未知能力点: {}", type);
                return false;
            }
        }
    }

    /**
     * 能力点1：基础单页抓取。
     *
     * <p>使用默认 HttpFetcher + HtmlParser + ConsolePipeline，
     * 爬取单个页面并输出解析结果到控制台。
     *
     * @return true 表示至少获取到一条结果（标题非空）
     */
    public boolean testBasic() {
        log.info("[SpiderExample] === 能力点1: 基础单页抓取 ===");

        // 限制只爬种子页面，避免无限追踪链接
        SpiderSite site = SpiderSite.builder()
                .maxDepth(0)
                .build();

        try {
            List<SpiderResult> results = Spider.create()
                    .addUrl(DEFAULT_URL)
                    .site(site)
                    .build()
                    .runSync();

            if (results == null || results.isEmpty()) {
                log.error("[SpiderExample] 基础抓取无结果");
                return false;
            }

            SpiderResult first = results.get(0);
            log.info("[SpiderExample] 基础抓取成功，URL={}, 标题={}", first.getUrl(), first.getTitle());

            return first.getTitle() != null && !first.getTitle().isEmpty();
        } catch (Exception e) {
            log.error("[SpiderExample] 基础抓取异常", e);
            return false;
        }
    }

    /**
     * 能力点2：多线程并发抓取。
     *
     * <p>设置 5 个线程并发爬取，使用 Lambda Pipeline 统计结果数量。
     * 验证多线程场景下结果的完整性和计数准确性。
     *
     * @return true 表示结果数量大于 0 且计数器匹配
     */
    public boolean testThreads() {
        log.info("[SpiderExample] === 能力点2: 多线程并发抓取 ===");

        // 结果计数器
        AtomicInteger counter = new AtomicInteger(0);

        // 限制只爬种子页面，避免无限追踪链接
        SpiderSite site = SpiderSite.builder()
                .maxDepth(0)
                .build();

        try {
            List<SpiderResult> results = Spider.create()
                    .addUrl(DEFAULT_URL)
                    .site(site)
                    .threads(THREAD_COUNT)
                    .pipeline((Consumer<SpiderResult>) result -> {
                        int count = counter.incrementAndGet();
                        log.info("[SpiderExample] 多线程收到结果 #{}，URL={}", count, result.getUrl());
                    })
                    .build()
                    .runSync();

            int expected = results.size();
            int actual = counter.get();
            log.info("[SpiderExample] 多线程完成，runSync结果数={}, Pipeline计数={}", expected, actual);

            return expected > 0 && expected == actual;
        } catch (Exception e) {
            log.error("[SpiderExample] 多线程抓取异常", e);
            return false;
        }
    }

    /**
     * 能力点3：POJO 映射抓取。
     *
     * <p>使用 {@code @SpiderField} 注解定义 CSS 选择器，将页面数据自动映射到 POJO。
     * 验证类型化输出的正确性。
     *
     * @return true 表示 POJO 至少有一个字段被成功提取
     */
    public boolean testPojo() {
        log.info("[SpiderExample] === 能力点3: POJO 映射抓取 ===");

        // POJO 计数器
        AtomicInteger pojoCount = new AtomicInteger(0);

        // 限制只爬种子页面，避免无限追踪链接
        SpiderSite site = SpiderSite.builder()
                .maxDepth(0)
                .build();

        try {
            Spider.create()
                    .addUrl(DEFAULT_URL)
                    .site(site)
                    .as(SimplePage.class, page -> {
                        int count = pojoCount.incrementAndGet();
                        log.info("[SpiderExample] POJO映射 #{}，title={}, description={}",
                                count, page.getTitle(), page.getDescription());
                    })
                    .run();

            int count = pojoCount.get();
            if (count == 0) {
                log.error("[SpiderExample] POJO 映射无结果");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("[SpiderExample] POJO 映射抓取异常", e);
            return false;
        }
    }

    /**
     * 能力点4：深度控制 + 链接追踪。
     *
     * <p>配置 maxDepth=1 限制爬取深度，设置域名过滤器只爬取同域名页面，
     * 使用 HtmlLinkExtractor 提取页面链接进行追踪。
     * 限制最大页面数为 10，避免无限爬取。
     *
     * @return true 表示至少爬取到 1 个页面
     */
    public boolean testDepth() {
        log.info("[SpiderExample] === 能力点4: 深度控制 + 链接追踪 ===");

        // 结果计数器
        AtomicInteger counter = new AtomicInteger(0);

        // 目标域名
        String targetDomain = URI.create(DEFAULT_URL).getHost();
        if (targetDomain == null) {
            log.error("[SpiderExample] 无法解析目标域名");
            return false;
        }

        SpiderSite site = SpiderSite.builder()
                .domain(targetDomain)
                .maxDepth(MAX_DEPTH)
                .maxPages(MAX_PAGES)
                .interval(500)
                .build();

        // 域名白名单过滤器
        SpiderUrlFilter domainFilter = request -> {
            String host = URI.create(request.getUrl()).getHost();
            return host != null && host.endsWith(targetDomain);
        };

        // HTML 链接提取器
        SpiderLinkExtractor linkExtractor = new HtmlLinkExtractor();

        try {
            Spider.create()
                    .addUrl(DEFAULT_URL)
                    .site(site)
                    .linkExtractor(linkExtractor)
                    .urlFilter(domainFilter)
                    .threads(THREAD_COUNT)
                    .pipeline((Consumer<SpiderResult>) result -> {
                        int count = counter.incrementAndGet();
                        log.info("[SpiderExample] 深度追踪 #{}，depth={}, URL={}",
                                count, getDepth(result), result.getUrl());
                    })
                    .run();

            int count = counter.get();
            log.info("[SpiderExample] 深度追踪完成，总页面数={}", count);

            return count > 0;
        } catch (Exception e) {
            log.error("[SpiderExample] 深度追踪异常", e);
            return false;
        }
    }

    /**
     * 从结果中提取爬取深度。
     *
     * @param result 爬取结果
     * @return 深度值，获取失败返回 -1
     */
    private int getDepth(SpiderResult result) {
        if (result.getMetadata() == null) {
            return -1;
        }
        Object depth = result.getMetadata().get("depth");
        return depth instanceof Integer ? (Integer) depth : -1;
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        log.info("""
                SpiderExample — 爬虫模块综合自检示例

                用法:
                  java SpiderExample [type]

                能力点:
                  basic    — 基础单页抓取（默认 HttpFetcher + HtmlParser + ConsolePipeline）
                  threads  — 多线程并发抓取（5线程 + Lambda Pipeline）
                  pojo     — POJO 映射抓取（@SpiderField + CSS选择器）
                  depth    — 深度控制 + 链接追踪（maxDepth=1 + 域名过滤）
                  all      — 执行全部能力点（默认）

                示例:
                  java SpiderExample
                  java SpiderExample --type threads
                  java SpiderExample --help
                """);
    }

    // ==================== POJO 映射定义 ====================

    /**
     * 简单页面 POJO — 用于 @SpiderField 映射演示。
     *
     * <p>通过 CSS 选择器从 HTML 中提取页面标题和描述信息。
     * 仅使用 example.com 的简单结构进行验证。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class SimplePage {

        /**
         * 页面标题
         */
        @SpiderField(selector = "title", attr = "text")
        private String title;

        /**
         * 页面描述（meta description）
         */
        @SpiderField(selector = "meta[name=description]", attr = "content")
        private String description;
    }
}
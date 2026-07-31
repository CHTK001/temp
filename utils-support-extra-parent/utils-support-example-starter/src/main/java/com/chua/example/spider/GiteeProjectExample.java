package com.chua.example.spider;

import com.chua.spider.support.Spider;
import com.chua.spider.support.model.SpiderResult;
import com.chua.spider.support.model.SpiderSite;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Gitee GVP 项目抓取示例 — 爬取 Gitee 最有价值开源项目 5 页数据。
 *
 * <p>本示例演示爬虫模块的实战能力：多种子 URL 并发抓取 + 自定义 Pipeline 解析。
 * 抓取目标为 Gitee GVP 列表页（https://gitee.com/gvp?page=1~5），
 * 通过 jsoup 解析 HTML 卡片结构，提取项目名称、描述、语言、Star 数、Fork 数。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认抓取 5 页 GVP 项目数据
 *   java GiteeProjectExample
 *
 *   # 指定页数（1~10）
 *   java GiteeProjectExample --pages 3
 *
 *   # 打印帮助
 *   java GiteeProjectExample --help
 * </pre>
 *
 * <h2>提取字段</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>CSS 选择器</th><th>说明</th></tr>
 *   <tr><td>项目名称</td><td>.project-name</td><td>data-url 为项目路径</td></tr>
 *   <tr><td>项目描述</td><td>.project-description</td><td>title 属性为完整描述</td></tr>
 *   <tr><td>编程语言</td><td>.project-labels .label</td><td>语言标签</td></tr>
 *   <tr><td>Star 数</td><td>.project-metas span 第 1 个</td><td>Star 数量</td></tr>
 *   <tr><td>Fork 数</td><td>.project-metas span 第 2 个</td><td>Fork 数量</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class GiteeProjectExample {

    /**
     * GVP 分类页 URL 列表
     *
     * <p>GVP（Gitee 最有价值开源项目）按技术分类，每分类页面包含不同的项目列表。
     * 由于 GVP 列表页前端分页参数无效（所有 page 返回同一页），
     * 改用不同分类页来获取多样化的项目数据。
     */
    private static final String[] GVP_CATEGORY_URLS = {
            "https://gitee.com/gvp/all",
            "https://gitee.com/gvp/new-tech",
            "https://gitee.com/gvp/Artificial-Intelligence",
            "https://gitee.com/gvp/program-develop",
            "https://gitee.com/gvp/enterprise-app"
    };

    /**
     * 默认抓取分类页数
     */
    private static final int DEFAULT_PAGES = 5;

    /**
     * 最大允许分类页数
     */
    private static final int MAX_PAGES = 10;

    /**
     * 请求间隔（毫秒）
     */
    private static final int REQUEST_INTERVAL_MS = 1000;

    /**
     * 并发线程数
     */
    private static final int THREAD_COUNT = 3;

    /**
     * 项目卡片 CSS 选择器（同时匹配 GVP 首页和分类页的卡片样式）
     */
    private static final String SELECTOR_PROJECT_CARD = ".project-card";

    /**
     * 项目名称 CSS 选择器
     */
    private static final String SELECTOR_PROJECT_NAME = ".project-name";

    /**
     * 项目描述 CSS 选择器
     */
    private static final String SELECTOR_PROJECT_DESC = ".project-description";

    /**
     * 项目语言标签 CSS 选择器
     */
    private static final String SELECTOR_PROJECT_LABEL = ".project-labels .label";

    /**
     * 项目元数据 CSS 选择器
     */
    private static final String SELECTOR_PROJECT_META = ".project-metas .meta span";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 主入口：解析参数并执行 GVP 项目抓取。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        int pageLimit = DEFAULT_PAGES;

        // 解析命令行参数
        if (args != null && args.length > 0) {
            String first = args[0];
            if ("--help".equals(first) || "-h".equals(first)) {
                printHelp();
                return;
            }
            if ("--pages".equals(first) && args.length > 1) {
                try {
                    pageLimit = Integer.parseInt(args[1]);
                    if (pageLimit < 1 || pageLimit > GVP_CATEGORY_URLS.length) {
                        log.warn("页数超出范围 [1, {}]，使用默认值 {}", GVP_CATEGORY_URLS.length, DEFAULT_PAGES);
                        pageLimit = DEFAULT_PAGES;
                    }
                } catch (NumberFormatException e) {
                    log.warn("页数参数格式错误，使用默认值 {}", DEFAULT_PAGES);
                }
            }
        }

        GiteeProjectExample example = new GiteeProjectExample();
        List<GiteeProject> projects = example.crawlGvp(pageLimit);

        if (projects.isEmpty()) {
            log.error("未抓取到任何项目数据");
            System.exit(EXIT_CODE_FAILURE);
        }

        // 打印抓取结果汇总
        printSummary(projects, pageLimit);
        System.exit(EXIT_CODE_SUCCESS);
    }

    /**
     * 抓取 GVP 多页项目数据。
     *
     * <p>从 GVP 分类页列表中选取指定数量的分类页面，
     * 配置 maxDepth=0 只爬种子页面，使用自定义 Pipeline 通过 jsoup 解析 HTML 卡片结构提取项目信息。
     *
     * @param pageLimit 抓取分类页数量
     * @return 所有分类页面的项目数据列表
     */
    public List<GiteeProject> crawlGvp(int pageLimit) {
        // 线程安全的项目结果集合
        List<GiteeProject> allProjects = new CopyOnWriteArrayList<>();

        // 站点配置：只爬种子页面，不追踪链接
        SpiderSite site = SpiderSite.builder()
                .maxDepth(0)
                .interval(REQUEST_INTERVAL_MS)
                .retryTimes(3)
                .timeout(30000)
                .build();

        // 构建 Spider 并添加每个分类页 URL
        Spider.Builder builder = Spider.create()
                .site(site)
                .threads(THREAD_COUNT)
                .pipeline((Consumer<SpiderResult>) result -> {
                    List<GiteeProject> pageProjects = parseProjects(result);
                    allProjects.addAll(pageProjects);
                    log.info("页面解析完成，URL={}, 项目数={}", result.getUrl(), pageProjects.size());
                });

        // 添加分类页种子 URL
        int actualPages = Math.min(pageLimit, GVP_CATEGORY_URLS.length);
        for (int i = 0; i < actualPages; i++) {
            builder.addUrl(GVP_CATEGORY_URLS[i]);
        }

        log.info("开始抓取 Gitee GVP 项目，共 {} 个分类页", actualPages);

        try {
            builder.build().runSync();
        } catch (Exception e) {
            log.error("抓取过程异常", e);
        }

        log.info("抓取完成，共获取 {} 个项目", allProjects.size());
        return allProjects;
    }

    /**
     * 从爬取结果中解析项目卡片数据。
     *
     * <p>使用 jsoup 解析 HTML，遍历 .project-card 元素，
     * 逐个提取项目名称、描述、语言、Star 数、Fork 数。
     *
     * @param result 爬取结果
     * @return 当前页面的项目列表
     */
    private List<GiteeProject> parseProjects(SpiderResult result) {
        List<GiteeProject> projects = new ArrayList<>();

        if (result == null || result.getHtml() == null || result.getHtml().isEmpty()) {
            log.warn("页面内容为空: {}", result != null ? result.getUrl() : "null");
            return projects;
        }

        try {
            Document doc = Jsoup.parse(result.getHtml());
            Elements cards = doc.select(SELECTOR_PROJECT_CARD);

            for (Element card : cards) {
                GiteeProject project = parseSingleCard(card);
                if (project != null) {
                    projects.add(project);
                }
            }
        } catch (Exception e) {
            log.error("HTML 解析异常: {}", result.getUrl(), e);
        }

        return projects;
    }

    /**
     * 解析单个项目卡片元素。
     *
     * <p>项目链接优先从父级 {@code <a href>} 标签提取，
     * 回退到 {@code .project-name} 的 {@code data-url} 属性。
     *
     * @param card jsoup 项目卡片元素
     * @return 项目数据对象，解析失败返回 null
     */
    private GiteeProject parseSingleCard(Element card) {
        try {
            // 提取项目名称
            Element nameEl = card.selectFirst(SELECTOR_PROJECT_NAME);
            if (nameEl == null) {
                return null;
            }
            String name = nameEl.attr("title");
            if (name == null || name.isEmpty()) {
                name = nameEl.text();
            }

            // 提取项目链接：从卡片内第一个 <a> 标签 href 获取
            String url = "";
            Element linkEl = card.selectFirst("a[href]");
            if (linkEl != null) {
                url = linkEl.attr("href");
            }
            // 回退到 data-url 属性
            if (url == null || url.isEmpty()) {
                url = nameEl.attr("data-url");
            }

            // 提取项目描述
            Element descEl = card.selectFirst(SELECTOR_PROJECT_DESC);
            String description = descEl != null ? descEl.attr("title") : "";
            if (description == null || description.isEmpty()) {
                description = descEl != null ? descEl.text() : "";
            }

            // 提取编程语言
            Elements labelEls = card.select(SELECTOR_PROJECT_LABEL);
            String language = labelEls.isEmpty() ? "" : labelEls.first().text();

            // 提取 Star 数和 Fork 数
            Elements metaEls = card.select(SELECTOR_PROJECT_META);
            String stars = metaEls.size() > 0 ? metaEls.get(0).text() : "0";
            String forks = metaEls.size() > 1 ? metaEls.get(1).text() : "0";

            return new GiteeProject(name, url, description, language, stars, forks);
        } catch (Exception e) {
            log.warn("单卡片解析异常", e);
            return null;
        }
    }

    /**
     * 打印抓取结果汇总。
     *
     * <p>输出抓取页数、项目总数，并逐条打印每个项目的关键信息。
     *
     * @param projects 项目数据列表
     * @param pageLimit 抓取分类页数量
     */
    private static void printSummary(List<GiteeProject> projects, int pageLimit) {
        //noinspection UseOfSystemOutCode
        System.out.println();
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");
        //noinspection UseOfSystemOutCode
        System.out.println("  Gitee GVP 项目抓取结果");
        //noinspection UseOfSystemOutCode
        System.out.println("  抓取分类页数: " + pageLimit);
        //noinspection UseOfSystemOutCode
        System.out.println("  项目总数: " + projects.size());
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");

        AtomicInteger index = new AtomicInteger(0);
        for (GiteeProject project : projects) {
            int no = index.incrementAndGet();
            //noinspection UseOfSystemOutCode
            System.out.printf("%n  [%03d] %s%n", no, project.name());
            //noinspection UseOfSystemOutCode
            System.out.printf("        URL:  https://gitee.com%s%n", project.url());
            //noinspection UseOfSystemOutCode
            System.out.printf("        语言: %s  ⭐ %s  🍴 %s%n", project.language(), project.stars(), project.forks());

            String desc = project.description();
            if (desc != null && !desc.isEmpty()) {
                // 描述截断显示
                String preview = desc.length() > 80 ? desc.substring(0, 80) + "..." : desc;
                //noinspection UseOfSystemOutCode
                System.out.printf("        描述: %s%n", preview);
            }
        }

        //noinspection UseOfSystemOutCode
        System.out.println();
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");
        //noinspection UseOfSystemOutCode
        System.out.println("  抓取完成，共 " + projects.size() + " 个项目");
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        //noinspection UseOfSystemOutCode
        System.out.println("""
                GiteeProjectExample — Gitee GVP 项目抓取示例

                用法:
                  java GiteeProjectExample [--pages N]

                选项:
                  --pages N    抓取分类页数量，范围 1~5，默认 5
                  --help       显示此帮助

                说明:
                  从 Gitee GVP 不同分类页面抓取开源项目数据，
                  每个分类页包含不同的项目列表。

                示例:
                  java GiteeProjectExample
                  java GiteeProjectExample --pages 3
                """);
    }

    // ==================== 数据容器 ====================

    /**
     * Gitee 项目数据容器。
     *
     * @param name        项目名称
     * @param url         项目路径（相对路径，如 /anyup/uView-Pro）
     * @param description 项目描述
     * @param language    编程语言
     * @param stars       Star 数
     * @param forks       Fork 数
     * @author CH
     * @since 4.0.0.42
     */
    private record GiteeProject(
            String name,
            String url,
            String description,
            String language,
            String stars,
            String forks
    ) {
    }
}
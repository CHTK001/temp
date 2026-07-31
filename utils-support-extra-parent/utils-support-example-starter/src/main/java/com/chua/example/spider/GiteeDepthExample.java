package com.chua.example.spider;

import com.chua.spider.support.Spider;
import com.chua.spider.support.SpiderLinkExtractor;
import com.chua.spider.support.SpiderUrlFilter;
import com.chua.spider.support.extractor.HtmlLinkExtractor;
import com.chua.spider.support.model.SpiderRequest;
import com.chua.spider.support.model.SpiderResponse;
import com.chua.spider.support.model.SpiderResult;
import com.chua.spider.support.model.SpiderSite;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Gitee 项目深度抓取示例 — maxDepth=1 从 GVP 列表页追踪到项目详情页。
 *
 * <p>本示例演示爬虫模块的深度追踪能力：
 * <ol>
 *   <li>种子 URL 为 GVP 分类列表页（深度 0）</li>
 *   <li>LinkExtractor 从列表页提取项目链接（深度 1）</li>
 *   <li>域名过滤器限制只爬 gitee.com 域名</li>
 *   <li>maxPages 限制最多抓取 10 个详情页，避免无限爬取</li>
 *   <li>自定义 Pipeline 分别处理列表页和详情页数据</li>
 * </ol>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认抓取 10 个项目详情
 *   java GiteeDepthExample
 *
 *   # 指定最大详情页数量
 *   java GiteeDepthExample --max 5
 *
 *   # 打印帮助
 *   java GiteeDepthExample --help
 * </pre>
 *
 * <h2>爬取流程</h2>
 * <pre>
 *   GVP 列表页 (depth=0)
 *     │
 *     ├─ HtmlLinkExtractor 提取项目链接
 *     ├─ DomainUrlFilter 过滤非 gitee.com 链接
 *     ├─ ProjectUrlFilter 只保留 /owner/repo 格式的项目链接
 *     │
 *     ▼
 *   项目详情页 (depth=1)
 *     │
 *     ├─ 提取项目标题、描述、Star/Fork/Watch 数
 *     ├─ 提取编程语言、许可证
 *     └─ Pipeline 输出完整项目信息
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class GiteeDepthExample {

    /**
     * GVP 全部分类页 URL
     */
    private static final String GVP_SEED_URL = "https://gitee.com/gvp/all";

    /**
     * 目标域名
     */
    private static final String TARGET_DOMAIN = "gitee.com";

    /**
     * 默认最大详情页数量
     */
    private static final int DEFAULT_MAX_PAGES = 10;

    /**
     * 最大爬取深度
     */
    private static final int MAX_DEPTH = 1;

    /**
     * 请求间隔（毫秒）
     */
    private static final int REQUEST_INTERVAL_MS = 500;

    /**
     * 并发线程数
     */
    private static final int THREAD_COUNT = 3;

    /**
     * 项目卡片 CSS 选择器
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
     * Star 数 CSS 选择器（详情页）
     */
    private static final String SELECTOR_STAR_COUNT = ".star-container .action-social-count";

    /**
     * Fork 数 CSS 选择器（详情页）
     */
    private static final String SELECTOR_FORK_COUNT = ".fork-container .action-social-count";

    /**
     * 项目标题 CSS 选择器（详情页）
     */
    private static final String SELECTOR_PROJECT_TITLE = ".git-project-title";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 主入口：解析参数并执行深度抓取。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        int maxPages = DEFAULT_MAX_PAGES;

        // 解析命令行参数
        if (args != null && args.length > 0) {
            String first = args[0];
            if ("--help".equals(first) || "-h".equals(first)) {
                printHelp();
                return;
            }
            if ("--max".equals(first) && args.length > 1) {
                try {
                    maxPages = Integer.parseInt(args[1]);
                    if (maxPages < 1 || maxPages > 50) {
                        log.warn("详情页数量超出范围 [1, 50]，使用默认值 {}", DEFAULT_MAX_PAGES);
                        maxPages = DEFAULT_MAX_PAGES;
                    }
                } catch (NumberFormatException e) {
                    log.warn("参数格式错误，使用默认值 {}", DEFAULT_MAX_PAGES);
                }
            }
        }

        GiteeDepthExample example = new GiteeDepthExample();
        List<ProjectDetail> details = example.crawl(maxPages);

        if (details.isEmpty()) {
            log.error("未抓取到任何项目详情");
            System.exit(EXIT_CODE_FAILURE);
        }

        printDetails(details);
        System.exit(EXIT_CODE_SUCCESS);
    }

    /**
     * 执行深度抓取。
     *
     * <p>从 GVP 列表页出发，maxDepth=1 追踪到项目详情页。
     * 使用域名过滤器限制爬取范围，使用项目链接过滤器只保留项目页面。
     *
     * @param maxPages 最大详情页数量
     * @return 项目详情列表
     */
    public List<ProjectDetail> crawl(int maxPages) {
        // 线程安全的结果集合
        List<ProjectDetail> allDetails = new CopyOnWriteArrayList<>();

        // 站点配置：限制深度和页数
        SpiderSite site = SpiderSite.builder()
                .domain(TARGET_DOMAIN)
                .maxDepth(MAX_DEPTH)
                .maxPages(maxPages)
                .interval(REQUEST_INTERVAL_MS)
                .retryTimes(3)
                .timeout(30000)
                .build();

        // 域名过滤器：只爬取 gitee.com 域名
        SpiderUrlFilter domainFilter = request -> {
            String url = request.getUrl();
            if (url == null || url.isEmpty()) {
                return false;
            }
            // 限制域名
            return url.contains("gitee.com/");
        };

        // 项目链接过滤器：只保留 /owner/repo 格式的项目页面
        SpiderUrlFilter projectFilter = new ProjectUrlFilter();

        // HTML 链接提取器
        SpiderLinkExtractor linkExtractor = new HtmlLinkExtractor();

        log.info("开始深度抓取，种子URL={}, maxDepth={}, maxPages={}", GVP_SEED_URL, MAX_DEPTH, maxPages);

        try {
            Spider.create()
                    .addUrl(GVP_SEED_URL)
                    .site(site)
                    .linkExtractor(linkExtractor)
                    .urlFilter(domainFilter)
                    .urlFilter(projectFilter)
                    .threads(THREAD_COUNT)
                    .pipeline((Consumer<SpiderResult>) result -> {
                        // 根据爬取深度分别处理：0=列表页，1=详情页
                        if (result.getDepth() == 0) {
                            // 列表页：打印发现的链接数
                            log.info("列表页解析完成，URL={}", result.getUrl());
                        } else {
                            // 详情页：提取项目详情
                            ProjectDetail detail = parseDetail(result);
                            if (detail != null) {
                                allDetails.add(detail);
                                log.info("详情页解析完成 #{}，depth={}，项目={}",
                                        allDetails.size(), result.getDepth(), detail.title());
                            }
                        }
                    })
                    .build()
                    .runSync();
        } catch (Exception e) {
            log.error("深度抓取异常", e);
        }

        log.info("深度抓取完成，共获取 {} 个项目详情", allDetails.size());
        return allDetails;
    }

    /**
     * 解析项目详情页。
     *
     * <p>从详情页 HTML 中提取项目标题、描述、Star/Fork 数、编程语言等信息。
     *
     * @param result 爬取结果
     * @return 项目详情对象，解析失败返回 null
     */
    private ProjectDetail parseDetail(SpiderResult result) {
        if (result == null || result.getHtml() == null || result.getHtml().isEmpty()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(result.getHtml());
            String url = result.getUrl();

            // 提取项目标题
            String title = extractTitle(doc);

            // 提取项目描述（meta description）
            String description = extractMetaContent(doc, "name", "Description");

            // 提取 Star 数
            String stars = extractSocialCount(doc, SELECTOR_STAR_COUNT);

            // 提取 Fork 数
            String forks = extractSocialCount(doc, SELECTOR_FORK_COUNT);

            // 提取编程语言（meta Keywords）
            String language = extractLanguageFromKeywords(doc);

            return new ProjectDetail(title, url, description, language, stars, forks);
        } catch (Exception e) {
            log.warn("详情页解析异常: {}", result.getUrl(), e);
            return null;
        }
    }

    /**
     * 从详情页提取项目标题。
     *
     * @param doc HTML 文档
     * @return 项目标题
     */
    private String extractTitle(Document doc) {
        // 优先从 git-project-title 提取
        Element titleEl = doc.selectFirst(SELECTOR_PROJECT_TITLE);
        if (titleEl != null) {
            return titleEl.text().trim();
        }
        // 回退到 <title> 标签
        String pageTitle = doc.title();
        if (pageTitle != null && !pageTitle.isEmpty()) {
            return pageTitle.trim();
        }
        return "";
    }

    /**
     * 从 meta 标签提取 content 属性值。
     *
     * @param doc      HTML 文档
     * @param attrName meta 标签的 name 属性值
     * @param attrValue 要匹配的 name 值
     * @return content 属性值，未找到返回空字符串
     */
    private String extractMetaContent(Document doc, String attrName, String attrValue) {
        Element metaEl = doc.selectFirst("meta[" + attrName + "=" + attrValue + "]");
        if (metaEl != null) {
            return metaEl.attr("content");
        }
        return "";
    }

    /**
     * 提取社交计数（Star/Fork）。
     *
     * @param doc      HTML 文档
     * @param selector CSS 选择器
     * @return 计数值，未找到返回 "0"
     */
    private String extractSocialCount(Document doc, String selector) {
        Element countEl = doc.selectFirst(selector);
        if (countEl != null) {
            String title = countEl.attr("title");
            if (title != null && !title.isEmpty()) {
                return title;
            }
            return countEl.text().trim();
        }
        return "0";
    }

    /**
     * 从 meta Keywords 中提取编程语言。
     *
     * <p>Gitee 详情页的 meta Keywords 格式为 "项目名,语言"，
     * 取逗号后的部分作为编程语言。
     *
     * @param doc HTML 文档
     * @return 编程语言，未找到返回空字符串
     */
    private String extractLanguageFromKeywords(Document doc) {
        String keywords = extractMetaContent(doc, "name", "Keywords");
        if (keywords != null && !keywords.isEmpty()) {
            int commaIdx = keywords.indexOf(',');
            if (commaIdx >= 0 && commaIdx < keywords.length() - 1) {
                return keywords.substring(commaIdx + 1).trim();
            }
        }
        return "";
    }

    /**
     * 打印项目详情列表。
     *
     * @param details 项目详情列表
     */
    private static void printDetails(List<ProjectDetail> details) {
        //noinspection UseOfSystemOutCode
        System.out.println();
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");
        //noinspection UseOfSystemOutCode
        System.out.println("  Gitee 项目深度抓取结果（maxDepth=1）");
        //noinspection UseOfSystemOutCode
        System.out.println("  项目详情数: " + details.size());
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");

        AtomicInteger index = new AtomicInteger(0);
        for (ProjectDetail detail : details) {
            int no = index.incrementAndGet();
            //noinspection UseOfSystemOutCode
            System.out.printf("%n  [%03d] %s%n", no, detail.title());
            //noinspection UseOfSystemOutCode
            System.out.printf("        URL:   %s%n", detail.url());
            //noinspection UseOfSystemOutCode
            System.out.printf("        语言:  %s%n", detail.language());
            //noinspection UseOfSystemOutCode
            System.out.printf("        Star:  %s  Fork: %s%n", detail.stars(), detail.forks());

            String desc = detail.description();
            if (desc != null && !desc.isEmpty()) {
                String preview = desc.length() > 100 ? desc.substring(0, 100) + "..." : desc;
                //noinspection UseOfSystemOutCode
                System.out.printf("        描述:  %s%n", preview);
            }
        }

        //noinspection UseOfSystemOutCode
        System.out.println();
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");
        //noinspection UseOfSystemOutCode
        System.out.println("  深度抓取完成，共 " + details.size() + " 个项目详情");
        //noinspection UseOfSystemOutCode
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        //noinspection UseOfSystemOutCode
        System.out.println("""
                GiteeDepthExample — Gitee 项目深度抓取示例（maxDepth=1）

                用法:
                  java GiteeDepthExample [--max N]

                选项:
                  --max N    最大详情页数量，范围 1~50，默认 10
                  --help     显示此帮助

                爬取流程:
                  1. 从 GVP 列表页提取项目链接
                  2. 追踪到项目详情页（深度=1）
                  3. 提取项目标题、描述、Star/Fork 数、编程语言

                示例:
                  java GiteeDepthExample
                  java GiteeDepthExample --max 5
                """);
    }

    // ==================== URL 过滤器 ====================

    /**
     * 项目链接过滤器 — 只保留 /owner/repo 格式的项目页面。
     *
     * <p>Gitee 项目 URL 格式为 {@code https://gitee.com/{owner}/{repo}}，
     * 排除 /gvp、/explore、/api、/help 等非项目路径。
     *
     * @author CH
     * @since 4.0.0.42
     */
    private static class ProjectUrlFilter implements SpiderUrlFilter {

        /**
         * 需要排除的路径前缀
         */
        private static final String[] EXCLUDED_PREFIXES = {
                "/gvp", "/explore", "/api", "/help", "/login", "/signup",
                "/settings", "/notifications", "/search", "/assets",
                "/static", "/about", "/all", "/organizations",
                "/milestones", "/trending", "/events", "/weibo"
        };

        @Override
        public boolean accept(SpiderRequest request) {
            String url = request.getUrl();
            if (url == null || url.isEmpty()) {
                return false;
            }

            // 种子 URL 始终允许
            if (GVP_SEED_URL.equals(url)) {
                return true;
            }

            // 提取路径部分
            String path = extractPath(url);
            if (path == null || path.isEmpty()) {
                return false;
            }

            // 排除非项目路径
            for (String prefix : EXCLUDED_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return false;
                }
            }

            // 项目路径格式：/owner/repo（两段路径）
            String[] segments = path.split("/");
            // split 后第一段为空字符串，项目路径至少 3 段（""/"owner"/"repo"）
            if (segments.length < 3) {
                return false;
            }

            // 排除过多路径段（如 /owner/repo/issues、/owner/repo/pulls 等）
            // 只保留 /owner/repo 或 /owner/repo/ 结尾
            if (segments.length > 3 && !segments[3].isEmpty()) {
                return false;
            }

            return true;
        }

        /**
         * 从完整 URL 中提取路径部分。
         *
         * @param url 完整 URL
         * @return 路径部分，如 "/jishenghua/JSH_ERP"
         */
        private String extractPath(String url) {
            try {
                int schemeEnd = url.indexOf("://");
                if (schemeEnd < 0) {
                    return url;
                }
                int pathStart = url.indexOf('/', schemeEnd + 3);
                if (pathStart < 0) {
                    return "";
                }
                int queryStart = url.indexOf('?', pathStart);
                if (queryStart >= 0) {
                    return url.substring(pathStart, queryStart);
                }
                return url.substring(pathStart);
            } catch (Exception e) {
                return "";
            }
        }
    }

    // ==================== 数据容器 ====================

    /**
     * 项目详情数据容器。
     *
     * @param title       项目标题
     * @param url         项目详情页 URL
     * @param description 项目描述
     * @param language    编程语言
     * @param stars       Star 数
     * @param forks       Fork 数
     * @author CH
     * @since 4.0.0.42
     */
    private record ProjectDetail(
            String title,
            String url,
            String description,
            String language,
            String stars,
            String forks
    ) {
    }
}
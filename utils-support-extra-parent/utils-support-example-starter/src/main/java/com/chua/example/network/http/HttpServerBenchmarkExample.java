package com.chua.example.network.http;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import com.chua.common.support.network.server.http.ConfigServer;
import com.chua.common.support.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HttpServer 全子类一键式接口压测(内部 k6 风格压测引擎)。
 *
 * <p>用法:</p>
 * <pre>{@code
 * java com.chua.example.network.http.HttpServerBenchmark \
 *   --servers=jdk,nio,vertx-http,http \
 *   --concurrency=100,500,1000,2000 \
 *   --delays=0,1000,3000,5000 \
 *   --payload=128 \
 *   --report=target/http-server-benchmark.html
 * }</pre>
 *
 * <p>每个 HttpServer 子类 × 每个服务端延迟场景(0/1s/3s/5s sleep) × 每个并发等级,
 * 内部多虚拟用户并发请求 /echo 接口,统计 RPS / 成功率 / p50 / p95 / p99 / max,
 * 输出单份 HTML 报告,内含各子类横向对比的 CSS 柱状图。</p>
 * @author CH
 * @since 2026/08/15
 */
@Slf4j
public final class HttpServerBenchmarkExample {

    private HttpServerBenchmarkExample() {
    }

    // ==================== 参数 ====================

    /** 单个压测场景结果 */
    public static final class ScenarioResult {
        /** 服务器类型 */
        public final String serverType;
        /** DelayMS */
        public final int delayMs;
        /** Concurrency */
        public final int concurrency;
        /** 总数 */
        public final long total;
        /** Errors */
        public final long errors;
        /** ElapsedMS */
        public final long elapsedMs;
        /** RPS */
        public final double rps;
        /** P50 */
        public final double p50;
        /** P95 */
        public final double p95;
        /** P99 */
        public final double p99;
        /** P999 */
        public final double p999;
        /** 最大值 */
        public final double max;
        /** Mean */
        public final double mean;
        /** Success比率 */
        public final double successRate;
        /** 开始MEMMB */
        public final long startMemMb;
        /** PeakMEMMB */
        public final long peakMemMb;
        /** 结束MEMMB */
        public final long endMemMb;

        ScenarioResult(String serverType, int delayMs, int concurrency,
                       long total, long errors, long elapsedMs, long[] sortedLatMs,
                       long startMemMb, long peakMemMb, long endMemMb) {
            this.serverType = serverType;
            this.delayMs = delayMs;
            this.concurrency = concurrency;
            this.total = total;
            this.errors = errors;
            this.elapsedMs = elapsedMs;
            this.rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
            this.p50 = percentile(sortedLatMs, 0.50);
            this.p95 = percentile(sortedLatMs, 0.95);
            this.p99 = percentile(sortedLatMs, 0.99);
            this.p999 = percentile(sortedLatMs, 0.999);
            this.max = sortedLatMs.length > 0 ? sortedLatMs[sortedLatMs.length - 1] : 0;
            this.mean = sortedLatMs.length > 0
                    ? Arrays.stream(sortedLatMs).average().orElse(0) : 0;
            this.successRate = total > 0 ? (total - errors) * 100.0 / total : 0;
            this.startMemMb = startMemMb;
            this.peakMemMb = peakMemMb;
            this.endMemMb = endMemMb;
        }
    }

    // ==================== 压测引擎 ====================

    /**
     * 对单个 Server 子类 × 单个延迟场景 × 单个并发等级执行一次 k6 风格压测。
     *
     * @param serverType     SPI 类型 (jdk/nio/vertx-http/...)
     * @param delayMs        服务端 sleep 毫秒 (0=无延迟)
     * @param concurrency    并发虚拟用户数
     * @param requestsPerVu  每虚拟用户请求数
     * @param payloadSize    请求体大小 (字节)
     * @return 压测结果
     */
    private static ScenarioResult runScenario(String serverType, int delayMs, int concurrency,
                                              int requestsPerVu, int payloadSize) {
        Server server = null;
        ExecutorService pool = null;
        try {
            byte[] payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'A');
            String body = new String(payload, StandardCharsets.UTF_8);

            // 启动 Server 并注册 /echo(handler 内 sleep delayMs 模拟服务端延迟/满接口)
            server = ServerBuilder.create().type(serverType).host("127.0.0.1").port(0).build();
            ((ConfigServer) server).registerMapping("/echo", (req, resp) -> {
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                resp.setResult(body);
            });
            server.start();
            int port = server.getPort();

            // 并发虚拟用户,每用户 requestsPerVu 次请求
            pool = ThreadUtils.newVirtualThreadPerTaskExecutor();
            // 启动时基线内存: 测试占用 = 当前实际内存 - 启动时内存
            long baselineMemMb = usedMemMb();
            AtomicLong peakUsedMb = new AtomicLong(baselineMemMb);
            Thread memSampler = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    long used = usedMemMb();
                    peakUsedMb.accumulateAndGet(used, Math::max);
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "bench-mem-sampler");
            memSampler.setDaemon(true);
            memSampler.start();
            CountDownLatch ready = new CountDownLatch(concurrency);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(concurrency);
            AtomicLong errors = new AtomicLong();
            List<long[]> latencies = Collections.synchronizedList(new ArrayList<>());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    try {
                        Thread.sleep(10);
                        ready.countDown();
                        start.await();
                        long[] mine = new long[requestsPerVu];
                        for (int k = 0; k < requestsPerVu; k++) {
                            // keep-alive 连接复用竞态:服务端恰好关闭连接时偶发失败,
                            // 重试一次(自动新建连接)即可消除
                            for (int attempt = 0; ; attempt++) {
                                try {
                                    HttpRequest req = HttpRequest.newBuilder(
                                                    URI.create("http://127.0.0.1:" + port + "/echo"))
                                            .timeout(Duration.ofSeconds(15))
                                            .GET().build();
                                    long s = System.nanoTime();
                                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                                    long lat = (System.nanoTime() - s) / 1_000_000L;
                                    if (resp.statusCode() != 200 && attempt >= 1) {
                                        errors.incrementAndGet();
                                    }
                                    if (resp.statusCode() != 200) {
                                        // 重试一次
                                        continue;
                                    }
                                    mine[k] = lat;
                                    break;
                                } catch (Exception e) {
                                    if (attempt >= 1) {
                                        errors.incrementAndGet();
                                        break;
                                    }
                                    // 重试一次
                                }
                            }
                        }
                        latencies.add(mine);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            if (!ready.await(60, TimeUnit.SECONDS)) {
                log.warn("[bench] {} delay={}ms 并发={} 就绪超时", serverType, delayMs, concurrency);
                start.countDown();
            }
            Thread.sleep(50);
            long wallStart = System.nanoTime();
            start.countDown();
            if (!done.await(60, TimeUnit.SECONDS)) {
                log.warn("[bench] {} delay={}ms 并发={} 完成超时", serverType, delayMs, concurrency);
            }
            long elapsedMs = (System.nanoTime() - wallStart) / 1_000_000L;

            memSampler.interrupt();
            long endUsedMb = usedMemMb();

            long[] all = merge(latencies);
            Arrays.sort(all);
            long total = (long) concurrency * requestsPerVu;
            // 测试占用内存 = 当前实际内存 - 启动时基线内存
            // GC 后占用可能低于基线,用 Math.max(0, ...) 截断避免出现负数
            return new ScenarioResult(serverType, delayMs, concurrency,
                    total, errors.get(), elapsedMs, all,
                    baselineMemMb,
                    Math.max(0, peakUsedMb.get() - baselineMemMb),
                    Math.max(0, endUsedMb - baselineMemMb));
        } catch (Exception e) {
            log.warn("[bench] {} delay={}ms 压测失败: {}", serverType, delayMs, e.getMessage());
            return null;
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
                }
            }
            if (pool != null) {
                pool.shutdownNow();
            }
            // 场景间隔离:上一个场景的虚拟线程/连接可能未完全释放,会污染下一个场景
            // (曾出现 nio 单跑正常 RPS=1056,但 jdk 之后连跑异常 RPS=38/p50=8s 的现象)
            System.gc();
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== 报告 ====================

    /**
     * 生成单份 HTML 报告:各子类 × 各延迟场景的图表对比。
     */
    private static void writeHtmlReport(String reportPath, List<ScenarioResult> results,
                                        int[] concurrencyLevels, int[] delays, int payloadSize) {
        // 加载现代化模板(resources/benchmark-report-template.html),替换占位符生成报告
        String template = loadTemplate();
        if (template == null) {
            log.warn("[bench] 报告模板加载失败,无法生成报告: {}", reportPath);
            return;
        }

        // ===== 实现名称列表(按出现顺序) =====
        List<String> serverNames = new ArrayList<>();
        for (ScenarioResult r : results) {
            if (!serverNames.contains(r.serverType)) {
                serverNames.add(r.serverType);
            }
        }
        int lastCc = concurrencyLevels[concurrencyLevels.length - 1];

        // ===== KPI 指标卡:每实现一行(平均成功率/峰值吞吐/平均p99) =====
        StringBuilder kpiCards = new StringBuilder();
        for (String s : serverNames) {
            List<ScenarioResult> sr = results.stream()
                    .filter(r -> r.serverType.equals(s)).toList();
            // 用平均成功率(而非 min):单次采样 0% 场景会把 min 拉到 0,造成"表格100%但KPI显示0"的错觉
            double rate = sr.stream().mapToDouble(r -> r.successRate).average().orElse(100);
            double rateMin = sr.stream().mapToDouble(r -> r.successRate).min().orElse(100);
            double rps = sr.stream().mapToDouble(r -> r.rps).max().orElse(0);
            double avgP99 = sr.stream().mapToDouble(r -> r.p99).average().orElse(0);
            kpiCards.append("<div class=\"kpi\"><div class=\"label\">")
                    .append(esc(s)).append("</div>")
                    .append("<div class=\"value\">")
                    .append(String.format(Locale.ROOT, "%.2f%%", rate))
                    .append("</div>")
                    .append("<div class=\"hint\">最低 ")
                    .append(String.format(Locale.ROOT, "%.2f%%", rateMin))
                    .append(" · 峰值吞吐 ")
                    .append(String.format(Locale.ROOT, "%,.0f", rps)).append(" req/s · 平均p99 ")
                    .append(String.format(Locale.ROOT, "%.1f", avgP99)).append(" ms</div></div>\n");
        }

        // ===== 下拉切换选项(无延迟/1s/3s/汇总/明细,所有内容跟随切换) =====
        StringBuilder tabButtons = new StringBuilder();
        tabButtons.append("<option value=\"tab-overview\" selected>总览</option>\n");
        for (int d : delays) {
            tabButtons.append("<option value=\"tab-delay-").append(d).append("\">")
                    .append(d == 0 ? "无延迟" : "业务" + (d / 1000) + "s延迟").append("</option>\n");
        }
        tabButtons.append("<option value=\"tab-summary\">延迟对比汇总</option>\n");
        tabButtons.append("<option value=\"tab-detail\">全量明细</option>\n");

        // ===== 面板内容 + 图表脚本 =====
        StringBuilder panels = new StringBuilder();
        StringBuilder scripts = new StringBuilder();

        // 按延迟分组: 每个延迟场景一个章节, 章节内对比各实现指标
        Map<Integer, List<ScenarioResult>> byDelay = new java.util.LinkedHashMap<>();
        for (ScenarioResult r : results) {
            byDelay.computeIfAbsent(r.delayMs, k -> new ArrayList<>()).add(r);
        }
        int section = 0;
        for (Map.Entry<Integer, List<ScenarioResult>> de : byDelay.entrySet()) {
            int delayMs = de.getKey();
            List<ScenarioResult> delayResults = de.getValue();
            section++;
            String delayLabel = delayMs == 0 ? "无延迟 (0ms)" : "服务端延迟 " + (delayMs / 1000) + "s (" + delayMs + "ms)";

            panels.append("<div id=\"tab-delay-").append(delayMs).append("\" class=\"panel\">\n");
            panels.append("<h2>").append(section).append("、").append(esc(delayLabel)).append("</h2>\n");

            // 指标对比表: 行=实现, 列=各并发 RPS + 最高并发下的成功率/延迟/内存
            panels.append("<div class=\"table-wrap\">\n<table>\n<tr><th>实现</th>");
            for (int cc : concurrencyLevels) {
                panels.append("<th>并发").append(cc).append(" RPS</th>");
            }
            panels.append("<th>成功率</th><th>mean(ms)</th><th>p50(ms)</th><th>p95(ms)</th><th>p99(ms)</th><th>峰值内存(MB)</th></tr>\n");
            for (String s : serverNames) {
                panels.append("<tr><td class=\"name\">").append(esc(s)).append("</td>");
                for (int cc : concurrencyLevels) {
                    ScenarioResult r = delayResults.stream()
                            .filter(x -> x.serverType.equals(s) && x.concurrency == cc)
                            .findFirst().orElse(null);
                    panels.append("<td>").append(r != null ? fmt(r.rps) : "-").append("</td>");
                }
                ScenarioResult base = delayResults.stream()
                        .filter(x -> x.serverType.equals(s) && x.concurrency == lastCc)
                        .findFirst().orElse(null);
                if (base == null) {
                    base = delayResults.stream()
                            .filter(x -> x.serverType.equals(s))
                            .findFirst().orElse(null);
                }
                panels.append("<td class=\"").append(base != null && base.successRate >= 100 ? "ok" : "err").append("\">")
                        .append(base != null ? String.format(Locale.ROOT, "%.2f%%", base.successRate) : "-").append("</td>")
                        .append("<td>").append(base != null ? String.format(Locale.ROOT, "%.1f", base.mean) : "-").append("</td>")
                        .append("<td>").append(base != null ? String.format(Locale.ROOT, "%.1f", base.p50) : "-").append("</td>")
                        .append("<td>").append(base != null ? String.format(Locale.ROOT, "%.1f", base.p95) : "-").append("</td>")
                        .append("<td>").append(base != null ? String.format(Locale.ROOT, "%.1f", base.p99) : "-").append("</td>")
                        .append("<td>").append(base != null ? base.peakMemMb : "-").append("</td></tr>\n");
            }
            panels.append("</table>\n</div>\n");

            // 图表一行 2 列:内存区域图 + p99/p95 折线图
            panels.append("<div class=\"charts-grid\">\n");
            panels.append("<div class=\"chart-wrap\"><h3>内存占用对比 (MB, 占用=当前-启动基线)</h3>\n");
            panels.append("<div id=\"chartMem").append(delayMs).append("\" class=\"chart\"></div>\n");
            scripts.append("<script>\n");
            scripts.append("var chm").append(delayMs).append("=echarts.init(document.getElementById('chartMem")
                    .append(delayMs).append("'));\n");
            StringBuilder svJs = new StringBuilder("[");
            for (String s : serverNames) {
                svJs.append("'").append(escJs(s)).append("',");
            }
            trimTrailingComma(svJs);
            svJs.append(']');
            StringBuilder memPeakJs = new StringBuilder("[");
            StringBuilder memEndJs = new StringBuilder("[");
            for (String s : serverNames) {
                ScenarioResult r = delayResults.stream()
                        .filter(x -> x.serverType.equals(s) && x.concurrency == lastCc)
                        .findFirst().orElse(null);
                memPeakJs.append(r != null ? r.peakMemMb : 0).append(',');
                memEndJs.append(r != null ? r.endMemMb : 0).append(',');
            }
            trimTrailingComma(memPeakJs);
            trimTrailingComma(memEndJs);
            memPeakJs.append(']');
            memEndJs.append(']');
            scripts.append("chm").append(delayMs).append(".setOption({title:{text:'")
                    .append(esc(delayLabel)).append(" 内存占用对比 (MB)'},tooltip:{trigger:'axis'},legend:{textStyle:{color:'#94a3b8'}},xAxis:{type:'category',data:")
                    .append(svJs).append(",axisLabel:{color:'#94a3b8'},axisLine:{lineStyle:{color:'#475569'}}},yAxis:{type:'value',name:'MB',nameTextStyle:{color:'#94a3b8'},axisLabel:{color:'#94a3b8'},splitLine:{lineStyle:{color:'#293548'}}},series:[{name:'峰值占用',type:'line',smooth:true,areaStyle:{opacity:.35},data:")
                    .append(memPeakJs).append("},{name:'结束占用',type:'line',smooth:true,areaStyle:{opacity:.2},data:")
                    .append(memEndJs).append("}]});\n");
            scripts.append("window.addEventListener('resize',function(){chm").append(delayMs).append(".resize();});\n");
            scripts.append("</script>\n");

            // 该延迟下 p99/p95 延迟折线图 (x=并发等级, 系列=各实现)
            panels.append("<div class=\"chart-wrap\"><h3>延迟分位数 p99 / p95 (ms, 越低越好)</h3>\n");
            panels.append("<div id=\"chartLat").append(delayMs).append("\" class=\"chart\"></div>\n");
            scripts.append("<script>\n");
            scripts.append("var chl").append(delayMs).append("=echarts.init(document.getElementById('chartLat")
                    .append(delayMs).append("'));\n");
            scripts.append("chl").append(delayMs).append(".setOption({title:{text:'")
                    .append(esc(delayLabel)).append(" 延迟 p99/p95'},tooltip:{trigger:'axis'},legend:{textStyle:{color:'#94a3b8'}},xAxis:{type:'category',data:")
                    .append(jsIntArray(concurrencyLevels)).append(",axisLabel:{color:'#94a3b8'},axisLine:{lineStyle:{color:'#475569'}}},yAxis:{type:'value',name:'ms',nameTextStyle:{color:'#94a3b8'},axisLabel:{color:'#94a3b8'},splitLine:{lineStyle:{color:'#293548'}}},series:[");
            for (String s : serverNames) {
                StringBuilder p99Js = new StringBuilder("[");
                StringBuilder p95Js = new StringBuilder("[");
                for (int cc : concurrencyLevels) {
                    ScenarioResult r = delayResults.stream()
                            .filter(x -> x.serverType.equals(s) && x.concurrency == cc)
                            .findFirst().orElse(null);
                    p99Js.append(r != null ? String.format(Locale.ROOT, "%.1f", r.p99) : "0").append(',');
                    p95Js.append(r != null ? String.format(Locale.ROOT, "%.1f", r.p95) : "0").append(',');
                }
                trimTrailingComma(p99Js);
                trimTrailingComma(p95Js);
                p99Js.append(']');
                p95Js.append(']');
                scripts.append("{name:'").append(escJs(s)).append(" p99',type:'line',smooth:true,data:").append(p99Js).append("},");
                scripts.append("{name:'").append(escJs(s)).append(" p95',type:'line',smooth:true,lineStyle:{type:'dashed'},data:").append(p95Js).append("},");
            }
            scripts.append("]});\n");
            scripts.append("window.addEventListener('resize',function(){chl").append(delayMs).append(".resize();});\n");
            scripts.append("</script>\n");
            // 闭合 chart-wrap(p99/p95)
            panels.append("</div>\n");
            // 闭合 charts-grid
            panels.append("</div>\n");
            // 闭合 tab-delay-{delayMs} 面板
            panels.append("</div>\n");
        }

        // 汇总: 各实现 × 各延迟场景的 RPS 横向对比表
        panels.append("<div id=\"tab-summary\" class=\"panel\">\n");
        panels.append("<h2>").append(section + 1).append("、延迟场景对比汇总 (最高并发 ").append(lastCc).append(")</h2>\n");
        panels.append("<div class=\"table-wrap\">\n<table>\n<tr><th>实现</th>");
        for (int d : delays) {
            panels.append("<th>").append(d == 0 ? "无延迟" : d + "ms").append(" RPS</th>");
        }
        for (int d : delays) {
            panels.append("<th>").append(d == 0 ? "无延迟" : d + "ms").append(" p99</th>");
        }
        panels.append("</tr>\n");
        for (String s : serverNames) {
            panels.append("<tr><td class=\"name\">").append(esc(s)).append("</td>");
            for (int d : delays) {
                ScenarioResult r = results.stream()
                        .filter(x -> x.serverType.equals(s) && x.delayMs == d && x.concurrency == lastCc)
                        .findFirst().orElse(null);
                panels.append("<td>").append(r != null ? fmt(r.rps) : "-").append("</td>");
            }
            for (int d : delays) {
                ScenarioResult r = results.stream()
                        .filter(x -> x.serverType.equals(s) && x.delayMs == d && x.concurrency == lastCc)
                        .findFirst().orElse(null);
                panels.append("<td>").append(r != null ? String.format(Locale.ROOT, "%.1f", r.p99) : "-").append("</td>");
            }
            panels.append("</tr>\n");
        }
        panels.append("</table>\n</div>\n");

        // 各实现 × 各延迟 RPS 折线图
        panels.append("<h3>吞吐量随服务端延迟变化</h3>\n");
        panels.append("<div id=\"chartDelaySummary\" class=\"chart\"></div>\n");
        scripts.append("<script>\n");
        scripts.append("var chs=echarts.init(document.getElementById('chartDelaySummary'));\n");
        scripts.append("chs.setOption({title:{text:'各实现吞吐量随服务端延迟变化 (并发=").append(lastCc)
                .append(")'},tooltip:{trigger:'axis'},legend:{},xAxis:{type:'category',data:")
                .append(jsIntArray(delays)).append("},yAxis:{type:'value',name:'RPS'},series:[");
        for (String s : serverNames) {
            StringBuilder lineJs = new StringBuilder("[");
            for (int d : delays) {
                ScenarioResult r = results.stream()
                        .filter(x -> x.serverType.equals(s) && x.delayMs == d && x.concurrency == lastCc)
                        .findFirst().orElse(null);
                lineJs.append(r != null ? String.format(Locale.ROOT, "%.0f", r.rps) : "0").append(',');
            }
            trimTrailingComma(lineJs);
            lineJs.append(']');
            scripts.append("{name:'").append(escJs(s)).append("',type:'line',data:").append(lineJs).append("},");
        }
        scripts.append("]});\n");
        scripts.append("window.addEventListener('resize',function(){chs.resize();});\n");
        scripts.append("</script>\n");
        // 闭合 tab-summary 面板
        panels.append("</div>\n");

        // 7) 全量明细表
        panels.append("<div id=\"tab-detail\" class=\"panel\">\n");
        panels.append("<h2>全量明细</h2>\n");
        panels.append("<div class=\"table-wrap\">\n<table>\n<tr><th>子类</th><th>延迟(ms)</th><th>并发</th><th>总请求</th><th>失败</th>")
                .append("<th>成功率</th><th>RPS</th><th>mean(ms)</th><th>p50</th><th>p95</th><th>p99</th><th>p99.9</th><th>max</th><th>耗时(ms)</th></tr>\n");
        for (ScenarioResult r : results) {
            panels.append("<tr><td class=\"name\">").append(esc(r.serverType)).append("</td>")
                    .append("<td>").append(r.delayMs == 0 ? "无" : r.delayMs).append("</td>")
                    .append("<td>").append(r.concurrency).append("</td>")
                    .append("<td>").append(r.total).append("</td>")
                    .append("<td>").append(r.errors).append("</td>")
                    .append("<td class=\"").append(r.successRate >= 100 ? "ok" : "err").append("\">")
                    .append(String.format(Locale.ROOT, "%.2f%%", r.successRate)).append("</td>")
                    .append("<td>").append(fmt(r.rps)).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.1f", r.mean)).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.1f", r.p50)).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.1f", r.p95)).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.1f", r.p99)).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.1f", r.p999)).append("</td>")
                    .append("<td>").append(String.format(Locale.ROOT, "%.1f", r.max)).append("</td>")
                    .append("<td>").append(r.elapsedMs).append("</td></tr>\n");
        }
        panels.append("</table>\n</div>\n");
        // 闭合 tab-detail 面板
        panels.append("</div>\n");

        // ===== 占位符替换模板 =====
        // 图表脚本包装为 window.__runCharts:ECharts 由模板多 CDN 兜底加载,
        // 就绪后回调 __runCharts() 才执行 echarts.init,避免 CDN 未加载时报 ReferenceError
        String chartScripts = "window.__runCharts = function() {\n"
                + stripScriptTags(scripts.toString())
                + "};\n";
        String html = template
                .replace("{{GENERATED_AT}}", LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .replace("{{JAVA_VERSION}}", System.getProperty("java.version"))
                .replace("{{OS_INFO}}", System.getProperty("os.name") + " " + System.getProperty("os.arch"))
                .replace("{{CPU_CORES}}", String.valueOf(Runtime.getRuntime().availableProcessors()))
                .replace("{{CONCURRENCY}}", Arrays.toString(concurrencyLevels))
                .replace("{{DELAYS}}", Arrays.toString(delays))
                .replace("{{PAYLOAD}}", String.valueOf(payloadSize))
                .replace("{{KPI_CARDS}}", kpiCards.toString())
                .replace("{{TAB_BUTTONS}}", tabButtons.toString())
                .replace("{{PANELS}}", panels.toString())
                .replace("{{CHART_SCRIPTS}}", chartScripts);

        try {
            Path path = Path.of(reportPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, html, StandardCharsets.UTF_8);
            log.info("[bench] 压测报告已写入: {}", reportPath);
        } catch (Exception e) {
            log.warn("[bench] 报告写入失败: {}", e.getMessage());
        }
    }

    /** 从 classpath 加载报告模板(resources/benchmark-report-template.html)。 */
    private static String loadTemplate() {
        try (var in = HttpServerBenchmarkExample.class.getResourceAsStream("/benchmark-report-template.html")) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[bench] 模板读取失败: {}", e.getMessage());
            return null;
        }
    }

    /** 移除脚本块包裹标签(<script>…</script>),仅保留 JS 内容,供合并进 __runCharts。 */
    private static String stripScriptTags(String js) {
        return js.replace("<script>\n", "").replace("</script>\n", "")
                .replace("<script>", "").replace("</script>", "");
    }

    /** 生成单个 KPI 指标卡。 */
    private static String kpiCard(String label, String value, String unit, String hint) {
        return "<div class=\"kpi\"><div class=\"label\">" + esc(label) + "</div>"
                + "<div class=\"value\">" + esc(value)
                + (unit != null && !unit.isEmpty() ? "<span class=\"unit\">" + esc(unit) + "</span>" : "")
                + "</div><div class=\"hint\">" + esc(hint) + "</div></div>\n";
    }

    // ==================== 工具 ====================

    /** UsedMemMb */
    private static long usedMemMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
    }

    /** 合并 */
    private static long[] merge(List<long[]> latencies) {
        int total = 0;
        for (long[] arr : latencies) {
            total += arr.length;
        }
        long[] all = new long[total];
        int off = 0;
        for (long[] arr : latencies) {
            System.arraycopy(arr, 0, all, off, arr.length);
            off += arr.length;
        }
        return all;
    }

    /** Percentile */
    private static double percentile(long[] sortedMs, double p) {
        if (sortedMs.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sortedMs.length) - 1;
        idx = Math.max(0, Math.min(idx, sortedMs.length - 1));
        return sortedMs[idx];
    }

    /** Fmt */
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    /** Esc */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** EscJs */
    private static String escJs(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** JsIntArray */
    private static String jsIntArray(int[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int v : arr) {
            sb.append(v).append(',');
        }
        trimTrailingComma(sb);
        return sb.append(']').toString();
    }

    /** 去空格TrailingComma */
    private static void trimTrailingComma(StringBuilder sb) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
    }

    /** 解析Ints */
    private static int[] parseInts(String csv, int[] defaults) {
        if (csv == null || csv.isBlank()) {
            return defaults;
        }
        String[] parts = csv.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    // ==================== 入口 ====================

    /** Main */
    public static void main(String[] args) {
        Map<String, String> kv = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int i = arg.indexOf('=');
                if (i > 0) {
                    kv.put(arg.substring(2, i), arg.substring(i + 1));
                }
            }
        }

        String[] servers = (kv.getOrDefault("servers", "jdk,nio,vertx-http,http"))
                .split(",");
        int[] concurrency = parseInts(kv.get("concurrency"), new int[]{100, 1000, 2000, 5000, 10000});
        int[] delays = parseInts(kv.get("delays"), new int[]{0, 1000, 3000});
        int payload = Integer.parseInt(kv.getOrDefault("payload", "128"));
        int requestsPerVu = Integer.parseInt(kv.getOrDefault("requests", "50"));
        int samples = Integer.parseInt(kv.getOrDefault("samples", "3"));
        String reportPath = kv.getOrDefault("report", "target/http-server-benchmark.html");

        log.info("===== HttpServer 全子类压测: servers={} concurrency={} delays={}ms payload={} samples={} =====",
                Arrays.toString(servers), Arrays.toString(concurrency), Arrays.toString(delays), payload, samples);

        List<ScenarioResult> results = new ArrayList<>();
        for (String type : servers) {
            type = type.trim();
            for (int d : delays) {
                for (int cc : concurrency) {
                    // 多次采样取中位数,消除本机负载/GC/线程调度抖动
                    List<ScenarioResult> sampled = new ArrayList<>();
                    for (int i = 0; i < samples; i++) {
                        ScenarioResult r = runScenario(type, d, cc, requestsPerVu, payload);
                        if (r != null) {
                            sampled.add(r);
                        }
                    }
                    if (sampled.isEmpty()) {
                        continue;
                    }
                    sampled.sort(java.util.Comparator.comparingDouble(r -> r.rps));
                    ScenarioResult median = sampled.get(sampled.size() / 2);
                    results.add(median);
                    log.info("[bench] {} delay={}ms 并发={} RPS={} p50={}ms p99={}ms 成功率={}% (samples={})",
                            type, d, cc, Math.round(median.rps), Math.round(median.p50), Math.round(median.p99),
                            String.format(Locale.ROOT, "%.2f", median.successRate), samples);
                }
            }
        }

        writeHtmlReport(reportPath, results, concurrency, delays, payload);
        // 可选模式:额外导出 benchmark-data.json(与报告同目录),供 HTML 动态渲染或二次分析
        if (Boolean.parseBoolean(kv.getOrDefault("json", "false"))) {
            writeJsonData(reportPath, results, concurrency, delays, payload);
        }
        log.info("===== 压测完成, 报告: {} =====", reportPath);
    }

    /**
     * 导出压测数据为 JSON(与报告同目录 benchmark-data.json)。
     * 结构: { generatedAt, concurrency, delays, payloadSize, scenarios: [...] }
     * scenarios 每项为单个场景快照,便于 HTML 读取渲染或后续分析。
     */
    private static void writeJsonData(String reportPath, List<ScenarioResult> results,
                                      int[] concurrencyLevels, int[] delays, int payloadSize) {
        try {
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("generatedAt", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            root.put("concurrency", Arrays.stream(concurrencyLevels).boxed().toList());
            root.put("delays", Arrays.stream(delays).boxed().toList());
            root.put("payloadSize", payloadSize);
            List<Map<String, Object>> scenarios = new ArrayList<>();
            for (ScenarioResult r : results) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("serverType", r.serverType);
                m.put("delayMs", r.delayMs);
                m.put("concurrency", r.concurrency);
                m.put("total", r.total);
                m.put("errors", r.errors);
                m.put("elapsedMs", r.elapsedMs);
                m.put("rps", r.rps);
                m.put("p50", r.p50);
                m.put("p95", r.p95);
                m.put("p99", r.p99);
                m.put("p999", r.p999);
                m.put("max", r.max);
                m.put("mean", r.mean);
                m.put("successRate", r.successRate);
                m.put("startMemMb", r.startMemMb);
                m.put("peakMemMb", r.peakMemMb);
                m.put("endMemMb", r.endMemMb);
                scenarios.add(m);
            }
            root.put("scenarios", scenarios);
            Path path = Path.of(reportPath);
            Path jsonPath = (path.getParent() != null ? path.getParent() : Path.of("."))
                    .resolve("benchmark-data.json");
            // 手写 JSON 序列化(避免依赖 Jackson,example 模块 classpath 不含该库)
            Files.writeString(jsonPath, toJson(root), StandardCharsets.UTF_8);
            log.info("[bench] 压测数据已导出: {}", jsonPath.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[bench] JSON 导出失败: {}", e.getMessage());
        }
    }

    /** 手写 JSON 序列化(Map/List/String/Number/Boolean),支持缩进。 */
    private static String toJson(Object value) {
        return toJson(value, 0);
    }

    /** ToJson */
    private static String toJson(Object value, int indent) {
        String pad = "  ".repeat(indent);
        String childPad = "  ".repeat(indent + 1);
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(childPad).append('"').append(escapeJson(String.valueOf(e.getKey())))
                        .append("\": ").append(toJson(e.getValue(), indent + 1));
                if (++i < map.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append(pad).append('}');
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(childPad).append(toJson(list.get(i), indent + 1));
                if (i < list.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append(pad).append(']');
            return sb.toString();
        }
        if (value instanceof String s) {
            return '"' + escapeJson(s) + '"';
        }
        if (value instanceof Double d || value instanceof Float f) {
            return String.format(Locale.ROOT, "%.2f", ((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    /** EscapeJson */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

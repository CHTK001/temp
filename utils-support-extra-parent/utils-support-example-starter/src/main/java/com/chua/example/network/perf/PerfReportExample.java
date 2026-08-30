package com.chua.example.network.perf;

import com.chua.common.support.utils.ThreadUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 统一的 Server 性能测试报告框架。
 *
 * <p>提供：</p>
 * <ul>
 *     <li>环境信息采集（JDK / OS / CPU / GC / 内存）</li>
 *     <li>SPI 信息（哪些实现被加载）</li>
 *     <li>压测结果统计（总请求 / 错误数 / RPS / p50 / p95 / p99 / max）</li>
 *     <li>统一格式报告输出</li>
 * </ul>
 *
 * <p>所有 Server 子类（TCP / HTTP / HTTP 反向代理 / MQTT）的 Example 都通过本类输出结构化报告。</p>
 * @author CH
 *
 * @since 4.0.0.42
 */
@Slf4j
public final class PerfReportExample {

    /** 创建 PerfReportExample 实例 */
    private PerfReportExample() {
    }

    /**
     * 独立入口：输出当前环境信息自演示。
     *
     * <p>参数格式 {@code --key=value}：</p>
     * <ul>
     *     <li>{@code --server=} 服务器名称（默认 perf-self-check）</li>
     *     <li>{@code --spi=} SPI 名称（默认 jdk）</li>
     *     <li>{@code --discovery=} 发现服务类型（默认 static）</li>
     * </ul>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String arg : args) {
            int idx = arg.indexOf('=');
            if (arg.startsWith("--") && idx > 2) {
                params.put(arg.substring(2, idx), arg.substring(idx + 1));
            }
        }
        printEnvironment(
                params.getOrDefault("server", "perf-self-check"),
                params.getOrDefault("spi", "jdk"),
                params.getOrDefault("discovery", "static"));
        System.exit(0);
    }

    /**
     * 打印环境信息（每个报告前必打）。
     */
    public static void printEnvironment(String serverName, String spiName, String discoveryType) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / 1024 / 1024;
        long totalMb = rt.totalMemory() / 1024 / 1024;
        long freeMb = rt.freeMemory() / 1024 / 1024;
        int availProcs = rt.availableProcessors();
        String os = System.getProperty("os.name") + " " + System.getProperty("os.arch") + " " + System.getProperty("os.version");

        log.info("  ┌──────────────────────────────────────────────────────────────");
        log.info("  │ 性能测试报告  : {} [{}]", serverName, spiName);
        log.info("  ├──────────────────────────────────────────────────────────────");
        log.info("  │ JDK 厂商     : {}", System.getProperty("java.vendor"));
        log.info("  │ JDK 版本     : {}", System.getProperty("java.version"));
        log.info("  │ JVM 名称     : {}", System.getProperty("java.vm.name"));
        log.info("  │ JVM 参数     : {}", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        log.info("  │ OS           : {}", os);
        log.info("  │ CPU 核数     : {}", availProcs);
        log.info("  │ 内存 (max/total/free) : {} / {} / {} MB", maxMb, totalMb, freeMb);
        log.info("  │ 发现服务     : {}", discoveryType);
        log.info("  │ 启动时间     : {}", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * 打印服务器配置信息（协议 / 监听 / 线程 / 连接 / 超时 / 压缩 / SSL 等）。
     *
     * @param setting 服务器配置
     */
    public static void printServerConfig(com.chua.common.support.network.server.ServerSetting setting) {
        log.info("  │ 服务器配置");
        log.info("  │   协议/类型   : {}", setting.getProtocol());
        log.info("  │   监听地址   : {}:{}", setting.getHost(), setting.getPort());
        log.info("  │   Boss线程   : {}", setting.getBossThreads());
        log.info("  │   Worker线程 : {}", setting.getWorkerThreads());
        log.info("  │   Backlog    : {}", setting.getBacklog());
        log.info("  │   最大连接数 : {}", setting.getMaxConnections());
        log.info("  │   最大并发数 : {}", setting.getMaxConcurrency());
        log.info("  │   请求体上限 : {} B", setting.getMaxRequestSize());
        log.info("  │   读超时     : {} ms", setting.getReadTimeout());
        log.info("  │   写超时     : {} ms", setting.getWriteTimeout());
        log.info("  │   Gzip       : {} (level {})", setting.isGzipEnabled(), setting.getGzipLevel());
        log.info("  │   SSL        : {}", setting.getSsl() != null && setting.getSsl().isEnabled());
        log.info("  │   Reactor    : {}", setting.isReactor());
    }

    /**
     * 资源监控器：压测期间后台采样进程 CPU 使用率与堆内存占用。
     *
     * <p>提供：起始内存 / 平均 CPU / 峰值内存 / 结束内存 / 统计时长。</p>
     */
    public static final class ResourceMonitor {

        /** 开始usedMB */
        private final long startUsedMb;
        /** 开始nanos */
        private final long startNanos;
        /** CPUPCT */
        private final List<Double> cpuPct = new ArrayList<>();
        /** running */
        private volatile boolean running = true;
        /** Sampler */
        private final Thread sampler;
        /** peakUsedMb */
        private volatile long peakUsedMb;

        /** 创建 ResourceMonitor 实例 */
        private ResourceMonitor() {
            this.startUsedMb = usedMb();
            this.peakUsedMb = startUsedMb;
            this.startNanos = System.nanoTime();
            this.sampler = new Thread(this::sample, "perf-resource-monitor");
            this.sampler.setDaemon(true);
            this.sampler.start();
        }

        /**
         * 启动资源监控。
         *
         * @return 监控器实例
         */
        public static ResourceMonitor start() {
            return new ResourceMonitor();
        }

        /**
         * 停止采样线程。
         */
        public void stop() {
            running = false;
            try {
                sampler.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 打印资源统计汇总。
         *
         * @param label 场景名称
         */
        public void printSummary(String label) {
            long used = usedMb();
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            double avgCpu = cpuPct.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            log.info("  │ 资源统计 [{}]", label);
            log.info("  │   起始内存   : {} MB (堆)", startUsedMb);
            log.info("  │   峰值内存   : {} MB (堆)", peakUsedMb);
            log.info("  │   结束内存   : {} MB (堆)", used);
            log.info("  │   平均CPU    : {} %", String.format(Locale.ROOT, "%.1f", avgCpu));
            log.info("  │   统计时长   : {} ms", elapsedMs);
        }

        /** Sample */
        private void sample() {
            while (running) {
                try {
                    sampleCpu();
                    sampleMemory();
                    ThreadUtils.sleep(200L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        /** SampleCpu */
        private void sampleCpu() {
            try {
                java.lang.management.OperatingSystemMXBean mx = ManagementFactory.getOperatingSystemMXBean();
                if (mx instanceof com.sun.management.OperatingSystemMXBean sun) {
                    double load = sun.getProcessCpuLoad();
                    if (load >= 0) {
                        cpuPct.add(load * 100.0);
                    }
                }
            } catch (Throwable ignored) {
                // 平台不支持时忽略 CPU 采样
            }
        }

        /** SampleMemory */
        private void sampleMemory() {
            long used = usedMb();
            if (used > peakUsedMb) {
                peakUsedMb = used;
            }
        }

        /** UsedMb */
        private static long usedMb() {
            Runtime rt = Runtime.getRuntime();
            return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        }
    }

    /**
     * 打印测试参数与性能结果。
     */
    public static void printResult(String testName, int concurrency, int connections, int requestsPerConn,
                                   int payloadSize, long total, long errors, long elapsedMs,
                                   long[] sortedLatencyNs, long startupMs) {
        double rps = (double) total * 1_000_000_000.0 / (double) (elapsedMs * 1_000_000L);
        double p50 = percentileUs(sortedLatencyNs, 0.50);
        double p95 = percentileUs(sortedLatencyNs, 0.95);
        double p99 = percentileUs(sortedLatencyNs, 0.99);
        double p999 = percentileUs(sortedLatencyNs, 0.999);
        double max = sortedLatencyNs.length > 0 ? sortedLatencyNs[sortedLatencyNs.length - 1] / 1000.0 : 0.0;
        double min = sortedLatencyNs.length > 0 ? sortedLatencyNs[0] / 1000.0 : 0.0;
        double mean = sortedLatencyNs.length > 0
                ? Arrays.stream(sortedLatencyNs).average().orElse(0) / 1000.0
                : 0.0;
        double stddev = sortedLatencyNs.length > 0 ? stddevUs(sortedLatencyNs, mean) : 0.0;

        log.info("  ├──────────────────────────────────────────────────────────────");
        log.info("  │ 测试参数");
        log.info("  │   客户端并发  : {}", concurrency);
        log.info("  │   连接数      : {}", connections);
        log.info("  │   每连接请求  : {}", requestsPerConn);
        log.info("  │   Payload    : {} B", payloadSize);
        log.info("  │   预热时间    : 50 ms (JIT)");
        log.info("  ├──────────────────────────────────────────────────────────────");
        log.info("  │ 吞吐量");
        log.info("  │   总请求数    : {}", total);
        log.info("  │   错误请求数  : {}", errors);
        log.info("  │   错误率      : {} %", total > 0 ? String.format(Locale.ROOT, "%.4f", errors * 100.0 / total) : "0");
        log.info("  │   总耗时      : {} ms (含 {}ms 启动/JIT 预热)", elapsedMs, startupMs);
        log.info("  │   吞吐量 RPS  : {} req/s", String.format(Locale.ROOT, "%.0f", rps));
        log.info("  ├──────────────────────────────────────────────────────────────");
        log.info("  │ 延迟 (单次请求/响应往返)");
        log.info("  │   min         : {} µs", String.format(Locale.ROOT, "%.2f", min));
        log.info("  │   mean        : {} µs", String.format(Locale.ROOT, "%.2f", mean));
        log.info("  │   stddev      : {} µs", String.format(Locale.ROOT, "%.2f", stddev));
        log.info("  │   p50         : {} µs", String.format(Locale.ROOT, "%.2f", p50));
        log.info("  │   p95         : {} µs", String.format(Locale.ROOT, "%.2f", p95));
        log.info("  │   p99         : {} µs", String.format(Locale.ROOT, "%.2f", p99));
        log.info("  │   p99.9       : {} µs", String.format(Locale.ROOT, "%.2f", p999));
        log.info("  │   max         : {} µs", String.format(Locale.ROOT, "%.2f", max));
        log.info("  ├──────────────────────────────────────────────────────────────");
        log.info("  │ 测试名称      : {}", testName);
        log.info("  └──────────────────────────────────────────────────────────────");
    }

    /**
     * 打印压测扫档对比表（多个并发等级横向对比）。
     *
     * @param sweepName    扫档测试名称
     * @param payloadSize  Payload 字节数
     * @param rows         每行: [concurrency, connections, requestsPerConn, total, errors, elapsedMs, sortedLatencyNs]
     */
    public static void printSweepResult(String sweepName, int payloadSize, List<SweepRow> rows) {
        log.info("  ┌──────────────────────────────────────────────────────────────");
        log.info("  │ 压测扫档对比  : {}", sweepName);
        log.info("  │ Payload      : {} B (单次请求/响应)", payloadSize);
        log.info("  ├─────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬─────────┐");
        log.info("  │ 并发 │  连接数  │  请求数  │ 错误率  │  RPS    │ p50 µs  │ p95 µs  │ p99 µs  │ p99.9   │  max µs │  总耗时  │");
        log.info("  ├─────┼─────────┼─────────┼─────────┼─────────┼─────────┼─────────┼─────────┼─────────┼─────────┼─────────┤");
        for (SweepRow r : rows) {
            double rps = (double) r.total * 1_000_000_000.0 / (double) (r.elapsedMs * 1_000_000L);
            double p50 = percentileUs(r.sortedLatencyNs, 0.50);
            double p95 = percentileUs(r.sortedLatencyNs, 0.95);
            double p99 = percentileUs(r.sortedLatencyNs, 0.99);
            double p999 = percentileUs(r.sortedLatencyNs, 0.999);
            double max = r.sortedLatencyNs.length > 0 ? r.sortedLatencyNs[r.sortedLatencyNs.length - 1] / 1000.0 : 0.0;
            String errPct = r.total > 0 ? String.format(Locale.ROOT, "%.2f%%", r.errors * 100.0 / r.total) : "0";
            log.info("  │ {} │  {}    │  {}   │ {}  │ {}  │ {}  │ {}  │ {}  │ {}  │ {}  │ {} ms │",
                    padLeft(String.valueOf(r.concurrency), 3),
                    padLeft(String.valueOf(r.connections), 5),
                    padLeft(String.valueOf(r.requestsPerConn), 5),
                    padLeft(errPct, 5),
                    padLeft(String.format(Locale.ROOT, "%.0f", rps), 5),
                    padLeft(String.format(Locale.ROOT, "%.1f", p50), 5),
                    padLeft(String.format(Locale.ROOT, "%.1f", p95), 5),
                    padLeft(String.format(Locale.ROOT, "%.1f", p99), 5),
                    padLeft(String.format(Locale.ROOT, "%.1f", p999), 5),
                    padLeft(String.format(Locale.ROOT, "%.1f", max), 5),
                    padLeft(String.valueOf(r.elapsedMs), 5));
        }
        log.info("  └─────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┴─────────┘");
    }

    public static class SweepRow {
        /** Concurrency */
        public final int concurrency;
        /** Connections */
        public final int connections;
        /** RequestsPERconn */
        public final int requestsPerConn;
        /** 总数 */
        public final long total;
        /** Errors */
        public final long errors;
        /** ElapsedMS */
        public final long elapsedMs;
        /** SortedlatencyNS */
        public final long[] sortedLatencyNs;

        /**
         * 创建 SweepRow 实例
         * @param concurrency concurrency
         * @param int int
         * @param int int
         * @param long long
         * @param long long
         * @param long long
         * @param long long
         * @param sortedLatencyNs sortedLatencyNs
         */
        public SweepRow(int concurrency, int connections, int requestsPerConn, long total, long errors, long elapsedMs, long[] sortedLatencyNs) {
            this.concurrency = concurrency;
            this.connections = connections;
            this.requestsPerConn = requestsPerConn;
            this.total = total;
            this.errors = errors;
            this.elapsedMs = elapsedMs;
            this.sortedLatencyNs = sortedLatencyNs;
        }
    }

    /**
     * 生成 Markdown 格式的压测报告文档，包含每个并发场景的 p50/p95/p99 与成功率。
     *
     * @param reportPath 报告文件路径
     * @param title      报告标题
     * @param env        环境描述（JDK/OS/CPU 等，可为 null）
     * @param rows       每个并发场景一行
     * @return 报告全文
     */
    public static String writeBenchmarkReport(String reportPath, String title, String env, List<SweepRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 生成时间: ").append(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        if (env != null && !env.isEmpty()) {
            sb.append(env).append("\n\n");
        }
        sb.append("## 场景说明\n\n");
        sb.append("| 参数 | 值 |\n|---|---|\n");
        sb.append("| 场景 | ").append(title).append(" |\n");
        sb.append("| 并发等级 | ");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            sb.append(rows.get(i).concurrency);
        }
        sb.append(" |\n\n");
        sb.append("## 压测结果\n\n");
        sb.append("| 并发 | 连接数 | 每连接请求 | 总请求 | 成功 | 失败 | 成功率 | RPS | p50(µs) | p95(µs) | p99(µs) | 最大(µs) | 总耗时(ms) |\n");
        sb.append("|:----:|:------:|:----------:|:------:|:----:|:----:|:------:|:---:|:-------:|:-------:|:-------:|:--------:|:----------:|\n");
        for (SweepRow r : rows) {
            double rps = (double) r.total * 1_000_000_000.0 / (double) (r.elapsedMs * 1_000_000L);
            double p50 = percentileUs(r.sortedLatencyNs, 0.50);
            double p95 = percentileUs(r.sortedLatencyNs, 0.95);
            double p99 = percentileUs(r.sortedLatencyNs, 0.99);
            double max = r.sortedLatencyNs.length > 0 ? r.sortedLatencyNs[r.sortedLatencyNs.length - 1] / 1000.0 : 0.0;
            long success = r.total - r.errors;
            String rate = r.total > 0 ? String.format(Locale.ROOT, "%.2f%%", success * 100.0 / r.total) : "-";
            sb.append(String.format(Locale.ROOT,
                    "| %d | %d | %d | %d | %d | %d | %s | %.0f | %.1f | %.1f | %.1f | %.1f | %d |%n",
                    r.concurrency, r.connections, r.requestsPerConn, r.total, success, r.errors, rate,
                    rps, p50, p95, p99, max, r.elapsedMs));
        }
        sb.append("\n");
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(reportPath), sb.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
            log.info("压测报告已写入: {}", reportPath);
        } catch (java.io.IOException e) {
            log.warn("压测报告写入失败: {}", e.getMessage(), e);
        }
        return sb.toString();
    }

    /**
     * 生成 HTML 格式的压测报告，包含环境 / 服务器配置 / 并发场景结果 / 资源统计。
     *
     * @param reportPath 报告文件路径（.html）
     * @param title      报告标题
     * @param serverConfig 服务器配置描述（可为 null）
     * @param rows       每个并发场景一行
     * @return 报告全文
     */
    public static String writeHtmlReport(String reportPath, String title, String serverConfig, List<SweepRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("<style>\n")
                .append("body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;background:#f7f8fa;color:#333;}\n")
                .append("h1{color:#1a73e8;}h2{color:#333;border-bottom:2px solid #1a73e8;padding-bottom:4px;}\n")
                .append("table{border-collapse:collapse;width:100%;background:#fff;margin:8px 0 20px;}\n")
                .append("th,td{border:1px solid #ddd;padding:8px 10px;text-align:right;}\n")
                .append("th{background:#1a73e8;color:#fff;text-align:center;}\n")
                .append("tr:nth-child(even){background:#f1f5fb;}\n")
                .append(".env{background:#eef3ff;padding:10px 14px;border-left:4px solid #1a73e8;margin:8px 0 16px;}\n")
                .append(".ok{color:#188038;font-weight:bold;}.err{color:#d93025;font-weight:bold;}\n")
                .append("</style>\n</head>\n<body>\n");
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n");
        sb.append("<p>生成时间: ").append(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        if (serverConfig != null && !serverConfig.isEmpty()) {
            sb.append("<div class=\"env\">").append(escapeHtml(serverConfig)).append("</div>\n");
        }
        sb.append("<h2>压测结果</h2>\n<table>\n<tr>")
                .append("<th>并发</th><th>连接数</th><th>每连接请求</th><th>总请求</th><th>成功</th><th>失败</th>")
                .append("<th>成功率</th><th>RPS</th><th>p50(µs)</th><th>p95(µs)</th><th>p99(µs)</th><th>最大(µs)</th><th>总耗时(ms)</th></tr>\n");
        for (SweepRow r : rows) {
            double rps = (double) r.total * 1_000_000_000.0 / (double) (r.elapsedMs * 1_000_000L);
            double p50 = percentileUs(r.sortedLatencyNs, 0.50);
            double p95 = percentileUs(r.sortedLatencyNs, 0.95);
            double p99 = percentileUs(r.sortedLatencyNs, 0.99);
            double max = r.sortedLatencyNs.length > 0 ? r.sortedLatencyNs[r.sortedLatencyNs.length - 1] / 1000.0 : 0.0;
            long success = r.total - r.errors;
            String rate = r.total > 0 ? String.format(Locale.ROOT, "%.2f%%", success * 100.0 / r.total) : "-";
            String rateClass = r.errors == 0 ? "ok" : "err";
            sb.append(String.format(Locale.ROOT,
                    "<tr><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td>"
                            + "<td class=\"%s\">%s</td><td>%.0f</td><td>%.1f</td><td>%.1f</td><td>%.1f</td><td>%.1f</td><td>%d</td></tr>%n",
                    r.concurrency, r.connections, r.requestsPerConn, r.total, success, r.errors,
                    rateClass, rate, rps, p50, p95, p99, max, r.elapsedMs));
        }
        sb.append("</table>\n</body>\n</html>\n");
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(reportPath), sb.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
            log.info("HTML 压测报告已写入: {}", reportPath);
        } catch (java.io.IOException e) {
            log.warn("HTML 压测报告写入失败: {}", e.getMessage(), e);
        }
        return sb.toString();
    }

    /** EscapeHtml */
    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** PadLeft */
    private static String padLeft(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width - s.length(); i++) {
            sb.append(' ');
        }
        sb.append(s);
        return sb.toString();
    }

    /**
     * 合并多个连接的延迟数组。
     */
    public static long[] mergeLatencies(List<long[]> latencies) {
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

    /**
     * 计算分位数（µs）。
     */
    public static double percentileUs(long[] sortedNs, double p) {
        if (sortedNs.length == 0) {
            return 0.0;
        }
        int idx = (int) Math.ceil(p * sortedNs.length) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sortedNs.length) {
            idx = sortedNs.length - 1;
        }
        return sortedNs[idx] / 1000.0;
    }

    /**
     * 计算标准差（µs）。
     */
    private static double stddevUs(long[] sortedNs, double meanUs) {
        if (sortedNs.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (long ns : sortedNs) {
            double diff = ns / 1000.0 - meanUs;
            sum += diff * diff;
        }
        return Math.sqrt(sum / sortedNs.length);
    }
}

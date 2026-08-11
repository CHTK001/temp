package com.chua.example.network.perf;

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
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class PerfReport {

    private PerfReport() {
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
        public final int concurrency;
        public final int connections;
        public final int requestsPerConn;
        public final long total;
        public final long errors;
        public final long elapsedMs;
        public final long[] sortedLatencyNs;

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

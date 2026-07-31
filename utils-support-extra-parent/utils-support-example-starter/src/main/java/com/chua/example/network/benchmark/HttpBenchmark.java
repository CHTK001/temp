package com.chua.example.network.benchmark;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 纯 Java HTTP 压测客户端 — 替代 wrk，在 Docker 容器内部运行。
 *
 * <p>支持：
 * <ul>
 *   <li>多线程并发请求</li>
 *   <li>连接数控制</li>
 *   <li>延迟统计（P50, P99, Max）</li>
 *   <li>吞吐量统计</li>
 * </ul>
 *
 * <p>用法：
 * <pre>{@code
 *   java HttpBenchmark url connections threads duration_seconds
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.44
 */
public class HttpBenchmark {

    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final LongAdder totalRequests = new LongAdder();
    private static final List<Long> latencies = new ArrayList<>();
    private static final Object latencyLock = new Object();

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java HttpBenchmark <url> <connections> <threads> <duration_seconds>");
            System.exit(1);
        }

        String url = args[0];
        int connections = Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);
        int durationSeconds = Integer.parseInt(args[3]);

        System.out.printf("Benchmarking: url=%s, connections=%d, threads=%d, duration=%ds%n",
                url, connections, threads, durationSeconds);

        runBenchmark(url, connections, threads, durationSeconds);
    }

    private static void runBenchmark(String url, int connections, int threads, int durationSeconds) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicLong stopFlag = new AtomicLong(0);

        long startTime = System.nanoTime();
        long endTime = startTime + (durationSeconds * 1_000_000_000L);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < connections; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                while (System.nanoTime() < endTime && stopFlag.get() == 0) {
                    long reqStart = System.nanoTime();
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        long latency = System.nanoTime() - reqStart;

                        if (response.statusCode() == 200) {
                            totalRequests.increment();
                            synchronized (latencyLock) {
                                latencies.add(latency);
                            }
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            }, executor);
            futures.add(future);
        }

        startLatch.countDown();

        try {
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        stopFlag.set(1);

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long actualDuration = (System.nanoTime() - startTime) / 1_000_000_000L;
        printResults(actualDuration, connections, threads);
    }

    private static void printResults(long duration, int connections, int threads) {
        long total = totalRequests.sum();
        double rps = (double) total / duration;
        int errors = errorCount.get();

        synchronized (latencyLock) {
            int n = latencies.size();
            if (n == 0) {
                System.out.println("No successful requests");
                return;
            }

            latencies.sort(Long::compare);

            long p50 = latencies.get((int) (n * 0.50));
            long p90 = latencies.get((int) (n * 0.90));
            long p99 = latencies.get((int) (n * 0.99));
            long max = latencies.get(n - 1);
            long avg = latencies.stream().mapToLong(Long::longValue).sum() / n;

            System.out.println();
            System.out.println("===== Benchmark Results =====");
            System.out.printf("Duration:    %ds%n", duration);
            System.out.printf("Connections: %d%n", connections);
            System.out.printf("Threads:     %d (virtual)%n", threads);
            System.out.printf("Total reqs:  %d%n", total);
            System.out.printf("Successful:  %d%n", total);
            System.out.printf("Errors:      %d%n", errors);
            System.out.printf("RPS:         %.2f req/s%n", rps);
            System.out.println();
            System.out.println("Latency (ns):");
            System.out.printf("  Avg:       %,d ns  (%.2f ms)%n", avg, avg / 1_000_000.0);
            System.out.printf("  P50:       %,d ns  (%.2f ms)%n", p50, p50 / 1_000_000.0);
            System.out.printf("  P90:       %,d ns  (%.2f ms)%n", p90, p90 / 1_000_000.0);
            System.out.printf("  P99:       %,d ns  (%.2f ms)%n", p99, p99 / 1_000_000.0);
            System.out.printf("  Max:       %,d ns  (%.2f ms)%n", max, max / 1_000_000.0);
        }
    }
}
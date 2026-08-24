package com.chua.example.network.rpc;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 四实现综合自检（SPI 形式）— 覆盖 {@code native / json / dubbo / sofa}。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=rpc} 调用，
 * 内部基于 {@link RpcServer} / {@link RpcClient} SPI 工厂切换四种实现，
 * 每种实现执行同一套「注册 → 代理调用 → 断言」用例：</p>
 * <ul>
 *   <li><b>native</b> — 纯 JDK TCP NIO 直连（注册中心：direct）</li>
 *   <li><b>json</b> — JSON-RPC 2.0 over HTTP（JDK HttpServer + jsonrpc4j）</li>
 *   <li><b>dubbo</b> — Apache Dubbo（注册中心：multicast 组播，免外部服务）</li>
 *   <li><b>sofa</b> — 蚂蚁 SOFA-RPC（注册中心：local，免外部服务）</li>
 * </ul>
 *
 * <p>除 echo/add 基础回环外，每种实现还断言<b>异常传播</b>（{@link RpcEchoServiceExample#fail(String)}
 * 抛出的远程异常原样传回客户端）、<b>复杂对象传输</b>（{@link RpcPayloadExample} 序列化往返）、
 * <b>集合传输</b>（{@link RpcEchoServiceExample#batch(List)} 列表往返）；native 额外覆盖
 * <b>并发调用</b>（多线程共享同一代理）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 四实现全部自检
 *   java ExampleRunner --example=rpc
 *
 *   # 只跑某一种实现
 *   java ExampleRunner --example=rpc --type=native
 *   java ExampleRunner --example=rpc --type=json
 *   java ExampleRunner --example=rpc --type=dubbo
 *   java ExampleRunner --example=rpc --type=sofa
 *
 *   # 压测（native 实现，自动调优配置）
 *   java ExampleRunner --example=rpc --type=bench --threads=16 --seconds=3 --ops=2000
 * </pre>
 *
 * <p><b>注意</b>：SPI 工厂创建的服务端实例不会自动触发 {@code afterPropertiesSet()}
 * （项目 {@code InitializingAware} 非 Spring InitializingBean，autowire 不处理），
 * 示例内统一手动调用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class RpcExample implements Example {

    /**
     * native 实现监听端口（避开默认 18866，防冲突）
     */
    private static final int NATIVE_PORT = 28866;

    /**
     * json 实现 HTTP 监听端口（避开默认 8080，防冲突）
     */
    private static final int JSON_PORT = 28080;

    /**
     * dubbo 实现协议端口（避开默认 20880，防冲突）
     */
    private static final int DUBBO_PORT = 20881;

    /**
     * sofa 实现 bolt 协议端口（避开默认 12200，防冲突）
     */
    private static final int SOFA_PORT = 12201;

    /**
     * 应用名称（dubbo / sofa 注册到注册中心时使用）
     */
    private static final String APP_NAME = "rpc-example";

    /**
     * 断言失败时的最大重试次数（dubbo 组播发现为异步，允许短暂等待）
     */
    private static final int MAX_RETRY = 5;

    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_DELAY_MS = 500L;

    @Override
    /** Name */
    public String name() {
        return "rpc";
    }

    @Override
    /** Module */
    public String module() {
        return "rpc";
    }

    @Override
    /** Description */
    public String description() {
        return "RPC 四实现自检（native / json / dubbo / sofa）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("===== rpc --test [type={}] =====", type);
        listRegisteredServers();

        return switch (type.toLowerCase()) {
            case "native" -> testNative();
            case "json" -> testJson();
            case "dubbo" -> testDubbo();
            case "sofa" -> testSofa();
            case "bench", "benchmark", "stress", "压测" -> benchmark(args);
            default -> {
                boolean passed = true;
                passed &= testNative();
                passed &= testJson();
                passed &= testDubbo();
                passed &= testSofa();
                yield passed;
            }
        };
    }

    /**
     * 列出已注册的 RPC 服务端 SPI 实现（防御式：某些实现无无参构造器，枚举失败不阻断主流程）。
     */
    private void listRegisteredServers() {
        log.info("[0] 已注册的 RPC 服务端实现:");
        try {
            com.chua.common.support.spi.ServiceProvider.of(RpcServer.class)
                    .collect()
                    .forEach(s -> log.info("    - {}", s.getClass().getName()));
        } catch (Exception e) {
            log.warn("    枚举 RPC 实现失败（不影响测试）: {}", e.toString());
        }
    }

    // ==================== benchmark（压测：自动调优配置 + 并发负载） ====================

    /**
     * 压测：基于 {@link RpcConsumerConfig#auto()} 自动调优配置，对 native 实现做并发负载测试。
     *
     * <p>流程：以 {@code auto} 配置启动服务端与客户端 → 预热 → 指定线程数并发调用，
     * 统计总调用数、成功率、QPS、平均/最大延迟，并输出分位延迟（P50/P90/P99）。</p>
     *
     * @param args 命令行参数：{@code threads}（并发线程数，默认 16）、
     *             {@code seconds}（压测时长秒，默认 3）、{@code ops}（单线程调用次数，默认 2000）、
     *             {@code protocol}（压测协议：native 默认 / dubbo）
     * @return 全部调用成功且 QPS &gt; 0 返回 {@code true}
     */
    private boolean benchmark(Map<String, String> args) {
        int threads = parseInt(args.get("threads"), 16);
        int seconds = parseInt(args.get("seconds"), 3);
        int ops = parseInt(args.get("ops"), 2000);
        // 压测协议：native（默认）/ dubbo，走各自端口与注册方式
        String protocolName = args.getOrDefault("protocol", "native").toLowerCase();
        log.info("\n[bench] {} 压测 (threads={}, seconds={}, ops/thread={})", protocolName, threads, seconds, ops);

        RpcServer server = null;
        RpcClient client = null;
        try {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            int port;
            if ("dubbo".equals(protocolName)) {
                registry.setProtocol("multicast");
                registry.setAddress("multicast://224.5.6.7:1234");
                port = DUBBO_PORT;
            } else {
                protocolName = "native";
                registry.setProtocol("direct");
                registry.setAddress("127.0.0.1:" + NATIVE_PORT);
                port = NATIVE_PORT;
            }

            // 自动调优配置：协议（服务端线程池/缓冲区）+ 消费者（超时/连接数）
            RpcProtocolConfig protocol = RpcProtocolConfig.auto(protocolName, port);
            // 手动覆盖：--workers 调服务端 worker 线程数，--connections 调客户端连接池
            int workers = parseInt(args.get("workers"), -1);
            if (workers > 0) {
                protocol = new RpcProtocolConfig(protocol.name(), protocol.host(), protocol.port(),
                        protocol.payload(), protocol.buffer(), workers, protocol.accepts(),
                        protocol.ioThreads(), protocol.alive(), protocol.queues(),
                        protocol.serialization(), protocol.codec(), protocol.transporter(),
                        protocol.dispatcher(), protocol.threadpool(), protocol.heartbeat(),
                        protocol.ssl(), protocol.register(), protocol.charset(),
                        protocol.keepAlive(), workers, workers, protocol.idleTimeout());
            }
            server = RpcServer.createService(protocolName, List.of(registry), protocol, APP_NAME);
            server.afterPropertiesSet();
            server.register(RpcEchoServiceExample.class.getName(), new RpcEchoServiceImplExample());

            RpcConsumerConfig consumer = RpcConsumerConfig.auto();
            consumer.setCheck(false);
            int conns = parseInt(args.get("connections"), -1);
            if (conns > 0) {
                consumer.setConnections(conns);
            }
            // 同 JVM 直调：跳过网络与序列化，仅用于压测无序列化方案的极限吞吐
            boolean inline = "true".equalsIgnoreCase(args.getOrDefault("inline", "false"));
            consumer.setInline(inline);
            if (inline) {
                log.info("  [inline] 同 JVM 直调已启用（零网络、零序列化）");
            }
            log.info("  [auto] 消费者自动调优: timeout={}ms, connectTimeout={}ms, connections={}, retryDelay={}ms",
                    consumer.getTimeout(), consumer.getConnectTimeout(),
                    consumer.getConnections(), consumer.getRetryDelay());
            log.info("  [auto] 协议自动调优: coreThreads={}, maxThreads={}, ioThreads={}, queues={}, buffer={}",
                    protocol.coreThreads(), protocol.maxThreads(),
                    protocol.ioThreads(), protocol.queues(), protocol.buffer());

            client = RpcClient.createClient(protocolName, List.of(registry), consumer, APP_NAME);
            RpcEchoServiceExample echo = client.get(RpcEchoServiceExample.class);

            // 预热：串行 200 次，建立连接与 JIT 热点
            log.info("  [warmup] 预热中...");
            for (int i = 0; i < 200; i++) {
                echo.echo("warm-" + i);
            }

            // 并发压测
            ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256), new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger(1);
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "rpc-bench-" + n.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger fail = new AtomicInteger();
            AtomicBoolean deadline = new AtomicBoolean(false);
            List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    long deadlineNanos = System.nanoTime() + seconds * 1_000_000_000L;
                    for (int i = 0; i < ops; i++) {
                        if (System.nanoTime() > deadlineNanos) {
                            deadline.set(true);
                            break;
                        }
                        long begin = System.nanoTime();
                        try {
                            echo.echo("load-" + tid + "-" + i);
                            ok.incrementAndGet();
                        } catch (Exception e) {
                            fail.incrementAndGet();
                        }
                        latencies.add(System.nanoTime() - begin);
                    }
                    done.countDown();
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            long wallBegin = System.nanoTime();
            start.countDown();
            done.await(seconds + 10L, TimeUnit.SECONDS);
            long wallElapsedMs = (System.nanoTime() - wallBegin) / 1_000_000;
            pool.shutdownNow();

            long total = ok.get() + fail.get();
            long qps = wallElapsedMs > 0 ? total * 1000 / Math.max(wallElapsedMs, 1) : 0;
            double successRate = total > 0 ? ok.get() * 100.0 / total : 0;
            long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            double avgUs = sorted.length > 0 ? java.util.Arrays.stream(sorted).average().orElse(0) / 1000 : 0;
            long maxUs = sorted.length > 0 ? sorted[sorted.length - 1] / 1000 : 0;
            long p50 = percentile(sorted, 50) / 1000;
            long p90 = percentile(sorted, 90) / 1000;
            long p99 = percentile(sorted, 99) / 1000;

            log.info("  [result] 总调用={}, 成功={}, 失败={}, 成功率={}%{}", total, ok.get(), fail.get(),
                    String.format("%.2f", successRate), deadline.get() ? " (达到时间上限)" : "");
            log.info("  [result] 墙钟={}ms, QPS={}, 平均延迟={}µs, 最大延迟={}µs",
                    wallElapsedMs, qps, String.format("%.1f", avgUs), maxUs);
            log.info("  [result] P50={}µs, P90={}µs, P99={}µs", p50, p90, p99);

            return fail.get() == 0 && qps > 0;
        } catch (Exception e) {
            fail("bench 压测异常: " + e);
            return false;
        } finally {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    /**
     * 解析整数参数，解析失败或非法时返回默认值。
     *
     * @param value  字符串值
     * @param defVal 默认值
     * @return 解析后的整数值
     */
    private static int parseInt(String value, int defVal) {
        if (value == null || value.isEmpty()) {
            return defVal;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    /**
     * 计算有序延迟数组的指定分位数（纳秒）。
     *
     * @param sorted 已排序的纳秒数组
     * @param p      分位（0-100）
     * @return 分位值（纳秒），空数组返回 0
     */
    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(idx, 0)];
    }

    // ==================== native（纯 JDK TCP NIO） ====================

    /**
     * native 实现自检：直连模式，服务端绑定 TCP 端口，客户端按地址直连。
     *
     * @return 全部用例通过返回 {@code true}
     */
    private boolean testNative() {
        log.info("\n[native] 直连 TCP NIO 自检");
        RpcServer server = null;
        RpcClient client = null;
        try {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setProtocol("direct");
            registry.setAddress("127.0.0.1:" + NATIVE_PORT);

            server = RpcServer.createService("native",
                    List.of(registry), protocol("native", NATIVE_PORT), APP_NAME);
            server.afterPropertiesSet();
            server.register(RpcEchoServiceExample.class.getName(), new RpcEchoServiceImplExample());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            client = RpcClient.createClient("native", List.of(registry), consumer, APP_NAME);

            RpcEchoServiceExample echo = client.get(RpcEchoServiceExample.class);
            assertEcho(echo, "native");
            assertRemoteFail(echo, "native");
            assertConcurrent(echo, "native");
            pass();
            return true;
        } catch (Exception e) {
            fail("native 自检异常: " + e);
            return false;
        } finally {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    // ==================== json（JSON-RPC 2.0 over HTTP） ====================

    /**
     * json 实现自检：HTTP POST + 裸方法名路由。
     *
     * @return 全部用例通过返回 {@code true}
     */
    private boolean testJson() {
        log.info("\n[json] JSON-RPC 2.0 over HTTP 自检");
        RpcServer server = null;
        RpcClient client = null;
        try {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setAddress("http://127.0.0.1:" + JSON_PORT);

            server = RpcServer.createService("json",
                    List.of(registry), protocol("json", JSON_PORT), APP_NAME);
            server.afterPropertiesSet();
            server.register(RpcEchoServiceExample.class.getName(), new RpcEchoServiceImplExample());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            consumer.setRetries(1);
            client = RpcClient.createClient("json", List.of(registry), consumer, APP_NAME);

            RpcEchoServiceExample echo = client.get(RpcEchoServiceExample.class);
            assertEcho(echo, "json");
            assertRemoteFail(echo, "json");
            pass();
            return true;
        } catch (Exception e) {
            fail("json 自检异常: " + e);
            return false;
        } finally {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    // ==================== dubbo（Apache Dubbo，组播注册中心） ====================

    /**
     * dubbo 实现自检：组播（multicast）注册中心，免外部服务。
     *
     * @return 全部用例通过返回 {@code true}
     */
    private boolean testDubbo() {
        log.info("\n[dubbo] Apache Dubbo 自检 (registry=multicast)");
        RpcServer server = null;
        RpcClient client = null;
        try {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setProtocol("multicast");
            registry.setAddress("multicast://224.5.6.7:1234");

            server = RpcServer.createService("dubbo",
                    List.of(registry), protocol("dubbo", DUBBO_PORT), APP_NAME);
            server.afterPropertiesSet();
            server.register(RpcEchoServiceExample.class.getName(), new RpcEchoServiceImplExample());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            // 组播发现异步，不阻塞 get() 调用
            consumer.setCheck(false);
            client = RpcClient.createClient("dubbo", List.of(registry), consumer, APP_NAME);

            RpcEchoServiceExample echo = client.get(RpcEchoServiceExample.class);
            assertEcho(echo, "dubbo");
            assertRemoteFail(echo, "dubbo");
            pass();
            return true;
        } catch (Exception e) {
            fail("dubbo 自检异常: " + e);
            return false;
        } finally {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    // ==================== sofa（SOFA-RPC，本地注册中心） ====================

    /**
     * sofa 实现自检：本地（local）注册中心，免外部服务。
     *
     * @return 全部用例通过返回 {@code true}
     */
    private boolean testSofa() {
        log.info("\n[sofa] SOFA-RPC 自检 (registry=local)");
        RpcServer server = null;
        RpcClient client = null;
        try {
            RpcRegistryConfig registry = new RpcRegistryConfig();
            registry.setProtocol("local");
            registry.setAddress("local");

            server = RpcServer.createService("sofa",
                    List.of(registry), protocol("bolt", SOFA_PORT), APP_NAME);
            server.afterPropertiesSet();
            server.register(RpcEchoServiceExample.class.getName(), new RpcEchoServiceImplExample());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            consumer.setCheck(true);
            client = RpcClient.createClient("sofa", List.of(registry), consumer, APP_NAME);

            RpcEchoServiceExample echo = client.get(RpcEchoServiceExample.class);
            assertEcho(echo, "sofa");
            assertRemoteFail(echo, "sofa");
            pass();
            return true;
        } catch (Exception e) {
            fail("sofa 自检异常: " + e);
            return false;
        } finally {
            closeQuietly(client);
            closeQuietly(server);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 组装协议配置：仅设置协议名、监听主机与端口，其余取默认。
     *
     * @param name 协议名（native / json / dubbo / bolt）
     * @param port 监听端口
     * @return 协议配置
     */
    static RpcProtocolConfig protocol(String name, int port) {
        return new RpcProtocolConfig(name, "0.0.0.0", port,
                null, null, 8, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null);
    }

    /**
     * 执行回显断言：echo 原样返回 + add 求和，失败自动重试（容忍组播发现延迟）。
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志）
     * @throws Exception 断言失败或重试耗尽时抛出
     */
    private static void assertEcho(RpcEchoServiceExample echo, String label) throws Exception {
        Throwable last = null;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                assertEquals("echo:hello", echo.echo("hello"), label + " echo 返回值");
                assertEquals(5, echo.add(2, 3), label + " add 返回值");
                assertComplexRoundTrip(echo, label);
                assertDeepScenarios(echo, label);
                log.info("    echo(\"hello\") = {}, add(2,3) = {}, payload/batch 往返 OK", "echo:hello", 5);
                return;
            } catch (Throwable t) {
                last = t;
                log.warn("  {} 第 {} 次调用失败: {}", label, i + 1, t.toString());
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        if (last instanceof Exception e) {
            throw e;
        }
        throw new AssertionError(label + " 断言失败", last);
    }

    /**
     * 复杂场景断言：复杂对象往返 + 集合参数/返回值往返。
     *
     * <p>覆盖比 {@code echo/add} 更深的序列化链路：</p>
     * <ul>
     *   <li>{@link RpcEchoServiceExample#echoPayload(RpcPayloadExample)} — 嵌套字段复杂对象原样往返</li>
     *   <li>{@link RpcEchoServiceExample#batch(List)} — 集合参数、集合返回值、泛型擦除后的元素还原</li>
     * </ul>
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志与异常消息）
     */
    private static void assertComplexRoundTrip(RpcEchoServiceExample echo, String label) {
        RpcPayloadExample sent = new RpcPayloadExample("订单-2026-0818", 42);
        RpcPayloadExample back = echo.echoPayload(sent);
        if (!sent.equals(back)) {
            throw new AssertionError(label + " echoPayload 对象往返不一致: 期望 " + sent + "，实际 " + back);
        }

        List<String> batch = echo.batch(List.of("a", "bb", "ccc"));
        if (batch == null || batch.size() != 3
                || !batch.get(0).equals("echo:a")
                || !batch.get(1).equals("echo:bb")
                || !batch.get(2).equals("echo:ccc")) {
            throw new AssertionError(label + " batch 集合往返不一致: " + batch);
        }
    }

    /**
     * 深度场景断言：null 往返、大对象往返、深层嵌套对象往返。
     *
     * <p>覆盖序列化/传输链路的边界条件：</p>
     * <ul>
     *   <li>{@link RpcEchoServiceExample#echoNullable(String)} — null 值往返（无类型信息、无字节内容）</li>
     *   <li>{@link RpcEchoServiceExample#echoLarge(String)} — 约 1MB 大对象往返（长度帧 + 缓冲区边界）</li>
     *   <li>{@link RpcEchoServiceExample#echoNested(RpcPayloadExample)} — 三层嵌套对象图往返</li>
     * </ul>
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志与异常消息）
     */
    private static void assertDeepScenarios(RpcEchoServiceExample echo, String label) {
        // null 往返：null 参数应原样返回 null（不 NPE、不误写为空串）
        if (echo.echoNullable(null) != null) {
            throw new AssertionError(label + " echoNullable(null) 未返回 null");
        }
        // null 显式字符串往返
        if (!"null-msg".equals(echo.echoNullable("null-msg"))) {
            throw new AssertionError(label + " echoNullable 显式字符串往返失败");
        }
        // 大对象：约 1MB 字符串往返
        String large = "L".repeat(1024 * 1024);
        if (!large.equals(echo.echoLarge(large))) {
            throw new AssertionError(label + " echoLarge 1MB 往返不一致");
        }
        // 深层嵌套：三层 RpcPayloadExample 对象图往返
        RpcPayloadExample leaf = new RpcPayloadExample("leaf", 1);
        RpcPayloadExample mid = new RpcPayloadExample("mid", 2, leaf);
        RpcPayloadExample root = new RpcPayloadExample("root", 3, mid);
        RpcPayloadExample back = echo.echoNested(root);
        if (!root.equals(back)) {
            throw new AssertionError(label + " echoNested 三层嵌套往返不一致: " + back);
        }
        log.info("    [深度] null/1MB大对象/三层嵌套对象 往返 OK");
    }

    /**
     * 并发断言：多线程共享同一远程代理并发调用，验证连接复用与线程安全。
     *
     * <p>使用 {@code 8} 个线程 × {@code 50} 次调用，每个线程携带独立消息，
     * 若任一调用结果被串扰（返回了别的线程的消息）或抛异常，则断言失败。</p>
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志与异常消息）
     * @throws Exception 并发断言失败或线程中断时抛出
     */
    private static void assertConcurrent(RpcEchoServiceExample echo, String label) throws Exception {
        int threads = 8;
        int perThread = 50;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256), new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "rpc-bench-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> errors = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                int threadId = t;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            String msg = "t" + threadId + "-" + i;
                            String resp = echo.echo(msg);
                            if (!("echo:" + msg).equals(resp)) {
                                synchronized (errors) {
                                    errors.add("线程" + threadId + " 第" + i + "次串扰: 期望 echo:" + msg + "，实际 " + resp);
                                }
                                failed.incrementAndGet();
                                return;
                            }
                            success.incrementAndGet();
                        }
                    } catch (Exception e) {
                        synchronized (errors) {
                            errors.add("线程" + threadId + " 异常: " + e);
                        }
                        failed.incrementAndGet();
                    }
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new AssertionError(label + " 并发测试超时未结束");
            }
        } finally {
            pool.shutdownNow();
        }

        if (failed.get() > 0) {
            throw new AssertionError(label + " 并发测试失败 " + failed.get() + " 次: " + errors);
        }
        log.info("    [并发] {} 线程 × {} 次/线程 = {} 次调用全部成功", threads, perThread, success.get());
    }

    /**
     * 断言远程异常传播：{@code fail()} 抛出的异常应传播回客户端，且异常链中应包含原始消息。
     *
     * <p>不同框架对远程异常的类型/包装不同（RpcException / JsonException / IllegalStateException），
     * 因此只断言「抛出 + 异常链中包含 boom 前缀」，不绑定具体异常类型。</p>
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志与异常消息）
     */
    private static void assertRemoteFail(RpcEchoServiceExample echo, String label) {
        try {
            echo.fail("boom-" + label);
            throw new AssertionError(label + " fail 应抛出异常但未抛");
        } catch (Exception expected) {
            // AssertionError（Error 子类）不会被此处捕获，直接向外传播
            for (Throwable cur = expected; cur != null; cur = cur.getCause()) {
                if (cur.getMessage() != null && cur.getMessage().contains("boom")) {
                    return;
                }
            }
            throw new AssertionError(label + " fail 异常链不含 boom: " + expected.getMessage(), expected);
        }
    }

    /**
     * 断言字符串相等。
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param msg      失败描述
     */
    private static void assertEquals(String expected, String actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /**
     * 断言整数相等。
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param msg      失败描述
     */
    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /**
     * 输出通过日志。
     */
    private static void pass() {
        log.info("  ✓ 通过");
    }

    /**
     * 输出失败日志。
     *
     * @param msg 失败描述
     */
    private static void fail(String msg) {
        log.info("  ✗ 失败: {}", msg);
    }

    /**
     * 静默关闭 RPC 客户端。
     *
     * @param client 客户端实例，可为 {@code null}
     */
    private static void closeQuietly(RpcClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 静默关闭 RPC 服务端。
     *
     * @param server 服务端实例，可为 {@code null}
     */
    private static void closeQuietly(RpcServer server) {
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 独立入口：支持 --type=native|json|dubbo|sofa|bench 参数。
     */
    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        boolean passed = new RpcExample().run(parsed);
        log.info("[RpcExample] type={}, passed={}", parsed.get("type"), passed);
        System.exit(passed ? 0 : 1);
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    result.put(arg.substring(2, eq), arg.substring(eq + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    result.put(arg.substring(2), args[++i]);
                }
            }
        }
        return result;
    }
}
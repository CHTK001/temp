package com.chua.example.network.rpc;

import com.chua.common.support.network.rpc.RpcClient;
import com.chua.common.support.network.rpc.RpcConsumerConfig;
import com.chua.common.support.network.rpc.RpcProtocolConfig;
import com.chua.common.support.network.rpc.RpcRegistryConfig;
import com.chua.common.support.network.rpc.RpcServer;
import com.chua.example.spi.Example;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

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
 * <p>除 echo/add 基础回环外，每种实现还断言<b>异常传播</b>（{@link RpcEchoService#fail(String)}
 * 抛出的远程异常原样传回客户端）与<b>复杂对象传输</b>（{@link RpcPayload} 序列化往返）。</p>
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
    public String name() {
        return "rpc";
    }

    @Override
    public String module() {
        return "rpc";
    }

    @Override
    public String description() {
        return "RPC 四实现自检（native / json / dubbo / sofa）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        log.info("===== rpc --test [type={}] =====", type);
        listRegisteredServers();

        return switch (type.toLowerCase()) {
            case "native" -> testNative();
            case "json" -> testJson();
            case "dubbo" -> testDubbo();
            case "sofa" -> testSofa();
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
            server.register(RpcEchoService.class.getName(), new RpcEchoServiceImpl());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            client = RpcClient.createClient("native", List.of(registry), consumer, APP_NAME);

            RpcEchoService echo = client.get(RpcEchoService.class);
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
            server.register(RpcEchoService.class.getName(), new RpcEchoServiceImpl());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            consumer.setRetries(1);
            client = RpcClient.createClient("json", List.of(registry), consumer, APP_NAME);

            RpcEchoService echo = client.get(RpcEchoService.class);
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
            server.register(RpcEchoService.class.getName(), new RpcEchoServiceImpl());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            // 组播发现异步，不阻塞 get() 调用
            consumer.setCheck(false);
            client = RpcClient.createClient("dubbo", List.of(registry), consumer, APP_NAME);

            RpcEchoService echo = client.get(RpcEchoService.class);
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
            server.register(RpcEchoService.class.getName(), new RpcEchoServiceImpl());

            RpcConsumerConfig consumer = new RpcConsumerConfig();
            consumer.setTimeout(5000);
            consumer.setCheck(true);
            client = RpcClient.createClient("sofa", List.of(registry), consumer, APP_NAME);

            RpcEchoService echo = client.get(RpcEchoService.class);
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
    private static void assertEcho(RpcEchoService echo, String label) throws Exception {
        Throwable last = null;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                assertEquals("echo:hello", echo.echo("hello"), label + " echo 返回值");
                assertEquals(5, echo.add(2, 3), label + " add 返回值");
                assertComplexRoundTrip(echo, label);
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
     *   <li>{@link RpcEchoService#echoPayload(RpcPayload)} — 嵌套字段复杂对象原样往返</li>
     *   <li>{@link RpcEchoService#batch(List)} — 集合参数、集合返回值、泛型擦除后的元素还原</li>
     * </ul>
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志与异常消息）
     */
    private static void assertComplexRoundTrip(RpcEchoService echo, String label) {
        RpcPayload sent = new RpcPayload("订单-2026-0818", 42);
        RpcPayload back = echo.echoPayload(sent);
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
     * 断言远程异常传播：{@code fail()} 抛出的异常应传播回客户端，且异常链中应包含原始消息。
     *
     * <p>不同框架对远程异常的类型/包装不同（RpcException / JsonException / IllegalStateException），
     * 因此只断言「抛出 + 异常链中包含 boom 前缀」，不绑定具体异常类型。</p>
     *
     * @param echo  远程代理对象
     * @param label 实现标识（用于日志与异常消息）
     */
    private static void assertRemoteFail(RpcEchoService echo, String label) {
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
}

package com.chua.example.ionet;

import com.chua.example.spi.Example;
import com.chua.ionet.support.client.IonetSyncClient;
import com.chua.ionet.support.common.IonetCmd;
import com.chua.ionet.support.server.IonetServer;
import com.iohao.net.extension.client.AbstractInputCommandRegion;
import com.iohao.net.framework.annotations.ActionController;
import com.iohao.net.framework.annotations.ActionMethod;
import com.iohao.net.framework.core.CmdInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ionet 基于 ioaha Action 真实链路的吞吐量测试示例（SPI 形式）。
 *
 * <p>区别于 SyncExampleSpi 的本地 publish/send 语义，本示例走真实网络链路：
 * 客户端 region 通过 {@code ofRequestCommand(...).execute()} 发送 Action 命令，
 * 服务端 {@code @ActionMethod} 处理并响应，客户端 callback 计数，统计真实 ops/s。</p>
 *
 * <p>调用：{@code ExampleRunner --example=ionet-perf --messages=2000}</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class IonetPerfExampleSpi implements Example {

    /** 服务端 Action 路由常量 */
    public interface PerfCmd {
        int cmd = 2;
        int echo = 0;
    }

    /** 服务端 Action：收到请求立即回显 */
    @ActionController(PerfCmd.cmd)
    public static class PerfAction {
        @ActionMethod(PerfCmd.echo)
        public String echo(String message) {
            log.info("[PerfAction] 收到请求: {}", message);
            return message;
        }
    }

    /** 客户端 Region：循环发送 echo 命令并计数 */
    public static class PerfRegion extends AbstractInputCommandRegion {
        final AtomicInteger received = new AtomicInteger();
        volatile CountDownLatch latch;

        @Override
        public void initInputCommand() {
            setCmd(PerfCmd.cmd);
            ofCommand(PerfCmd.echo)
                    .setTitle("perf-echo")
                    .setRequestData(() -> "ping")
                    .callback(result -> {
                        if (latch != null) {
                            received.incrementAndGet();
                            latch.countDown();
                        }
                    });
        }

        /**
         * 发送一批请求。
         *
         * @param count 请求数
         */
        public void sendBatch(int count) {
            latch = new CountDownLatch(count);
            CmdInfo cmd = IonetCmd.of(PerfCmd.cmd, PerfCmd.echo);
            for (int i = 0; i < count; i++) {
                ofRequestCommand(cmd).execute();
            }
        }

        /**
         * 发送单条请求（多线程并发时使用）。
         */
        public void sendOne() {
            CmdInfo cmd = IonetCmd.of(PerfCmd.cmd, PerfCmd.echo);
            ofRequestCommand(cmd).execute();
        }

        /**
         * 等待全部响应。
         *
         * @param timeout 超时秒数
         * @return 是否收满
         */
        public boolean awaitAll(int timeout) {
            try {
                return latch.await(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @Override
    public String name() {
        return "ionet-perf";
    }

    @Override
    public String module() {
        return "ionet";
    }

    @Override
    public String description() {
        return "ionet 基于 ioaha Action 真实链路的吞吐量测试";
    }

    @Override
    public boolean run(Map<String, String> args) {
        int messages = Integer.parseInt(args.getOrDefault("messages", "2000"));
        int threads = Integer.parseInt(args.getOrDefault("threads", "1"));
        int port = 10100 + (int) (Math.random() * 1000);
        log.info("===== ionet-perf 真实链路吞吐开始: messages={} threads={} port={} =====",
                messages, threads, port);

        IonetServer server = IonetServer.builder()
                .port(port)
                .scanActionPackage(PerfAction.class)
                .build();

        PerfRegion region = new PerfRegion();
        IonetSyncClient client = IonetSyncClient.builder()
                .host("127.0.0.1")
                .port(port)
                .addRegion(region)
                .build();
        try {
            server.start();
            client.connect();
            if (!client.awaitConnection(30, TimeUnit.SECONDS)) {
                log.error("  ionet 客户端连接超时");
                return false;
            }
            log.info("  客户端已连接，{} 线程并发发送 {} 条请求...", threads, messages);

            long start = System.nanoTime();
            if (threads <= 1) {
                region.sendBatch(messages);
            } else {
                int perThread = messages / threads;
                int remainder = messages % threads;
                Thread[] workers = new Thread[threads];
                for (int t = 0; t < threads; t++) {
                    int count = perThread + (t < remainder ? 1 : 0);
                    workers[t] = new Thread(() -> {
                        for (int i = 0; i < count; i++) {
                            try {
                                region.sendOne();
                            } catch (Exception e) {
                                log.warn("  ionet 请求发送异常: {}", e.getMessage());
                            }
                        }
                    }, "ionet-perf-" + t);
                    workers[t].start();
                }
                for (Thread w : workers) {
                    try {
                        w.join();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            boolean ok = region.awaitAll(30);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            double ops = ok ? messages * 1000.0 / Math.max(elapsedMs, 1) : 0;
            log.info("  [ionet] Action 真实链路吞吐({}线程): {} 条/{}ms = {} ops/s, 收到 {} 条",
                    threads, messages, elapsedMs, Math.round(ops), region.received.get());
            return ok && region.received.get() == messages;
        } catch (Exception e) {
            log.error("  ionet-perf 异常: {}", e.getMessage(), e);
            return false;
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 独立入口：{@code java ... IonetPerfExampleSpi --messages=2000}
     */
    public static void main(String[] args) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String kv = arg.substring(2);
                int eq = kv.indexOf('=');
                map.put(eq > 0 ? kv.substring(0, eq) : kv, eq > 0 ? kv.substring(eq + 1) : "");
            }
        }
        new IonetPerfExampleSpi().run(map);
    }
}

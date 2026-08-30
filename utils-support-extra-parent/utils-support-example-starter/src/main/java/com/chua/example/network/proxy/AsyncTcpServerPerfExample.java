package com.chua.example.network.proxy;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * TCP echo 异步压测端：启动目标 TcpServer，用 Vert.x NetClient 异步 keep-alive 打真实吞吐。
 *
 * <p>与 AsyncServerPerfMain（HTTP 异步）同口径（并发/时长/客户端异步），公平对比 tcp vs http。</p>
 *
 * <p>用法：{@code java ... AsyncTcpServerPerfMain <type> [durSec] [conc]}，如
 * {@code vertx-tcp 5 2000}、{@code jdk-tcp 5 2000}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AsyncTcpServerPerfExample {

    /** log */
/** 单次 echo 载荷（64B，与 VertxTcpPerfMain 一致） */
    private static final byte[] PAYLOAD = new byte[64];

    static {
        java.util.Arrays.fill(PAYLOAD, (byte) 'A');
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        String type = args.length > 0 ? args[0] : "vertx-tcp";
        int durSec = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int conc = args.length > 2 ? Integer.parseInt(args[2]) : 2000;
        // 每连接并发流数：与 HTTP 压测端(并发多 inflight)同口径。
        // 诊断结论:此前 TCP 压测每连接 1 inflight 串行,受 RTT 限制单连接吞吐,
        // 远低于 HTTP 每连接并发多流;此处每连接并发 streams 个在途 echo 拉平口径。
        int streams = args.length > 3 ? Integer.parseInt(args[3]) : 8;

        // 1. 启动目标 TcpServer（项目自己的实现）
        Server server = ServerBuilder.create().type(type).host("127.0.0.1").port(0).build();
        server.start();
        int port = server.getPort();
        log.info("[AsyncTcpPerf] 服务器已启动: type={}, port={}, 开始异步压测 dur={}s conc={} streams={}",
                type, port, durSec, conc, streams);

        // 2. 异步压测端：Vert.x NetClient，每连接 keep-alive 复用，并发回显
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 2))
                .setPreferNativeTransport(true));
        NetClient client = vertx.createNetClient(new NetClientOptions()
                .setTcpNoDelay(true)
                .setConnectTimeout(5000)
                .setReconnectAttempts(0));

        LongAdder ok = new LongAdder();
        LongAdder errors = new LongAdder();
        Semaphore inflight = new Semaphore(conc);
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch allDone = new CountDownLatch(1);
        long t0 = System.nanoTime();
        long deadline = System.nanoTime() + (long) durSec * 1_000_000_000L;

        // 递归发送：一个 echo 完成 → 若未到期限继续发下一个（连接由 Vert.x 池化复用）
        java.util.function.BiConsumer<NetSocket, Buffer> fire = new java.util.function.BiConsumer<>() {
            @Override
            /** Accept */
            public void accept(NetSocket socket, Buffer buf) {
                socket.write(buf).onComplete(ar -> {
                    if (ar.succeeded()) {
                        ok.increment();
                    } else {
                        errors.increment();
                    }
                    inflight.release();
                    next(socket, buf);
                });
            }
        };

        // 建立 conc 个连接，每连接并发 streams 个在途 echo（与 HTTP 压测端并发多流同口径）
        for (int i = 0; i < conc; i++) {
            try {
                inflight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            client.connect(port, "127.0.0.1").onSuccess(socket -> {
                socket.handler(data -> {
                    // 响应到达：计数后补发一个，维持每连接 streams 个在途
                    ok.increment();
                    inflight.release();
                    next(socket, Buffer.buffer(PAYLOAD));
                }).exceptionHandler(err -> {
                    errors.increment();
                    inflight.release();
                });
                // 每连接预发 streams 个 echo（并发在途）
                for (int s = 0; s < streams; s++) {
                    socket.write(Buffer.buffer(PAYLOAD));
                }
            }).onFailure(err -> {
                errors.increment();
                inflight.release();
            });
        }
        boolean finished = allDone.await(durSec + 10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long total = ok.sum();
        long errs = errors.sum();
        double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
        log.info("[AsyncTcpPerf] {} 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                type, total, elapsedMs, Math.round(rps), total, errs, finished);
        client.close();
        vertx.close();
        server.stop();
    }

    /** Next */
    private static void next(NetSocket socket, Buffer buf) {
        // 连接已关时 write 会 fail，由 exceptionHandler 兜底计数，此处直接写
        if (socket != null) {
            socket.write(buf);
        }
    }
}

package com.chua.example.network.proxy;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.vertx.support.server.VertxTcpProxyServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;

import static java.util.Arrays.fill;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * VertxTcpProxyServer 异步压测端：后端 Vert.x echo server ← VertxTcpProxyServer ← Vert.x NetClient。
 *
 * <p>与 AsyncTcpServerPerfMain 同口径（durSec/conc/streams，每连接并发多流），
 * 公平对比代理 vs 直连。</p>
 *
 * <p>用法：{@code java ... AsyncTcpProxyPerfMain [durSec] [conc] [streams]}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AsyncTcpProxyPerfExample {
    private AsyncTcpProxyPerfExample() { }


    /** 单次 echo 载荷（64B） */
    private static final byte[] PAYLOAD = new byte[64];

    static {
        fill(PAYLOAD, (byte) 'A');
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        int durSec = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int conc = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
        int streams = args.length > 2 ? Integer.parseInt(args[2]) : 8;

        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 2))
                .setPreferNativeTransport(true));

        // 1. 后端 Vert.x echo server
        NetServer backend = vertx.createNetServer(new NetServerOptions()
                .setHost("127.0.0.1").setPort(0).setTcpNoDelay(true));
        backend.connectHandler(socket -> socket.handler(socket::write));
        backend.listen().toCompletionStage().toCompletableFuture().join();
        int backendPort = backend.actualPort();

        // 2. VertxTcpProxyServer（前端端口 0 随机）
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(0);
        setting.setProtocol("vertx-tcp-proxy");
        VertxTcpProxyServer proxy = new VertxTcpProxyServer(setting,
                new InetSocketAddress("127.0.0.1", backendPort));
        proxy.start();
        int proxyPort = proxy.getPort();
        log.info("[AsyncTcpProxy] 代理已启动: proxyPort={}, backend={}", proxyPort, backendPort);

        // 3. 异步压测端：每连接并发 streams 个在途 echo
        NetClient client = vertx.createNetClient(new NetClientOptions()
                .setTcpNoDelay(true)
                .setConnectTimeout(5000)
                .setReconnectAttempts(0));

        LongAdder ok = new LongAdder();
        LongAdder errors = new LongAdder();
        Semaphore inflight = new Semaphore(conc);
        CountDownLatch allDone = new CountDownLatch(1);
        long t0 = System.nanoTime();
        long deadline = System.nanoTime() + (long) durSec * 1_000_000_000L;

        for (int i = 0; i < conc; i++) {
            try {
                inflight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            client.connect(proxyPort, "127.0.0.1").onSuccess(socket -> {
                socket.handler(data -> {
                    ok.increment();
                    inflight.release();
                    if (socket != null) {
                        socket.write(Buffer.buffer(PAYLOAD));
                    }
                }).exceptionHandler(err -> {
                    errors.increment();
                    inflight.release();
                });
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
        log.info("[AsyncTcpProxy] vertx-tcp-proxy 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                total, elapsedMs, Math.round(rps), total, errs, finished);
        client.close();
        proxy.stop();
        backend.close();
        vertx.close();
    }
}

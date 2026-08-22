package com.chua.example.network.proxy;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.vertx.support.server.VertxHttpProxyServer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * VertxHttpProxyServer 异步压测端：后端 Vert.x http echo ← VertxHttpProxyServer ← Vert.x HttpClient。
 *
 * <p>与 AsyncServerPerfMain（vertx-http 直连）同口径（durSec/conc），公平对比代理 vs 直连。</p>
 *
 * <p>用法：{@code java ... AsyncHttpProxyPerfMain [durSec] [conc]}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AsyncHttpProxyPerfExample {

    /** log */
/** Main */
    public static void main(String[] args) throws Exception {
        int durSec = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int conc = args.length > 1 ? Integer.parseInt(args[1]) : 2000;

        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 2))
                .setPreferNativeTransport(true));

        // 1. 后端 Vert.x http echo server
        HttpServer backend = vertx.createHttpServer(new HttpServerOptions()
                .setHost("127.0.0.1").setPort(0).setTcpNoDelay(true));
        backend.requestHandler(req -> req.response().end("http-echo"));
        backend.listen().toCompletionStage().toCompletableFuture().join();
        int backendPort = backend.actualPort();

        // 2. VertxHttpProxyServer（前端端口 0 随机）
        ServerSetting setting = ServerSetting.defaults();
        setting.setHost("127.0.0.1");
        setting.setPort(0);
        setting.setProtocol("vertx-http-proxy");
        VertxHttpProxyServer proxy = new VertxHttpProxyServer(setting,
                new InetSocketAddress("127.0.0.1", backendPort));
        proxy.start();
        int proxyPort = proxy.getPort();
        log.info("[AsyncHttpProxy] 代理已启动: proxyPort={}, backend={}", proxyPort, backendPort);

        // 3. 异步压测端：Vert.x HttpClient 并发 inflight GET
        HttpClient client = vertx.createHttpClient(new HttpClientOptions()
                .setTcpNoDelay(true)
                .setKeepAlive(true)
                .setKeepAliveTimeout(durSec + 10)
                .setConnectTimeout(5000));
        RequestOptions opts = new RequestOptions()
                .setHost("127.0.0.1")
                .setPort(proxyPort)
                .setURI("/echo")
                .setMethod(HttpMethod.GET);

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
            fire(client, opts, inflight, ok, errors, deadline, allDone);
        }
        boolean finished = allDone.await(durSec + 10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long total = ok.sum();
        long errs = errors.sum();
        double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
        log.info("[AsyncHttpProxy] vertx-http-proxy 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                total, elapsedMs, Math.round(rps), total, errs, finished);
        client.close();
        proxy.stop();
        backend.close();
        vertx.close();
    }

    /**
     * Fire
     * @param client client
     * @param opts opts
     * @param inflight inflight
     * @param ok ok
     * @param errors errors
     * @param deadline deadline
     * @param allDone allDone
     */
    private static void fire(HttpClient client, RequestOptions opts, Semaphore inflight,
                             LongAdder ok, LongAdder errors, long deadline, CountDownLatch allDone) {
        if (System.nanoTime() > deadline) {
            allDone.countDown();
            inflight.release();
            return;
        }
        client.request(opts).onSuccess(req -> req.send().onSuccess(resp -> {
            ok.increment();
            inflight.release();
            fire(client, opts, inflight, ok, errors, deadline, allDone);
        }).onFailure(err -> {
            errors.increment();
            inflight.release();
            fire(client, opts, inflight, ok, errors, deadline, allDone);
        })).onFailure(err -> {
            errors.increment();
            inflight.release();
            allDone.countDown();
        });
    }
}

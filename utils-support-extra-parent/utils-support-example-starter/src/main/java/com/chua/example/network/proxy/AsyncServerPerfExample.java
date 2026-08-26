package com.chua.example.network.proxy;

import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.ServerBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 服务器 + 异步压测端组合：启动目标 HttpServer，用 Vert.x 异步客户端 keep-alive 打真实吞吐。
 *
 * <p>用法：{@code java ... AsyncServerPerfMain <type> [durSec] [conc]}，如
 * {@code vertx-http 10 5000}、{@code nio 10 5000}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AsyncServerPerfExample {
    private AsyncServerPerfExample() { }


    /** Main */
    public static void main(String[] args) throws Exception {
        String type = args.length > 0 ? args[0] : "vertx-http";
        int durSec = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int conc = args.length > 2 ? Integer.parseInt(args[2]) : 5000;

        // 1. 启动目标服务器（项目自己的实现）
        Server server = ServerBuilder.create().type(type).host("127.0.0.1").port(0).build();
        server.start();
        int port = server.getPort();
        log.info("[AsyncServerPerf] 服务器已启动: type={}, port={}, 开始异步压测 dur={}s conc={}",
                type, port, durSec, conc);

        // 2. 异步压测端（Vert.x HttpClient，keep-alive + 并发批处理）
        Vertx vertx = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(Math.max(Runtime.getRuntime().availableProcessors(), 2))
                .setPreferNativeTransport(true));
        HttpClient client = vertx.createHttpClient(new HttpClientOptions()
                .setTcpNoDelay(true)
                .setKeepAlive(true)
                .setKeepAliveTimeout(durSec + 10)
                .setPipelining(false)
                .setConnectTimeout(5000));

        RequestOptions opts = new RequestOptions()
                .setHost("127.0.0.1")
                .setPort(port)
                .setURI("/echo")
                .setMethod(HttpMethod.GET);

        LongAdder ok = new LongAdder();
        LongAdder errors = new LongAdder();
        Semaphore inflight = new Semaphore(conc);
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch allDone = new CountDownLatch(1);
        long t0 = System.nanoTime();
        long deadline = System.nanoTime() + (long) durSec * 1_000_000_000L;

        // 填充并发 inflight
        for (int i = 0; i < conc; i++) {
            try {
                inflight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            sendOne(client, opts, inflight, ok, errors, deadline, stop, allDone);
        }
        boolean finished = allDone.await(durSec + 10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long total = ok.sum();
        long errs = errors.sum();
        double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
        log.info("[AsyncServerPerf] {} 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                type, total, elapsedMs, Math.round(rps), total, errs, finished);

        client.close();
        vertx.close();
        server.stop();
    }

    /**
     * 发送One
     * @param client client
     * @param opts opts
     * @param inflight inflight
     * @param ok ok
     * @param errors errors
     * @param deadline deadline
     * @param stop stop
     * @param allDone allDone
     */
    private static void sendOne(HttpClient client, RequestOptions opts, Semaphore inflight,
                                LongAdder ok, LongAdder errors, long deadline,
                                AtomicBoolean stop, CountDownLatch allDone) {
        if (System.nanoTime() > deadline) {
            stop.set(true);
            allDone.countDown();
            inflight.release();
            return;
        }
        client.request(opts).onSuccess(req -> req.send().onSuccess(resp -> {
            ok.increment();
            inflight.release();
            next(client, opts, inflight, ok, errors, deadline, stop, allDone);
        }).onFailure(err -> {
            errors.increment();
            inflight.release();
            next(client, opts, inflight, ok, errors, deadline, stop, allDone);
        })).onFailure(err -> {
            errors.increment();
            inflight.release();
            stop.set(true);
            allDone.countDown();
        });
    }

    /**
     * Next
     * @param client client
     * @param opts opts
     * @param inflight inflight
     * @param ok ok
     * @param errors errors
     * @param deadline deadline
     * @param stop stop
     * @param allDone allDone
     */
    private static void next(HttpClient client, RequestOptions opts, Semaphore inflight,
                             LongAdder ok, LongAdder errors, long deadline,
                             AtomicBoolean stop, CountDownLatch allDone) {
        if (System.nanoTime() < deadline && !stop.get()) {
            try {
                inflight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop.set(true);
                allDone.countDown();
                return;
            }
            sendOne(client, opts, inflight, ok, errors, deadline, stop, allDone);
        } else {
            stop.set(true);
            allDone.countDown();
        }
    }
}

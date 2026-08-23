package com.chua.example.network.proxy;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * 异步压测端：Vert.x HttpClient 全异步 keep-alive 批处理，测真实吞吐上限。
 *
 * <p>启动目标服务后运行：{@code java ... AsyncHttpPerfMain <port> [durSec] [conc]}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AsyncHttpPerfExample {

    /** Main */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        int durSec = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        int conc = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        if (port == 0) {
            log.error("用法: java ... AsyncHttpPerfMain <port> [durSec] [conc]");
            return;
        }

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
        // 信号量限制 inflight 并发请求数
        Semaphore inflight = new Semaphore(conc);
        AtomicBoolean stop = new AtomicBoolean(false);
        long t0 = System.nanoTime();
        long deadline = System.nanoTime() + (long) durSec * 1_000_000_000L;
        CountDownLatch allDone = new CountDownLatch(1);

        // 递归发送：一个响应完成 → 若未到期限继续发下一个
        java.util.function.Consumer<Object> fire = new java.util.function.Consumer<>() {
            @Override
            /** Accept */
            public void accept(Object o) {
                sendOne(client, opts, inflight, ok, errors, deadline, stop, allDone);
            }
        };
        // 填充并发
        for (int i = 0; i < conc; i++) {
            try {
                inflight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            fire.accept(null);
        }
        boolean finished = allDone.await(durSec + 10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long total = ok.sum();
        long errs = errors.sum();
        double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
        log.info("AsyncPerf: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                total, elapsedMs, Math.round(rps), total, errs, finished);
        client.close();
        vertx.close();
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
        // 先判断是否已到期限
        if (System.nanoTime() > deadline) {
            stop.set(true);
            allDone.countDown();
            inflight.release();
            return;
        }
        client.request(opts).onSuccess(req -> req.send().onSuccess(resp -> {
            ok.increment();
            inflight.release();
            if (System.nanoTime() < deadline && !stop.get()) {
                try {
                    inflight.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                sendOne(client, opts, inflight, ok, errors, deadline, stop, allDone);
            } else {
                stop.set(true);
                allDone.countDown();
            }
        }).onFailure(err -> {
            errors.increment();
            inflight.release();
            if (System.nanoTime() < deadline && !stop.get()) {
                try {
                    inflight.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                sendOne(client, opts, inflight, ok, errors, deadline, stop, allDone);
            } else {
                stop.set(true);
                allDone.countDown();
            }
        })).onFailure(err -> {
            errors.increment();
            inflight.release();
            stop.set(true);
            allDone.countDown();
        });
    }
}

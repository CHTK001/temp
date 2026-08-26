package com.chua.example.network.proxy;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.vertx.support.server.VertxHttpProxyServer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * VertxHttpProxyServer 真实吞吐测试（独立 main）。
 *
 * <p>链路：Vert.x 后端 HTTP 回显 ← VertxHttpProxyServer（事件循环转发）← 并发 JDK HttpClient。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class VertxHttpProxyPerfExample {
    private VertxHttpProxyPerfExample() { }


    /** Main */
    public static void main(String[] args) throws Exception {
        int concurrency = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        int requestsPerConn = 200;

        Vertx vertx = null;
        HttpServer backend = null;
        VertxHttpProxyServer proxy = null;
        try {
            // 1. Vert.x 后端回显服务
            vertx = Vertx.vertx(new io.vertx.core.VertxOptions().setPreferNativeTransport(true));
            backend = vertx.createHttpServer(new HttpServerOptions().setHost("127.0.0.1").setPort(0));
            backend.requestHandler(req -> req.response().putHeader("Content-Type", "text/plain").end("http-backend-echo"));
            backend.listen().toCompletionStage().toCompletableFuture().join();
            int backendPort = backend.actualPort();

            // 2. VertxHttpProxyServer
            ServerSetting setting = ServerSetting.defaults();
            setting.setHost("127.0.0.1");
            setting.setPort(0);
            setting.setProtocol("vertx-http-proxy");
            proxy = new VertxHttpProxyServer(setting, new InetSocketAddress("127.0.0.1", backendPort));
            proxy.start();
            int proxyPort = proxy.getPort();
            log.info("VertxHttpProxyServer started on {}, backend={}", proxyPort, backendPort);

            // 3. 并发 HttpClient 压测
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch ready = new CountDownLatch(concurrency);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(concurrency);
            LongAdder ok = new LongAdder();
            LongAdder errors = new LongAdder();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int k = 0; k < requestsPerConn; k++) {
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create("http://127.0.0.1:" + proxyPort + "/echo"))
                                    .timeout(Duration.ofSeconds(10))
                                    .GET().build();
                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            if (resp.statusCode() == 200 && "http-backend-echo".equals(resp.body())) {
                                ok.increment();
                            } else {
                                errors.increment();
                            }
                        }
                    } catch (Exception e) {
                        errors.increment();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            long t0 = System.nanoTime();
            start.countDown();
            boolean finished = done.await(120, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            long total = ok.sum();
            long errs = errors.sum();
            double rps = elapsedMs > 0 ? total * 1000.0 / elapsedMs : 0;
            log.info("VertxHttpProxy 吞吐: {} 请求/{}ms = {} req/s, ok={}, errors={}, finished={}",
                    total, elapsedMs, Math.round(rps), total, errs, finished);
            pool.shutdownNow();
        } finally {
            if (proxy != null) {
                try {
                    proxy.stop();
                } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
                }
            }
            if (backend != null) {
                try {
                    backend.close();
                } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
                }
            }
            if (vertx != null) {
                try {
                    vertx.close();
                } catch (Exception ignored) {
            log.warn("Caught: {}", ignored.getMessage());
        
                }
            }
        }
    }
}

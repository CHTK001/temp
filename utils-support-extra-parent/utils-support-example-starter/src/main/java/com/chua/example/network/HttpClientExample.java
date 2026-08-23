package com.chua.example.network;

import com.chua.common.support.network.client.ClientRequest;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClient;
import com.chua.common.support.network.client.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * HTTP 客户端示例：同步 + 响应式（Mono）两种方式对比。
 *
 * <pre>{@code
 *   java HttpClientExample
 *   java HttpClientExample sync "https://httpbin.org/get"
 *   java HttpClientExample async "https://httpbin.org/get"
 *   java HttpClientExample batch
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class HttpClientExample {

    private HttpClientExample() {
    }

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : null;
        String url = args.length > 1 ? args[1] : "https://httpbin.org/get";

        switch (cmd) {
            case "sync":
                exampleSync(url);
                break;
            case "async":
                exampleAsync(url);
                break;
            case "batch":
                exampleBatch();
                break;
            default:
                exampleSync(url);
                exampleAsync(url);
                exampleBatch();
                break;
        }
    }

    /** 同步 GET */
    private static void exampleSync(String url) throws Exception {
        log.info("===== [sync] GET {} =====", url);
        HttpClient client = HttpClientFactory.getClient();
        long t0 = System.currentTimeMillis();
        ClientResponse resp = client.get(url);
        long dt = System.currentTimeMillis() - t0;
        log.info("  status={} bodyLen={} cost={}ms", resp.getStatusCode(), resp.getBodyString().length(), dt);
    }

    /** 响应式异步 GET（Mono，NIO 非阻塞） */
    private static void exampleAsync(String url) {
        log.info("===== [reactive] GET {} =====", url);
        HttpClient client = HttpClientFactory.getClient();
        long t0 = System.currentTimeMillis();
        Mono<ClientResponse> mono = client.executeAsync(ClientRequest.of(url))
                .timeout(Duration.ofSeconds(10));
        // block() 仅用于示例演示，生产环境应使用 subscribe()
        ClientResponse resp = mono.block();
        long dt = System.currentTimeMillis() - t0;
        log.info("  status={} bodyLen={} cost={}ms", resp.getStatusCode(), resp.getBodyString().length(), dt);
    }

    /** 并发批量请求（NIO 并行，按完成顺序） */
    private static void exampleBatch() {
        log.info("===== [reactive] 并发批量 GET =====");
        HttpClient client = HttpClientFactory.getClient();
        List<String> urls = List.of(
                "https://httpbin.org/get",
                "https://httpbin.org/headers",
                "https://httpbin.org/ip"
        );
        long t0 = System.currentTimeMillis();
        Mono<ClientResponse>[] monos = urls.stream()
                .map(url -> client.executeAsync(ClientRequest.of(url)).timeout(Duration.ofSeconds(10)))
                .toArray(Mono[]::new);
        Mono.zip(monos)
                .doOnNext(tuple -> log.info("  全部完成，耗时={}ms", System.currentTimeMillis() - t0))
                .doOnError(err -> log.warn("  请求失败: {}", err.getMessage()))
                .block();
    }
}

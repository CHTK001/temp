package com.chua.example.onnx;

import com.chua.common.support.network.client.ClientRequest;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClient;
import com.chua.common.support.network.client.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * HTTP 客户端响应式能力示例。
 *
 * <pre>{@code
 *   java HttpClientExample
 *   java HttpClientExample get "https://httpbin.org/get"
 *   java HttpClientExample async "https://httpbin.org/get"
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class HttpClientExample extends ExampleBase {

    private HttpClientExample() {
    }

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : null;
        String url = args.length > 1 ? args[1] : null;

        switch (cmd) {
            case "get":
                exampleGet(url);
                break;
            case "async":
                exampleAsync(url);
                break;
            default:
                log.info("HTTP 客户端示例");
                log.info("==================");
                exampleGet(url != null ? url : "https://httpbin.org/get");
                exampleAsync(url != null ? url : "https://httpbin.org/get");
                exampleBatch();
                break;
        }
    }

    /** 同步 GET */
    private static void exampleGet(String url) throws Exception {
        log.info("[sync] GET {}", url);
        HttpClient client = HttpClientFactory.getClient();
        long t0 = System.currentTimeMillis();
        ClientResponse resp = client.get(url);
        log.info("  status={} bodyLen={} cost={}ms",
                resp.getStatusCode(), resp.getBodyString().length(), System.currentTimeMillis() - t0);
    }

    /** 响应式异步 GET（Mono） */
    private static void exampleAsync(String url) {
        log.info("[reactive] GET {}", url);
        HttpClient client = HttpClientFactory.getClient();
        long t0 = System.currentTimeMillis();
        Mono<ClientResponse> mono = client.executeAsync(ClientRequest.of(url))
                .timeout(Duration.ofSeconds(10));
        // block 仅用于示例演示，生产环境应使用 subscribe()
        ClientResponse resp = mono.block();
        log.info("  status={} bodyLen={} cost={}ms",
                resp.getStatusCode(), resp.getBodyString().length(), System.currentTimeMillis() - t0);
    }

    /** 并发批量请求 */
    private static void exampleBatch() {
        log.info("[reactive] 并发批量请求");
        HttpClient client = HttpClientFactory.getClient();
        long t0 = System.currentTimeMillis();
        List<String> urls = List.of(
                "https://httpbin.org/get",
                "https://httpbin.org/headers",
                "https://httpbin.org/ip"
        );
        // 并发执行，按完成顺序发出
        client.executeAsync(ClientRequest.of(urls.get(0)))
                .zipWith(client.executeAsync(ClientRequest.of(urls.get(1))))
                .zipWith(client.executeAsync(ClientRequest.of(urls.get(2))))
                .doOnNext(tuple -> log.info("  并发请求完成"))
                .doOnComplete(() -> log.info("  总计耗时={}ms", System.currentTimeMillis() - t0))
                .subscribe();
    }
}

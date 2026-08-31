package com.chua.example.network.http;

import com.chua.common.support.network.client.Callback;
import com.chua.common.support.network.client.ClientRequest;
import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.network.client.HttpClient;
import com.chua.common.support.network.client.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 响应式 HTTP 客户端示例（executeAsync 返回 Mono）。
 *
 * <p>改写自 ReactiveHttpClientTest，覆盖三个场景：</p>
 * <ol>
 *   <li>executeAsync 返回非 null 的 {@link Mono}</li>
 *   <li>同步 get/post 访问拒绝端点抛异常（同步路径不受响应式改造影响）</li>
 *   <li>回调式 executeAsync 在连接失败时触发 onError</li>
 * </ol>
 *
 * <p>目标端点固定为 {@code http://localhost:1/}（保留端口，无监听必然连接拒绝，
 * 不涉及本机绑定，因此无需 --port 参数）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class ReactiveHttpClientExample {

    /** 私有构造，防止实例化 */
    private ReactiveHttpClientExample() { }

    /**
     * 必然连接拒绝的目标地址（端口 1 无服务监听）
     */
    private static final String REFUSED_PING_URL = "http://localhost:1/_ping";

    /**
     * 连接拒绝探测根地址
     */
    private static final String REFUSED_ROOT_URL = "http://localhost:1/";

    /**
     * 回调等待超时（毫秒）
     */
    private static final long CALLBACK_TIMEOUT_MILLIS = 10_000L;

    /**
     * Main 入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= testExecuteAsyncReturnsMono();
        passed &= testGetAndPostStillWork();
        passed &= testCallbackStyleStillWorks();
        if (!passed) {
            log.info("[FAIL] ReactiveHttpClient 存在失败场景");
            System.exit(1);
        }
        log.info("[PASS] ReactiveHttpClient 全部场景通过");
        System.exit(0);
    }

    /**
     * 场景一：executeAsync 应返回非 null 的 Mono。
     *
     * @return 通过返回 true
     */
    private static boolean testExecuteAsyncReturnsMono() {
        try {
            HttpClient client = HttpClientFactory.getClient();
            Mono<ClientResponse> mono = client.executeAsync(ClientRequest.of(REFUSED_PING_URL));
            if (mono == null) {
                log.info("[FAIL] executeAsync 应返回非 null Mono");
                return false;
            }
            log.info("[PASS] executeAsync 返回 Mono");
            return true;
        } catch (Exception e) {
            log.info("[FAIL] executeAsync 异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 场景二：同步 get/post 访问拒绝端点应抛异常。
     *
     * @return 通过返回 true
     */
    private static boolean testGetAndPostStillWork() {
        boolean getThrew = false;
        boolean postThrew = false;
        try {
            HttpClient client = HttpClientFactory.getClient();
            try {
                client.get(REFUSED_ROOT_URL);
            } catch (Exception e) {
                getThrew = true;
            }
            try {
                client.post(REFUSED_ROOT_URL, "body");
            } catch (Exception e) {
                postThrew = true;
            }
        } catch (Exception e) {
            log.info("[FAIL] 获取客户端实例异常: {}", e.getMessage());
            return false;
        }
        if (!getThrew || !postThrew) {
            log.info("[FAIL] 同步 get/post 应对拒绝端点抛异常(getThrew={} postThrew={})", getThrew, postThrew);
            return false;
        }
        log.info("[PASS] 同步 get/post 拒绝路径抛异常");
        return true;
    }

    /**
     * 场景三：回调式 executeAsync 在连接失败时应触发 onError。
     *
     * @return 通过返回 true
     */
    private static boolean testCallbackStyleStillWorks() {
        CountDownLatch done = new CountDownLatch(1);
        boolean[] errored = {false};
        try {
            HttpClient client = HttpClientFactory.getClient();
            client.executeAsync(ClientRequest.of(REFUSED_ROOT_URL),
                    new Callback<ClientResponse>() {
                        @Override
                        public void onSuccess(ClientResponse resp) {
                            done.countDown();
                        }

                        @Override
                        public void onError(Throwable err) {
                            errored[0] = true;
                            done.countDown();
                        }
                    });
            boolean finished = done.await(CALLBACK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!finished) {
                log.info("[FAIL] 回调超时未触发(onSuccess/onError 均未回调)");
                return false;
            }
            if (!errored[0]) {
                log.info("[FAIL] 拒绝端点应触发 onError 回调");
                return false;
            }
            log.info("[PASS] 回调式 executeAsync 触发 onError");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("[FAIL] 回调等待被中断");
            return false;
        } catch (Exception e) {
            log.info("[FAIL] 回调场景异常: {}", e.getMessage());
            return false;
        }
    }
}

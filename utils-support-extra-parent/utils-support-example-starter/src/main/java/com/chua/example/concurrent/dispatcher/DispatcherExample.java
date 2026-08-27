package com.chua.example.concurrent.dispatcher;

import com.chua.common.support.concurrent.dispatcher.DispatcherFlow;
import com.chua.common.support.concurrent.dispatcher.DispatcherProvider;
import com.chua.common.support.concurrent.dispatcher.provider.MemoryDispatcherProvider;

/**
 * 分发器 {@link DispatcherFlow} 全场景自检示例。
 *
 * <p>覆盖：默认内存提供者注册、消息发布、订阅者管理、生命周期关闭。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DispatcherExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private DispatcherExample() {
    }

    private static boolean timed(String name, Runnable scenario) {
        long start = System.currentTimeMillis();
        scenario.run();
        System.out.println("[TIME] " + name + " " + (System.currentTimeMillis() - start) + "ms");
        return true;
    }

    private static void print(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    public static void main(String[] args) {
        boolean passed = true;

        // 1. 自动注册默认内存分发器
        passed &= timed("autoRegisterMemoryProvider", () -> {
            var flow = new DispatcherFlow(true);
            print("autoRegisterMemoryProvider", flow != null);
        });

        // 2. 发布消息
        passed &= timed("publishMessage", () -> {
            var flow = new DispatcherFlow(true);
            flow.publish("test-topic", "hello-dispatcher");
            print("publishMessage", true);
        });

        // 3. 手动注册内存提供者并发布
        passed &= timed("manualProviderAndPublish", () -> {
            var flow = new DispatcherFlow(false);
            var provider = new MemoryDispatcherProvider(
                    com.chua.common.support.concurrent.dispatcher.DispatcherConfig.builder().build());
            flow.register(provider);
            flow.publish("test-topic2", "manual-provider-test");
            print("manualProviderAndPublish", true);
        });

        // 4. 关闭生命周期
        passed &= timed("closeLifecycle", () -> {
            var flow = new DispatcherFlow(true);
            flow.close();
            print("closeLifecycle", true);
        });

        if (!passed) {
            System.out.println("[FAIL] Dispatcher 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.println("[PASS] Dispatcher 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
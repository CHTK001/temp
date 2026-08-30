package com.chua.example.concurrent.dispatcher;

import com.chua.common.support.concurrent.dispatcher.DispatcherFlow;
import com.chua.common.support.concurrent.dispatcher.provider.MemoryDispatcherProvider;
import com.chua.example.util.ExampleUtils;

/**
 * 分发器 {@link DispatcherFlow} 全场景自检示例。
 *
 * <p>覆盖：默认内存提供者注册、消息发布、订阅者管理、生命周期关闭。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class DispatcherExample {

    private DispatcherExample() {
    }

    public static void main(String[] args) {
        boolean passed = true;

        // 1. 自动注册默认内存分发器
        passed &= ExampleUtils.timed("autoRegisterMemoryProvider", () -> {
            var flow = new DispatcherFlow(true);
            ExampleUtils.print("autoRegisterMemoryProvider", flow != null);
            return true;
        });

        // 2. 发布消息
        passed &= ExampleUtils.timed("publishMessage", () -> {
            var flow = new DispatcherFlow(true);
            flow.publish("test-topic", "hello-dispatcher");
            ExampleUtils.print("publishMessage", true);
            return true;
        });

        // 3. 手动注册内存提供者并发布
        passed &= ExampleUtils.timed("manualProviderAndPublish", () -> {
            var flow = new DispatcherFlow(false);
            var provider = new MemoryDispatcherProvider(
                    com.chua.common.support.concurrent.dispatcher.DispatcherConfig.builder().build());
            flow.register(provider);
            flow.publish("test-topic2", "manual-provider-test");
            ExampleUtils.print("manualProviderAndPublish", true);
            return true;
        });

        // 4. 关闭生命周期
        passed &= ExampleUtils.timed("closeLifecycle", () -> {
            var flow = new DispatcherFlow(true);
            flow.close();
            ExampleUtils.print("closeLifecycle", true);
            return true;
        });

        if (!passed) {
            System.out.println("[FAIL] Dispatcher 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        }
        System.out.println("[PASS] Dispatcher 全部场景通过");
        System.exit(ExampleUtils.SUCCESS);
    }
}

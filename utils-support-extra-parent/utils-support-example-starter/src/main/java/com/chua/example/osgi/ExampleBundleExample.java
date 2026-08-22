package com.chua.osgi.support.example;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.osgi.BundleContext;
import com.chua.common.support.osgi.BundleApplication;
import com.chua.common.support.spi.annotations.Spi;

/**
 * OSGI Bundle 应用示例，演示如何在 Bundle 中注册服务。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("example-bundle")
@Slf4j
public class ExampleBundleExample implements BundleApplication {

    /** 服务名称 */
    private static final String serviceName = "example-service";

    @Override
    /** OnBundle开始 */
    public void onBundleStart(BundleContext context) {
        context.registerService(ExampleService.class, new ExampleServiceImpl());
        log.info("[ExampleBundle] Bundle started, registered service: " + serviceName);
    }

    @Override
    /** OnBundle停止 */
    public void onBundleStop(BundleContext context) {
        context.unregisterService(ExampleService.class, new ExampleServiceImpl());
        log.info("[ExampleBundle] Bundle stopped");
    }

    public interface ExampleService {
        String getName();
        String getMessage(String input);
    }

    public static class ExampleServiceImpl implements ExampleService {
        @Override
        /** 获取Name */
        public String getName() {
            return serviceName;
        }

        @Override
        /** 获取Message */
        public String getMessage(String input) {
            return "Hello from OSGi example: " + input;
        }
    }

    /**
     * 独立入口：演示 OSGi Bundle 注册。
     */
    public static void main(String[] args) {
        log.info("[ExampleBundleExample] OSGi bundle example (requires OSGi runtime)");
        log.info("Usage: java ExampleBundleExample");
    }
}

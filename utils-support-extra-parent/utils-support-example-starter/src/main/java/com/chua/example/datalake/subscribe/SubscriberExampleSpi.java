package com.chua.example.datalake.subscribe;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * SubscriberExample SPI 适配器 — 调用 {@link SubscriberExample} 的公开 test 方法。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class SubscriberExampleSpi implements Example {

    @Override
    public String name() {
        return "subscriber";
    }

    @Override
    public String module() {
        return "datalake";
    }

    @Override
    public String description() {
        return "Subscriber 自检（push 推送 / reset 偏移重置）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        boolean passed = true;
        passed &= SubscriberExample.testPush();
        passed &= SubscriberExample.testReset();
        return passed;
    }
}

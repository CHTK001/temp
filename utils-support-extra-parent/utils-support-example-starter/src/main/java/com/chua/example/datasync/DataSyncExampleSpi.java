package com.chua.example.datasync;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * DataSyncExample SPI 适配器 — 转发到 {@link DataSyncExample#runTest(String)}。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DataSyncExampleSpi implements Example {

    @Override
    public String name() {
        return "data-sync";
    }

    @Override
    public String module() {
        return "datasync";
    }

    @Override
    public String description() {
        return "DataSync 数据源/调度集成自检（basic / repeat / direct / all）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        return new DataSyncExample().runTest(type);
    }
}

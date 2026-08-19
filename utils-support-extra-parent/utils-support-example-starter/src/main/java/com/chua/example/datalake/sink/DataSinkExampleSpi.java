package com.chua.example.datalake.sink;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * DataSinkExample SPI 适配器 — 调用 {@link DataSinkExample} 的公开 test 方法。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DataSinkExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "data-sink";
    }

    @Override
    /** Module */
    public String module() {
        return "datalake";
    }

    @Override
    /** Description */
    public String description() {
        return "DataSink 自检（access / store / write）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        boolean passed = true;
        passed &= DataSinkExample.testAccessSinks();
        passed &= DataSinkExample.testStoreSinks();
        passed &= DataSinkExample.testWrite();
        return passed;
    }
}

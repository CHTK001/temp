package com.chua.example.datalake.server;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * DatalakeServerExample SPI 适配器 — 调用 {@link DatalakeServerExample#testBasicLifecycle()}。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DatalakeServerExampleSpi implements Example {

    @Override
    public String name() {
        return "datalake-server";
    }

    @Override
    public String module() {
        return "datalake";
    }

    @Override
    public String description() {
        return "DatalakeServer 生命周期自检（启动 / 接收数据 / 关闭）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        return DatalakeServerExample.testBasicLifecycle();
    }
}

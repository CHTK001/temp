package com.chua.example.tui;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * TuiDashboardExample SPI 适配器 — 转发到 {@link TuiDashboardExample#runTest(String)}。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class TuiDashboardExampleSpi implements Example {

    @Override
    public String name() {
        return "tui-dashboard";
    }

    @Override
    public String module() {
        return "tui";
    }

    @Override
    public String description() {
        return "TUI Dashboard 自检（layout / widget / builder / dashboard / colspan 等）";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "layout");
        return new TuiDashboardExample().runTest(type);
    }
}

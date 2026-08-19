package com.chua.example.datalake.integrated;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * DatalakeIntegratedExample SPI 适配器 — 转发到 {@link DatalakeIntegratedExample#runTest(int)}。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DatalakeIntegratedExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "datalake-integrated";
    }

    @Override
    /** Module */
    public String module() {
        return "datalake";
    }

    @Override
    /** Description */
    public String description() {
        return "Datalake + DataSync 端到端集成自检（可指定运行时长秒数）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        int duration = Integer.parseInt(args.getOrDefault("duration", "3"));
        return new DatalakeIntegratedExample().runTest(duration);
    }
}

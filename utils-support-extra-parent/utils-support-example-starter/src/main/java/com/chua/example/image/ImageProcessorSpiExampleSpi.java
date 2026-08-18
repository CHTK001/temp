package com.chua.example.image;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * ImageProcessorSpiExample SPI 适配器 — 通过 {@link com.chua.example.runner.ExampleRunner} 调度。
 *
 * <p>转发到 {@link ImageProcessorSpiExample#runTest(String)} 保持行为一致。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class ImageProcessorSpiExampleSpi implements Example {

    @Override
    public String name() {
        return "image-processor-spi";
    }

    @Override
    public String module() {
        return "common";
    }

    @Override
    public String description() {
        return "ImageProcessor SPI 发现/优先级/自动降级/子类注册 能力自检";
    }

    @Override
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        return new ImageProcessorSpiExample().runTest(type);
    }
}
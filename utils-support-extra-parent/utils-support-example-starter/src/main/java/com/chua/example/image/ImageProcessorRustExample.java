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
public class ImageProcessorRustExample implements Example {

    @Override
    /** Name */
    public String name() {
        return "image-processor-spi";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "ImageProcessor SPI 发现/优先级/自动降级 (rust/opencv/jdk) + 13种图像操作能力自检";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all");
        String input = args.getOrDefault("input", "D:/images/test_1.jpg");
        String output = args.getOrDefault("output", "D:/images/utils");
        return new ImageProcessorSpiExample().runTest(type, input, output);
    }
}
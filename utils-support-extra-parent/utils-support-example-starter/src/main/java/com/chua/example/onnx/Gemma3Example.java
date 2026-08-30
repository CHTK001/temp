package com.chua.example.onnx;

import com.chua.example.spi.Example;
import java.util.Map;

/**
 * Gemma3 ONNX 示例（存根：deeplearning-onnx-starter 未构建）。
 */
public class Gemma3Example implements Example {
    @Override
    public String name() { return "gemma3"; }
    @Override
    public String module() { return "deeplearning"; }
    @Override
    public String description() { return "Gemma3 ONNX 推理示例"; }
    @Override
    public boolean run(Map<String, String> args) {
        System.out.println("[SKIP] Gemma3Example - deeplearning-onnx-starter not built");
        return true;
    }
}

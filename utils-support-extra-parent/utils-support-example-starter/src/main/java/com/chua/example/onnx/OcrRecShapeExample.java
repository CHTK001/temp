package com.chua.example.onnx;

import lombok.extern.slf4j.Slf4j;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.chua.common.support.utils.NativeLoader;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询 PP-OCRv6 rec ONNX 输入/输出维度是否动态。
 *@author CH`n *
 * @since 4.0.0.42
 */
public final class OcrRecShapeExample {

    /** 创建 OcrRecShapeDiag 实例 */
    private OcrRecShapeDiag() {
    }

    /** Main */
    public static void main(String[] args) throws Exception {
        Path tmpDir = Files.createTempDirectory("rec-shape-");
        tmpDir.toFile().deleteOnExit();
        Path modelDir = tmpDir.resolve("rec");
        Files.createDirectories(modelDir);
        NativeLoader.of("paddleocrv6-rec-shape")
                .from(OcrRecShapeDiag.class.getClassLoader())
                .basePath("ocr/PP-OCRv6/medium/rec_infer/")
                .toTarget(modelDir)
                .glob("*")
                .withMd5(true)
                .extractOnly(true)
                .load();

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        try (OrtSession session = env.createSession(modelDir.resolve("inference.onnx").toString(), opts)) {
            log.info("== inputs ==");
            for (var e : session.getInputInfo().entrySet()) {
                log.info(e.getKey() + " -> " + e.getValue());
            }
            log.info("== outputs ==");
            for (var e : session.getOutputInfo().entrySet()) {
                log.info(e.getKey() + " -> " + e.getValue());
            }
        }
    }
}
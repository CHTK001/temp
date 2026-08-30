package com.chua.example.lang.protobuf;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;
import com.chua.example.util.ExampleUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Protobuf 序列化驱动型示例 — 同名 SPI {@link ProtobufSerializerExampleSpi} 的独立 main 入口。
 *
 * <p>解析 --key=value 与 --key value 两种形式的命令行参数进 LinkedHashMap，
 * 经 {@code ServiceProvider.of(Example.class).collect()} 发现全部 Example 实现后按
 * {@code name()} 命中 protobuf-serializer，真实执行基本类型 / 嵌套对象 / 集合 /
 * null 输入 / Serialization 接口 / 压缩比六组自检；关键输出以 [PASS]/[FAIL] 前缀呈现，
 * 任一场景失败即以退出码 1 终止。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java ProtobufSerializerExample
 *   java ProtobufSerializerExample --mode=selfcheck
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class ProtobufSerializerExample {

    private ProtobufSerializerExample() {
    }

    /**
     * 独立入口：解析参数 → SPI 发现 protobuf-serializer → 执行空参与带参两轮自检。
     *
     * @param args 支持 --key=value 与 --key value（SPI 内部未使用，仅验证容错）
     */
    public static void main(String[] args) {
        Map<String, String> params = ExampleUtils.parseArgs(args);
        Example example = discover("protobuf-serializer");
        if (example == null || !"protobuf".equals(example.module())) {
            log.info("[FAIL] spi-discovery: 应发现 protobuf-serializer 且 module=protobuf");
            System.exit(1);
        }
        log.info("[PASS] spi-discovery: " + example.getClass().getSimpleName()
                + " module=" + example.module());
        if (!example.run(new LinkedHashMap<>())) {
            log.info("[FAIL] run-empty: 空参数自检未通过");
            System.exit(1);
        }
        log.info("[PASS] run-empty: 空参数自检通过");
        if (!example.run(params)) {
            log.info("[FAIL] run-args: 携带自定义参数自检未通过 params=" + params);
            System.exit(1);
        }
        log.info("[PASS] run-args: 携带参数自检通过 params=" + params);
        log.info("[PASS] ProtobufSerializerExample 全部场景执行完成");
    }

    /**
     * 经 ServiceProvider 发现全部 Example 实现并按 SPI name() 命中目标。
     *
     * @param name 目标示例名称
     * @return 命中的实现，未发现返回 null
     */
    private static Example discover(String name) {
        for (Example candidate : ServiceProvider.of(Example.class).collect()) {
            if (name.equals(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

}

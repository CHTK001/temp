package com.chua.example.spi;

import java.util.Map;

/**
 * 示例 SPI 接口 — 所有 Example 实现该接口，通过 SPI 自动注册与发现。
 *
 * <p>统一调度入口 {@code com.chua.example.runner.ExampleRunner} 根据
 * {@link #name()} 路由到对应实现；每个实现应专注于一个 starter 的核心能力演示。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface Example {

    /**
     * 示例唯一名称（小写，连字符分隔），用于命令行 --example=xxx 路由。
     *
     * @return 示例名称
     */
    String name();

    /**
     * 示例展示的 starter / 模块名（用于列表展示）。
     *
     * @return 模块名
     */
    String module();

    /**
     * 示例简短描述（一行）。
     *
     * @return 描述
     */
    String description();

    /**
     * 运行示例自检。
     *
     * @param args 命令行参数（实现可自行解析）
     * @return 全部测试通过返回 true
     */
    boolean run(Map<String, String> args);
}

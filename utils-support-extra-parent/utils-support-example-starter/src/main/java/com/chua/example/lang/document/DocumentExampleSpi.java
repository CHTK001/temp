package com.chua.example.lang.document;

import com.chua.example.spi.Example;

import java.util.Map;

/**
 * DocumentExample SPI 适配器 — 转发到 {@link DocumentExample#runTest()}。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class DocumentExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "document";
    }

    @Override
    /** Module */
    public String module() {
        return "common";
    }

    @Override
    /** Description */
    public String description() {
        return "文档解析与导出自检（database / 自定义模板 / 渲染）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        return new DocumentExample().runTest();
    }
}

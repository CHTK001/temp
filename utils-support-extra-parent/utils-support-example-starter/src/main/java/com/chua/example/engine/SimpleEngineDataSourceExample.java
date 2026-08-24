package com.chua.example.engine;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;

/**
 * 简化的 EngineDataSource 实现，构造时不连接任何后端，仅用于单元测试与示例代码。
 *
 * @author CH
 * @since 4.0.0
  *
 * <p>SPI 实现载体：SPI 引擎实现载体，由宿主 Example 按类型加载，无独立 main 入口。</p>
 */
public class SimpleEngineDataSourceExample implements EngineDataSource<Object> {

    /** 名称 */
    private final String name;
    /** URL */
    private final String url;
    /** Username */
    private final String username;
    /** 密码 */
    private final String password;
    /** 来源 */
    private Object source;

    /**
     * 创建 SimpleEngineDataSourceExample 实例
     * @param url url
     */
    public SimpleEngineDataSourceExample(String url) {
        this("default", url, null, null);
    }

    /**
     * 创建 SimpleEngineDataSourceExample 实例
     * @param name name
     * @param String String
     * @param String String
     * @param String String
     */
    public SimpleEngineDataSourceExample(String name, String url, String username, String password) {
        this.name = name;
        this.url = url;
        this.username = username;
        this.password = password;
        this.source = url;
    }

    @Override
    /** Name */
    public String name() {
        return name;
    }

    @Override
    /** Url */
    public String url() {
        return url;
    }

    @Override
    /** Username */
    public String username() {
        return username;
    }

    @Override
    /** Password */
    public String password() {
        return password;
    }

    @Override
    /** 获取Source */
    public Object getSource() {
        return source;
    }

    @Override
    @SuppressWarnings("unchecked")
    /** 设置Source */
    public EngineDataSource<Object> setSource(Object source) {
        this.source = source;
        return this;
    }

    @Override
    /** 获取Dialect */
    public Dialect getDialect() {
        return null;
    }

    @Override
    /** 设置Dialect */
    public EngineDataSource<Object> setDialect(Dialect dialect) {
        return this;
    }
}

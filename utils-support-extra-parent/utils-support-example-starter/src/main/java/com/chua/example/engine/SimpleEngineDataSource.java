package com.chua.example.engine;

import com.chua.common.support.lang.datasource.dialect.Dialect;
import com.chua.common.support.lang.datasource.engine.EngineDataSource;

public class SimpleEngineDataSource implements EngineDataSource<Object> {

    private final String name;
    private final String url;
    private final String username;
    private final String password;
    private Object source;

    public SimpleEngineDataSource(String url) {
        this("default", url, null, null);
    }

    public SimpleEngineDataSource(String name, String url, String username, String password) {
        this.name = name;
        this.url = url;
        this.username = username;
        this.password = password;
        this.source = url;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    @SuppressWarnings("unchecked")
    public EngineDataSource<Object> setSource(Object source) {
        this.source = source;
        return this;
    }

    @Override
    public Dialect getDialect() {
        return null;
    }

    @Override
    public EngineDataSource<Object> setDialect(Dialect dialect) {
        return this;
    }
}
package com.chua.example.osgi;

/**
 * OSGi 集成测试服务实现。
 *@author CH`n *
 * @since 4.0.0.42
 */
public final class HelloServiceImplExample implements HelloService {
    /** Greeting */
    private final String greeting;

    /** 创建 HelloServiceExampleImplExample 实例 */
    public HelloServiceExampleImplExample() {
        this("default");
    }

    /**
     * 创建 HelloServiceExampleImplExample 实例
     * @param greeting greeting
     */
    public HelloServiceExampleImplExample(String greeting) {
        this.greeting = greeting;
    }

    /** Greet */`n

    @Override`n    public String greet() {
        return greeting;
    }
}

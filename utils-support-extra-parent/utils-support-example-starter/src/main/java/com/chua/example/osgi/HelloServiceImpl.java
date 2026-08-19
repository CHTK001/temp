package com.chua.example.osgi;

/**
 * OSGi 集成测试服务实现。
 *
 * @since 4.0.0.42
 */
public final class HelloServiceImpl implements HelloService {
    /** Greeting */
    private final String greeting;

    /** 创建 HelloServiceImpl 实例 */
    public HelloServiceImpl() {
        this("default");
    }

    /**
     * 创建 HelloServiceImpl 实例
     * @param greeting greeting
     */
    public HelloServiceImpl(String greeting) {
        this.greeting = greeting;
    }

    @Override
    /** Greet */
    public String greet() {
        return greeting;
    }
}

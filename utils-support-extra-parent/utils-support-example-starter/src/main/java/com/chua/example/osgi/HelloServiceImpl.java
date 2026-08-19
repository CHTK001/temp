package com.chua.example.osgi;

/**
 * OSGi 集成测试服务实现。
 *
 * @since 4.0.0.42
 */
public final class HelloServiceImpl implements HelloService {
    /** Greeting */
    private final String greeting;

    public HelloServiceImpl() {
        this("default");
    }

    public HelloServiceImpl(String greeting) {
        this.greeting = greeting;
    }

    @Override
    public String greet() {
        return greeting;
    }
}

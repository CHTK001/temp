package com.chua.example.osgi;

/**
 * OSGi 集成测试服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class HelloServiceImpl implements HelloService {
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

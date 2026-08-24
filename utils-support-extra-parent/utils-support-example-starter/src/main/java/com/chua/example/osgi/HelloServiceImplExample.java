package com.chua.example.osgi;

/**
 * OSGi 集成测试服务实现。
 *
 * @author CH
 * @since 4.0.0.42
  *
 * <p>SPI 实现载体：由 OSGi 容器注册为服务实现，无独立 main 入口。</p>
 */
public final class HelloServiceImplExample implements HelloServiceExample {
    /** Greeting */
    private final String greeting;

    /** 创建 HelloServiceImplExample 实例 */
    public HelloServiceImplExample() {
        this("default");
    }

    /**
     * 创建 HelloServiceImplExample 实例
     *
     * @param greeting greeting
     */
    public HelloServiceImplExample(String greeting) {
        this.greeting = greeting;
    }

    /** Greet */
    @Override
    public String greet() {
        return greeting;
    }
}

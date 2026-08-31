package com.chua.example.osgi;

import lombok.extern.slf4j.Slf4j;

/**
 * OSGi 集成测试服务实现。
 *
 * @author CH
 * @since 4.0.0.42
  *
 * <p>SPI 实现载体：由 OSGi 容器注册为服务实现，无独立 main 入口。</p>
 */
@Slf4j
public final class HelloServiceImplExample implements HelloServiceExample {

    /** 私有构造，防止实例化 */
    private HelloServiceImplExample() { }
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
    /**
     * 自检入口：验证问候返回值。
     *
     * @param args args[0] 可选问候语
     */
    public static void main(String[] args) {
        HelloServiceImplExample svc = new HelloServiceImplExample(
                args.length > 0 ? args[0] : "default");
        boolean ok = svc.greet() != null && !svc.greet().isEmpty();
        log.info("greet=" + svc.greet() + " -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}

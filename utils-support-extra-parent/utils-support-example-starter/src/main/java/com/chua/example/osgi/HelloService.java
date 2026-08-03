package com.chua.example.osgi;

/**
 * OSGi 集成测试服务接口（顶层接口，便于按 FQN 通过 Class.forName 反射查找）。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface HelloService {
    String greet();
}

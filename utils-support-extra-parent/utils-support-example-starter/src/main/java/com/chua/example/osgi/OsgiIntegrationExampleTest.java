package com.chua.example.osgi;

import com.chua.common.support.objects.DefaultObjectContext;
import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.objects.ObjectContextConfig;
import com.chua.common.support.objects.definition.BeanDefinition;
import com.chua.common.support.objects.register.BeanDefinitionRegister;
import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.spi.annotations.SpiIgnore;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OSGi 容器集成单元测试入口。
 *
 * <p>通过 {@code main} 方法以断言形式覆盖重构后的核心行为：</p>
 * <ul>
 *   <li>{@link OsgiBeanDefinitionRegister} 被 {@link SpiIgnore} 标注，不会通过 SPI 自动加入</li>
 *   <li>{@code setOsgiLauncher(OsgiLauncher)} 注入后，所有查询委派给注入的 launcher</li>
 *   <li>{@code ObjectContext.registerBean(BeanDefinitionRegister)} 可编程式挂接 OSGi 注册器</li>
 *   <li>挂接后，{@link ObjectContext#getBeanOfType(Class)} 可实时查询 OSGi 服务</li>
 *   <li>重复注册同名 Register 应被忽略</li>
 * </ul>
 *
 * <p>运行方式：</p>
 * <pre>
 * cd utils-support-parent-starter/utils-support-extra-parent/utils-support-example-starter
 * mvn -q exec:java -Dexec.mainClass=com.chua.example.osgi.OsgiIntegrationExampleTest
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OsgiIntegrationExampleTest {

    /**
     * 测试入口。
     *
     * @param args 命令行参数（忽略）
     */
    public static void main(String[] args) {
        Result r = new Result("OsgiIntegration");

        r.run("OsgiBeanDefinitionRegister_isAnnotatedWithSpiIgnore",
                OsgiIntegrationExampleTest::t1_registerIsSpiIgnored);
        r.run("OsgiBeanDefinitionRegister_registerAndUnregisterThrowUnsupported",
                OsgiIntegrationExampleTest::t2_registerThrowsUnsupported);
        r.run("OsgiBeanDefinitionRegister_queryWithoutLauncherReturnsEmpty",
                OsgiIntegrationExampleTest::t3_noLauncherReturnsEmpty);
        r.run("OsgiBeanDefinitionRegister_queryWithLauncherDelegatesCorrectly",
                OsgiIntegrationExampleTest::t4_injectedLauncherDelegates);
        r.run("OsgiBeanDefinitionRegister_inactiveLauncherReturnsEmpty",
                OsgiIntegrationExampleTest::t5_inactiveLauncherReturnsEmpty);
        r.run("ObjectContext_registerBean_acceptsRegister",
                OsgiIntegrationExampleTest::t6_objectContextAcceptsRegister);
        r.run("ObjectContext_registerBean_returnsBeanThroughOsgiRegister",
                OsgiIntegrationExampleTest::t7_objectContextLooksUpOsgiService);
        r.run("ObjectContext_registerBean_duplicateIgnored",
                OsgiIntegrationExampleTest::t8_duplicateRegisterIgnored);
        r.run("OsgiBeanDefinitionRegister_closeDisablesQueries",
                OsgiIntegrationExampleTest::t9_closeDisablesQueries);

        r.summary();
        if (!r.failures.isEmpty()) {
            System.err.println("FAILURES:");
            for (String f : r.failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // ==================== 测试用例 ====================

    /**
     * 1. OsgiBeanDefinitionRegister 应标注 @SpiIgnore。
     */
    private static void t1_registerIsSpiIgnored() {
        assertTrue(OsgiBeanDefinitionRegister.class.isAnnotationPresent(SpiIgnore.class),
                "OsgiBeanDefinitionRegister 应标注 @SpiIgnore，不参与 SPI 自动注册");
    }

    /**
     * 2. register/unregister 应抛 UnsupportedOperationException。
     */
    private static void t2_registerThrowsUnsupported() {
        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        try {
            register.register(null);
            fail("register(null) 应抛 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        try {
            register.unregister("any-name");
            fail("unregister(String) 应抛 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        try {
            register.unregister((BeanDefinition) null);
            fail("unregister(BeanDefinition) 应抛 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    /**
     * 3. 未注入 launcher 时查询返回 null/empty。
     */
    private static void t3_noLauncherReturnsEmpty() {
        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.initialize();

        assertNull(register.getBeanDefinition("osgi:java.lang.Runnable"),
                "无 launcher 时 getBeanDefinition 应返回 null");
        assertTrue(register.getBeanDefinitionOfType("java.lang.Runnable").isEmpty(),
                "无 launcher 时 getBeanDefinitionOfType 应返回空列表");
        assertFalse(register.containsBean("osgi:java.lang.Runnable"),
                "无 launcher 时 containsBean 应返回 false");
    }

    /**
     * 4. 注入 launcher 后，getBeanDefinition / getBeanDefinitionOfType 委派给 launcher。
     */
    private static void t4_injectedLauncherDelegates() {
        StubLauncher launcher = new StubLauncher();
        launcher.activate();
        HelloServiceImpl impl = new HelloServiceImpl("hello-osgi");
        launcher.putService(HelloService.class, impl);

        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();

        BeanDefinition def = register.getBeanDefinition("osgi:com.chua.example.osgi.HelloService");
        assertNotNull(def, "应返回 launcher 提供的 BeanDefinition");
        assertEquals(HelloService.class, def.getType(), "BeanDefinition 的类型应一致");
        assertEquals("hello-osgi", def.getBean(), "BeanDefinition 实例应一致");

        Collection<BeanDefinition> defs = register.getBeanDefinitionOfType(
                "com.chua.example.osgi.HelloService");
        assertEquals(1, defs.size(), "应返回 1 个 BeanDefinition");

        assertTrue(register.containsBean("osgi:com.chua.example.osgi.HelloService"),
                "containsBean 应返回 true");
    }

    /**
     * 5. launcher 未激活时查询返回 null/empty。
     */
    private static void t5_inactiveLauncherReturnsEmpty() {
        StubLauncher launcher = new StubLauncher();
        // 未 activate()
        launcher.putService(HelloService.class, new HelloServiceImpl("never"));

        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();

        assertNull(register.getBeanDefinition("osgi:com.chua.example.osgi.HelloService"),
                "inactive launcher 应返回 null");
        assertTrue(register.getBeanDefinitionOfType(
                "com.chua.example.osgi.HelloService").isEmpty(),
                "inactive launcher 应返回空列表");
    }

    /**
     * 6. ObjectContext.registerBean(BeanDefinitionRegister) 接受 OSGi 注册器并返回 true。
     */
    private static void t6_objectContextAcceptsRegister() {
        ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
        StubLauncher launcher = new StubLauncher();
        launcher.activate();

        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();

        boolean added = ctx.registerBean(register);
        assertTrue(added, "首次注册应返回 true");
    }

    /**
     * 7. 挂接到 ObjectContext 后，可通过 getBeanOfType 实时查询 OSGi 服务。
     */
    private static void t7_objectContextLooksUpOsgiService() {
        ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());

        StubLauncher launcher = new StubLauncher();
        launcher.activate();
        launcher.putService(HelloService.class, new HelloServiceImpl("via-osgi"));

        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();
        ctx.registerBean(register);

        HelloService svc = ctx.getBeanOfType(HelloService.class);
        assertNotNull(svc, "应能通过 ObjectContext 查到 OSGi 服务");
        assertEquals("via-osgi", svc.greet(), "返回值应来自 OSGi 服务实例");
    }

    /**
     * 8. 重复注册同名 Register 应被忽略，返回 false。
     */
    private static void t8_duplicateRegisterIgnored() {
        ObjectContext ctx = new DefaultObjectContext(ObjectContextConfig.defaults());
        StubLauncher launcher = new StubLauncher();
        launcher.activate();

        OsgiBeanDefinitionRegister first = new OsgiBeanDefinitionRegister();
        first.setOsgiLauncher(launcher);
        first.initialize();
        assertTrue(ctx.registerBean(first), "首次注册应成功");

        OsgiBeanDefinitionRegister second = new OsgiBeanDefinitionRegister();
        second.setOsgiLauncher(launcher);
        second.initialize();
        assertFalse(ctx.registerBean(second), "同名重复注册应被忽略，返回 false");
    }

    /**
     * 9. close() 后查询返回空，isClosed() 为 true。
     */
    private static void t9_closeDisablesQueries() {
        StubLauncher launcher = new StubLauncher();
        launcher.activate();
        launcher.putService(HelloService.class, new HelloServiceImpl("closing"));

        OsgiBeanDefinitionRegister register = new OsgiBeanDefinitionRegister();
        register.setOsgiLauncher(launcher);
        register.initialize();

        register.close();
        assertTrue(register.isClosed(), "close 后 isClosed 应为 true");
        assertNull(register.getBeanDefinition("osgi:com.chua.example.osgi.HelloService"),
                "close 后 getBeanDefinition 应返回 null");
        assertTrue(register.getBeanDefinitionOfType(
                "com.chua.example.osgi.HelloService").isEmpty(),
                "close 后 getBeanDefinitionOfType 应返回空列表");
    }

    // ==================== 测试辅助 ====================

    /**
     * 轻量级 OsgiLauncher 桩，仅用于本测试。
     */
    private static final class StubLauncher implements OsgiLauncher {
        private volatile boolean active;
        private final Map<Class<?>, List<Object>> services = new HashMap<>();

        void activate() {
            this.active = true;
        }

        <T> void putService(Class<T> type, T instance) {
            services.computeIfAbsent(type, k -> new ArrayList<>()).add(instance);
        }

        @Override
        public void start(Map<String, String> config) {
            this.active = true;
        }

        @Override
        public void stop() {
            this.active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> getServices(Class<T> type) {
            List<Object> list = services.get(type);
            if (list == null) {
                return Collections.emptyList();
            }
            return (List<T>) list;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getService(Class<T> type) {
            List<Object> list = services.get(type);
            if (list == null || list.isEmpty()) {
                return null;
            }
            return (T) list.get(0);
        }

        @Override
        public List<OsgiBundle> getBundles() {
            return Collections.emptyList();
        }

        @Override
        public OsgiBundle installBundle(String url) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void uninstallBundle(String bundleSymbolicName) {
            // no-op
        }
    }

    /**
     * 测试服务接口。
     */
    public interface HelloService {
        String greet();
    }

    /**
     * 测试服务实现。
     */
    public static final class HelloServiceImpl implements HelloService {
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

    // ==================== 断言工具 ====================

    /**
     * 测试服务接口。
     */
    public interface HelloService {
        String greet();
    }

    /**
     * 测试服务实现。
     */
    public static final class HelloServiceImpl implements HelloService {
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

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertFalse(boolean cond, String msg) {
        if (cond) {
            throw new AssertionError(msg);
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " ==> expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    private static void assertNull(Object obj, String msg) {
        if (obj != null) {
            throw new AssertionError(msg + " ==> expected null but was: <" + obj + ">");
        }
    }

    private static void assertNotNull(Object obj, String msg) {
        if (obj == null) {
            throw new AssertionError(msg + " ==> expected non-null but was null");
        }
    }

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    /**
     * 测试结果统计。
     */
    private static final class Result {
        final String name;
        final AtomicInteger total = new AtomicInteger();
        final AtomicInteger pass = new AtomicInteger();
        final List<String> failures = Collections.synchronizedList(new ArrayList<>());

        Result(String name) {
            this.name = name;
        }

        void run(String caseName, Runnable body) {
            total.incrementAndGet();
            try {
                body.run();
                pass.incrementAndGet();
                System.out.println("  [PASS] " + caseName);
            } catch (Throwable t) {
                failures.add(caseName + " -> " + t.getMessage());
                System.out.println("  [FAIL] " + caseName + " -> " + t.getMessage());
            }
        }

        void summary() {
            System.out.println("[" + name + "] pass=" + pass.get() + "/" + total.get()
                    + (failures.isEmpty() ? "" : ", failures=" + failures));
        }
    }
}

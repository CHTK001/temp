package com.chua.example.objects;

import com.chua.common.support.lang.script.marker.ScriptMarker;
import com.chua.common.support.lang.script.marker.listener.FileScriptListener;
import com.chua.common.support.lang.script.marker.listener.Listener;
import com.chua.common.support.objects.definition.ScriptBeanDefinition;
import com.chua.common.support.spi.ServiceProvider;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ObjectContext 脚本热重载综合示例 — 演示通过 {@link ScriptBeanDefinition}
 * 在 ObjectContext 中注册脚本 Bean 并支持热重载。
 *
 * <p>本示例覆盖 Java/Groovy 脚本注册、方法调用、热重载等核心能力，
 * 并提供自检流程验证脚本 Bean 的完整生命周期。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行模式：执行全部能力点自检
 *   java ObjectContextExample
 *
 *   # 指定能力点测试（java / groovy / reload）
 *   java ObjectContextExample --type java
 *
 *   # 打印帮助
 *   java ObjectContextExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>Java 脚本</td><td>{@link #testJavaScript()}</td><td>注册 Java 脚本并调用方法</td></tr>
 *   <tr><td>Groovy 脚本</td><td>{@link #testGroovyScript()}</td><td>注册 Groovy 脚本并调用方法</td></tr>
 *   <tr><td>热重载</td><td>{@link #testHotReload()}</td><td>修改脚本文件，验证自动重载</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class ObjectContextExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 脚本文件目录
     */
    private static final String SCRIPT_DIR = "target/script-example";

    /**
     * Java 脚本文件名
     */
    private static final String JAVA_SCRIPT_FILE = "HelloScript.java";

    /**
     * Groovy 脚本文件名
     */
    private static final String GROOVY_SCRIPT_FILE = "HelloScript.groovy";

    /**
     * 热重载等待时间（毫秒），确保文件修改时间戳足够新
     */
    private static final long RELOAD_SLEEP_MS = 2000L;

    /**
     * 主入口：根据命令行参数运行指定能力点自检。
     *
     * @param args 命令行参数，args[0]=能力点类型，默认执行全部
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
                ? args[0].toLowerCase()
                : "all";

        if ("--help".equals(type) || "-h".equals(type)) {
            printHelp();
            return;
        }

        ObjectContextExample example = new ObjectContextExample();
        boolean passed = example.runTest(type);
        System.out.println("[ObjectContextExample] self-test type=" + type + ", passed=" + passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 启动自检流程：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = "all";
        }
        switch (type.toLowerCase()) {
            case "java" -> {
                return testJavaScript();
            }
            case "groovy" -> {
                return testGroovyScript();
            }
            case "reload" -> {
                return testHotReload();
            }
            case "all" -> {
                return testJavaScript()
                        && testGroovyScript()
                        && testHotReload();
            }
            default -> {
                System.err.println("[ObjectContextExample] 未知能力点: " + type);
                return false;
            }
        }
    }

    /**
     * Java 原生脚本自检：编写 Java 脚本文件，通过 ScriptBeanDefinition + JavaScriptMarker 注册并调用方法。
     *
     * @return true 表示 Java 脚本注册和调用成功
     */
    public boolean testJavaScript() {
        System.out.println("===== [java] Java 脚本示例 =====");

        Path scriptDir = Path.of(SCRIPT_DIR);
        Path javaScriptPath = scriptDir.resolve(JAVA_SCRIPT_FILE);
        try {
            Files.createDirectories(scriptDir);
        } catch (IOException e) {
            System.err.println("[ObjectContextExample] 创建脚本目录失败: " + e.getMessage());
            return false;
        }

        String scriptContent = ""
                + "public class HelloScript {\n"
                + "    public String sayHello(String name) {\n"
                + "        return \"Hello, \" + name + \" from Java!\";\n"
                + "    }\n"
                + "}\n";

        try {
            Files.writeString(javaScriptPath, scriptContent);
        } catch (IOException e) {
            System.err.println("[ObjectContextExample] 写入 Java 脚本失败: " + e.getMessage());
            return false;
        }

        try {
            ScriptMarker javaMarker = ServiceProvider.of(ScriptMarker.class).getExtension("java");
            if (javaMarker == null) {
                System.err.println("[ObjectContextExample] 未找到 Java 脚本标记器");
                return false;
            }

            Listener listener = new FileScriptListener(javaScriptPath);
            ScriptBeanDefinition definition = new ScriptBeanDefinition("helloJava", javaMarker, listener);

            Object instance = definition.createInstance();
            if (instance == null) {
                System.err.println("[ObjectContextExample] 创建 Java 脚本实例失败");
                return false;
            }
            Class<?> scriptClass = definition.getBeanClass();
            if (scriptClass == null) {
                scriptClass = instance.getClass();
            }

            System.out.println("  编译成功: " + scriptClass.getName());

            Method sayHello = scriptClass.getMethod("sayHello", String.class);
            String result = (String) sayHello.invoke(instance, "Java");
            System.out.println("  调用结果: " + result);

            boolean passed = "Hello, Java from Java!".equals(result);
            System.out.println("  [java] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[ObjectContextExample] Java 脚本测试异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * Groovy 脚本自检：编写 Groovy 脚本文件，通过 ScriptBeanDefinition 注册并调用方法。
     *
     * @return true 表示 Groovy 脚本注册和调用成功
     */
    public boolean testGroovyScript() {
        System.out.println("===== [groovy] Groovy 脚本示例 =====");

        Path scriptDir = Path.of(SCRIPT_DIR);
        Path groovyScriptPath = scriptDir.resolve(GROOVY_SCRIPT_FILE);
        try {
            Files.createDirectories(scriptDir);
        } catch (IOException e) {
            System.err.println("[ObjectContextExample] 创建脚本目录失败: " + e.getMessage());
            return false;
        }

        String scriptContent = ""
                + "class HelloScript {\n"
                + "    String sayHello(String name) {\n"
                + "        return \"Hello, \" + name + \" from Groovy!\"\n"
                + "    }\n"
                + "}\n";

        try {
            Files.writeString(groovyScriptPath, scriptContent);
        } catch (IOException e) {
            System.err.println("[ObjectContextExample] 写入 Groovy 脚本失败: " + e.getMessage());
            return false;
        }

        try {
            ScriptMarker groovyMarker = ServiceProvider.of(ScriptMarker.class).getExtension("groovy");
            if (groovyMarker == null) {
                System.err.println("[ObjectContextExample] 未找到 Groovy 脚本标记器");
                return false;
            }

            Listener listener = new FileScriptListener(groovyScriptPath);
            ScriptBeanDefinition definition = new ScriptBeanDefinition("helloGroovy", groovyMarker, listener);

            Class<?> scriptClass = definition.getBeanClass();
            if (scriptClass == null) {
                Object instance = definition.createInstance();
                scriptClass = definition.getBeanClass();
                if (scriptClass == null && instance != null) {
                    scriptClass = instance.getClass();
                }
            }

            if (scriptClass == null) {
                System.err.println("[ObjectContextExample] Groovy 脚本编译失败");
                return false;
            }

            System.out.println("  编译成功: " + scriptClass.getName());

            Object instance = definition.getBean();
            if (instance == null) {
                instance = definition.createInstance();
            }
            if (instance == null) {
                System.err.println("[ObjectContextExample] 创建 Groovy 脚本实例失败");
                return false;
            }

            Method sayHello = scriptClass.getMethod("sayHello", String.class);
            String result = (String) sayHello.invoke(instance, "Groovy");
            System.out.println("  调用结果: " + result);

            boolean passed = "Hello, Groovy from Groovy!".equals(result);
            System.out.println("  [groovy] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[ObjectContextExample] Groovy 脚本测试异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 热重载自检：修改脚本文件，验证 ScriptBeanDefinition 检测到变更并重新编译。
     *
     * @return true 表示热重载成功
     */
    public boolean testHotReload() {
        System.out.println("===== [reload] 热重载示例 =====");

        Path scriptDir = Path.of(SCRIPT_DIR);
        Path groovyScriptPath = scriptDir.resolve(GROOVY_SCRIPT_FILE);
        try {
            Files.createDirectories(scriptDir);
        } catch (IOException e) {
            System.err.println("[ObjectContextExample] 创建脚本目录失败: " + e.getMessage());
            return false;
        }

        String originalScript = ""
                + "class HelloScript {\n"
                + "    String sayHello(String name) {\n"
                + "        return \"Hello, \" + name + \" from Groovy!\"\n"
                + "    }\n"
                + "}\n";

        String updatedScript = ""
                + "class HelloScript {\n"
                + "    String sayHello(String name) {\n"
                + "        return \"Hi, \" + name + \" from Groovy (reloaded)!\"\n"
                + "    }\n"
                + "}\n";

        try {
            ScriptMarker groovyMarker = ServiceProvider.of(ScriptMarker.class).getExtension("groovy");
            if (groovyMarker == null) {
                System.err.println("[ObjectContextExample] 未找到 Groovy 脚本标记器");
                return false;
            }

            Files.writeString(groovyScriptPath, originalScript);

            Listener listener = new FileScriptListener(groovyScriptPath);
            ScriptBeanDefinition definition = new ScriptBeanDefinition("helloReload", groovyMarker, listener);

            Object instance1 = definition.createInstance();
            if (instance1 == null) {
                System.err.println("[ObjectContextExample] 首次创建实例失败");
                return false;
            }
            Class<?> class1 = definition.getBeanClass();
            if (class1 == null) {
                class1 = instance1.getClass();
            }
            Method sayHello1 = class1.getMethod("sayHello", String.class);
            String result1 = (String) sayHello1.invoke(instance1, "World");
            System.out.println("  首次调用: " + result1);

            Thread.sleep(RELOAD_SLEEP_MS);

            Files.writeString(groovyScriptPath, updatedScript);

            Object instance2 = definition.createInstance();
            if (instance2 == null) {
                System.err.println("[ObjectContextExample] 热重载后创建实例失败");
                return false;
            }
            Class<?> class2 = definition.getBeanClass();
            if (class2 == null) {
                class2 = instance2.getClass();
            }
            Method sayHello2 = class2.getMethod("sayHello", String.class);
            String result2 = (String) sayHello2.invoke(instance2, "World");
            System.out.println("  重载调用: " + result2);

            boolean passed = "Hello, World from Groovy!".equals(result1)
                    && "Hi, World from Groovy (reloaded)!".equals(result2)
                    && class1 != class2;

            System.out.println("  [reload] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[ObjectContextExample] 热重载测试异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("ObjectContext 脚本热重载综合示例");
        System.out.println();
        System.out.println("用法: java ObjectContextExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  java         Java 脚本能力点");
        System.out.println("  groovy       Groovy 脚本能力点");
        System.out.println("  reload       热重载能力点");
        System.out.println("  all          测试全部能力点（默认）");
        System.out.println("  --help       显示此帮助");
    }
}

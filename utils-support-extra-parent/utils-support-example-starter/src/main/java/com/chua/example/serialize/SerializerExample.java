package com.chua.example.serialize;

import com.chua.common.support.serialize.JavaSerializer;
import com.chua.common.support.serialize.JsonSerializer;
import com.chua.common.support.serialize.Serializer;
import com.chua.common.support.serialize.SerializerFlow;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * 序列化综合示例 — 演示 {@link SerializerFlow} 的能力矩阵。
 *
 * <p>本示例覆盖 JSON 序列化、Java 原生序列化、SerializerFlow 链式调用等核心能力，
 * 并提供自检流程用于验证 {@code com.chua.common.support.serialize} 包下的工具类可用性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行模式：执行全部能力点自检
 *   java SerializerExample
 *
 *   # 指定能力点测试（json / java / flow）
 *   java SerializerExample --type json
 *
 *   # 打印帮助
 *   java SerializerExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>JSON 序列化</td><td>{@link #testJson()}</td><td>基于 Jackson 的 JSON 序列化/反序列化</td></tr>
 *   <tr><td>Java 原生序列化</td><td>{@link #testJava()}</td><td>基于 ObjectInputStream/ObjectOutputStream</td></tr>
 *   <tr><td>SerializerFlow 链式调用</td><td>{@link #testFlow()}</td><td>统一入口 + 切换序列化器</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class SerializerExample {

    /**
     * 测试用户名
     */
    private static final String TEST_NAME = "CH";

    /**
     * 测试用户年龄
     */
    private static final int TEST_AGE = 18;

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 主入口：根据命令行参数运行指定能力点自检。
     *
     * @param args 命令行参数，args[0]=能力点类型（json / java / flow / all），默认 all
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
                ? args[0].toLowerCase()
                : "all";

        if ("--help".equals(type) || "-h".equals(type)) {
            printHelp();
            return;
        }

        SerializerExample example = new SerializerExample();
        boolean passed = example.runTest(type);
        System.out.println("[SerializerExample] self-test type=" + type + ", passed=" + passed);
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
            case "json" -> {
                return testJson();
            }
            case "java" -> {
                return testJava();
            }
            case "flow" -> {
                return testFlow();
            }
            case "all" -> {
                return testJson() && testJava() && testFlow();
            }
            default -> {
                System.err.println("[SerializerExample] 未知能力点: " + type);
                return false;
            }
        }
    }

    /**
     * JSON 序列化自检：使用 {@link JsonSerializer} 完成 User 对象的序列化与反序列化。
     *
     * @return true 表示反序列化对象非空且字段一致
     */
    public boolean testJson() {
        System.out.println("===== [json] JSON 序列化示例 =====");
        try {
            User user = new User(TEST_NAME, TEST_AGE);
            Serializer<User> serializer = new JsonSerializer<>(User.class);

            // 1. 序列化
            byte[] bytes = serializer.serialize(user);
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("  序列化结果: " + jsonStr);

            // 2. 反序列化
            User copy = serializer.deserialize(bytes);
            System.out.println("  反序列化结果: " + copy);

            boolean passed = copy != null
                    && TEST_NAME.equals(copy.getName())
                    && TEST_AGE == copy.getAge();
            System.out.println("  [json] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[SerializerExample] json failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Java 原生序列化自检：使用 {@link JavaSerializer} 完成 User 对象的序列化与反序列化。
     *
     * @return true 表示反序列化对象非空且字段一致
     */
    public boolean testJava() {
        System.out.println("===== [java] Java 原生序列化示例 =====");
        try {
            User user = new User(TEST_NAME, TEST_AGE);
            Serializer<User> serializer = new JavaSerializer<>();

            // 1. 序列化
            byte[] bytes = serializer.serialize(user);
            System.out.println("  序列化字节数: " + bytes.length);

            // 2. 反序列化
            User copy = serializer.deserialize(bytes);
            System.out.println("  反序列化结果: " + copy);

            boolean passed = copy != null
                    && TEST_NAME.equals(copy.getName())
                    && TEST_AGE == copy.getAge();
            System.out.println("  [java] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[SerializerExample] java failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * SerializerFlow 链式调用自检：演示统一入口、切换序列化器与类型安全反序列化。
     *
     * @return true 表示两种序列化器反序列化结果均正确
     */
    public boolean testFlow() {
        System.out.println("===== [flow] SerializerFlow 链式调用示例 =====");
        try {
            User user = new User(TEST_NAME, TEST_AGE);
            SerializerFlow flow = new SerializerFlow();

            // 1. 默认 JSON 序列化
            byte[] jsonBytes = flow.serialize(user);
            System.out.println("  默认 JSON 序列化字节数: " + jsonBytes.length);
            User fromJson = flow.deserialize(jsonBytes, User.class);
            System.out.println("  JSON 反序列化结果: " + fromJson);
            boolean jsonOk = fromJson != null && TEST_NAME.equals(fromJson.getName());

            // 2. 切换为 Java 原生序列化
            byte[] javaBytes = flow.use(new JavaSerializer<>()).serialize(user);
            System.out.println("  Java 原生序列化字节数: " + javaBytes.length);
            User fromJava = flow.deserialize(javaBytes, User.class);
            System.out.println("  Java 反序列化结果: " + fromJava);
            boolean javaOk = fromJava != null && TEST_NAME.equals(fromJava.getName());

            // 3. 当前序列化器类型
            String currentSerializer = flow.getSerializer().getClass().getSimpleName();
            System.out.println("  当前序列化器: " + currentSerializer);

            boolean passed = jsonOk && javaOk;
            System.out.println("  [flow] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[SerializerExample] flow failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("SerializerExample — 序列化工具示例");
        System.out.println();
        System.out.println("用法: java SerializerExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  json      JSON 序列化能力点");
        System.out.println("  java      Java 原生序列化能力点");
        System.out.println("  flow      SerializerFlow 链式调用能力点");
        System.out.println("  all       测试全部能力点（默认）");
        System.out.println("  --help    显示此帮助");
    }

    /**
     * 测试用户实体。
     *
     * <p>实现 {@link Serializable} 以支持 Java 原生序列化。</p>
     *
     * @author CH
     * @since 4.0.0.43
     */
    public static class User implements Serializable {

        /**
         * 序列化版本号
         */
        private static final long serialVersionUID = 1L;

        /**
         * 用户名
         */
        private String name;

        /**
         * 年龄
         */
        private int age;

        /**
         * 默认构造方法（反序列化需要）
         */
        public User() {
        }

        /**
         * 全参构造方法。
         *
         * @param name 用户名
         * @param age  年龄
         */
        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        /**
         * 获取用户名。
         *
         * @return 用户名
         */
        public String getName() {
            return name;
        }

        /**
         * 设置用户名。
         *
         * @param name 用户名
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取年龄。
         *
         * @return 年龄
         */
        public int getAge() {
            return age;
        }

        /**
         * 设置年龄。
         *
         * @param age 年龄
         */
        public void setAge(int age) {
            this.age = age;
        }

        @Override
        public String toString() {
            return "User{name='" + name + "', age=" + age + "}";
        }
    }
}

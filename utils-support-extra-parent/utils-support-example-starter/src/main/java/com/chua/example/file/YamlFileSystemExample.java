package com.chua.example.file;

import com.chua.common.support.file.FileSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * YamlFileSystem 独立完整测试示例。
 *
 * <p>演示能力：</p>
 * <ul>
 *   <li>写 YAML（Map / 对象）</li>
 *   <li>读 YAML（toMap）</li>
 *   <li>读取配置并反序列化</li>
 * </ul>
 *
 * <pre>{@code
 * java com.chua.example.file.YamlFileSystemExample
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class YamlFileSystemExample {

    private File workDir;
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        YamlFileSystemExample example = new YamlFileSystemExample();
        example.setUp();
        example.testWriteAndRead();
        example.testWritePojo();
        example.testReadWithSection();
        example.cleanUp();

        System.out.println("\n========================================");
        System.out.println("[YamlFileSystemExample] 全部测试 "
                + (example.allPassed ? "✅ PASS" : "❌ FAIL"));
        System.exit(example.allPassed ? 0 : 1);
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("yaml-example-").toFile();
        System.out.println("[YAML] 工作目录: " + workDir);
    }

    void testWriteAndRead() throws Exception {
        System.out.println("\n===== 1. 写入 + 读取 Map =====");
        File yml = new File(workDir, "config.yml");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "张三");
        data.put("age", 25);
        data.put("active", true);

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("host", "localhost");
        nested.put("port", 8080);
        data.put("server", nested);

        FileSystem.create("yaml").write(yml).write(data);

        System.out.println("  [写入] " + yml.length() + " bytes");
        String content = new String(Files.readAllBytes(yml.toPath()));
        System.out.println("  [内容]\n" + content);

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        System.out.println("  [读取] name=" + loaded.get("name") + ", age=" + loaded.get("age"));

        check("张三".equals(loaded.get("name")), "name 不匹配");
    }

    void testWritePojo() throws Exception {
        System.out.println("\n===== 2. 写入 POJO =====");
        File yml = new File(workDir, "pojo.yml");

        Config config = new Config("MyApp", 1.0, Arrays.asList("dev", "prod"));
        FileSystem.create("yaml").write(yml).write(config);

        String content = new String(Files.readAllBytes(yml.toPath()));
        System.out.println("  [POJO 内容]\n" + content);

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        System.out.println("  [读取] " + loaded);
        check(loaded.containsKey("name") && loaded.containsKey("version"), "缺少字段");
    }

    void testReadWithSection() throws Exception {
        System.out.println("\n===== 3. 带层级结构 =====");
        File yml = new File(workDir, "app.yml");

        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", "demo");

        Map<String, Object> db = new LinkedHashMap<>();
        db.put("url", "jdbc:mysql://localhost:3306/demo");
        db.put("user", "root");
        db.put("password", "123456");
        app.put("database", db);

        FileSystem.create("yaml").write(yml).write(app);

        String content = new String(Files.readAllBytes(yml.toPath()));
        System.out.println("  [层级内容]\n" + content);

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        check(loaded.containsKey("database"), "缺少 database");
        System.out.println("  [PASS]");
    }

    void cleanUp() { deleteDir(workDir); }

    private void check(boolean condition, String msg) {
        if (!condition) { System.err.println("  [FAIL] " + msg); allPassed = false; }
        else { System.out.println("  [PASS]"); }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) deleteDir(f); else f.delete();
        }
        dir.delete();
    }

    public static class Config {
        private String name;
        private double version;
        private List<String> profiles;
        public Config() {}
        public Config(String name, double version, List<String> profiles) {
            this.name = name; this.version = version; this.profiles = profiles;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getVersion() { return version; }
        public void setVersion(double version) { this.version = version; }
        public List<String> getProfiles() { return profiles; }
        public void setProfiles(List<String> profiles) { this.profiles = profiles; }
    }
}

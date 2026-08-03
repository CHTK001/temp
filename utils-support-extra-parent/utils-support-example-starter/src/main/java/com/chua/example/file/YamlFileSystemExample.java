package com.chua.example.file;

import com.chua.common.support.file.FileSystem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@Slf4j
public class YamlFileSystemExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 临时工作目录
     */
    private File workDir;

    /**
     * 全部测试是否通过
     */
    private boolean allPassed = true;

    public static void main(String[] args) throws Exception {
        YamlFileSystemExample example = new YamlFileSystemExample();
        boolean passed = example.runTest();
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    public boolean runTest() throws Exception {
        setUp();
        testWriteAndRead();
        testWritePojo();
        testReadWithSection();
        cleanUp();

        log.info("========================================");
        log.info("[YamlFileSystemExample] 全部测试 {}", allPassed ? "✅ PASS" : "❌ FAIL");
        return allPassed;
    }

    void setUp() throws Exception {
        workDir = Files.createTempDirectory("yaml-example-").toFile();
        log.info("[YAML] 工作目录: {}", workDir);
    }

    void testWriteAndRead() throws Exception {
        log.info("\n===== 1. 写入 + 读取 Map =====");
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

        log.info("  [写入] {} bytes", yml.length());
        String content = new String(Files.readAllBytes(yml.toPath()));
        log.info("  [内容]\n{}", content);

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        log.info("  [读取] name={}, age={}", loaded.get("name"), loaded.get("age"));

        check("张三".equals(loaded.get("name")), "name 不匹配");
    }

    void testWritePojo() throws Exception {
        log.info("\n===== 2. 写入 POJO =====");
        File yml = new File(workDir, "pojo.yml");

        Config config = new Config("MyApp", 1.0, Arrays.asList("dev", "prod"));
        FileSystem.create("yaml").write(yml).write(config);

        String content = new String(Files.readAllBytes(yml.toPath()));
        log.info("  [POJO 内容]\n{}", content);

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        log.info("  [读取] {}", loaded);
        check(loaded.containsKey("name") && loaded.containsKey("version"), "缺少字段");
    }

    void testReadWithSection() throws Exception {
        log.info("\n===== 3. 带层级结构 =====");
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
        log.info("  [层级内容]\n{}", content);

        Map<String, Object> loaded = FileSystem.create("yaml").read(yml).toMap();
        check(loaded.containsKey("database"), "缺少 database");
        log.info("  [PASS]");
    }

    void cleanUp() {
        deleteDir(workDir);
    }

    private void check(boolean condition, String msg) {
        if (!condition) {
            log.info("  [FAIL] {}", msg);
            allPassed = false;
        } else {
            log.info("  [PASS]");
        }
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * 测试配置实体。
     *
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class Config {
        /**
         * 应用名称
         */
        private String name;

        /**
         * 版本号
         */
        private double version;

        /**
         * 启用的 profiles
         */
        private List<String> profiles;
    }
}

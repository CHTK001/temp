package com.chua.example.engine;

import com.chua.common.support.lang.datasource.page.Page;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Lambda 全操作符测试：InMemoryEngine + FileEngine（CSV/JSON）完整操作符矩阵。
 *
 * <p>覆盖 EngineQueryWrapper 全部 16 个查询操作符 + orderByAsc/Desc + page + update + delete，
 * 同时验证 FileEngine 多格式文件加载的 Lambda 查询能力。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java MemoryLambdaExample                    # 全部
 *   java MemoryLambdaExample --engine memory     # 仅 InMemory
 *   java MemoryLambdaExample --engine file       # 仅 File
 *   java MemoryLambdaExample --engine all        # 全部（默认）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MemoryLambdaExample {

    /** 测试员工实体 */
    public static class Emp {
        /** 主键 */
        private int id;
        /** 姓名 */
        private String name;
        /** 年龄 */
        private int age;
        /** 城市 */
        private String city;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    /** 全部模式 */
    private static final String MODE_ALL = "all";
    /** 内存引擎模式 */
    private static final String MODE_MEMORY = "memory";
    /** 文件引擎模式 */
    private static final String MODE_FILE = "file";
    /** 失败计数 */
    private static int failed;

    /**
     * 入口。
     *
     * @param args --engine=memory|file|all
     */
    public static void main(String[] args) {
        var cli = com.chua.common.support.lang.cmd.CommandLine.builder()
                .programName("MemoryLambdaExample")
                .option(com.chua.common.support.lang.cmd.CliOption.of("engine", "e",
                        "引擎类型：memory|file|all"))
                .build();
        var result = cli.parse(args);
        if (result.has("help")) {
            cli.printHelp();
            return;
        }
        var engine = result.getString("engine");
        if (engine == null || engine.isBlank()) {
            engine = MODE_ALL;
        }

        if (MODE_MEMORY.equals(engine) || MODE_ALL.equals(engine)) {
            runInMemoryLambdaScenarios();
        }
        if (MODE_FILE.equals(engine) || MODE_ALL.equals(engine)) {
            runFileLambdaScenarios();
        }

        if (failed > 0) {
            log.error("[FAIL] lambda 场景失败数: {}", failed);
            System.exit(1);
        }
        System.out.println("[PASS] memory-lambda all scenarios covered");
        System.exit(0);
    }

    /**
     * InMemoryEngine Lambda 全操作符场景。
     */
    private static void runInMemoryLambdaScenarios() {
        var engine = new com.chua.datasource.support.engine.InMemoryEngine();
        engine.store("emp", List.of(
                empOf(1, "Alice", 20, "Beijing"),
                empOf(2, "Bob", 30, "Shanghai"),
                empOf(3, "Cathy", 25, "Beijing"),
                empOf(4, "Dave", 35, "Guangzhou"),
                empOf(5, null, 28, "Shanghai")));

        /* === 查询操作符 === */
        check("eq", "Alice", nameOf(engine.query(Emp.class).eq(Emp::getId, 1).one()));
        check("ne", 4, engine.query(Emp.class).ne(Emp::getId, 1).list().size());
        check("gt", 3, engine.query(Emp.class).gt(Emp::getAge, 25).list().size());
        check("ge", 4, engine.query(Emp.class).ge(Emp::getAge, 25).list().size());
        check("lt", 1, engine.query(Emp.class).lt(Emp::getAge, 25).list().size());
        check("le", 2, engine.query(Emp.class).le(Emp::getAge, 25).list().size());

        /* like / likeLeft / likeRight */
        check("like", 1, engine.query(Emp.class).like(Emp::getName, "li").list().size());
        check("likeLeft", 1, engine.query(Emp.class).likeLeft(Emp::getName, "li").list().size());
        check("likeRight", 1, engine.query(Emp.class).likeRight(Emp::getName, "Ali").list().size());

        /* in / notIn */
        check("in", 2, engine.query(Emp.class).in(Emp::getId, List.of(1, 3)).list().size());
        check("notIn", 3, engine.query(Emp.class).notIn(Emp::getId, List.of(1, 3)).list().size());

        /* isNull / isNotNull */
        check("isNull", 1, engine.query(Emp.class).isNull(Emp::getName).list().size());
        check("isNotNull", 4, engine.query(Emp.class).isNotNull(Emp::getName).list().size());

        /* between */
        check("between", 4, engine.query(Emp.class).between(Emp::getAge, 20, 30).list().size());

        /* orderByAsc / orderByDesc */
        check("orderByAsc", "Alice",
                nameOf(engine.query(Emp.class).orderByAsc(Emp::getAge).one()));
        check("orderByDesc", "Dave",
                nameOf(engine.query(Emp.class).orderByDesc(Emp::getAge).one()));

        /* page */
        Page<Emp> p1 = engine.query(Emp.class).orderByAsc(Emp::getId).page(1, 2);
        check("page total", 5, (int) p1.getTotal());
        check("page size", 2, p1.getRecords().size());
        check("page first", "Alice", nameOf(p1.getRecords().get(0)));

        /* 组合条件 */
        check("组合 eq+gt", 1, engine.query(Emp.class)
                .eq(Emp::getCity, "Beijing").gt(Emp::getAge, 22).list().size());

        /* update / delete */
        int upd = engine.update(Emp.class).set(Emp::getAge, 99).eq(Emp::getId, 1).update();
        check("update", 1, upd);
        check("update readback", 99,
                engine.query(Emp.class).eq(Emp::getId, 1).one().getAge());
        engine.update(Emp.class).set(Emp::getAge, 20).eq(Emp::getId, 1).update(); // restore

        int del = engine.delete(Emp.class).eq(Emp::getId, 5).remove();
        check("delete", 1, del);
        check("delete verify", 4, engine.query(Emp.class).list().size());
    }

    /**
     * FileEngine Lambda 场景：CSV + JSON 双格式。
     * <p>Excel/DBF 需要各自 starter 模块在 classpath 上，此处仅验证 CSV 和 JSON。</p>
     */
    private static void runFileLambdaScenarios() {
        Path dir;
        try {
            dir = Paths.get(System.getProperty("java.io.tmpdir"), "test-output", "memory-lambda");
            Files.createDirectories(dir);
        } catch (IOException e) {
            failed++;
            log.info("[FAIL] 创建临时目录失败: {}", e.getMessage());
            return;
        }

        /* --- CSV 格式 --- */
        try {
            Path csv = dir.resolve("emp.csv");
            Files.writeString(csv, "id,name,age,city\n1,Alice,20,Beijing\n2,Bob,30,Shanghai\n"
                    + "3,Cathy,25,Beijing\n4,Dave,35,Guangzhou");
            var csvEngine = new com.chua.datasource.support.engine.FileEngine();
            csvEngine.load("emp", csv.toString());

            check("csv eq", "Alice",
                    nameOf(csvEngine.query(Emp.class).eq(Emp::getId, 1).one()));
            check("csv gt", 2, csvEngine.query(Emp.class).gt(Emp::getAge, 25).list().size());
            check("csv lt", 1, csvEngine.query(Emp.class).lt(Emp::getAge, 25).list().size());
            check("csv in", 2, csvEngine.query(Emp.class).in(Emp::getId, List.of(1, 3)).list().size());
            check("csv between", 3, csvEngine.query(Emp.class)
                    .between(Emp::getAge, 20, 30).list().size());
            check("csv like", 1, csvEngine.query(Emp.class).like(Emp::getName, "li").list().size());
            check("csv orderByDesc", "Dave",
                    nameOf(csvEngine.query(Emp.class).orderByDesc(Emp::getAge).one()));
            check("csv orderByAsc", "Alice",
                    nameOf(csvEngine.query(Emp.class).orderByAsc(Emp::getAge).one()));
            check("csv page", 2, csvEngine.query(Emp.class).orderByAsc(Emp::getId).page(1, 2)
                    .getRecords().size());

            int csvUpd = csvEngine.update(Emp.class).set(Emp::getAge, 88)
                    .eq(Emp::getId, 2).update();
            check("csv update", 1, csvUpd);
            int csvDel = csvEngine.delete(Emp.class).eq(Emp::getId, 4).remove();
            check("csv delete", 1, csvDel);
        } catch (IOException e) {
            failed++;
            log.info("[FAIL] csv 场景: {}", e.getMessage());
        }

        /* --- JSON 格式 --- */
        try {
            Path json = dir.resolve("emp.json");
            Files.writeString(json, "[{\"id\":1,\"name\":\"Alice\",\"age\":20,\"city\":\"Beijing\"},"
                    + "{\"id\":2,\"name\":\"Bob\",\"age\":30,\"city\":\"Shanghai\"},"
                    + "{\"id\":3,\"name\":\"Cathy\",\"age\":25,\"city\":\"Beijing\"},"
                    + "{\"id\":4,\"name\":\"Dave\",\"age\":35,\"city\":\"Guangzhou\"}]");
            var jsonEngine = new com.chua.datasource.support.engine.FileEngine();
            jsonEngine.load("emp", json.toString());

            check("json eq", "Alice",
                    nameOf(jsonEngine.query(Emp.class).eq(Emp::getId, 1).one()));
            check("json ne", 3, jsonEngine.query(Emp.class).ne(Emp::getId, 1).list().size());
            check("json gt", 2, jsonEngine.query(Emp.class).gt(Emp::getAge, 25).list().size());
            check("json ge", 3, jsonEngine.query(Emp.class).ge(Emp::getAge, 25).list().size());
            check("json lt", 1, jsonEngine.query(Emp.class).lt(Emp::getAge, 25).list().size());
            check("json le", 2, jsonEngine.query(Emp.class).le(Emp::getAge, 25).list().size());
            check("json like", 1, jsonEngine.query(Emp.class).like(Emp::getName, "li").list().size());
            check("json likeLeft", 1, jsonEngine.query(Emp.class).likeLeft(Emp::getName, "li").list().size());
            check("json likeRight", 1, jsonEngine.query(Emp.class).likeRight(Emp::getName, "Ali").list().size());
            check("json in", 2, jsonEngine.query(Emp.class).in(Emp::getId, List.of(1, 3)).list().size());
            check("json notIn", 2, jsonEngine.query(Emp.class).notIn(Emp::getId, List.of(1, 3)).list().size());
            check("json between", 3, jsonEngine.query(Emp.class).between(Emp::getAge, 20, 30).list().size());
            check("json orderByDesc", "Dave",
                    nameOf(jsonEngine.query(Emp.class).orderByDesc(Emp::getAge).one()));
            check("json orderByAsc", "Alice",
                    nameOf(jsonEngine.query(Emp.class).orderByAsc(Emp::getAge).one()));
            check("json page", 2, jsonEngine.query(Emp.class).orderByAsc(Emp::getId).page(1, 2)
                    .getRecords().size());

            int jsonUpd = jsonEngine.update(Emp.class).set(Emp::getAge, 88)
                    .eq(Emp::getId, 2).update();
            check("json update", 1, jsonUpd);
            int jsonDel = jsonEngine.delete(Emp.class).eq(Emp::getId, 4).remove();
            check("json delete", 1, jsonDel);
        } catch (IOException e) {
            failed++;
            log.info("[FAIL] json 场景: {}", e.getMessage());
        }

        /* 清理 */
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        } catch (IOException ignored) {}
    }

    /* ==================== 辅助 ==================== */

    private static void check(String scene, Object expected, Object actual) {
        var pass = expected == null ? actual == null : expected.equals(actual);
        log.info("{} {} => {}", pass ? "[PASS]" : "[FAIL]", scene, pass ? actual : "expected=" + expected + " actual=" + actual);
        if (!pass) failed++;
    }

    private static String nameOf(Object obj) {
        if (obj == null) return null;
        return ((Emp) obj).getName();
    }

    private static Emp empOf(int id, String name, int age, String city) {
        var e = new Emp();
        e.setId(id);
        e.setName(name);
        e.setAge(age);
        e.setCity(city);
        return e;
    }
}

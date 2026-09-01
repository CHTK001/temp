package com.chua.example.engine;

import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.chua.common.support.utils.CollectionUtils.isNotEmpty;

/**
 * 内存引擎原生 SQL 示例：SQL 编译为二叉表达式树 AST 后直接作用于 List 行引用。
 *
 * <p>覆盖场景：SELECT 投影 / COUNT(*)、WHERE 全操作符（比较 / LIKE / IN / BETWEEN /
 * 括号优先级）、ORDER BY / LIMIT / OFFSET、? 参数绑定（按行重置）、
 * INSERT 多值、UPDATE SET、DELETE、响应式 query / typed query / batch。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 全部模式（同步 SQL + 响应式 SQL）
 *   java MemorySqlExample
 *
 *   # 仅同步路径
 *   java MemorySqlExample --mode sql
 *
 *   # 仅响应式路径
 *   java MemorySqlExample --mode reactor
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class MemorySqlExample {

    /** 私有构造，防止实例化 */
    private MemorySqlExample() { }

    /** 员工测试实体：内存 SQL 的 UPDATE SET 需要可变 bean，故不使用 record */
    public static class Emp {
        /** 主键 */
        private int id;
        /** 姓名 */
        private String name;
        /** 年龄 */
        private int age;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    /** 默认运行模式：全部场景 */
    private static final String MODE_ALL = "all";

    /** 同步模式标识 */
    private static final String MODE_SQL = "sql";

    /** 响应式模式标识 */
    private static final String MODE_REACTOR = "reactor";

    /** 文件引擎模式标识 */
    private static final String MODE_FILE = "file";

    /** 失败计数：任一断言失败即非零退出 */
    private static int failed;

    /**
     * 入口：按 --mode 执行对应场景组，结果经 System.exit 表达。
     *
     * @param args 支持 --mode=sql|reactor|all 或 -m sql|reactor|all
     */
    public static void main(String[] args) {
        var cli = CommandLine.parse(args)
                .register("mode", "m", "mode: sql|reactor|all", MODE_ALL);
        if (cli.isHelp()) {
            cli.help();
            return;
        }
        var mode = cli.get("mode");
        if (mode == null || mode.isBlank()) {
            mode = MODE_ALL;
        }

        if (MODE_SQL.equals(mode) || MODE_ALL.equals(mode)) {
            runSyncScenarios();
        }
        if (MODE_REACTOR.equals(mode) || MODE_ALL.equals(mode)) {
            runReactorScenarios();
        }
        if (MODE_FILE.equals(mode) || MODE_ALL.equals(mode)) {
            runFileScenarios();
        }

        if (failed > 0) {
            log.error("[FAIL] memory-sql 场景失败数: {}", failed);
            System.exit(1);
        }
        log.info("[PASS] memory-sql all scenarios covered");
        System.exit(0);
    }

    /**
     * 同步 SQL 场景组：查询管道 + DML 往返值断言。
     */
    private static void runSyncScenarios() {
        var engine = new com.chua.datasource.support.engine.InMemoryEngine();
        engine.store("emp", List.of(empOf(1, "Alice", 20), empOf(2, "Bob", 30), empOf(3, "Cathy", 25)));

        check("COUNT(*)", 3, engine.querySql("SELECT COUNT(*) FROM emp").get(0).get("cnt"));
        check("投影不含未选列", false, engine.querySql("SELECT name, age FROM emp").get(0).containsKey("id"));
        check("WHERE =", "Alice", first(engine.querySql("SELECT name FROM emp WHERE age = 20"), "name"));
        check("WHERE 括号+参数绑定", "Bob", first(engine.querySql(
                "SELECT name FROM emp WHERE (age > ? OR age < ?) AND id = 2", 28, 21), "name"));
        check("LIKE 前缀", "Alice",
                first(engine.querySql("SELECT name FROM emp WHERE name LIKE 'A%'"), "name"));
        check("IN", 2, engine.querySql("SELECT name FROM emp WHERE id IN (1, 3)").size());
        check("BETWEEN", 2, engine.querySql("SELECT name FROM emp WHERE age BETWEEN 20 AND 25").size());
        check("IS NOT NULL", 3, engine.querySql("SELECT name FROM emp WHERE age IS NOT NULL").size());
        check("ORDER BY DESC LIMIT", "Bob",
                first(engine.querySql("SELECT name FROM emp ORDER BY age DESC LIMIT 1"), "name"));
        check("LIMIT OFFSET", "Bob",
                first(engine.querySql("SELECT name FROM emp ORDER BY age ASC LIMIT 1 OFFSET 2"), "name"));

        check("INSERT 参数化", 1,
                engine.executeSql("INSERT INTO emp (id, name, age) VALUES (?, ?, ?)", 9, "Zoe", 45));
        check("INSERT 读回", 45,
                engine.querySql("SELECT age FROM emp WHERE name = 'Zoe'").get(0).get("age"));
        check("UPDATE SET", 1, engine.executeSql("UPDATE emp SET age = ? WHERE name = 'Alice'", 99));
        check("UPDATE 读回新值", 99,
                engine.querySql("SELECT age FROM emp WHERE name = 'Alice'").get(0).get("age"));
        check("DELETE IN 参数化", 2, engine.executeSql("DELETE FROM emp WHERE id IN (?, ?)", 2, 9));
        check("DELETE 后计数", 2, engine.querySql("SELECT COUNT(*) FROM emp").get(0).get("cnt"));
    }

    /**
     * 响应式 SQL 场景组：query / typed query / execute / batch。
     */
    private static void runReactorScenarios() {
        var reactor = new com.chua.datasource.support.engine.InMemoryReactorEngine();
        reactor.store("r_emp", List.of(rowOf(1, "Alice", 20), rowOf(2, "Bob", 30), rowOf(3, "Cathy", 25)));

        var ordered = reactor.query("SELECT name FROM r_emp WHERE age > ? ORDER BY age DESC", 20)
                .map(row -> row.get("name")).collectList().block();
        mark("响应式 query 排序序列", List.of("Bob", "Cathy").equals(ordered));

        var inserted = reactor.execute(
                "INSERT INTO r_emp (id, name, age) VALUES (?, ?, ?)", 9, "Zoe", 45).block();
        mark("响应式 execute 返回", Integer.valueOf(1).equals(inserted));

        var batchParams = List.of(new Object[]{10, "R1", 11}, new Object[]{11, "R2", 12});
        var batchResult = reactor.batch(
                "INSERT INTO r_emp (id, name, age) VALUES (?, ?, ?)", batchParams).collectList().block();
        mark("响应式 batch 返回", batchResult != null && batchResult.equals(List.of(1, 1)));

        var count = reactor.query("SELECT COUNT(*) FROM r_emp").blockFirst();
        mark("响应式 COUNT", count != null && ((Number) count.get("cnt")).intValue() == 6);

        reactor.query("SELECT id, name, age FROM r_emp WHERE id = 9", Emp.class)
                .doOnNext(e -> mark("typed 转 bean", "Zoe".equals(e.getName()) && e.getAge() == 45))
                .blockLast();

        reactor.execute("DELETE FROM r_emp WHERE id IN (9, 10, 11)").block();
    }

    /**
     * 文件引擎 SQL 场景组：JSON 加载 → SQL CRUD → 自动持久化回读验证。
     *
     * @throws java.io.IOException 临时文件 IO 异常
     */
    private static void runFileScenarios() {
        try {
            var dir = java.nio.file.Paths.get(
                    System.getProperty("java.io.tmpdir"), "test-output", "memory-sql-file");
            java.nio.file.Files.createDirectories(dir);
            var json = dir.resolve("emp.json");
            java.nio.file.Files.writeString(json, "[{\"id\":1,\"name\":\"Alice\",\"age\":20},"
                    + "{\"id\":2,\"name\":\"Bob\",\"age\":30},{\"id\":3,\"name\":\"Cathy\",\"age\":25}]");

            var engine = new com.chua.datasource.support.engine.FileEngine();
            engine.load("emp", json.toString());

            check("file COUNT(*)", 3,
                    engine.querySql("SELECT COUNT(*) FROM emp").get(0).get("cnt"));
            check("file WHERE 参数", "Bob", first(engine.querySql(
                    "SELECT name FROM emp WHERE age > ? AND age < ?", 21, 31), "name"));
            check("file INSERT", 1,
                    engine.executeSql("INSERT INTO emp (id, name, age) VALUES (?, ?, ?)", 9, "Zoe", 45));
            check("file UPDATE", 1,
                    engine.executeSql("UPDATE emp SET age = ? WHERE name = 'Alice'", 99));
            check("file DELETE", 2,
                    engine.executeSql("DELETE FROM emp WHERE id IN (?, ?)", 2, 9));

            /* 重新加载同一文件，验证 DML 已自动持久化落盘 */
            var reloaded = new com.chua.datasource.support.engine.FileEngine();
            reloaded.load("emp", json.toString());
            check("file 持久化回读 COUNT", 2,
                    reloaded.querySql("SELECT COUNT(*) FROM emp").get(0).get("cnt"));
            check("file 持久化回读 UPDATE 值", 99,
                    reloaded.querySql("SELECT age FROM emp WHERE name = 'Alice'").get(0).get("age"));

            /* 响应式文件引擎：同一文件走 FileReactorEngine SQL 路径 */
            var rxEng = new com.chua.datasource.support.engine.FileReactorEngine();
            rxEng.getDelegate().load("emp", json.toString());

            var rxRow = rxEng.query("SELECT COUNT(*) FROM emp").blockFirst();
            check("file-reactor COUNT", 2,
                    rxRow != null ? ((Number) rxRow.get("cnt")).intValue() : null);

            var rxAlice = rxEng.query("SELECT name FROM emp WHERE name = 'Alice'", Emp.class)
                    .blockFirst();
            check("file-reactor typed", "Alice",
                    rxAlice != null ? rxAlice.getName() : null);

            var rxIns = rxEng.execute(
                    "INSERT INTO emp (id, name, age) VALUES (?, ?, ?)", 88, "Rex", 10).block();
            check("file-reactor execute", 1, rxIns);

            var rxBat = rxEng.batch(
                    "INSERT INTO emp (id, name, age) VALUES (?, ?, ?)",
                    List.of(new Object[]{89, "R2", 11}, new Object[]{90, "R3", 12}))
                    .collectList().block();
            check("file-reactor batch", List.of(1, 1), rxBat);

            check("file-reactor DELETE", 3,
                    rxEng.execute("DELETE FROM emp WHERE id IN (88,89,90)").block());

            try (var paths = java.nio.file.Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(pp -> {
                            try {
                                java.nio.file.Files.deleteIfExists(pp);
                            } catch (Exception ignored) {
                            }
                        });
            }
        } catch (java.io.IOException e) {
            failed++;
            log.error("[FAIL] file 场景 IO 异常: {}", e.getMessage());
        }
    }

    /* ==================== 断言输出辅助 ==================== */

    /**
     * 等值断言并打印单条结果标记。
     *
     * @param scene    场景名
     * @param expected 期望值
     * @param actual   实际值
     */
    private static void check(String scene, Object expected, Object actual) {
        var pass = expected == null ? actual == null : expected.equals(actual);
        if (pass) {
            log.info("[PASS] " + scene + " => " + actual);
        } else {
            failed++;
            log.info("[FAIL] " + scene + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 布尔标记（响应式回调内使用）。
     *
     * @param scene 场景名
     * @param pass  是否通过
     */
    private static void mark(String scene, boolean pass) {
        if (pass) {
            log.info("[PASS] " + scene);
        } else {
            failed++;
            log.info("[FAIL] " + scene);
        }
    }

    /**
     * 取结果首行的指定列值。
     *
     * @param rows  结果行集合
     * @param field 列名
     * @return 首行该列的值，空集合返回 null
     */
    private static Object first(List<Map<String, Object>> rows, String field) {
        return isNotEmpty(rows) ? rows.get(0).get(field) : null;
    }

    /**
     * 构造员工实体。
     *
     * @param id   主键
     * @param name 姓名
     * @param age  年龄
     * @return 实体实例
     */
    private static Emp empOf(int id, String name, int age) {
        var e = new Emp();
        e.setId(id);
        e.setName(name);
        e.setAge(age);
        return e;
    }

    /**
     * 构造 Map 行（响应式表数据）。
     *
     * @param id   主键
     * @param name 姓名
     * @param age  年龄
     * @return 行映射
     */
    private static Map<String, Object> rowOf(int id, String name, int age) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("age", age);
        return row;
    }
}

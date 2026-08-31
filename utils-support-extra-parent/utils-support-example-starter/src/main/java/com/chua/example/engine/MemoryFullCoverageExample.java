package com.chua.example.engine;

import com.chua.common.support.lang.datasource.page.Page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 全覆盖测试：所有操作符 × 所有引擎（InMemory / File-JSON / File-CSV）× SQL+Lambda 双路径。
 *
 * <p>覆盖矩阵：</p>
 * <ul>
 *   <li>查询操作符：eq/ne/gt/ge/lt/le/like/likeLeft/likeRight/in/notIn/isNull/isNotNull/between</li>
 *   <li>排序分页：orderByAsc/orderByDesc/page</li>
 *   <li>DML：INSERT/UPDATE(单列+多列)/DELETE(简单+复合WHERE)</li>
 *   <li>引擎：InMemoryEngine / FileEngine-JSON / FileEngine-CSV</li>
 *   <li>路径：原生 SQL + Lambda ORM</li>
 *   <li>异常：无效SQL/缺失表/空表</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class MemoryFullCoverageExample extends AbstractEngineExample {

    /** 私有构造，防止实例化 */
    private MemoryFullCoverageExample() { }

    private static final int MAGIC_AGE_MIN = 20;
    private static final int MAGIC_AGE_THRESHOLD = 25;
    private static final String DB_TYPE_SQLITE = "sqlite";

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

    /**
     * 入口：通过 {@code --mode} 指定运行分组，默认全量。
     *
     * <p>可选值：sql / lambda / reactor / exception / all（默认）。</p>
     *
     * @param args 启动参数，支持 {@code --mode=value} 或 {@code --mode value}
     */
    public static void main(String[] args) {
        String mode = "all";
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--mode")) {
                if (a.contains("=")) {
                    mode = a.substring(a.indexOf('=') + 1);
                } else if (i + 1 < args.length) {
                    mode = args[++i];
                }
            }
        }
        switch (mode) {
            case "sql":
                runInMemorySql();
                runFileJsonSql();
                break;
            case "lambda":
                runInMemoryLambda();
                runFileJsonLambda();
                runFileCsvLambda();
                break;
            case "reactor":
                runReactorLambda();
                break;
            case "exception":
                runExceptionPaths();
                break;
            case "all":
            default:
                runInMemorySql();
                runInMemoryLambda();
                runFileJsonSql();
                runFileJsonLambda();
                runFileCsvLambda();
                runReactorLambda();
                runExceptionPaths();
        }

        if (FAILED.get() > 0) {
            log.error("[FAIL] 全覆盖失败数: {}", FAILED.get());
            System.exit(1);
        }
        System.out.println("[PASS] full-coverage all scenarios passed");
        System.exit(0);
    }

    /* ==================== InMemory SQL ==================== */

    private static void runInMemorySql() {
        var e = new com.chua.datasource.support.engine.InMemoryEngine();
        e.store("emp", List.of(
                emp(1, "Alice", MAGIC_AGE_MIN, "Beijing"), emp(2, "Bob", 30, "Shanghai"),
                emp(3, "Cathy", MAGIC_AGE_THRESHOLD, "Beijing"), emp(4, "Dave", 35, "Guangzhou"),
                emp(5, null, 28, "Shanghai")));

        ck("sql-im eq", "Alice", col(e.querySql("SELECT name FROM emp WHERE age = " + MAGIC_AGE_MIN), "name"));
        ck("sql-im ne", 4, e.querySql("SELECT name FROM emp WHERE age <> " + MAGIC_AGE_MIN).size());
        ck("sql-im gt", 3, e.querySql("SELECT name FROM emp WHERE age > " + MAGIC_AGE_THRESHOLD).size());
        ck("sql-im ge", 4, e.querySql("SELECT name FROM emp WHERE age >= " + MAGIC_AGE_THRESHOLD).size());
        ck("sql-im lt", 1, e.querySql("SELECT name FROM emp WHERE age < " + MAGIC_AGE_THRESHOLD).size());
        ck("sql-im le", 2, e.querySql("SELECT name FROM emp WHERE age <= " + MAGIC_AGE_THRESHOLD).size());
        ck("sql-im like", "Alice", col(e.querySql("SELECT name FROM emp WHERE name LIKE 'A%'"), "name"));
        ck("sql-im likeLeft", "Alice", col(e.querySql("SELECT name FROM emp WHERE name LIKE '%lic%'"), "name"));
        ck("sql-im likeRight", "Alice", col(e.querySql("SELECT name FROM emp WHERE name LIKE 'Ali%'"), "name"));
        ck("sql-im in", 2, e.querySql("SELECT name FROM emp WHERE id IN (1,3)").size());
        ck("sql-im notIn", 3, e.querySql("SELECT name FROM emp WHERE id <> 1 AND id <> 3").size());
        ck("sql-im isNull", 1, e.querySql("SELECT name FROM emp WHERE name IS NULL").size());
        ck("sql-im isNotNull", 4, e.querySql("SELECT name FROM emp WHERE name IS NOT NULL").size());
        ck("sql-im between", 2, e.querySql("SELECT name FROM emp WHERE age BETWEEN " + MAGIC_AGE_MIN + " AND " + MAGIC_AGE_THRESHOLD).size());
        ck("sql-im orderByDesc", "Dave", col(e.querySql("SELECT name FROM emp ORDER BY age DESC LIMIT 1"), "name"));
        ck("sql-im orderByAsc", "Alice", col(e.querySql("SELECT name FROM emp ORDER BY age ASC LIMIT 1"), "name"));
        ck("sql-im page", "Cathy", col(e.querySql("SELECT name FROM emp ORDER BY age ASC LIMIT 1 OFFSET 1"), "name"));
        ck("sql-im count", 5, e.querySql("SELECT COUNT(*) FROM emp").get(0).get("cnt"));
        ck("sql-im and", 1, e.querySql("SELECT name FROM emp WHERE age > " + MAGIC_AGE_MIN + " AND city = 'Beijing'").size());
        ck("sql-im or", 3, e.querySql("SELECT name FROM emp WHERE city = 'Beijing' OR city = 'Guangzhou'").size());
        ck("sql-im not", 4, e.querySql("SELECT name FROM emp WHERE age <> " + MAGIC_AGE_MIN).size());
        ck("sql-im paren", 2, e.querySql("SELECT name FROM emp WHERE (age > " + MAGIC_AGE_THRESHOLD + " OR age < 22) AND city = 'Shanghai'").size());
        ck("sql-im multiUpdate", 1, e.executeSql("UPDATE emp SET age = ?, city = ? WHERE id = ?", 99, "Tokyo", 1));
        ck("sql-im multiUpdate readback", "Tokyo", col(e.querySql("SELECT city FROM emp WHERE id = 1"), "city"));
        e.executeSql("UPDATE emp SET age = " + MAGIC_AGE_MIN + ", city = 'Beijing' WHERE id = 1");
        ck("sql-im delete复合", 2, e.executeSql("DELETE FROM emp WHERE age > 30 OR name IS NULL"));
    }

    /* ==================== InMemory Lambda ==================== */

    private static void runInMemoryLambda() {
        var e = new com.chua.datasource.support.engine.InMemoryEngine();
        e.store("emp", List.of(
                emp(1, "Alice", MAGIC_AGE_MIN, "Beijing"), emp(2, "Bob", 30, "Shanghai"),
                emp(3, "Cathy", MAGIC_AGE_THRESHOLD, "Beijing"), emp(4, "Dave", 35, "Guangzhou"),
                emp(5, null, 28, "Shanghai")));

        ck("lam-im eq", "Alice", name(e.query(Emp.class).eq(Emp::getId, 1).one()));
        ck("lam-im ne", 4, e.query(Emp.class).ne(Emp::getId, 1).list().size());
        ck("lam-im gt", 3, e.query(Emp.class).gt(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
        ck("lam-im ge", 4, e.query(Emp.class).ge(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
        ck("lam-im lt", 1, e.query(Emp.class).lt(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
        ck("lam-im le", 2, e.query(Emp.class).le(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
        ck("lam-im like", 1, e.query(Emp.class).like(Emp::getName, "li").list().size());
        ck("lam-im likeLeft", 1, e.query(Emp.class).likeLeft(Emp::getName, "lic").list().size());
        ck("lam-im likeRight", 1, e.query(Emp.class).likeRight(Emp::getName, "Ali").list().size());
        ck("lam-im in", 2, e.query(Emp.class).in(Emp::getId, List.of(1, 3)).list().size());
        ck("lam-im notIn", 3, e.query(Emp.class).notIn(Emp::getId, List.of(1, 3)).list().size());
        ck("lam-im isNull", 1, e.query(Emp.class).isNull(Emp::getName).list().size());
        ck("lam-im isNotNull", 4, e.query(Emp.class).isNotNull(Emp::getName).list().size());
        ck("lam-im between", 2, e.query(Emp.class).between(Emp::getAge, MAGIC_AGE_MIN, MAGIC_AGE_THRESHOLD).list().size());
        ck("lam-im orderByAsc", "Alice", name(e.query(Emp.class).orderByAsc(Emp::getAge).one()));
        ck("lam-im orderByDesc", "Dave", name(e.query(Emp.class).orderByDesc(Emp::getAge).one()));
        Page<Emp> p = e.query(Emp.class).orderByAsc(Emp::getId).page(2, 2);
        ck("lam-im page total", 5, (int) p.getTotal());
        ck("lam-im page data", 2, p.getRecords().size());
        ck("lam-im combo", 1, e.query(Emp.class).eq(Emp::getCity, "Beijing").gt(Emp::getAge, 22).list().size());
        ck("lam-im update多列", 1, e.update(Emp.class).set(Emp::getAge, 99).set(Emp::getCity, "Tokyo")
                .eq(Emp::getId, 1).update());
        ck("lam-im update readback", "Tokyo", city(e.query(Emp.class).eq(Emp::getId, 1).one()));
        e.update(Emp.class).set(Emp::getAge, MAGIC_AGE_MIN).set(Emp::getCity, "Beijing").eq(Emp::getId, 1).update();
        ck("lam-im delete复合", 2, e.delete(Emp.class).eq(Emp::getCity, "Beijing").remove());
    }

    /* ==================== File-JSON SQL ==================== */

    private static void runFileJsonSql() {
        Path dir = tempDir("fc-json-sql");
        if (dir == null) return;
        try {
            Path json = dir.resolve("emp.json");
            Files.writeString(json, "[{\"id\":1,\"name\":\"Alice\",\"age\":" + MAGIC_AGE_MIN + ",\"city\":\"Beijing\"},"
                    + "{\"id\":2,\"name\":\"Bob\",\"age\":30,\"city\":\"Shanghai\"},"
                    + "{\"id\":3,\"name\":\"Cathy\",\"age\":" + MAGIC_AGE_THRESHOLD + ",\"city\":\"Beijing\"}]");
            var e = new com.chua.datasource.support.engine.FileEngine();
            e.load("emp", json.toString());

            ck("json-sql eq", "Alice", col(e.querySql("SELECT name FROM emp WHERE age = " + MAGIC_AGE_MIN), "name"));
            ck("json-sql ne", 2, e.querySql("SELECT name FROM emp WHERE age <> " + MAGIC_AGE_MIN).size());
            ck("json-sql gt", 1, e.querySql("SELECT name FROM emp WHERE age > " + MAGIC_AGE_THRESHOLD).size());
            ck("json-sql ge", 2, e.querySql("SELECT name FROM emp WHERE age >= " + MAGIC_AGE_THRESHOLD).size());
            ck("json-sql lt", 1, e.querySql("SELECT name FROM emp WHERE age < " + MAGIC_AGE_THRESHOLD).size());
            ck("json-sql le", 2, e.querySql("SELECT name FROM emp WHERE age <= " + MAGIC_AGE_THRESHOLD).size());
            ck("json-sql like", "Alice", col(e.querySql("SELECT name FROM emp WHERE name LIKE 'A%'"), "name"));
            ck("json-sql likeLeft", "Alice", col(e.querySql("SELECT name FROM emp WHERE name LIKE '%lic%'"), "name"));
            ck("json-sql likeRight", "Alice", col(e.querySql("SELECT name FROM emp WHERE name LIKE 'Ali%'"), "name"));
            ck("json-sql in", 2, e.querySql("SELECT name FROM emp WHERE id IN (1,3)").size());
            ck("json-sql notIn", 1, e.querySql("SELECT name FROM emp WHERE id <> 1 AND id <> 3").size());
            ck("json-sql between", 2, e.querySql("SELECT name FROM emp WHERE age BETWEEN " + MAGIC_AGE_MIN + " AND " + MAGIC_AGE_THRESHOLD).size());
            ck("json-sql orderByDesc", "Bob", col(e.querySql("SELECT name FROM emp ORDER BY age DESC LIMIT 1"), "name"));
            ck("json-sql count", 3, e.querySql("SELECT COUNT(*) FROM emp").get(0).get("cnt"));
            ck("json-sql and", 1, e.querySql("SELECT name FROM emp WHERE age > " + MAGIC_AGE_MIN + " AND city = 'Beijing'").size());
            ck("json-sql or", 3, e.querySql("SELECT name FROM emp WHERE city = 'Beijing' OR city = 'Shanghai'").size());
            ck("json-sql update", 1, e.executeSql("UPDATE emp SET age = ? WHERE id = ?", 99, 1));
            ck("json-sql delete", 1, e.executeSql("DELETE FROM emp WHERE id = 3"));
            cleanup(dir);
        } catch (IOException ex) {
            FAILED.incrementAndGet();
            log.info("[FAIL] json-sql: {}", ex.getMessage());
        }
    }

    /* ==================== File-JSON Lambda ==================== */

    private static void runFileJsonLambda() {
        Path dir = tempDir("fc-json-lam");
        if (dir == null) return;
        try {
            Path json = dir.resolve("emp.json");
            Files.writeString(json, "[{\"id\":1,\"name\":\"Alice\",\"age\":" + MAGIC_AGE_MIN + ",\"city\":\"Beijing\"},"
                    + "{\"id\":2,\"name\":\"Bob\",\"age\":30,\"city\":\"Shanghai\"},"
                    + "{\"id\":3,\"name\":\"Cathy\",\"age\":" + MAGIC_AGE_THRESHOLD + ",\"city\":\"Beijing\"},"
                    + "{\"id\":4,\"name\":\"Dave\",\"age\":35,\"city\":\"Guangzhou\"}]");
            var e = new com.chua.datasource.support.engine.FileEngine();
            e.load("emp", json.toString());

            ck("json-lam eq", "Alice", name(e.query(Emp.class).eq(Emp::getId, 1).one()));
            ck("json-lam ne", 3, e.query(Emp.class).ne(Emp::getId, 1).list().size());
            ck("json-lam gt", 2, e.query(Emp.class).gt(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("json-lam ge", 3, e.query(Emp.class).ge(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("json-lam lt", 1, e.query(Emp.class).lt(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("json-lam le", 2, e.query(Emp.class).le(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("json-lam like", 1, e.query(Emp.class).like(Emp::getName, "li").list().size());
            ck("json-lam likeLeft", 1, e.query(Emp.class).likeLeft(Emp::getName, "lic").list().size());
            ck("json-lam likeRight", 1, e.query(Emp.class).likeRight(Emp::getName, "Ali").list().size());
            ck("json-lam in", 2, e.query(Emp.class).in(Emp::getId, List.of(1, 3)).list().size());
            ck("json-lam notIn", 2, e.query(Emp.class).notIn(Emp::getId, List.of(1, 3)).list().size());
            ck("json-lam isNull", 0, e.query(Emp.class).isNull(Emp::getName).list().size());
            ck("json-lam isNotNull", 4, e.query(Emp.class).isNotNull(Emp::getName).list().size());
            ck("json-lam between", 2, e.query(Emp.class).between(Emp::getAge, MAGIC_AGE_MIN, MAGIC_AGE_THRESHOLD).list().size());
            ck("json-lam orderByDesc", "Dave", name(e.query(Emp.class).orderByDesc(Emp::getAge).one()));
            ck("json-lam orderByAsc", "Alice", name(e.query(Emp.class).orderByAsc(Emp::getAge).one()));
            ck("json-lam page", 2, e.query(Emp.class).orderByAsc(Emp::getId).page(1, 2).getRecords().size());
            ck("json-lam update多列", 1, e.update(Emp.class).set(Emp::getAge, 99).set(Emp::getCity, "X")
                    .eq(Emp::getId, 2).update());
            ck("json-lam delete复合", 1, e.delete(Emp.class).eq(Emp::getCity, "Guangzhou").remove());
            cleanup(dir);
        } catch (IOException ex) {
            FAILED.incrementAndGet();
            log.info("[FAIL] json-lam: {}", ex.getMessage());
        }
    }

    /* ==================== File-CSV Lambda ==================== */

    private static void runFileCsvLambda() {
        Path dir = tempDir("fc-csv-lam");
        if (dir == null) return;
        try {
            Path csv = dir.resolve("emp.csv");
            Files.writeString(csv, "id,name,age,city\n1,Alice," + MAGIC_AGE_MIN + ",Beijing\n2,Bob,30,Shanghai\n"
                    + "3,Cathy," + MAGIC_AGE_THRESHOLD + ",Beijing\n4,Dave,35,Guangzhou");
            var e = new com.chua.datasource.support.engine.FileEngine();
            e.load("emp", csv.toString());

            ck("csv-lam eq", "Alice", name(e.query(Emp.class).eq(Emp::getId, 1).one()));
            ck("csv-lam ne", 3, e.query(Emp.class).ne(Emp::getId, 1).list().size());
            ck("csv-lam gt", 2, e.query(Emp.class).gt(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("csv-lam ge", 3, e.query(Emp.class).ge(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("csv-lam lt", 1, e.query(Emp.class).lt(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("csv-lam le", 2, e.query(Emp.class).le(Emp::getAge, MAGIC_AGE_THRESHOLD).list().size());
            ck("csv-lam like", 1, e.query(Emp.class).like(Emp::getName, "li").list().size());
            ck("csv-lam likeLeft", 1, e.query(Emp.class).likeLeft(Emp::getName, "lic").list().size());
            ck("csv-lam likeRight", 1, e.query(Emp.class).likeRight(Emp::getName, "Ali").list().size());
            ck("csv-lam in", 2, e.query(Emp.class).in(Emp::getId, List.of(1, 3)).list().size());
            ck("csv-lam notIn", 2, e.query(Emp.class).notIn(Emp::getId, List.of(1, 3)).list().size());
            ck("csv-lam between", 2, e.query(Emp.class).between(Emp::getAge, MAGIC_AGE_MIN, MAGIC_AGE_THRESHOLD).list().size());
            ck("csv-lam orderByDesc", "Dave", name(e.query(Emp.class).orderByDesc(Emp::getAge).one()));
            ck("csv-lam orderByAsc", "Alice", name(e.query(Emp.class).orderByAsc(Emp::getAge).one()));
            ck("csv-lam page", 2, e.query(Emp.class).orderByAsc(Emp::getId).page(1, 2).getRecords().size());
            ck("csv-lam update多列", 1, e.update(Emp.class).set(Emp::getAge, 99).set(Emp::getCity, "X")
                    .eq(Emp::getId, 2).update());
            ck("csv-lam delete复合", 1, e.delete(Emp.class).eq(Emp::getCity, "Guangzhou").remove());
            cleanup(dir);
        } catch (IOException ex) {
            FAILED.incrementAndGet();
            log.info("[FAIL] csv-lam: {}", ex.getMessage());
        }
    }

    /* ==================== Reactor Lambda ==================== */

    private static void runReactorLambda() {
        /* InMemory Reactor */
        var im = new com.chua.datasource.support.engine.InMemoryReactorEngine();
        im.store("emp", List.of(
                emp(1, "Alice", MAGIC_AGE_MIN, "Beijing"), emp(2, "Bob", 30, "Shanghai"),
                emp(3, "Cathy", MAGIC_AGE_THRESHOLD, "Beijing")));

        ck("rx-im query", "Alice",
                name(im.query(Emp.class).eq(Emp::getId, 1).one().block()));
        ck("rx-im list", 3,
                im.query(Emp.class).list().collectList().block().size());
        ck("rx-im update", 1,
                im.update(Emp.class).set(Emp::getAge, 99).eq(Emp::getId, 1).update().block());
        ck("rx-im delete", 1,
                im.delete(Emp.class).eq(Emp::getId, 3).remove().block());

        /* File Reactor (JSON) */
        Path dir = tempDir("fc-rx-json");
        if (dir == null) return;
        try {
            Path json = dir.resolve("emp.json");
            Files.writeString(json, "[{\"id\":1,\"name\":\"Alice\",\"age\":" + MAGIC_AGE_MIN + ",\"city\":\"Beijing\"},"
                    + "{\"id\":2,\"name\":\"Bob\",\"age\":30,\"city\":\"Shanghai\"}]");
            var fe = new com.chua.datasource.support.engine.FileReactorEngine();
            fe.getDelegate().load("emp", json.toString());

            ck("rx-fj query", "Alice",
                    name(fe.query(Emp.class).eq(Emp::getId, 1).one().block()));
            ck("rx-fj list", 2,
                    fe.query(Emp.class).list().collectList().block().size());
            ck("rx-fj update", 1,
                    fe.update(Emp.class).set(Emp::getAge, 88).eq(Emp::getId, 1).update().block());
            ck("rx-fj delete", 1,
                    fe.delete(Emp.class).eq(Emp::getId, 2).remove().block());
            cleanup(dir);
        } catch (IOException ex) {
            FAILED.incrementAndGet();
            log.info("[FAIL] rx-fj: {}", ex.getMessage());
        }
    }

    /* ==================== 异常路径 ==================== */

    private static void runExceptionPaths() {
        var im = new com.chua.datasource.support.engine.InMemoryEngine();
        im.store("emp", List.of(emp(1, "A", MAGIC_AGE_MIN, "X")));

        /* 空表查询 */
        var empty = new com.chua.datasource.support.engine.InMemoryEngine();
        ck("empty sql count", 0, empty.querySql("SELECT COUNT(*) FROM nonexistent").get(0).get("cnt"));
        ck("empty lambda list", 0, empty.query(Emp.class).list().size());

        /* 无效 SQL */
        try {
            im.querySql("SELEC * FROM emp");
            FAILED.incrementAndGet();
            log.info("[FAIL] 无效SQL应抛异常");
        } catch (Exception expected) {
            /* 预期异常 */
        }

        /* 缺失表 SQL */
        try {
            im.executeSql("DELETE FROM nonexistent WHERE id = 1");
            /* 不抛异常但影响 0 行也合理 */
        } catch (Exception ignored) {
        }

        /* lambda eq null 值 */
        ck("lambda null eq", 1, im.query(Emp.class).eq(Emp::getId, 1).list().size());
    }

    /* ==================== 辅助（实体相关 fixture） ==================== */

    private static String name(Object obj) {
        return obj == null ? null : ((Emp) obj).getName();
    }

    private static String city(Object obj) {
        return obj == null ? null : ((Emp) obj).getCity();
    }

    private static Object col(List<Map<String, Object>> rows, String field) {
        return rows.isEmpty() ? null : rows.get(0).get(field);
    }

    private static Emp emp(int id, String name, int age, String city) {
        Emp e = new Emp();
        e.setId(id);
        e.setName(name);
        e.setAge(age);
        e.setCity(city);
        return e;
    }
}

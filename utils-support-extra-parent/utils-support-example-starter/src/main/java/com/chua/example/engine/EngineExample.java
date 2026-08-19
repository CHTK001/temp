package com.chua.example.engine;

import com.chua.common.support.lang.datasource.engine.Engine;
import com.chua.common.support.lang.datasource.page.Page;
import com.chua.common.support.utils.CommandLine;
import com.chua.datasource.support.engine.FileEngine;
import com.chua.datasource.support.engine.InMemoryEngine;
import com.chua.duckdb.support.engine.DuckDBEngine;
import com.chua.elasticsearch.support.engine.ElasticsearchEngine;
import com.chua.hbase.support.engine.HBaseEngine;
import com.chua.influxdb.support.engine.InfluxDbEngine;
import com.chua.lucene.support.engine.LuceneEngine;
import com.chua.mysql.support.engine.MysqlEngine;
import com.chua.neo4j.support.engine.Neo4jEngine;
import com.chua.nitrite.support.engine.NitriteEngine;
import com.chua.parquet.support.engine.ParquetEngine;
import com.chua.solr.support.engine.SolrEngine;
import com.chua.sqlite.support.engine.SqliteEngine;
import com.chua.tablesaw.support.engine.TablesawEngine;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * 统一 EngineExample：通过命令行参数 {@code --type} 切换引擎，使用统一 User 实体和统一 CRUD 测试流。
 *
 * <p>支持引擎：neo4j、sqlite、mysql、lucene、file、memory、duckdb、hbase、influxdb、
 * parquet、elasticsearch、tablesaw、solr、nitrite。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认 sqlite 引擎，运行完整 CRUD 自检
 *   java EngineExample
 *
 *   # 指定引擎类型
 *   java EngineExample --type mysql
 *   java EngineExample -t elasticsearch
 *
 *   # 打印帮助
 *   java EngineExample --help
 * </pre>
 *
 * @author CH
 * @since 2024/12/12
 */
@Slf4j
public class EngineExample {

    /**
     * 测试用户实体。
     *
     * @param id   主键
     * @param name 姓名
     * @param age  年龄
     * @param role 角色
 * @author CH
     * @since 4.0.0.42
     */
    public record User(Integer id, String name, Integer age, String role) {}

    /**
     * 默认引擎类型
     */
    private static final String DEFAULT_ENGINE_TYPE = "sqlite";

    /**
     * Nitrite 默认数据库文件路径
     */
    private static final String DEFAULT_NITRITE_FILE = "data/nitrite.db";

    /**
     * SQLite 默认数据库文件路径
     */
    private static final String DEFAULT_SQLITE_FILE = "data/example.db";

    /**
     * MySQL 默认端口
     */
    private static final int DEFAULT_MYSQL_PORT = 3306;

    /**
     * MySQL 默认主机
     */
    private static final String DEFAULT_MYSQL_HOST = "localhost";

    /**
     * MySQL 默认数据库
     */
    private static final String DEFAULT_MYSQL_DATABASE = "test_db";

    /**
     * MySQL 默认用户名
     */
    private static final String DEFAULT_MYSQL_USER = "root";

    /**
     * MySQL 默认密码
     */
    private static final String DEFAULT_MYSQL_PASSWORD = "123456";

    /**
     * Neo4j 默认 URI
     */
    private static final String DEFAULT_NEO4J_URI = "bolt://localhost:7687";

    /**
     * Neo4j 默认用户名
     */
    private static final String DEFAULT_NEO4J_USER = "neo4j";

    /**
     * Neo4j 默认密码
     */
    private static final String DEFAULT_NEO4J_PASSWORD = "neo4j123";

    /**
     * Lucene 默认索引路径
     */
    private static final String DEFAULT_LUCENE_INDEX = "data/lucene-index";

    /**
     * File 引擎默认目录
     */
    private static final String DEFAULT_FILE_DIR = "data/filestore";

    /**
     * DuckDB 默认文件路径
     */
    private static final String DEFAULT_DUCKDB_FILE = "data/duckdb.db";

    /**
     * HBase 默认 ZK
     */
    private static final String DEFAULT_HBASE_ZK = "localhost:2181";

    /**
     * InfluxDB 默认 URL
     */
    private static final String DEFAULT_INFLUXDB_URL = "http://localhost:8086";

    /**
     * Parquet 默认文件路径
     */
    private static final String DEFAULT_PARQUET_FILE = "data/test.parquet";

    /**
     * Elasticsearch 默认 URL
     */
    private static final String DEFAULT_ELASTICSEARCH_URL = "http://172.16.0.40:9200";

    /**
     * Solr 默认 URL
     */
    private static final String DEFAULT_SOLR_URL = "http://172.16.0.40:18983/solr";

    /**
     * Tablesaw 默认 CSV 文件路径
     */
    private static final String DEFAULT_TABLESAW_FILE = "data/test.csv";

    /**
     * 测试用户数据
     */
    private static final List<User> TEST_USERS = List.of(
            new User(1, "Alice", 30, "admin"),
            new User(2, "Bob", 25, "user"),
            new User(3, "Charlie", 35, "user"),
            new User(4, "David", 28, "admin"),
            new User(5, "Eve", 22, "user")
    );

    private static void printStep(String step) {
        System.out.println("\n========================================");
        System.out.println("  " + step);
        System.out.println("========================================");
    }

    private static void printResult(String label, Object actual) {
        System.out.printf("  %-40s : %s%n", label, actual);
    }

    private static void assertEq(String label, Object actual, Object expected) {
        boolean ok = Objects.equals(actual, expected);
        printResult(label + " => " + actual, ok ? "PASS" : "FAIL (expected " + expected + ")");
        if (!ok) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(String label, boolean condition) {
        printResult(label, condition ? "PASS" : "FAIL");
        if (!condition) {
            throw new AssertionError(label + " failed");
        }
    }

    private static void assertContains(String label, List<?> list, Object expected) {
        boolean contains = list.stream().anyMatch(item -> {
            try {
                Method m = item.getClass().getMethod("name");
                return Objects.equals(m.invoke(item), expected);
            } catch (Exception e) {
                return false;
            }
        });
        printResult(label + " (contains " + expected + ")", contains ? "PASS" : "FAIL");
        if (!contains) {
            throw new AssertionError(label + ": list does not contain " + expected);
        }
    }

    private static <T> void printList(String label, List<T> list) {
        System.out.println("  " + label + " (" + list.size() + " rows):");
        for (T item : list) {
            System.out.println("    " + item);
        }
    }

    private static Engine setupDataSource(String engineType) {
        switch (engineType) {
            case "neo4j":
                return setupNeo4j();
            case "sqlite":
                return setupSqlite();
            case "mysql":
                return setupMySql();
            case "lucene":
                return setupLucene();
            case "file":
                return setupFile();
            case "memory":
                return setupInMemory();
            case "duckdb":
                return setupDuckDB();
            case "hbase":
                return setupHBase();
            case "influxdb":
                return setupInfluxDb();
            case "parquet":
                return setupParquet();
            case "elasticsearch":
                return setupElasticsearch();
            case "tablesaw":
                return setupTablesaw();
            case "solr":
                return setupSolr();
            case "nitrite":
                return setupNitrite();
            default:
                throw new IllegalArgumentException("Unsupported engine type: " + engineType);
        }
    }

    private static Engine setupNeo4j() {
        String uri = System.getProperty("neo4j.uri", DEFAULT_NEO4J_URI);
        String user = System.getProperty("neo4j.user", DEFAULT_NEO4J_USER);
        String password = System.getProperty("neo4j.password", DEFAULT_NEO4J_PASSWORD);
        System.setProperty("ENABLE_NEO4J_BOOTSTRAP_FACTORY_CLASS", "deprecated");
        Neo4jEngine engine = new Neo4jEngine();
        engine.connect(uri, user, password);
        System.out.println("[SETUP] Neo4j engine connected: " + uri);
        return engine;
    }

    private static Engine setupSqlite() {
        String dbFile = System.getProperty("sqlite.file", DEFAULT_SQLITE_FILE);
        System.out.println("[SETUP] SQLite file: " + dbFile);
        return new SqliteEngine().addDataSource("default", dbFile);
    }

    private static Engine setupMySql() {
        String host = System.getProperty("mysql.host", DEFAULT_MYSQL_HOST);
        int port = Integer.parseInt(System.getProperty("mysql.port", String.valueOf(DEFAULT_MYSQL_PORT)));
        String database = System.getProperty("mysql.database", DEFAULT_MYSQL_DATABASE);
        String username = System.getProperty("mysql.user", DEFAULT_MYSQL_USER);
        String password = System.getProperty("mysql.password", DEFAULT_MYSQL_PASSWORD);
        System.out.println("[SETUP] MySQL: jdbc:mysql://" + host + ":" + port + "/" + database);
        return new MysqlEngine().addDataSource("default", host, port, database, username, password);
    }

    private static Engine setupLucene() {
        String indexPath = System.getProperty("lucene.index", DEFAULT_LUCENE_INDEX);
        System.out.println("[SETUP] Lucene index: " + indexPath);
        return new LuceneEngine(Paths.get(indexPath));
    }

    private static Engine setupFile() {
        String dataDir = System.getProperty("file.dir", DEFAULT_FILE_DIR);
        System.out.println("[SETUP] File store: " + dataDir);
        return new FileEngine();
    }

    private static Engine setupInMemory() {
        System.out.println("[SETUP] InMemory engine");
        return new InMemoryEngine();
    }

    private static Engine setupDuckDB() {
        String dbFile = System.getProperty("duckdb.file", DEFAULT_DUCKDB_FILE);
        System.out.println("[SETUP] DuckDB: " + dbFile);
        return new DuckDBEngine();
    }

    private static Engine setupHBase() {
        String zkQuorum = System.getProperty("hbase.zk", DEFAULT_HBASE_ZK);
        System.out.println("[SETUP] HBase ZK: " + zkQuorum);
        return new HBaseEngine();
    }

    private static Engine setupInfluxDb() {
        String url = System.getProperty("influxdb.url", DEFAULT_INFLUXDB_URL);
        String token = System.getProperty("influxdb.token", "");
        System.out.println("[SETUP] InfluxDB: " + url);
        return new InfluxDbEngine();
    }

    private static Engine setupParquet() {
        String filePath = System.getProperty("parquet.file", DEFAULT_PARQUET_FILE);
        System.out.println("[SETUP] Parquet: " + filePath);
        return new ParquetEngine();
    }

    private static Engine setupElasticsearch() {
        String esUrl = System.getProperty("elasticsearch.url", DEFAULT_ELASTICSEARCH_URL);
        System.out.println("[SETUP] Elasticsearch engine: " + esUrl);
        return new ElasticsearchEngine().addDataSource("default", new SimpleEngineDataSource(esUrl));
    }

    private static Engine setupTablesaw() {
        String csvFile = System.getProperty("tablesaw.file", DEFAULT_TABLESAW_FILE);
        System.out.println("[SETUP] Tablesaw: " + csvFile);
        try {
            Path p = Paths.get(csvFile);
            if (Files.notExists(p)) {
                Files.createDirectories(p.getParent());
                try (BufferedWriter w = Files.newBufferedWriter(p)) {
                    w.write("id,name,age,role\n");
                    for (var u : TEST_USERS) {
                        w.write(u.id() + "," + u.name() + "," + u.age() + "," + u.role());
                        w.newLine();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[WARN] Tablesaw CSV 创建失败: " + e.getMessage());
        }
        return new TablesawEngine().load("test", csvFile);
    }

    private static Engine setupSolr() {
        String solrUrl = System.getProperty("solr.url", DEFAULT_SOLR_URL);
        System.out.println("[SETUP] Solr engine: " + solrUrl);
        return new SolrEngine().addDataSource("default", new SimpleEngineDataSource(solrUrl));
    }

    private static Engine setupNitrite() {
        String filePath = System.getProperty("nitrite.file", DEFAULT_NITRITE_FILE);
        System.out.println("[SETUP] Nitrite file: " + filePath);
        return new NitriteEngine().addDataSource("default", filePath);
    }

    private static void storeData(String engineType, Engine engine) throws Exception {
        engine.store("User", TEST_USERS);
    }

    private static void runCommonCrudTests(String engineType, Engine engine) throws Exception {
        printStep("STEP 1: list() - query all users");
        List<?> allUsers = engine.query(User.class).list();
        printList("All Users", allUsers);
        assertEq("Total users", allUsers.size(), TEST_USERS.size());

        printStep("STEP 2: eq() - find by name=Alice");
        List<?> alice = engine.query(User.class).eq(User::name, "Alice").list();
        printList("eq(name=Alice)", alice);
        assertEq("eq result size", alice.size(), 1);

        printStep("STEP 3: gt() - age > 26");
        List<?> gtUsers = engine.query(User.class).gt(User::age, 26).list();
        printList("gt(age>26)", gtUsers);
        assertTrue("gt(age>26) count >= 2", gtUsers.size() >= 2);

        printStep("STEP 4: between() - age between 25 and 32");
        List<?> betweenUsers = engine.query(User.class).between(User::age, 25, 32).list();
        printList("between(age 25-32)", betweenUsers);
        assertTrue("between count >= 2", betweenUsers.size() >= 2);

        printStep("STEP 5: isNull() - role IS NULL");
        List<?> nullRole = engine.query(User.class).isNull(User::role).list();
        printList("isNull(role)", nullRole);
        assertEq("isNull count", nullRole.size(), 0);

        printStep("STEP 6: isNotNull() - role IS NOT NULL");
        List<?> notNullRole = engine.query(User.class).isNotNull(User::role).list();
        printList("isNotNull(role)", notNullRole);
        assertEq("isNotNull count", notNullRole.size(), TEST_USERS.size());

        printStep("STEP 7: in() - name IN [Alice, Bob]");
        List<?> inUsers = engine.query(User.class).in(User::name, List.of("Alice", "Bob")).list();
        printList("in([Alice,Bob])", inUsers);
        assertEq("in count", inUsers.size(), 2);

        printStep("STEP 8: notIn() - name NOT IN [Alice]");
        List<?> notInUsers = engine.query(User.class).notIn(User::name, List.of("Alice")).list();
        printList("notIn([Alice])", notInUsers);
        assertEq("notIn count", notInUsers.size(), TEST_USERS.size() - 1);

        printStep("STEP 9: like() - name LIKE 'A%'");
        List<?> likeUsers = engine.query(User.class).like(User::name, "A%").list();
        printList("like(name LIKE 'A%')", likeUsers);
        assertContains("like contains Alice", likeUsers, "Alice");

        printStep("STEP 10: one() - find single user by id=1");
        Object oneUser = engine.query(User.class).eq(User::id, 1).one();
        System.out.println("  one() result: " + oneUser);
        assertTrue("one() not null", oneUser != null);

        printStep("STEP 11: page() - page 1 size 2");
        Page<?> page = engine.query(User.class).page(1, 2);
        printList("page(1,2)", page.getRecords());
        assertEq("page size", page.getRecords().size(), 2);
        assertEq("page total", page.getTotal(), (long) TEST_USERS.size());

        printStep("STEP 12: update() - set age=31 where name=Alice");
        int updated = engine.update(User.class)
                .set(User::age, 31)
                .eq(User::name, "Alice")
                .update();
        System.out.println("  update affected rows: " + updated);
        assertTrue("update affected > 0", updated > 0);

        printStep("STEP 13: verify update");
        List<?> afterUpdate = engine.query(User.class).eq(User::name, "Alice").list();
        System.out.println("  after update Alice: " + afterUpdate);
        assertTrue("Alice still found", !afterUpdate.isEmpty());

        printStep("STEP 14: delete() - remove where name=Bob");
        int deleted = engine.delete(User.class)
                .eq(User::name, "Bob")
                .remove();
        System.out.println("  delete affected rows: " + deleted);
        assertTrue("delete affected > 0", deleted > 0);

        printStep("STEP 15: verify delete");
        List<?> afterDelete = engine.query(User.class).eq(User::name, "Bob").list();
        System.out.println("  after delete Bob: " + afterDelete);
        assertEq("Bob not found", afterDelete.size(), 0);

        System.out.println("\n========================================");
        System.out.println("  ALL TESTS PASSED");
        System.out.println("========================================");
    }

    public static void main(String[] args) throws Exception {
        CommandLine cli = CommandLine.parse(args)
                .program("EngineExample")
                .register("type", "t", "引擎类型（neo4j|sqlite|mysql|lucene|file|memory|duckdb|hbase|influxdb|parquet|elasticsearch|tablesaw|solr|nitrite）", DEFAULT_ENGINE_TYPE)
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String engineType = cli.get("type", DEFAULT_ENGINE_TYPE);
        System.out.println("[MAIN] Engine type: " + engineType);

        Engine engine = null;
        try {
            engine = setupDataSource(engineType);
            storeData(engineType, engine);
            runCommonCrudTests(engineType, engine);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (engine != null) {
                try {
                    engine.close();
                    System.out.println("[MAIN] Engine closed.");
                } catch (Exception e) {
                    System.err.println("[WARN] Error closing engine: " + e.getMessage());
                }
            }
        }
    }
}

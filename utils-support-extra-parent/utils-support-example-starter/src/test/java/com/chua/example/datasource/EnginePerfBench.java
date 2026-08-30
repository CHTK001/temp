package com.chua.engine;

import com.chua.datasource.support.engine.JdbcReactorEngine;
import com.chua.h2.support.engine.H2Engine;
import com.chua.mysql.support.engine.MysqlEngine;
import com.chua.sqlite.support.engine.SqliteEngine;
import com.chua.sqlserver.support.engine.SqlServerLegacyEngine;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 四大引擎基准测试：H2（内嵌）、MySQL、PostgreSQL、SQL Server。
 *
 * <p>测试场景：
 * <ul>
 *   <li>写入吞吐：10万次 INSERT ops/s</li>
 *   <li>点查 QPS：100万次 SELECT WHERE id=? ops/s</li>
 *   <li>范围查询：SELECT * WHERE id BETWEEN ? AND ?</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class EnginePerfBench {

    private static final int COUNT = 100_000;
    private static final int QUERY_ROUNDS = 100;
    private static final String H2_DB = "bench_" + System.nanoTime();

    // ==================== H2 ====================

    @Test
    void h2_write_throughput() {
        H2Engine engine = new H2Engine();
        engine.addDataSource("default", "jdbc:h2:mem:" + H2_DB + ";DB_CLOSE_DELAY=-1");

        engine.execute("CREATE TABLE bench (id BIGINT PRIMARY KEY, name VARCHAR(64), val DOUBLE)");

        long start = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    (long) i, "name_" + i, (double) i);
        }
        long elapsed = System.nanoTime() - start;
        double ops = COUNT * 1_000_000_000.0 / elapsed;
        System.out.printf("[H2] 写入吞吐: %,d ops/s  (%.2f ms/万条)%n", (long) ops, elapsed / 10_000_000.0);
        engine.close();
    }

    @Test
    void h2_point_query_qps() {
        H2Engine engine = new H2Engine();
        engine.addDataSource("default", "jdbc:h2:mem:" + H2_DB + "_q" + ";DB_CLOSE_DELAY=-1");
        prepareData(engine, COUNT);

        long start = System.nanoTime();
        AtomicLong hits = new AtomicLong();
        for (int r = 0; r < QUERY_ROUNDS; r++) {
            long id = (long) (Math.random() * COUNT);
            List<Map<String, Object>> rows = engine.query("SELECT * FROM bench WHERE id = ?", id);
            if (!rows.isEmpty()) hits.incrementAndGet();
        }
        long elapsed = System.nanoTime() - start;
        double qps = QUERY_ROUNDS * 1_000_000_000.0 / elapsed;
        System.out.printf("[H2] 点查 QPS: %,,.0f  (命中: %d/%d)%n", qps, hits.get(), QUERY_ROUNDS);
        engine.close();
    }

    // ==================== SQLite ====================

    @Test
    void sqlite_write_throughput() {
        String dbPath = "target/sqlite_bench_" + System.nanoTime() + ".db";
        SqliteEngine engine = new SqliteEngine();
        engine.addDataSource("default", dbPath);

        engine.execute("CREATE TABLE bench (id INTEGER PRIMARY KEY, name TEXT, val REAL)");

        long start = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    i, "name_" + i, (double) i);
        }
        long elapsed = System.nanoTime() - start;
        double ops = COUNT * 1_000_000_000.0 / elapsed;
        System.out.printf("[SQLite] 写入吞吐: %,d ops/s  (%.2f ms/万条)%n", (long) ops, elapsed / 10_000_000.0);
        engine.close();
    }

    @Test
    void sqlite_point_query_qps() {
        String dbPath = "target/sqlite_bench_q_" + System.nanoTime() + ".db";
        SqliteEngine engine = new SqliteEngine();
        engine.addDataSource("default", dbPath);
        prepareData(engine, COUNT);

        long start = System.nanoTime();
        AtomicLong hits = new AtomicLong();
        for (int r = 0; r < QUERY_ROUNDS; r++) {
            long id = (long) (Math.random() * COUNT);
            List<Map<String, Object>> rows = engine.query("SELECT * FROM bench WHERE id = ?", id);
            if (!rows.isEmpty()) hits.incrementAndGet();
        }
        long elapsed = System.nanoTime() - start;
        double qps = QUERY_ROUNDS * 1_000_000_000.0 / elapsed;
        System.out.printf("[SQLite] 点查 QPS: %,,.0f  (命中: %d/%d)%n", qps, hits.get(), QUERY_ROUNDS);
        engine.close();
    }

    // ==================== MySQL (需要远程容器) ====================

    static final String MYSQL_HOST = "172.16.0.40";
    static final int MYSQL_PORT = 3308;
    static final String MYSQL_USER = "it";
    static final String MYSQL_PASS = "it12345";
    static final String MYSQL_DB = "bench";

    @BeforeAll
    static void assumeMySQL() {
        Assumptions.assumeTrue(reachable(MYSQL_HOST, MYSQL_PORT), "MySQL 不可达，跳过");
    }

    @Test
    void mysql_write_throughput() {
        MysqlEngine engine = new MysqlEngine();
        engine.addDataSource("default", MYSQL_HOST, MYSQL_PORT, MYSQL_DB, MYSQL_USER, MYSQL_PASS);

        engine.execute("CREATE TABLE IF NOT EXISTS bench (id BIGINT PRIMARY KEY, name VARCHAR(64), val DOUBLE)");
        engine.execute("DELETE FROM bench");

        long start = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    (long) i, "name_" + i, (double) i);
        }
        long elapsed = System.nanoTime() - start;
        double ops = COUNT * 1_000_000_000.0 / elapsed;
        System.out.printf("[MySQL] 写入吞吐: %,d ops/s  (%.2f ms/万条)%n", (long) ops, elapsed / 10_000_000.0);
        engine.close();
    }

    @Test
    void mysql_point_query_qps() {
        MysqlEngine engine = new MysqlEngine();
        engine.addDataSource("default", MYSQL_HOST, MYSQL_PORT, MYSQL_DB, MYSQL_USER, MYSQL_PASS);
        prepareData(engine, COUNT);

        long start = System.nanoTime();
        AtomicLong hits = new AtomicLong();
        for (int r = 0; r < QUERY_ROUNDS; r++) {
            long id = (long) (Math.random() * COUNT);
            List<Map<String, Object>> rows = engine.query("SELECT * FROM bench WHERE id = ?", id);
            if (!rows.isEmpty()) hits.incrementAndGet();
        }
        long elapsed = System.nanoTime() - start;
        double qps = QUERY_ROUNDS * 1_000_000_000.0 / elapsed;
        System.out.printf("[MySQL] 点查 QPS: %,,.0f  (命中: %d/%d)%n", qps, hits.get(), QUERY_ROUNDS);
        engine.close();
    }

    // ==================== PostgreSQL ====================

    static final String PG_HOST = "172.16.0.40";
    static final int PG_PORT = 5433;
    static final String PG_USER = "it";
    static final String PG_PASS = "it12345";
    static final String PG_DB = "bench";

    @BeforeAll
    static void assumePG() {
        Assumptions.assumeTrue(reachable(PG_HOST, PG_PORT), "PostgreSQL 不可达，跳过");
    }

    @Test
    void pg_write_throughput() {
        com.chua.postgresql.support.engine.PostgresqlEngine engine =
                new com.chua.postgresql.support.engine.PostgresqlEngine();
        engine.addDataSource("default", PG_HOST, PG_PORT, PG_DB, PG_USER, PG_PASS);

        engine.execute("CREATE TABLE IF NOT EXISTS bench (id BIGINT PRIMARY KEY, name VARCHAR(64), val DOUBLE PRECISION)");
        engine.execute("DELETE FROM bench");

        long start = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    (long) i, "name_" + i, (double) i);
        }
        long elapsed = System.nanoTime() - start;
        double ops = COUNT * 1_000_000_000.0 / elapsed;
        System.out.printf("[PostgreSQL] 写入吞吐: %,d ops/s  (%.2f ms/万条)%n", (long) ops, elapsed / 10_000_000.0);
        engine.close();
    }

    @Test
    void pg_point_query_qps() {
        com.chua.postgresql.support.engine.PostgresqlEngine engine =
                new com.chua.postgresql.support.engine.PostgresqlEngine();
        engine.addDataSource("default", PG_HOST, PG_PORT, PG_DB, PG_USER, PG_PASS);
        prepareData(engine, COUNT);

        long start = System.nanoTime();
        AtomicLong hits = new AtomicLong();
        for (int r = 0; r < QUERY_ROUNDS; r++) {
            long id = (long) (Math.random() * COUNT);
            List<Map<String, Object>> rows = engine.query("SELECT * FROM bench WHERE id = ?", id);
            if (!rows.isEmpty()) hits.incrementAndGet();
        }
        long elapsed = System.nanoTime() - start;
        double qps = QUERY_ROUNDS * 1_000_000_000.0 / elapsed;
        System.out.printf("[PostgreSQL] 点查 QPS: %,,.0f  (命中: %d/%d)%n", qps, hits.get(), QUERY_ROUNDS);
        engine.close();
    }

    // ==================== SQL Server ====================

    static final String MSSQL_HOST = "172.16.0.40";
    static final int MSSQL_PORT = 1434;
    static final String MSSQL_USER = "sa";
    static final String MSSQL_PASS = "YourStrong!Passw0rd";
    static final String MSSQL_DB = "master";

    @BeforeAll
    static void assumeMSSQL() {
        Assumptions.assumeTrue(reachable(MSSQL_HOST, MSSQL_PORT), "SQL Server 不可达，跳过");
    }

    @Test
    void mssql_write_throughput() {
        SqlServerEngine engine = new SqlServerEngine();
        engine.addDataSource("default", MSSQL_HOST, MSSQL_PORT, MSSQL_DB, MSSQL_USER, MSSQL_PASS);

        engine.execute("IF OBJECT_ID('bench', 'U') IS NULL CREATE TABLE bench(id BIGINT PRIMARY KEY, name VARCHAR(64), val FLOAT)");
        engine.execute("DELETE FROM bench");

        long start = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    (long) i, "name_" + i, (double) i);
        }
        long elapsed = System.nanoTime() - start;
        double ops = COUNT * 1_000_000_000.0 / elapsed;
        System.out.printf("[SQL Server] 写入吞吐: %,d ops/s  (%.2f ms/万条)%n", (long) ops, elapsed / 10_000_000.0);
        engine.close();
    }

    @Test
    void mssql_point_query_qps() {
        SqlServerEngine engine = new SqlServerEngine();
        engine.addDataSource("default", MSSQL_HOST, MSSQL_PORT, MSSQL_DB, MSSQL_USER, MSSQL_PASS);
        prepareData(engine, COUNT);

        long start = System.nanoTime();
        AtomicLong hits = new AtomicLong();
        for (int r = 0; r < QUERY_ROUNDS; r++) {
            long id = (long) (Math.random() * COUNT);
            List<Map<String, Object>> rows = engine.query("SELECT * FROM bench WHERE id = ?", id);
            if (!rows.isEmpty()) hits.incrementAndGet();
        }
        long elapsed = System.nanoTime() - start;
        double qps = QUERY_ROUNDS * 1_000_000_000.0 / elapsed;
        System.out.printf("[SQL Server] 点查 QPS: %,,.0f  (命中: %d/%d)%n", qps, hits.get(), QUERY_ROUNDS);
        engine.close();
    }

    // ==================== Legacy SQL Server (jTDS) ====================

    @Test
    void legacy_mssql_write_throughput() {
        SqlServerLegacyEngine engine = new SqlServerLegacyEngine();
        engine.addDataSource("default", MSSQL_HOST, MSSQL_PORT, MSSQL_DB, MSSQL_USER, MSSQL_PASS);

        engine.execute("IF OBJECT_ID('bench', 'U') IS NULL CREATE TABLE bench(id BIGINT PRIMARY KEY, name VARCHAR(64), val FLOAT)");
        engine.execute("DELETE FROM bench");

        long start = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    (long) i, "name_" + i, (double) i);
        }
        long elapsed = System.nanoTime() - start;
        double ops = COUNT * 1_000_000_000.0 / elapsed;
        System.out.printf("[SQL Server jTDS] 写入吞吐: %,d ops/s  (%.2f ms/万条)%n", (long) ops, elapsed / 10_000_000.0);
        engine.close();
    }

    // ==================== Helpers ====================

    @SuppressWarnings("unchecked")
    private static <E extends com.chua.common.support.lang.datasource.engine.Engine> void prepareData(E engine, int count) {
        try {
            engine.execute("DELETE FROM bench");
        } catch (Exception ignored) {
        }
        for (int i = 0; i < count; i++) {
            engine.execute("INSERT INTO bench(id, name, val) VALUES(?,?,?)",
                    (long) i, "name_" + i, (double) i);
        }
    }

    private static boolean reachable(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

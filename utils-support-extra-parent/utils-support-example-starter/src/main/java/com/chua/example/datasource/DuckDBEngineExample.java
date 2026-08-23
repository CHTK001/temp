package com.chua.example.datasource;

import com.chua.duckdb.support.engine.DuckDBEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * DuckDB 引擎示例，演示 JdbcEngine 体系的 Lambda 链式查询/更新/删除。
 *
 * <h2>用法</h2>
 * <pre>
 *   java DuckDBEngineExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class DuckDBEngineExample {

    public static void main(String[] args) {
        try (DuckDBEngine engine = new DuckDBEngine()) {
            engine.addDataSource("default", "jdbc:duckdb:");
            engine.execute("CREATE TABLE IF NOT EXISTS user (id INTEGER, name VARCHAR, age INTEGER)");
            engine.execute("INSERT INTO user VALUES (1, 'zhangsan', 20), (2, 'lisi', 30), (3, 'wangwu', 40)");

            // Lambda 查询
            List<User> all = engine.query(User.class).list();
            log.info("Lambda 查询: {} 条", all.size());

            // Lambda 带条件查询
            List<User> adults = engine.query(User.class).gt("age", 20).list();
            log.info("年龄 > 20: {} 条", adults.size());

            // Lambda 更新
            int updated = engine.update(User.class).set("age", 25).eq("id", 1).update();
            log.info("Lambda 更新: 影响 {} 行", updated);

            // Lambda 删除
            int deleted = engine.delete(User.class).eq("id", 3).remove();
            log.info("Lambda 删除: 影响 {} 行", deleted);

            // 验证
            List<User> after = engine.query(User.class).orderByAsc("id").list();
            log.info("最终数据: {} 条", after.size());
            for (User u : after) {
                log.info("  id={}, name={}, age={}", u.id, u.name, u.age);
            }
        }
        log.info("完成");
    }

    public static class User {
        public int id;
        public String name;
        public int age;
    }
}
package com.chua.example.datasource;

import com.chua.common.support.lang.datasource.flyway.Flyway;
import com.chua.duckdb.support.engine.DuckDBEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * DuckDB 引擎示例，演示对象化 DDL 建表、Flyway 迁移与 Lambda 方法引用 CRUD。
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

    /** 私有构造，防止实例化 */
    private DuckDBEngineExample() { }

    public static void main(String[] args) {
        try (DuckDBEngine engine = new DuckDBEngine()) {
            engine.addDataSource("default", "jdbc:duckdb:");

            // 1) 对象化 DDL 建表（不使用裸 SQL）
            engine.meta().table("user")
                    .create("user")
                    .column("id", "INTEGER").primaryKey()
                    .column("name", "VARCHAR(50)").notNull()
                    .column("age", "INTEGER")
                    .execute();
            log.info("对象化 DDL 建表完成");

            // 2) 数据初始化通过 Flyway 迁移（版本管理）
            Flyway flyway = engine.flyway()
                    .location("classpath:db/migration/duckdb");
            int migrated = flyway.migrate();
            log.info("Flyway 迁移执行: {} 个脚本", migrated);

            // 3) Lambda 方法引用查询
            List<User> all = engine.query(User.class).list();
            log.info("Lambda 查询: {} 条", all.size());

            List<User> adults = engine.query(User.class).gt(User::getAge, 20).list();
            log.info("年龄 > 20: {} 条", adults.size());

            // 4) Lambda 方法引用更新/删除
            int updated = engine.update(User.class)
                    .set(User::getAge, 25)
                    .eq(User::getId, 1)
                    .update();
            log.info("Lambda 更新: 影响 {} 行", updated);

            int deleted = engine.delete(User.class).eq(User::getId, 3).remove();
            log.info("Lambda 删除: 影响 {} 行", deleted);

            // 5) 验证
            List<User> after = engine.query(User.class).orderByAsc(User::getId).list();
            log.info("最终数据: {} 条", after.size());
            for (User user : after) {
                log.info("  id={}, name={}, age={}", user.getId(), user.getName(), user.getAge());
            }
        }
        log.info("完成");
    }

    /**
     * 用户实体，字段与 user 表列一一对应。
     */
    public static class User {

        /**
         * 主键
         */
        private Integer id;

        /**
         * 名称
         */
        private String name;

        /**
         * 年龄
         */
        private Integer age;

        public Integer getId() { return id; }

        public void setId(Integer id) { this.id = id; }

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public Integer getAge() { return age; }

        public void setAge(Integer age) { this.age = age; }
    }
}

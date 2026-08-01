package com.chua.example.engine;

import com.chua.datasource.support.engine.InMemoryEngine;
import com.chua.common.support.lang.datasource.page.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 内存引擎基准测试：在 10k ~ 1M 多档数据量下，输出 store/index/query/update/delete 等操作的耗时。
 *
 * @author CH
 * @since 4.0.0
 */
public class InMemoryEngineBenchmark {

    public record User(Long id, String name, Integer age, String role) {
    }

    private static final List<String> NAMES = List.of("Alice", "Bob", "Charlie", "David", "Eve",
            "Frank", "Grace", "Henry", "Ivy", "Jack", "Kate", "Leo", "Mia", "Noah", "Olivia");
    private static final List<String> ROLES = List.of("admin", "user", "guest", "manager", "dev");

    public static void main(String[] args) {
        int[] scales = {10_000, 50_000, 100_000, 500_000, 1_000_000};
        for (int n : scales) {
            benchmark(n);
        }
        System.out.println("\n========================================");
        System.out.println("  BENCHMARK COMPLETE");
        System.out.println("========================================");
    }

    private static void benchmark(int size) {
        System.out.println("\n========================================");
        System.out.println("  DATA SIZE: " + String.format("%,d", size));
        System.out.println("========================================");

        Random rnd = new Random(42);
        List<User> users = new ArrayList<>(size);
        for (long i = 1; i <= size; i++) {
            users.add(new User(i,
                    NAMES.get(rnd.nextInt(NAMES.size())),
                    20 + rnd.nextInt(40),
                    ROLES.get(rnd.nextInt(ROLES.size()))));
        }

        InMemoryEngine engine = new InMemoryEngine();

        time("store", () -> engine.store("user", users));

        time("index", () -> engine.index());

        time("query list()", () -> {
            List<User> all = engine.query(User.class).list();
            if (all.size() != size) throw new AssertionError();
        });

        time("query eq()", () -> {
            List<User> r = engine.query(User.class).eq(User::name, "Alice").list();
            if (r.isEmpty()) throw new AssertionError();
        });

        time("query eq(idx)", () -> {
            List<User> r = engine.query(User.class).eq(User::id, 1L).list();
            if (r.isEmpty()) throw new AssertionError();
        });

        time("query gt()", () -> {
            List<User> r = engine.query(User.class).gt(User::age, 40).list();
            if (r.isEmpty()) throw new AssertionError();
        });

        time("query between()", () -> {
            List<User> r = engine.query(User.class).between(User::age, 25, 35).list();
            if (r.isEmpty()) throw new AssertionError();
        });

        time("query like()", () -> {
            List<User> r = engine.query(User.class).like(User::name, "A%").list();
            if (r.isEmpty()) throw new AssertionError();
        });

        time("query in()", () -> {
            List<User> r = engine.query(User.class)
                    .in(User::role, List.of("admin", "dev")).list();
            if (r.isEmpty()) throw new AssertionError();
        });

        time("query page()", () -> {
            Page<User> p = engine.query(User.class).page(1, 20);
            if (p.getRecords().size() != 20) throw new AssertionError();
        });

        time("update one", () -> {
            engine.update(User.class)
                    .set(User::age, 99)
                    .eq(User::id, 1L)
                    .update();
        });

        time("delete one", () -> {
            engine.delete(User.class)
                    .eq(User::id, 2L)
                    .remove();
        });
    }

    private static void time(String label, Runnable task) {
        long start = System.nanoTime();
        task.run();
        long elapsed = System.nanoTime() - start;
        System.out.printf("  %-25s %8.2f ms%n", label, elapsed / 1_000_000.0);
    }
}
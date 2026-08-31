package com.chua.example.lang.serialize;

import com.chua.common.support.base.serialize.Serialization;
import com.chua.common.support.lang.json.JacksonJsonProvider;
import com.chua.common.support.lang.json.JsonProvider;
import com.chua.example.util.UtilsExample;
import com.chua.common.support.serialize.JavaSerializer;
import com.chua.common.support.serialize.JsonSerializer;
import com.chua.common.support.serialize.Serializer;
import com.chua.example.spi.Example;
import com.chua.fastjson.support.json.FastjsonJsonProvider;
import com.chua.filesystem.support.serialize.SmileSerialization;
import com.chua.fory.support.json.ForyJsonProvider;
import com.chua.fory.support.serialize.ForySerialization;
import com.chua.gson.support.json.GsonJsonProvider;
import com.chua.protobuf.support.serialize.ProtobufSerialization;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 全部序列化实现性能基准（SPI 形式）— 覆盖当前 classpath 上全部序列化实现。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.RunnerExample --example=serialization-bench} 调用。
 * 覆盖三类实现：</p>
 * <ul>
 *   <li><b>JSON 文本</b>（{@link JsonProvider} 门面实现）：jackson / gson / fastjson / fory-json</li>
 *   <li><b>二进制</b>（{@link Serialization} 接口实现）：fury（Apache Fury）/ protobuf（protostuff）/ smile（Jackson Smile）</li>
 *   <li><b>基准</b>（{@link Serializer} 接口实现）：java（JDK 原生）/ json-serializer（Json 门面 → Jackson）</li>
 * </ul>
 *
 * <p>测量方法：预热（默认 300ms）+ 计时窗口（默认 600ms，取 2 轮最优）计算吞吐 ops/s，
 * 同时报告序列化产物大小（JSON 按 UTF-8 字节，二进制按 byte[] 长度）与往返正确性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 全部实现（默认）
 *   java RunnerExample --example=serialization-bench
 *
 *   # 指定实现（按名称模糊匹配，如 jackson / gson / fastjson / fory / fury / protobuf / smile / java / json）
 *   java RunnerExample --example=serialization-bench --impl=fury
 *
 *   # 调整测量参数
 *   java RunnerExample --example=serialization-bench --warmup-ms=500 --measure-ms=1000 --rounds=3 --list-size=100
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SerializationBenchmarkExample implements Example {

    /** 私有构造，防止实例化 */
    private SerializationBenchmarkExample() { }

    @Override
    /** Name */
    public String name() {
        return "serialization-bench";
    }

    @Override
    /** Module */
    public String module() {
        return "serialize";
    }

    @Override
    /** Description */
    public String description() {
        return "全部序列化实现性能基准（Jackson/Gson/Fastjson/Fory-JSON/Fury/Protobuf/Smile/Java/JsonSerializer）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String impl = args.getOrDefault("impl", "all").toLowerCase();
        long warmupMs = Long.parseLong(args.getOrDefault("warmup-ms", "300"));
        long measureMs = Long.parseLong(args.getOrDefault("measure-ms", "600"));
        int rounds = Integer.parseInt(args.getOrDefault("rounds", "2"));
        int listSize = Integer.parseInt(args.getOrDefault("list-size", "50"));

        List<BenchAdapter> adapters = new ArrayList<>();
        for (BenchAdapter a : buildAdapters()) {
            if ("all".equals(impl) || a.label().toLowerCase().contains(impl)) {
                adapters.add(a);
            }
        }
        if (adapters.isEmpty()) {
            log.info("[PERF] 无匹配实现: " + impl);
            return false;
        }

        log.info("===== serialization-bench =====");
        System.out.printf("[PERF] impl=%s  warmup=%dms  measure=%dms  rounds=%d%n",
                impl, warmupMs, measureMs, rounds);
        log.info("[PERF] " + header());

        boolean passed = true;
        for (BenchAdapter a : adapters) {
            passed &= bench(a, createUser(), User.class, "单对象", warmupMs, measureMs, rounds);
        }
        UserList list = new UserList();
        for (int i = 0; i < listSize; i++) {
            list.getUsers().add(createUser());
        }
        for (BenchAdapter a : adapters) {
            passed &= bench(a, list, UserList.class, "集合x" + listSize, warmupMs, measureMs, rounds);
        }
        log.info("===== serialization-bench " + (passed ? "PASSED" : "FAILED") + " =====");
        return passed;
    }

    /**
     * 构建全部可用序列化适配器。
     *
     * @return 适配器列表
     */
    private static List<BenchAdapter> buildAdapters() {
        List<BenchAdapter> out = new ArrayList<>();
        // JSON 文本（JsonProvider 门面实现）
        out.add(jsonAdapter("jackson", new JacksonJsonProvider()));
        out.add(jsonAdapter("gson", new GsonJsonProvider()));
        out.add(jsonAdapter("fastjson", new FastjsonJsonProvider()));
        out.add(jsonAdapter("fory-json", new ForyJsonProvider()));
        // 二进制（Serialization 接口实现）
        out.add(binaryAdapter("fury", new ForySerialization()));
        out.add(binaryAdapter("protobuf", new ProtobufSerialization()));
        out.add(binaryAdapter("smile", new SmileSerialization()));
        // 基准（Serializer 接口实现）— 按目标类型构造，避免 JsonSerializer 类型绑定导致集合反序列化类型错位
        out.add(serializerFactoryAdapter("java", cls -> new JavaSerializer<Serializable>()));
        out.add(serializerFactoryAdapter("json-serializer", cls -> new JsonSerializer<>(cls)));
        return out;
    }

    /**
     * 对单个适配器执行单负载基准并输出一行结果。
     *
     * @param a          适配器
     * @param payload    负载对象
     * @param type       目标类型
     * @param label      负载标签
     * @param warmupMs   预热毫秒
     * @param measureMs  计时毫秒
     * @param rounds     轮数（取最优）
     * @return 往返正确返回 true
     */
    private boolean bench(BenchAdapter a, Object payload, Class<?> type, String label,
                          long warmupMs, long measureMs, int rounds) {
        try {
            Object serialized = a.serialize(payload);
            boolean ok = verify(a, payload, type, serialized);
            int size = a.sizeOf(serialized);
            long[] sink = {0L};
            Runnable serTask = () -> {
                try {
                    sink[0] += a.sizeOf(a.serialize(payload));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            };
            Runnable desTask = () -> {
                try {
                    sink[0] += a.deserialize(serialized, type).hashCode();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            };
            warmup(serTask, warmupMs);
            warmup(desTask, warmupMs);
            long serOps = bestOf(serTask, measureMs, rounds);
            long desOps = bestOf(desTask, measureMs, rounds);
            System.out.printf("[PERF] %-14s %-9s %-9s %7s %12s %12s  %s%n",
                    a.label(), a.category(), label,
                    String.format("%,d", size),
                    String.format("%,d", serOps),
                    String.format("%,d", desOps),
                    ok ? "[OK]" : "[FAIL]");
            return ok;
        } catch (Exception e) {
            System.out.printf("[PERF] %-14s %s 失败: %s%n", a.label(), label, e.getMessage());
            return false;
        }
    }

    /**
     * 表头。
     *
     * @return 表头字符串
     */
    private static String header() {
        return String.format("%-14s %-9s %-9s %7s %12s %12s  %s",
                "实现", "类别", "负载", "大小(B)", "序列化(ops/s)", "反序列化(ops/s)", "往返");
    }

    /**
     * 预热指定时长。
     *
     * @param task     任务
     * @param budgetMs 预算毫秒
     */
    private static void warmup(Runnable task, long budgetMs) {
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            task.run();
        }
    }

    /**
     * 计时窗口内尽可能多地执行任务，返回 ops/s。
     *
     * @param task     任务
     * @param budgetMs 预算毫秒
     * @return ops/s
     */
    private static long measureOps(Runnable task, long budgetMs) {
        long start = System.nanoTime();
        long deadline = start + budgetMs * 1_000_000L;
        long count = 0;
        while (System.nanoTime() < deadline) {
            task.run();
            count++;
        }
        long elapsed = System.nanoTime() - start;
        return count * 1_000_000_000L / elapsed;
    }

    /**
     * 多轮取最优 ops/s。
     *
     * @param task     任务
     * @param budgetMs 每轮预算毫秒
     * @param rounds   轮数
     * @return 最优 ops/s
     */
    private static long bestOf(Runnable task, long budgetMs, int rounds) {
        long best = 0;
        for (int i = 0; i < rounds; i++) {
            best = Math.max(best, measureOps(task, budgetMs));
        }
        return best;
    }

    /**
     * 校验反序列化结果与原始负载关键字段一致。
     *
     * @param a          适配器
     * @param payload    原始负载
     * @param type       目标类型
     * @param serialized 序列化产物
     * @return 一致返回 true
     */
    private static boolean verify(BenchAdapter a, Object payload, Class<?> type, Object serialized) {
        try {
            Object back = a.deserialize(serialized, type);
            if (payload instanceof User orig && back instanceof User u) {
                boolean same = orig.getName().equals(u.getName())
                        && orig.getAge() == u.getAge()
                        && orig.getAddress().getCity().equals(u.getAddress().getCity())
                        && orig.getTags().size() == u.getTags().size()
                        && orig.getOrders().size() == u.getOrders().size()
                        && orig.getAttrs().get("k1").equals(u.getAttrs().get("k1"));
                if (!same) {
                    log.info("[PERF] 字段差异(" + a.label() + "): name=" + u.getName()
                            + " age=" + u.getAge() + " city=" + u.getAddress().getCity()
                            + " tags.size=" + u.getTags().size() + " orders.size=" + u.getOrders().size()
                            + " attrs.k1=" + u.getAttrs().get("k1"));
                }
                return same;
            }
            if (payload instanceof UserList ol && back instanceof UserList ul) {
                if (ol.getUsers().size() != ul.getUsers().size()) {
                    log.info("[PERF] 集合大小差异(" + a.label() + "): 期望=" + ol.getUsers().size()
                            + " 实际=" + ul.getUsers().size());
                    return false;
                }
                User o0 = ol.getUsers().get(0);
                User u0 = ul.getUsers().get(0);
                boolean same = o0.getName().equals(u0.getName()) && o0.getOrders().size() == u0.getOrders().size();
                if (!same) {
                    log.info("[PERF] 集合元素差异(" + a.label() + "): name=" + u0.getName()
                            + " orders.size=" + u0.getOrders().size());
                }
                return same;
            }
            log.info("[PERF] 类型不匹配(" + a.label() + "): 期望=" + type.getSimpleName()
                    + " 实际=" + (back == null ? "null" : back.getClass().getSimpleName()));
            return false;
        } catch (Exception e) {
            log.info("[PERF] 往返校验异常: " + e);
            return false;
        }
    }

    /**
     * JSON 文本适配器（JsonProvider 门面实现）。
     *
     * @param label 标签
     * @param p     实现
     * @return 适配器
     */
    private static BenchAdapter jsonAdapter(String label, JsonProvider p) {
        return new BenchAdapter() {
            @Override
            /** Label */
            public String label() {
                return label;
            }

            @Override
            /** Category */
            public String category() {
                return "JSON文本";
            }

            @Override
            /** 序列化 */
            public Object serialize(Object o) {
                return p.toJson(o);
            }

            @Override
            /** 反序列化 */
            public Object deserialize(Object d, Class<?> t) {
                return p.fromJson((String) d, t);
            }

            @Override
            /** 获取大小Of */
            public int sizeOf(Object d) {
                return ((String) d).getBytes(StandardCharsets.UTF_8).length;
            }
        };
    }

    /**
     * 二进制适配器（Serialization 接口实现）。
     *
     * @param label 标签
     * @param s     实现
     * @return 适配器
     */
    private static BenchAdapter binaryAdapter(String label, Serialization s) {
        return new BenchAdapter() {
            @Override
            /** Label */
            public String label() {
                return label;
            }

            @Override
            /** Category */
            public String category() {
                return "二进制";
            }

            @Override
            /** 序列化 */
            public Object serialize(Object o) throws Exception {
                return s.serialize(o);
            }

            @SuppressWarnings("unchecked")
            @Override
            /** 反序列化 */
            public Object deserialize(Object d, Class<?> t) throws Exception {
                return s.deserialize((byte[]) d, (Class<Object>) t);
            }

            @Override
            /** 获取大小Of */
            public int sizeOf(Object d) {
                return ((byte[]) d).length;
            }
        };
    }

    /**
     * Serializer 接口适配器（byte[] 语义，按目标类型构造实例）。
     *
     * <p>JsonSerializer 在构造时绑定目标类型（{@code new JsonSerializer<>(User.class)}），
     * 若复用单一实例，集合负载（UserList）反序列化时会被错误地当作 User 处理，
     * 导致往返校验失败。故此处改为按 {@code Class<?>} 工厂构造，确保类型正确。</p>
     *
     * @param label   标签
     * @param factory 按目标类型构造 Serializer 的工厂
     * @return 适配器
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BenchAdapter serializerFactoryAdapter(String label, Function<Class<?>, Serializer> factory) {
        return new BenchAdapter() {
            @Override
            /** Label */
            public String label() {
                return label;
            }

            @Override
            /** Category */
            public String category() {
                return "Serializer";
            }

            @Override
            /** 序列化 */
            public Object serialize(Object o) {
                return factory.apply(o.getClass()).serialize((Serializable) o);
            }

            @Override
            /** 反序列化 */
            public Object deserialize(Object d, Class<?> t) {
                return factory.apply(t).deserialize((byte[]) d);
            }

            @Override
            /** 获取大小Of */
            public int sizeOf(Object d) {
                return ((byte[]) d).length;
            }
        };
    }

    /**
     * 基准适配器接口。
     */
    private interface BenchAdapter {

        /**
         * 标签。
         *
         * @return 标签
         */
        String label();

        /**
         * 类别。
         *
         * @return 类别
         */
        String category();

        /**
         * 序列化。
         *
         * @param o 对象
         * @return 产物（JSON 为 String，二进制为 byte[]）
         */
        Object serialize(Object o) throws Exception;

        /**
         * 反序列化。
         *
         * @param d 产物
         * @param t 目标类型
         * @return 对象
         */
        Object deserialize(Object d, Class<?> t) throws Exception;

        /**
         * 产物大小（字节）。
         *
         * @param d 产物
         * @return 字节数
         */
        int sizeOf(Object d);
    }

    /**
     * 构建单对象负载（嵌套对象 / 列表 / Map / 基本类型）。
     *
     * <p><b>注意</b>：集合 / Map / 嵌套对象必须在构造后填充，而非字段内联初始化——
     * protostuff 的 {@code mergeFrom} 会将序列化数据追加到已初始化的集合上
     * （字段预置 4 个元素会反序列化出 8 个），空集合 + 构造后 add 对所有实现公平。</p>
     *
     * @return 负载对象
     */
    private static User createUser() {
        User user = new User();
        user.setName("张三");
        user.setAge(30);
        user.setSalary(12345.67);
        user.setActive(true);
        user.getTags().addAll(List.of("java", "json", "spi", "bench"));
        user.getAttrs().putAll(Map.of("k1", "v1", "k2", "v2", "k3", "v3"));
        user.setAddress(new Address("北京市", "朝阳区", "100020"));
        user.getOrders().addAll(List.of(new Order("o1", 10.5), new Order("o2", 20.75)));
        return user;
    }

    /**
     * 单对象负载（嵌套对象 / 列表 / Map / 基本类型）。
     */
    @Data
    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 姓名
         */
        private String name;
        /**
         * 年龄
         */
        private int age;
        /**
         * 薪资
         */
        private double salary;
        /**
         * 是否在职
         */
        private boolean active;
        /**
         * 标签
         */
        private List<String> tags = new ArrayList<>();
        /**
         * 属性 Map
         */
        private Map<String, String> attrs = new LinkedHashMap<>();
        /**
         * 地址（嵌套对象）
         */
        private Address address;
        /**
         * 订单列表
         */
        private List<Order> orders = new ArrayList<>();
    }

    /**
     * 集合负载容器。
     */
    @Data
    static class UserList implements Serializable {
        private static final long serialVersionUID = 2L;
        /**
         * 用户列表
         */
        private List<User> users = new ArrayList<>();
    }

    /**
     * 嵌套地址对象。
     */
    @Data
    static class Address implements Serializable {
        private static final long serialVersionUID = 3L;
        /**
         * 省份
         */
        private String province;
        /**
         * 城市
         */
        private String city;
        /**
         * 邮编
         */
        private String zip;

        /** 创建 Address 实例 */
        public Address() {
        }

        /**
         * 创建 Address 实例
         * @param province province
         * @param String String
         * @param String String
         */
        public Address(String province, String city, String zip) {
            this.province = province;
            this.city = city;
            this.zip = zip;
        }
    }

    /**
     * 嵌套订单对象。
     */
    @Data
    static class Order implements Serializable {
        private static final long serialVersionUID = 4L;
        /**
         * 订单号
         */
        private String id;
        /**
         * 金额
         */
        private double amount;

        /** 创建 Order 实例 */
        public Order() {
        }

        /**
         * 创建 Order 实例
         * @param id id
         * @param double double
         */
        public Order(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    /**
     * 独立入口：支持 --impl=xxx --warmup-ms=N --measure-ms=N --rounds=N --list-size=N 参数。
     */
    public static void main(String[] args) {
        Map<String, String> parsed = UtilsExample.parseArgs(args);
        boolean passed = new SerializationBenchmarkExample().run(parsed);
        log.info("[SerializationBenchmarkExample] impl={}, passed={}",
                parsed.getOrDefault("impl", "all"), passed);
        System.exit(passed ? 0 : 1);
    }
}

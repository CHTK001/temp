package com.chua.example.lang.json;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.Json5;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonNode;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.json.JsonProvider;
import com.chua.common.support.lang.json.annotation.JsonIgnore;
import com.chua.common.support.lang.json.annotation.JsonName;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.example.spi.Example;
import com.chua.fastjson.support.json.FastjsonJsonArray;
import com.chua.fastjson.support.json.FastjsonJsonNode;
import com.chua.fastjson.support.json.FastjsonJsonObject;
import com.chua.fastjson.support.json.FastjsonJsonProvider;
import com.chua.fory.support.json.ForyJsonArray;
import com.chua.fory.support.json.ForyJsonNode;
import com.chua.fory.support.json.ForyJsonObject;
import com.chua.fory.support.json.ForyJsonProvider;
import com.chua.gson.support.json.GsonJsonArray;
import com.chua.gson.support.json.GsonJsonNode;
import com.chua.gson.support.json.GsonJsonObject;
import com.chua.gson.support.json.GsonJsonProvider;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * JsonProvider 统一门户自检示例（SPI 形式）— 演示 gson / fory / fastjson 三实现切换。
 *
 * <p>通过统一入口 {@code com.chua.example.runner.ExampleRunner --example=json-provider} 调用，
 * 内部覆盖：SPI 发现、门面切换、统一门户注解（@JsonName / @JsonIgnore）、编解码往返、
 * ignores、通用 Type 反序列化、stream 入口与节点子类切换。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 全部实现（默认）
 *   java ExampleRunner --example=json-provider
 *
 *   # 指定实现
 *   java ExampleRunner --example=json-provider --type=gson
 *   java ExampleRunner --example=json-provider --type=fory
 *   java ExampleRunner --example=json-provider --type=fastjson
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class JsonProviderExampleSpi implements Example {

    @Override
    /** Name */
    public String name() {
        return "json-provider";
    }

    @Override
    /** Module */
    public String module() {
        return "json";
    }

    @Override
    /** Description */
    public String description() {
        return "JsonProvider 统一门户注解与节点类型自检（gson / fory / fastjson 三实现 SPI 切换）";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        String type = args.getOrDefault("type", "all").toLowerCase();
        boolean passed = true;
        switch (type) {
            case "gson" -> passed = testProvider("gson", new GsonJsonProvider(),
                    GsonJsonObject.class, GsonJsonArray.class, GsonJsonNode.class);
            case "fory" -> passed = testProvider("fory", new ForyJsonProvider(),
                    ForyJsonObject.class, ForyJsonArray.class, ForyJsonNode.class);
            case "fastjson" -> passed = testProvider("fastjson", new FastjsonJsonProvider(),
                    FastjsonJsonObject.class, FastjsonJsonArray.class, FastjsonJsonNode.class);
            case "all" -> {
                passed &= testProvider("gson", new GsonJsonProvider(),
                        GsonJsonObject.class, GsonJsonArray.class, GsonJsonNode.class);
                passed &= testProvider("fory", new ForyJsonProvider(),
                        ForyJsonObject.class, ForyJsonArray.class, ForyJsonNode.class);
                passed &= testProvider("fastjson", new FastjsonJsonProvider(),
                        FastjsonJsonObject.class, FastjsonJsonArray.class, FastjsonJsonNode.class);
            }
            default -> {
                log.warn("不支持的 type: {}，可选: gson / fory / fastjson / all", type);
                passed = false;
            }
        }
        return passed;
    }

    /**
     * 对单个实现执行完整自检矩阵。
     *
     * @param type       SPI 名称
     * @param provider   实现实例
     * @param objCls     节点对象子类
     * @param arrCls     节点数组子类
     * @param nodeCls    节点值子类
     * @return 全部通过返回 true
     */
    private boolean testProvider(String type, JsonProvider provider,
                                 Class<?> objCls, Class<?> arrCls, Class<?> nodeCls) {
        log.info("\n===== json-provider --type={} =====", type);
        boolean passed = true;
        try {
            passed &= testSpiDiscovery(type, provider);
            passed &= testSetImplementation(provider);
            passed &= testUnifiedAnnotations();
            passed &= testRoundTrip();
            passed &= testToJsonIgnores(provider);
            passed &= testTypeDeserialization(provider, type);
            if ("gson".equals(type) || "fastjson".equals(type)) {
                passed &= testStreamEntries(provider);
            }
            passed &= testNodeFactorySubclasses(objCls, arrCls, nodeCls);
        } finally {
            Json.setImplementation(new com.chua.common.support.lang.json.JacksonJsonProvider());
        }
        return passed;
    }

    /** TestSpiDiscovery */
    private boolean testSpiDiscovery(String type, JsonProvider provider) {
        log.info("  [TC-01] SPI 发现 ({})", type);
        try {
            JsonProvider byName = ServiceProvider.of(JsonProvider.class).getExtension(type);
            assertNotNull(byName, "应能通过 SPI 发现 " + type + " 实现");
            assertInstanceOf(provider.getClass(), byName, "SPI 返回类型应为 " + provider.getClass().getSimpleName());
            pass();
            return true;
        } catch (Exception e) {
            fail("SPI 发现异常: " + e.getMessage());
            return false;
        }
    }

    /** Test设置Implementation */
    private boolean testSetImplementation(JsonProvider provider) {
        log.info("  [TC-02] Json.setImplementation 门面委托");
        try {
            Json.setImplementation(provider);
            assertSame(provider, Json.getImplementation(), "门面实现应切换为 " + provider.getClass().getSimpleName());
            pass();
            return true;
        } catch (Exception e) {
            fail("setImplementation 异常: " + e.getMessage());
            return false;
        }
    }

    /** TestUnifiedAnnotations */
    private boolean testUnifiedAnnotations() {
        log.info("  [TC-03] 统一门户注解 @JsonName / @JsonIgnore");
        try {
            String json = Json.toJson(new User("chua", "secret", 18));
            assertTrue(json.contains("user_name"), "应输出 @JsonName 重命名字段");
            assertTrue(json.contains("\"age\":18"), "应输出普通字段");
            assertFalse(json.contains("password"), "被 @JsonIgnore 标记的字段不应序列化");

            User user = Json.fromJson("{\"user_name\":\"chua\",\"password\":\"x\",\"age\":18}", User.class);
            assertEquals("chua", user.getName(), "反序列化 @JsonName 字段");
            assertEquals(18, user.getAge(), "反序列化普通字段");
            assertEquals(null, user.getPassword(), "反序列化 @JsonIgnore 字段应为 null");

            // prettyFormat 同样应走门户注解桥接（fastjson 专属验证点）
            String pretty = Json.prettyFormat(new User("chua", "secret", 18));
            assertTrue(pretty.contains("user_name"), "prettyFormat 应输出 @JsonName 字段");
            assertFalse(pretty.contains("password"), "prettyFormat 也应排除 @JsonIgnore 字段");
            pass();
            return true;
        } catch (Exception e) {
            fail("统一注解异常: " + e.getMessage());
            return false;
        }
    }

    /** TestRoundTrip */
    private boolean testRoundTrip() {
        log.info("  [TC-04] 基础编解码往返");
        try {
            JsonObject obj = Json.getJsonObject("{\"k\":\"v\"}");
            assertEquals("v", obj.get("k"), "getJsonObject 解析");

            JsonArray array = Json.getJsonArray("[1,2,3]");
            assertEquals(3, array.size(), "getJsonArray 解析");

            assertTrue(Json.isJson("{\"a\":1}"), "isJson 应识别合法 JSON");
            assertFalse(Json.isJson("plain"), "isJson 应拒绝非 JSON");
            assertTrue(Json.validate("{\"a\":1}"), "validate 应通过合法 JSON");
            assertFalse(Json.validate("not json"), "validate 应拒绝非法 JSON");

            Map<String, Object> map = Json.fromJson("{\"a\":1}");
            assertEquals(1, ((Number) map.get("a")).intValue(), "fromJson 到 Map");
            pass();
            return true;
        } catch (Exception e) {
            fail("往返异常: " + e.getMessage());
            return false;
        }
    }

    /** TestToJsonIgnores */
    private boolean testToJsonIgnores(JsonProvider provider) {
        log.info("  [TC-05] toJson ignores 剔除字段");
        try {
            String json = provider.toJson(new User("chua", "secret", 18), "age");
            assertFalse(json.contains("age"), "ignores 字段不应输出");
            assertTrue(json.contains("user_name"), "其余字段正常输出");
            pass();
            return true;
        } catch (Exception e) {
            fail("ignores 异常: " + e.getMessage());
            return false;
        }
    }

    /** TestTypeDeserialization */
    private boolean testTypeDeserialization(JsonProvider provider, String type) {
        log.info("  [TC-06] 通用 Type 反序列化 ({})", type);
        try {
            String json = "[{\"name\":\"a\",\"age\":1}]";
            Type listType = switch (type) {
                case "gson" -> new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {
                }.getType();
                case "fory" -> new ParameterizedType() {
                    @Override
                    /** 获取ActualTypeArguments */
                    public Type[] getActualTypeArguments() {
                        return new Type[]{Map.class};
                    }

                    @Override
                    /** 获取RawType */
                    public Type getRawType() {
                        return List.class;
                    }

                    @Override
                    /** 获取OwnerType */
                    public Type getOwnerType() {
                        return null;
                    }
                };
                default -> new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {
                }.getType();
            };
            List<Map<String, Object>> list = provider.fromJson(json, listType);
            assertNotNull(list, "Type 反序列化结果不应为 null");
            assertEquals(1, list.size(), "列表长度");
            assertEquals("a", list.get(0).get("name"), "元素字段");
            pass();
            return true;
        } catch (Exception e) {
            fail("Type 反序列化异常: " + e.getMessage());
            return false;
        }
    }

    /** TestStreamEntries */
    private boolean testStreamEntries(JsonProvider provider) {
        log.info("  [TC-07] stream / reader / writer 入口");
        try {
            InputStream stream = new java.io.ByteArrayInputStream("{\"k\":\"v\"}".getBytes());
            assertEquals("v", provider.getJsonObject(stream).get("k"), "InputStream 入口");

            Reader reader = new java.io.StringReader("{\"k\":\"v\"}");
            assertEquals("v", provider.getJsonObject(
                    new InputStreamReader(new java.io.ByteArrayInputStream("{\"k\":\"v\"}".getBytes()))).get("k"),
                    "Reader 入口");
            assertEquals("v", provider.fromJson(reader, Map.class).get("k"), "fromJson(Reader, Class)");

            Writer writer = new java.io.StringWriter();
            provider.toJson(new User("chua", "secret", 18), writer);
            assertTrue(writer.toString().contains("user_name"), "toJson(Writer) 应输出 @JsonName 字段");
            pass();
            return true;
        } catch (Exception e) {
            fail("stream 入口异常: " + e.getMessage());
            return false;
        }
    }

    /** TestNodeFactorySubclasses */
    private boolean testNodeFactorySubclasses(Class<?> objCls, Class<?> arrCls, Class<?> nodeCls) {
        log.info("  [TC-08] 节点工厂返回 {} / {} / {}", objCls.getSimpleName(), arrCls.getSimpleName(), nodeCls.getSimpleName());
        try {
            assertInstanceOf(objCls, Json.createJsonObject(), "Json.createJsonObject");
            assertInstanceOf(objCls, JsonObject.create(), "JsonObject.create");
            assertInstanceOf(objCls, Json.getJsonObject("{\"a\":1}"), "Json.getJsonObject");
            assertInstanceOf(objCls, Json5.getJsonObject("{\"a\":1}"), "Json5.getJsonObject");
            assertInstanceOf(arrCls, Json.createJsonArray(), "Json.createJsonArray");
            assertInstanceOf(arrCls, JsonArray.of(), "JsonArray.of");
            assertInstanceOf(arrCls, Json.getJsonArray("[1,2]"), "Json.getJsonArray");
            assertInstanceOf(arrCls, Json5.getJsonArray("[1,2]"), "Json5.getJsonArray");
            assertInstanceOf(nodeCls, Json.createJsonNode("v"), "Json.createJsonNode");
            assertInstanceOf(nodeCls, JsonNode.valueOf("v"), "JsonNode.valueOf");
            assertInstanceOf(nodeCls, Json.parse("{\"a\":1}"), "Json.parse");
            assertInstanceOf(nodeCls, Json.build(), "Json.build");
            assertInstanceOf(nodeCls, Json.buildArray(), "Json.buildArray");
            pass();
            return true;
        } catch (Exception e) {
            fail("节点工厂异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 统一门户注解测试实体。
     */
    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        @JsonName("user_name")
        /** 名称 */
        private String name;

        @JsonIgnore
        /** 密码 */
        private String password;

        /** AGE */
        private int age;

        /** 创建 User 实例 */
        public User() {
        }

        /**
         * 创建 User 实例
         * @param name name
         * @param String String
         * @param int int
         */
        public User(String name, String password, int age) {
            this.name = name;
            this.password = password;
            this.age = age;
        }

        /** 获取Name */
        public String getName() {
            return name;
        }

        /** 设置Name */
        public void setName(String name) {
            this.name = name;
        }

        /** 获取Password */
        public String getPassword() {
            return password;
        }

        /** 设置Password */
        public void setPassword(String password) {
            this.password = password;
        }

        /** 获取Age */
        public int getAge() {
            return age;
        }

        /** 设置Age */
        public void setAge(int age) {
            this.age = age;
        }
    }

    /** AssertTrue */
    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }

    /** AssertFalse */
    private static void assertFalse(boolean condition, String msg) {
        if (condition) {
            throw new AssertionError(msg);
        }
    }

    /** AssertNotNull */
    private static void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new AssertionError(msg);
        }
    }

    /** AssertSame */
    private static void assertSame(Object expected, Object actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /** AssertInstanceOf */
    private static void assertInstanceOf(Class<?> type, Object o, String msg) {
        if (o == null || !type.isInstance(o)) {
            throw new AssertionError(msg + " — 期望类型 " + type.getSimpleName() + "，实际 "
                    + (o == null ? "null" : o.getClass().getSimpleName()));
        }
    }

    /** Assert判断相等 */
    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " — 期望 " + expected + "，实际 " + actual);
        }
    }

    /** Pass */
    private static void pass() {
        log.info("  ✓ 通过");
    }

    /** Fail */
    private static void fail(String msg) {
        log.info("  ✗ 失败: {}", msg);
    }
}

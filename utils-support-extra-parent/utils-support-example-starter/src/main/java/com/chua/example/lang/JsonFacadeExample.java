package com.chua.example.lang;

import com.chua.common.support.lang.json.JacksonJsonProvider;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonNode;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.json.JsonProvider;
import com.chua.common.support.lang.json.JsonReference;
import com.chua.common.support.spi.ServiceProvider;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Json 静态门面示例：演示默认 Jackson 实现的 SPI 自动发现、全局实现替换、
 * Jackson 专属方法（getMapper / TypeReference）以及常用静态方法行为。
 *
 * <p>改写自 common-starter 单元测试 {@code JsonFacadeTest}，全部断言场景以 [PASS]/[FAIL]
 * 控制台输出呈现，任一场景失败即以退出码 1 终止。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class JsonFacadeExample {

    private JsonFacadeExample() {
    }

    /**
     * 示例入口：依次执行默认实现发现、SPI 名称注册、桩实现替换、null 拒绝、
     * TypeReference 反序列化与常用静态方法六组场景，结束后恢复默认 Jackson 实现。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        try {
            log.info("===== Json门面 场景1: 默认实现为 Jackson =====");
            if (!Json.getImplementation().getClass().getSimpleName().equals("JacksonJsonProvider")) {
                log.info("[FAIL] 默认实现: 门面实现应由 SPI 发现为 JacksonJsonProvider");
                System.exit(1);
            }
            log.info("[PASS] 默认实现: 门面实现为 SPI 发现的 JacksonJsonProvider");
            JsonProvider spiDefault = ServiceProvider.of(JsonProvider.class).getDefault();
            if (spiDefault == null) {
                log.info("[FAIL] 默认实现: SPI getDefault 应返回 @SpiDefault 实现");
                System.exit(1);
            }
            log.info("[PASS] 默认实现: SPI getDefault 非空");
            if (!spiDefault.getClass().getSimpleName().equals("JacksonJsonProvider")) {
                log.info("[FAIL] 默认实现: SPI 默认实现应为 JacksonJsonProvider");
                System.exit(1);
            }
            log.info("[PASS] 默认实现: SPI 默认实现为 JacksonJsonProvider");
            if (Json.getMapper() == null) {
                log.info("[FAIL] 默认实现: Jackson 实现下 getMapper 不应为 null");
                System.exit(1);
            }
            log.info("[PASS] 默认实现: getMapper 仅在 Jackson 实现下可用");
            User source = new User("chua", 18);
            String json = Json.toJson(source);
            if (!json.contains("\"name\"")) {
                log.info("[FAIL] 默认实现: toJson 应输出 name 字段, 实际 " + json);
                System.exit(1);
            }
            log.info("[PASS] 默认实现: toJson 输出 name 字段");
            User parsed = Json.fromJson(json, User.class);
            if (!"chua".equals(parsed.getName())) {
                log.info("[FAIL] 默认实现: 反序列化 name 应为 chua");
                System.exit(1);
            }
            log.info("[PASS] 默认实现: fromJson 往返 name=chua");
            if (parsed.getAge() != 18) {
                log.info("[FAIL] 默认实现: 反序列化 age 应为 18");
                System.exit(1);
            }
            log.info("[PASS] 默认实现: fromJson 往返 age=18");

            log.info("===== Json门面 场景2: SPI 名称注册 =====");
            JsonProvider byName = ServiceProvider.of(JsonProvider.class).getExtension("jackson");
            if (byName == null) {
                log.info("[FAIL] SPI名称注册: 名称 jackson 应可发现实现");
                System.exit(1);
            }
            log.info("[PASS] SPI名称注册: 名称 jackson 发现实现");
            if (!byName.getClass().getSimpleName().equals("JacksonJsonProvider")) {
                log.info("[FAIL] SPI名称注册: jackson 实现应为 JacksonJsonProvider");
                System.exit(1);
            }
            log.info("[PASS] SPI名称注册: jackson 实现类型正确");

            log.info("===== Json门面 场景3: 桩实现全局替换 =====");
            JsonProvider stub = new StubJsonProvider();
            Json.setImplementation(stub);
            if (Json.getImplementation() != stub) {
                log.info("[FAIL] 桩替换: setImplementation 后门面应返回同一实例");
                System.exit(1);
            }
            log.info("[PASS] 桩替换: 门面实现切换为桩实例");
            if (!"{\"provider\":\"stub\"}".equals(Json.toJson(new User("chua", 18)))) {
                log.info("[FAIL] 桩替换: toJson 未委托到桩实现");
                System.exit(1);
            }
            log.info("[PASS] 桩替换: toJson 委托到桩实现");
            User stubUser = Json.fromJson("{}", User.class);
            if (!"stub".equals(stubUser.getName())) {
                log.info("[FAIL] 桩替换: fromJson 未委托到桩实现");
                System.exit(1);
            }
            log.info("[PASS] 桩替换: fromJson 委托到桩实现 name=stub");
            try {
                Json.getMapper();
                log.info("[FAIL] 桩替换: 非 Jackson 实现下 getMapper 应抛 UnsupportedOperationException");
                System.exit(1);
            } catch (UnsupportedOperationException e) {
                log.info("[PASS] 桩替换: 非 Jackson 实现下 getMapper 抛出 UnsupportedOperationException");
            }
            Json.setImplementation(new JacksonJsonProvider());

            log.info("===== Json门面 场景4: setImplementation(null) 拒绝 =====");
            try {
                Json.setImplementation(null);
                log.info("[FAIL] null拒绝: setImplementation(null) 应抛 IllegalArgumentException");
                System.exit(1);
            } catch (IllegalArgumentException e) {
                log.info("[PASS] null拒绝: setImplementation(null) 抛出 IllegalArgumentException");
            }
            if (!Json.getImplementation().getClass().getSimpleName().equals("JacksonJsonProvider")) {
                log.info("[FAIL] null拒绝: 全局实现不应被破坏");
                System.exit(1);
            }
            log.info("[PASS] null拒绝: 全局实现仍为 JacksonJsonProvider");

            log.info("===== Json门面 场景5: TypeReference 泛型反序列化 =====");
            String listJson = "[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]";
            List<User> users = Json.fromJson(listJson, new TypeReference<List<User>>() {
            });
            if (users == null) {
                log.info("[FAIL] TypeReference: fromJson 返回 null");
                System.exit(1);
            }
            log.info("[PASS] TypeReference: fromJson 非空");
            if (users.size() != 2) {
                log.info("[FAIL] TypeReference: 列表长度应为 2, 实际 " + users.size());
                System.exit(1);
            }
            log.info("[PASS] TypeReference: 列表长度 -> 2");
            if (!"a".equals(users.get(0).getName())) {
                log.info("[FAIL] TypeReference: users[0].name 应为 a");
                System.exit(1);
            }
            log.info("[PASS] TypeReference: users[0].name=a");
            if (users.get(1).getAge() != 2) {
                log.info("[FAIL] TypeReference: users[1].age 应为 2");
                System.exit(1);
            }
            log.info("[PASS] TypeReference: users[1].age=2");

            log.info("===== Json门面 场景6: 常用静态方法 =====");
            JsonObject obj = Json.getJsonObject("{\"k\":\"v\"}");
            if (!"v".equals(obj.get("k"))) {
                log.info("[FAIL] 常用静态方法: getJsonObject 的 k 应为 v");
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: getJsonObject 解析 k=v");
            JsonArray array = Json.getJsonArray("[1,2,3]");
            if (array.size() != 3) {
                log.info("[FAIL] 常用静态方法: getJsonArray 长度应为 3, 实际 " + array.size());
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: getJsonArray 长度 -> 3");
            if (!Json.isJson("{\"a\":1}")) {
                log.info("[FAIL] 常用静态方法: isJson 应识别合法 JSON");
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: isJson 识别合法 JSON");
            if (Json.isJson("plain text")) {
                log.info("[FAIL] 常用静态方法: isJson 应拒绝纯文本");
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: isJson 拒绝纯文本");
            if (!Json.validate("{\"a\":1}")) {
                log.info("[FAIL] 常用静态方法: validate 应通过合法 JSON");
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: validate 通过合法 JSON");
            if (Json.validate("not json")) {
                log.info("[FAIL] 常用静态方法: validate 应拒绝非法 JSON");
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: validate 拒绝非法 JSON");
            byte[] bytes = Json.toJsonByte(new User("c", 3));
            if (!"c".equals(Json.fromJson(bytes, User.class).getName())) {
                log.info("[FAIL] 常用静态方法: toJsonByte/fromJson 字节往返失败");
                System.exit(1);
            }
            log.info("[PASS] 常用静态方法: toJsonByte/fromJson 字节往返 name=c");
            log.info("[PASS] Json门面 全部场景执行完成");
        } finally {
            Json.setImplementation(new JacksonJsonProvider());
        }
    }

    /**
     * 测试数据实体。
     */
    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 名称 */
        private String name;
        /** 年龄 */
        private int age;

        /** 创建 User 实例 */
        public User() {
        }

        /**
         * 创建 User 实例
         *
         * @param name 名称
         * @param age  年龄
         */
        public User(String name, int age) {
            this.name = name;
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

        /** 获取Age */
        public int getAge() {
            return age;
        }

        /** 设置Age */
        public void setAge(int age) {
            this.age = age;
        }
    }

    /**
     * 自定义桩实现，仅覆盖 toJson / fromJson 验证委托链路。
     */
    static class StubJsonProvider implements JsonProvider {
        @Override
        /** 解析 */
        public JsonNode parse(String json) {
            return new JsonNode(new JsonObject());
        }

        @Override
        /** 解析 */
        public JsonNode parse(byte[] json) {
            return parse(new String(json));
        }

        @Override
        /** 构建 */
        public JsonNode build() {
            return new JsonNode(new JsonObject());
        }

        @Override
        /** 构建Array */
        public JsonNode buildArray() {
            return new JsonNode(new JsonArray());
        }

        @Override
        /** 获取JsonObject */
        public JsonObject getJsonObject(String json) {
            return new JsonObject();
        }

        @Override
        /** 获取JsonReference */
        public JsonReference getJsonReference(String json) {
            return new JsonReference(json);
        }

        @Override
        /** 获取JsonArray */
        public JsonArray getJsonArray(byte[] jsonArray) {
            return new JsonArray();
        }

        @Override
        /** 获取JsonArray */
        public JsonArray getJsonArray(String json) {
            return new JsonArray();
        }

        @Override
        /** 获取JsonObject */
        public JsonObject getJsonObject(byte[] bytes) {
            return new JsonObject();
        }

        @Override
        /** 获取JsonObject */
        public JsonObject getJsonObject(InputStreamReader inputStreamReader) {
            return new JsonObject();
        }

        @Override
        /** 获取JsonObject */
        public JsonObject getJsonObject(InputStream inputStream) {
            return new JsonObject();
        }

        @Override
        /** 获取JsonObject */
        public JsonObject getJsonObject(InputStream inputStream, String charset) {
            return new JsonObject();
        }

        @Override
        /** FromJsonToList */
        public <T> List<T> fromJsonToList(InputStream inputStream, Class<T> targetType) {
            return List.of();
        }

        @Override
        /** FromJsonToList */
        public <T> List<T> fromJsonToList(String json, Class<T> targetType) {
            return List.of();
        }

        @Override
        /** FromJson */
        public <T> T fromJson(String json, Class<T> target) {
            return target.cast(new User("stub", 1));
        }

        @Override
        /** FromJson */
        public <T> T fromJson(byte[] bytes, Class<T> target) {
            return fromJson(new String(bytes), target);
        }

        @Override
        /** FromJson */
        public JsonObject fromJson(byte[] bytes, Charset charset) {
            return new JsonObject();
        }

        @Override
        /** FromJson */
        public <T> T fromJson(InputStreamReader inputStreamReader, Class<T> target) {
            return fromJson("", target);
        }

        @Override
        /** FromJson */
        public <T> T fromJson(InputStream inputStream, Class<T> target) {
            return fromJson("", target);
        }

        @Override
        /** ToJson */
        public String toJson(Object object, String... ignores) {
            return "{\"provider\":\"stub\"}";
        }

        @Override
        /** Pretty格式化 */
        public String prettyFormat(Object object) {
            return toJson(object);
        }

        @Override
        /** ToPrettyJson */
        public String toPrettyJson(Object obj) {
            return prettyFormat(obj);
        }

        @Override
        /** ToJsonByte */
        public byte[] toJsonByte(Object object) {
            return toJson(object).getBytes();
        }

        @Override
        /** 是否Json */
        public boolean isJson(Object ext) {
            return false;
        }

        @Override
        public List<?> toList(String string) {
            return List.of();
        }

        @Override
        /** ToJSONBytes */
        public byte[] toJSONBytes(Object object) {
            return toJsonByte(object);
        }

        @Override
        /** ToJSONString */
        public String toJSONString(Object object) {
            return toJson(object);
        }

        @Override
        /** 校验 */
        public boolean validate(String jsonStr) {
            return true;
        }

        @Override
        /** FromJson */
        public Map<String, Object> fromJson(String string) {
            return Map.of();
        }

        @Override
        /** FromJson */
        public <T> T fromJson(String stringValue, Type type) {
            return fromJson(stringValue, (Class<T>) Object.class);
        }

        @Override
        /** FromJson */
        public <T> T fromJson(Reader reader, Class<T> target) {
            return fromJson("", target);
        }

        @Override
        /** ToJson */
        public void toJson(Object object, Writer writer) {
            try {
                writer.write(toJson(object));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        /** FromJson */
        public <T> T fromJson(InputStream stream, Type type) {
            return fromJson("", (Class<T>) Object.class);
        }

        @Override
        /** FromJson */
        public <T> T fromJson(Reader reader, Type type) {
            return fromJson("", (Class<T>) Object.class);
        }
    }
}

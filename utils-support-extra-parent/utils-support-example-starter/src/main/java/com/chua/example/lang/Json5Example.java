package com.chua.example.lang;

import com.chua.common.support.lang.json.JacksonJsonProvider;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.Json5;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonNode;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.json.JsonProvider;
import com.chua.common.support.lang.json.JsonReference;
import com.chua.common.support.spi.ServiceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Json5 门面示例：演示 JSON5 语法（单引号 / 注释 / 尾随逗号 / 未加引号键名）经 SPI 默认实现解析，
 * 注释预处理、列表解析、序列化字节往返，以及与 {@link Json} 门面共享同一实现、双向统一切换的能力。
 *
 * <p>改写自 common-starter 单元测试 {@code Json5Test}，全部断言场景以 [PASS]/[FAIL]
 * 控制台输出呈现，任一场景失败即以退出码 1 终止。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class Json5Example {

    private Json5Example() {
    }

    /**
     * 示例入口：依次执行 JSON5 语法解析、注释预处理与列表、统一切换、序列化、SPI 一致性五组场景，
     * 结束后恢复默认 Jackson 实现。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        try {
            String json5 = "{\n" +
                    "  name: 'chua', // 单行注释\n" +
                    "  age: 18,\n" +
                    "}";
            log.info("===== Json5 场景1: JSON5 语法解析 =====");
            User user = Json5.fromJson(json5, User.class);
            if (user == null) {
                log.info("[FAIL] Json5语法解析: fromJson 返回 null");
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: fromJson 反序列化非空");
            if (!"chua".equals(user.getName())) {
                log.info("[FAIL] Json5语法解析: name 应为 chua, 实际 " + user.getName());
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: 未加引号键名 + 单引号值 -> name=chua");
            if (user.getAge() != 18) {
                log.info("[FAIL] Json5语法解析: age 应为 18, 实际 " + user.getAge());
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: 尾随逗号容忍 -> age=18");
            Map<String, Object> map = Json5.fromJson(json5);
            if (!"chua".equals(map.get("name"))) {
                log.info("[FAIL] Json5语法解析: map.name 应为 chua, 实际 " + map.get("name"));
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: fromJson 到 Map -> name=chua");
            JsonObject jsonObject = Json5.getJsonObject(json5);
            if (!"chua".equals(jsonObject.get("name"))) {
                log.info("[FAIL] Json5语法解析: jsonObject.name 应为 chua");
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: getJsonObject -> name=chua");
            if (!Json5.isJson5(json5)) {
                log.info("[FAIL] Json5语法解析: isJson5 应识别 JSON5 文档");
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: isJson5 识别成功");
            if (!Json5.validate("{\"a\":1}")) {
                log.info("[FAIL] Json5语法解析: validate 应通过合法 JSON");
                System.exit(1);
            }
            log.info("[PASS] Json5语法解析: validate 通过合法 JSON");

            log.info("===== Json5 场景2: 注释预处理与列表 =====");
            String listJson5 = "[\n" +
                    "  // 注释\n" +
                    "  {\"name\":\"a\",\"age\":1},\n" +
                    "  {\"name\":\"b\",\"age\":2},\n" +
                    "]";
            String clean = Json5.preprocessJson5(listJson5);
            if (clean.contains("//")) {
                log.info("[FAIL] 注释预处理: preprocessJson5 未去除单行注释");
                System.exit(1);
            }
            log.info("[PASS] 注释预处理: 单行注释被剔除");
            List<User> users = Json5.fromJsonList(clean, User.class);
            if (users == null) {
                log.info("[FAIL] 注释预处理: fromJsonList 返回 null");
                System.exit(1);
            }
            log.info("[PASS] 注释预处理: fromJsonList 非空");
            if (users.size() != 2) {
                log.info("[FAIL] 注释预处理: 列表长度应为 2, 实际 " + users.size());
                System.exit(1);
            }
            log.info("[PASS] 注释预处理: 尾随逗号列表长度 -> 2");
            if (!"a".equals(users.get(0).getName())) {
                log.info("[FAIL] 注释预处理: users[0].name 应为 a");
                System.exit(1);
            }
            log.info("[PASS] 注释预处理: users[0].name=a");
            if (users.get(1).getAge() != 2) {
                log.info("[FAIL] 注释预处理: users[1].age 应为 2");
                System.exit(1);
            }
            log.info("[PASS] 注释预处理: users[1].age=2");
            JsonArray array = Json5.getJsonArray(clean);
            if (array.size() != 2) {
                log.info("[FAIL] 注释预处理: JsonArray 长度应为 2, 实际 " + array.size());
                System.exit(1);
            }
            log.info("[PASS] 注释预处理: getJsonArray 长度 -> 2");

            log.info("===== Json5 场景3: 与 Json 门面统一切换 =====");
            if (Json.getImplementation() != Json5.getImplementation()) {
                log.info("[FAIL] 统一切换: 默认实现应一致");
                System.exit(1);
            }
            log.info("[PASS] 统一切换: 默认实现一致");
            if (!(Json5.getImplementation() instanceof JacksonJsonProvider)) {
                log.info("[FAIL] 统一切换: 默认实现应为 JacksonJsonProvider");
                System.exit(1);
            }
            log.info("[PASS] 统一切换: 默认实现为 JacksonJsonProvider");
            JsonProvider stub = new StubJsonProvider();
            Json5.setImplementation(stub);
            if (Json.getImplementation() != stub) {
                log.info("[FAIL] 统一切换: Json5.setImplementation 后 Json 门面应跟随");
                System.exit(1);
            }
            log.info("[PASS] 统一切换: 经 Json5 切换后 Json 门面跟随");
            if (!"{\"provider\":\"stub\"}".equals(Json5.toJson(new User("chua", 18)))) {
                log.info("[FAIL] 统一切换: Json5.toJson 未委托到桩实现");
                System.exit(1);
            }
            log.info("[PASS] 统一切换: Json5.toJson 委托到桩实现");
            if (!"{\"provider\":\"stub\"}".equals(Json.toJson(new User("chua", 18)))) {
                log.info("[FAIL] 统一切换: Json.toJson 未跟随桩实现");
                System.exit(1);
            }
            log.info("[PASS] 统一切换: Json.toJson 输出与桩一致");
            Json.setImplementation(stub);
            if (Json5.getImplementation() != stub) {
                log.info("[FAIL] 统一切换: Json.setImplementation 后 Json5 门面应跟随");
                System.exit(1);
            }
            log.info("[PASS] 统一切换: 经 Json 切换后 Json5 门面跟随");

            Json.setImplementation(new JacksonJsonProvider());
            log.info("===== Json5 场景4: 序列化与字节数组 =====");
            String json = Json5.toJson(new User("chua", 18));
            if (!json.contains("\"name\":\"chua\"")) {
                log.info("[FAIL] 序列化: 输出应包含 \"name\":\"chua\", 实际 " + json);
                System.exit(1);
            }
            log.info("[PASS] 序列化: toJson 字段名输出正确");
            byte[] bytes = Json5.toJsonByte(new User("c", 3));
            User roundTrip = Json5.fromJson(bytes, User.class);
            if (!"c".equals(roundTrip.getName())) {
                log.info("[FAIL] 序列化: 字节反序列化 name 应为 c");
                System.exit(1);
            }
            log.info("[PASS] 序列化: toJsonByte/fromJson 往返 name=c");
            if (roundTrip.getAge() != 3) {
                log.info("[FAIL] 序列化: 字节反序列化 age 应为 3");
                System.exit(1);
            }
            log.info("[PASS] 序列化: toJsonByte/fromJson 往返 age=3");
            JsonObject obj = Json5.fromJson("{\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            if (!"v".equals(obj.get("k"))) {
                log.info("[FAIL] 序列化: fromJson(bytes,charset) 的 k 应为 v");
                System.exit(1);
            }
            log.info("[PASS] 序列化: fromJson(bytes,charset) 指定 UTF-8 解析");

            log.info("===== Json5 场景5: SPI 一致性 =====");
            JsonProvider byName = ServiceProvider.of(JsonProvider.class).getExtension("jackson");
            if (byName == null) {
                log.info("[FAIL] SPI一致性: 名称 jackson 应可发现实现");
                System.exit(1);
            }
            log.info("[PASS] SPI一致性: 名称 jackson 发现实现");
            if (!(byName instanceof JacksonJsonProvider)) {
                log.info("[FAIL] SPI一致性: jackson 实现应为 JacksonJsonProvider");
                System.exit(1);
            }
            log.info("[PASS] SPI一致性: jackson 实现类型正确");
            if (!(Json5.getImplementation() instanceof JacksonJsonProvider)) {
                log.info("[FAIL] SPI一致性: Json5 默认实现应为 JacksonJsonProvider");
                System.exit(1);
            }
            log.info("[PASS] SPI一致性: Json5 门面默认实现正确");
            log.info("[PASS] Json5 全部场景执行完成");
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
     * 简单桩实现，仅覆盖 toJson / fromJson 验证委托链路。
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

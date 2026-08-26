package com.chua.example.lang;

import com.chua.common.support.lang.json.JacksonJsonProvider;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.Json5;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonNode;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.json.JsonProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 节点工厂 SPI 示例：演示 {@link JsonProvider} 的节点工厂方法
 * （createJsonObject / createJsonArray / createJsonNode）被 {@link Json} 门面及
 * {@link JsonObject} / {@link JsonArray} / {@link JsonNode} 静态工厂委托调用，
 * 节点类型随当前实现切换（默认通用类，自定义实现返回其节点子类）。
 *
 * <p>改写自 common-starter 单元测试 {@code NodeFactorySpiTest}，全部断言场景以
 * [PASS]/[FAIL] 控制台输出呈现，任一场景失败即以退出码 1 终止。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class NodeFactorySpiExample {

    private NodeFactorySpiExample() {
    }

    /**
     * 示例入口：依次执行默认通用节点类型、实现切换后返回节点子类、Json5 共享节点工厂三组场景，
     * 结束后恢复默认 Jackson 实现。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        try {
            log.info("===== 节点工厂 场景1: 默认实现返回通用节点类型 =====");
            if (!(Json.createJsonObject() instanceof JsonObject)) {
                log.info("[FAIL] 通用节点: Json.createJsonObject 应返回 JsonObject");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: Json.createJsonObject -> JsonObject");
            if (!(JsonObject.create() instanceof JsonObject)) {
                log.info("[FAIL] 通用节点: JsonObject.create 应返回 JsonObject");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: JsonObject.create -> JsonObject");
            if (!(Json.createJsonObject(Map.of("a", 1)) instanceof JsonObject)) {
                log.info("[FAIL] 通用节点: Json.createJsonObject(Map) 应返回 JsonObject");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: Json.createJsonObject(Map) -> JsonObject");
            if (!(Json.createJsonArray() instanceof JsonArray)) {
                log.info("[FAIL] 通用节点: Json.createJsonArray 应返回 JsonArray");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: Json.createJsonArray -> JsonArray");
            if (!(JsonArray.of() instanceof JsonArray)) {
                log.info("[FAIL] 通用节点: JsonArray.of 应返回 JsonArray");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: JsonArray.of -> JsonArray");
            if (!(Json.createJsonArray(List.of(1, 2)) instanceof JsonArray)) {
                log.info("[FAIL] 通用节点: Json.createJsonArray(Collection) 应返回 JsonArray");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: Json.createJsonArray(Collection) -> JsonArray");
            if (!(Json.createJsonNode("v") instanceof JsonNode)) {
                log.info("[FAIL] 通用节点: Json.createJsonNode 应返回 JsonNode");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: Json.createJsonNode -> JsonNode");
            if (!(JsonNode.valueOf("v") instanceof JsonNode)) {
                log.info("[FAIL] 通用节点: JsonNode.valueOf 应返回 JsonNode");
                System.exit(1);
            }
            log.info("[PASS] 通用节点: JsonNode.valueOf -> JsonNode");

            log.info("===== 节点工厂 场景2: 切换实现后返回节点子类 =====");
            Json.setImplementation(new StubNodeProvider());
            if (!(Json.createJsonObject() instanceof StubJsonObject)) {
                log.info("[FAIL] 实现切换: Json.createJsonObject 应返回 StubJsonObject");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 门面委托 Json.createJsonObject -> StubJsonObject");
            if (!(Json.createJsonArray() instanceof StubJsonArray)) {
                log.info("[FAIL] 实现切换: Json.createJsonArray 应返回 StubJsonArray");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 门面委托 Json.createJsonArray -> StubJsonArray");
            if (!(Json.createJsonNode("v") instanceof StubJsonNode)) {
                log.info("[FAIL] 实现切换: Json.createJsonNode 应返回 StubJsonNode");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 门面委托 Json.createJsonNode -> StubJsonNode");
            if (!(JsonObject.create() instanceof StubJsonObject)) {
                log.info("[FAIL] 实现切换: JsonObject.create 应返回 StubJsonObject");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 静态工厂 JsonObject.create -> StubJsonObject");
            if (!(JsonArray.of() instanceof StubJsonArray)) {
                log.info("[FAIL] 实现切换: JsonArray.of 应返回 StubJsonArray");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 静态工厂 JsonArray.of -> StubJsonArray");
            if (!(JsonNode.valueOf("v") instanceof StubJsonNode)) {
                log.info("[FAIL] 实现切换: JsonNode.valueOf 应返回 StubJsonNode");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 静态工厂 JsonNode.valueOf -> StubJsonNode");
            JsonObject obj = Json.getJsonObject("{\"child\":{\"k\":1}}");
            if (!(obj.getJsonObject("child") instanceof StubJsonObject)) {
                log.info("[FAIL] 实现切换: 嵌套导航 Map 包装应走节点工厂返回 StubJsonObject");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 嵌套导航 getJsonObject(child) -> StubJsonObject");
            if (!(Json.parse("{\"a\":1}") instanceof StubJsonNode)) {
                log.info("[FAIL] 实现切换: Json.parse 应返回 StubJsonNode");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 解析路径 Json.parse -> StubJsonNode");
            if (!(Json.build() instanceof StubJsonNode)) {
                log.info("[FAIL] 实现切换: Json.build 应返回 StubJsonNode");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 构建路径 Json.build -> StubJsonNode");
            if (!(Json.buildArray() instanceof StubJsonNode)) {
                log.info("[FAIL] 实现切换: Json.buildArray 应返回 StubJsonNode");
                System.exit(1);
            }
            log.info("[PASS] 实现切换: 构建路径 Json.buildArray -> StubJsonNode");

            log.info("===== 节点工厂 场景3: Json5 门面共享节点工厂 =====");
            if (!(Json5.getJsonObject("{\"a\":1}") instanceof StubJsonObject)) {
                log.info("[FAIL] Json5共享: Json5.getJsonObject 应返回 StubJsonObject");
                System.exit(1);
            }
            log.info("[PASS] Json5共享: Json5.getJsonObject -> StubJsonObject");
            if (!(Json5.getJsonArray("[1,2]") instanceof StubJsonArray)) {
                log.info("[FAIL] Json5共享: Json5.getJsonArray 应返回 StubJsonArray");
                System.exit(1);
            }
            log.info("[PASS] Json5共享: Json5.getJsonArray -> StubJsonArray");
            if (Json.getImplementation() != Json5.getImplementation()) {
                log.info("[FAIL] Json5共享: Json 与 Json5 门面实现应一致");
                System.exit(1);
            }
            log.info("[PASS] Json5共享: Json 与 Json5 实现一致");
            log.info("[PASS] 节点工厂 全部场景执行完成");
        } finally {
            Json.setImplementation(new JacksonJsonProvider());
        }
    }

    /**
     * 测试用节点子类（对象）。
     */
    static class StubJsonObject extends JsonObject {
        /** 创建 StubJsonObject 实例 */
        public StubJsonObject() {
        }

        /**
         * 创建 StubJsonObject 实例
         *
         * @param m 源 Map
         */
        public StubJsonObject(Map m) {
            super(m);
        }
    }

    /**
     * 测试用节点子类（数组）。
     */
    static class StubJsonArray extends JsonArray {
        /** 创建 StubJsonArray 实例 */
        public StubJsonArray() {
        }

        /**
         * 创建 StubJsonArray 实例
         *
         * @param collection 源集合
         */
        public StubJsonArray(Collection collection) {
            super(collection);
        }
    }

    /**
     * 测试用节点子类（节点）。
     */
    static class StubJsonNode extends JsonNode {
        /**
         * 创建 StubJsonNode 实例
         *
         * @param value 节点值
         */
        public StubJsonNode(Object value) {
            super(value);
        }
    }

    /**
     * 覆写节点工厂方法的自定义实现（基于 Jackson 复用其余解析逻辑）。
     */
    static class StubNodeProvider extends JacksonJsonProvider {
        @Override
        /** 创建JsonObject */
        public JsonObject createJsonObject() {
            return new StubJsonObject();
        }

        @Override
        /** 创建JsonObject */
        public JsonObject createJsonObject(Map map) {
            return new StubJsonObject(map);
        }

        @Override
        /** 创建JsonArray */
        public JsonArray createJsonArray() {
            return new StubJsonArray();
        }

        @Override
        /** 创建JsonArray */
        public JsonArray createJsonArray(Collection collection) {
            return new StubJsonArray(collection);
        }

        @Override
        /** 创建JsonNode */
        public JsonNode createJsonNode(Object value) {
            return new StubJsonNode(value);
        }
    }
}

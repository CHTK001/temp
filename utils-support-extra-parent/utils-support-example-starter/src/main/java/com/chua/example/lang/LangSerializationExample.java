package com.chua.example.lang;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.example.util.UtilsExample;

/**
 * Lang/Serialization 基础自检示例。
 *
 * <p>验证 Json 工具类的基本序列化与反序列化能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class LangSerializationExample {

    private LangSerializationExample() {
    }

    public static void main(String[] args) {
        boolean passed = true;

        // 1. 测试Json序列化
        try {
            JsonObject obj = new JsonObject();
            obj.fluentPut("key1", "value1");
            obj.fluentPut("key2", 123);
            String json = obj.toJSONString();
            boolean ok = json != null && json.contains("key1") && json.contains("value1");
            passed &= ok;
            System.out.println((ok ? "[PASS]" : "[FAIL]") + " Json序列化基本功能");
        } catch (Exception e) {
            passed = false;
            System.out.println("[FAIL] Json序列化异常: " + e);
        }

        // 2. 测试Json反序列化
        try {
            String json = "{\"name\":\"test\",\"age\":18}";
            JsonObject parsed = Json.getJsonObject(json);
            boolean ok = "test".equals(parsed.get("name")) && Integer.valueOf(parsed.get("age").toString()) == 18;
            passed &= ok;
            System.out.println((ok ? "[PASS]" : "[FAIL]") + " Json反序列化基本功能");
        } catch (Exception e) {
            passed = false;
            System.out.println("[FAIL] Json反序列化异常: " + e);
        }

        if (!passed) {
            System.out.println("[FAIL] LangSerializationExample 存在失败场景");
            System.exit(UtilsExample.FAILURE);
        }
        System.out.println("[PASS] LangSerializationExample 全部场景通过");
        System.exit(UtilsExample.SUCCESS);
    }
}

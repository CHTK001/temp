package com.chua.example.lang;

import com.chua.common.support.serialize.JsonSerializer;

/**
 * Lang/Serialization 基础自检示例。
 *
 * <p>验证 JsonSerializer 的序列化与反序列化能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class LangSerializationExample {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    private LangSerializationExample() {
    }

    public static void main(String[] args) {
        boolean passed = true;

        // 1. 测试序列化
        try {
            JsonSerializer<String> serializer = new JsonSerializer<>(String.class);
            byte[] data = serializer.serialize("hello world");
            boolean ok = data != null && data.length > 0;
            passed &= ok;
            System.out.println((ok ? "[PASS]" : "[FAIL]") + " 序列化测试");
        } catch (Exception e) {
            passed = false;
            System.out.println("[FAIL] 序列化异常: " + e);
        }

        // 2. 测试反序列化
        try {
            JsonSerializer<String> serializer = new JsonSerializer<>(String.class);
            byte[] data = serializer.serialize("test data");
            String recovered = serializer.deserialize(data);
            boolean ok = "test data".equals(recovered);
            passed &= ok;
            System.out.println((ok ? "[PASS]" : "[FAIL]") + " 反序列化测试");
        } catch (Exception e) {
            passed = false;
            System.out.println("[FAIL] 反序列化异常: " + e);
        }

        if (!passed) {
            System.out.println("[FAIL] LangSerializationExample 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        }
        System.out.println("[PASS] LangSerializationExample 全部场景通过");
        System.exit(EXIT_CODE_SUCCESS);
    }
}
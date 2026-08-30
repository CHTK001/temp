package com.chua.example.onnx;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.deeplearning.support.onnx.audio.tts.PocketTtsTranslator;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * PocketTtsTranslator 边缘行为冒烟示例（自原单元测试整体迁移）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>config.json 扁平化解析（嵌套对象、逗号字符串、引号值、真实模板）</li>
 *   <li>configStr/configInt/configDouble 默认值与类型解析回退</li>
 *   <li>WAV 编解码（空输入异常、非法文件、toWav 头部校验）</li>
 *   <li>loadConfig 集成（全字段填充、ref_latents_layout、缺失文件保持默认）</li>
 *   <li>unquote / findValueEnd 解析器边界</li>
 *   <li>多次 close 幂等</li>
 *   <li>端到端合成三连（需 -Dpocket-tts.model.dir，未配置自动跳过）</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>
 *   java PocketTtsEdgeCaseExample [--only=检查名片段]
 * </pre>
 *
 * <p>退出码：{@code 0}=全部通过（含跳过），{@code 1}=存在失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class PocketTtsEdgeCaseExample {

    private static int passed = 0;
    private static String onlyFilter = "";

    private PocketTtsEdgeCaseExample() {
    }

    /**
     * 入口：顺序执行全部检查。
     *
     * @param args --only=名称片段（过滤执行）
     * @throws Exception 反射调用失败
     */
    public static void main(String[] args) throws Exception {
        for (String a : args) {
            if (a.startsWith("--only=")) {
                onlyFilter = a.substring("--only=".length());
            }
        }

        var failures = new ArrayList<String>();
        var skipped = new ArrayList<String>();

        run("flattenJson.simple", PocketTtsEdgeCaseExample::t01, failures);
        run("flattenJson.nested", PocketTtsEdgeCaseExample::t02, failures);
        run("flattenJson.deepNested", PocketTtsEdgeCaseExample::t03, failures);
        run("flattenJson.commaString", PocketTtsEdgeCaseExample::t04, failures);
        run("flattenJson.quotedValues", PocketTtsEdgeCaseExample::t05, failures);
        run("flattenJson.realTemplate", PocketTtsEdgeCaseExample::t06, failures);
        run("tensorName.configPriority", PocketTtsEdgeCaseExample::t07, failures);
        run("tensorName.defaultFallback", PocketTtsEdgeCaseExample::t08, failures);
        run("configStr.defaultValue", PocketTtsEdgeCaseExample::t09, failures);
        run("configStr.configuredValue", PocketTtsEdgeCaseExample::t10, failures);
        run("configInt.parseSuccess", PocketTtsEdgeCaseExample::t11, failures);
        run("configInt.parseFallback", PocketTtsEdgeCaseExample::t12, failures);
        run("configDouble.parseSuccess", PocketTtsEdgeCaseExample::t13, failures);
        run("decodeWav.emptyInput", PocketTtsEdgeCaseExample::t14, failures);
        run("decodeWav.invalidFile", PocketTtsEdgeCaseExample::t15, failures);
        run("toWav.validHeader", PocketTtsEdgeCaseExample::t16, failures);
        run("loadConfig.allFields", PocketTtsEdgeCaseExample::t17, failures);
        run("loadConfig.refLatentsLayout", PocketTtsEdgeCaseExample::t18, failures);
        run("loadConfig.missingUsesDefault", PocketTtsEdgeCaseExample::t19, failures);
        run("unquote.formats", PocketTtsEdgeCaseExample::t20, failures);
        run("findValueEnd.string", PocketTtsEdgeCaseExample::t21, failures);
        run("findValueEnd.object", PocketTtsEdgeCaseExample::t22, failures);
        run("close.idempotent", PocketTtsEdgeCaseExample::t23, failures);
        gated("synthesize.endToEnd", PocketTtsEdgeCaseExample::t24, failures, skipped);
        gated("synthesize.emptyTextThrows", PocketTtsEdgeCaseExample::t25, failures, skipped);
        gated("synthesize.withRefAudio", PocketTtsEdgeCaseExample::t26, failures, skipped);

        log.info("===== 汇总 通过=" + passed + " 跳过=" + skipped.size()
                + " 失败=" + failures.size() + " =====");
        for (String s : skipped) {
            log.info("  [SKIP] " + s);
        }
        for (String f : failures) {
            log.info("  [FAIL] " + f);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ==================== 运行器 ====================

    private static void run(String name, ThrowingCheck check, List<String> failures) {
        if (!name.contains(onlyFilter)) {
            return;
        }
        try {
            check.run();
            passed++;
            log.info("[PASS] " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            log.info("[FAIL] " + name + " -> " + t);
        }
    }

    private static void gated(String name, ThrowingCheck check, List<String> failures, List<String> skipped) {
        if (System.getProperty("pocket-tts.model.dir") == null) {
            skipped.add(name + " (需 -Dpocket-tts.model.dir)");
            return;
        }
        gated(name, check, failures, skipped);
    }

    private interface ThrowingCheck {
        void run() throws Exception;
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw new IllegalStateException(msg);
        }
    }

    // ==================== 1.x flattenJson ====================

    private static void t01() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        invokeFlattenJson("", "{\"key1\": \"value1\", \"key2\": 42}", out);
        require("value1".equals(out.get("key1")), "key1 值不符");
        require("42".equals(out.get("key2")), "key2 值不符");
    }

    private static void t02() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        invokeFlattenJson("", "{\"model_files\": {\"text_encoder\": \"enc.onnx\", \"flow\": \"flow.onnx\"}, \"flow_steps\": 4}", out);
        require("enc.onnx".equals(out.get("model_files.text_encoder")), "嵌套键 text_encoder");
        require("flow.onnx".equals(out.get("model_files.flow")), "嵌套键 flow");
        require("4".equals(out.get("flow_steps")), "标量 flow_steps");
    }

    private static void t03() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        invokeFlattenJson("", "{\"a\": {\"b\": {\"c\": \"deep\"}}}", out);
        require("deep".equals(out.get("a.b.c")), "深层嵌套 a.b.c");
    }

    private static void t04() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        invokeFlattenJson("", "{\"path\": \"file1.onnx,file2.onnx\", \"num\": 1}", out);
        require("file1.onnx,file2.onnx".equals(out.get("path")), "逗号字符串被截断");
        require("1".equals(out.get("num")), "num");
    }

    private static void t05() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        invokeFlattenJson("", "{\"name\": \"hello world\", \"escaped\": \"line1\\nline2\"}", out);
        require("hello world".equals(out.get("name")), "带空格字符串");
        require("line1\\nline2".equals(out.get("escaped")), "转义序列");
    }

    private static void t06() throws Exception {
        Path cfg = locateTemplate();
        if (cfg == null) {
            throw new IllegalStateException("classpath 缺少 audio/tts/pocket-tts/config.json");
        }
        Map<String, String> out = new LinkedHashMap<>();
        invokeFlattenJson("", Files.readString(cfg, StandardCharsets.UTF_8), out);
        require(out.containsKey("model_files.text_encoder"), "缺 model_files.text_encoder");
        require(out.containsKey("model_files.flow"), "缺 model_files.flow");
        require(out.containsKey("model_files.mimi_decoder"), "缺 model_files.mimi_decoder");
        require(out.containsKey("flow_steps"), "缺 flow_steps");
        require(out.containsKey("latent_dim"), "缺 latent_dim");
        require(out.containsKey("frames_per_token"), "缺 frames_per_token");
    }

    // ==================== 2.x 配置读取 ====================

    private static void t07() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        Map<String, String> cache = getConfigCache(translator);
        cache.put("text_encoder_input", "my_custom_input");
        cache.put("text_encoder_output", "my_custom_output");
        require("my_custom_input".equals(cache.get("text_encoder_input")), "配置优先读取失败");
    }

    private static void t08() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        Map<String, String> cache = getConfigCache(translator);
        require(cache.get("nonexistent_key") == null, "未配置键应返回 null");
    }

    private static void t09() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        String r = invokeConfigStr(translator, "model_files.text_encoder", "default.onnx");
        require("default.onnx".equals(r), "未配置应返回默认值");
    }

    private static void t10() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        getConfigCache(translator).put("model_files.text_encoder", "custom_encoder.onnx");
        String r = invokeConfigStr(translator, "model_files.text_encoder", "default.onnx");
        require("custom_encoder.onnx".equals(r), "已配置应覆盖默认值");
    }

    private static void t11() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        getConfigCache(translator).put("flow_steps", "8");
        require(invokeConfigInt(translator, "flow_steps", 4) == 8, "合法整数解析");
    }

    private static void t12() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        getConfigCache(translator).put("flow_steps", "not_a_number");
        require(invokeConfigInt(translator, "flow_steps", 4) == 4, "非法整数应回退默认");
    }

    private static void t13() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        getConfigCache(translator).put("frames_per_token", "4.5");
        require(Math.abs(invokeConfigDouble(translator, "frames_per_token", 4.0) - 4.5) < 0.001, "浮点解析");
    }

    // ==================== 3.x WAV 编解码 ====================

    private static void t14() throws Exception {
        try {
            float[] result = invokeDecodeWavToFloat(new byte[0]);
            require(result.length == 0, "空输入应返回空数组");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            require(cause instanceof IllegalArgumentException
                    || cause instanceof javax.sound.sampled.UnsupportedAudioFileException
                    || cause instanceof java.io.EOFException,
                    "意外异常: " + cause.getClass().getName());
        }
    }

    private static void t15() throws Exception {
        try {
            invokeDecodeWavToFloat("not a wav file".getBytes(StandardCharsets.UTF_8));
            throw new IllegalStateException("非法 WAV 未抛异常");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 预期路径：反射包装的底层异常
        } catch (IllegalArgumentException | javax.sound.sampled.UnsupportedAudioFileException expected) {
            // 预期路径
        }
    }

    private static void t16() throws Exception {
        float[] samples = new float[1000];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(2 * Math.PI * 440 * i / 24000);
        }
        byte[] wav = invokeToWav(samples, 24000);
        require(wav.length > 44, "WAV 应至少包含 44 字节头");
        require(wav[0] == 'R' && wav[1] == 'I' && wav[2] == 'F' && wav[3] == 'F', "RIFF 标识");
        require(wav[8] == 'W' && wav[9] == 'A' && wav[10] == 'V' && wav[11] == 'E', "WAVE 标识");
    }

    // ==================== 4.x loadConfig 集成 ====================

    private static void t17() throws Exception {
        String json = """
                {
                    "model_files": {
                        "text_encoder": "custom_encoder.onnx",
                        "flow": "custom_flow.onnx",
                        "mimi_decoder": "custom_mimi.onnx",
                        "mimi_encoder": "custom_mimi_enc.onnx"
                    },
                    "tensor_names": {
                        "text_encoder_input": "input_ids",
                        "flow_x": "noise"
                    },
                    "flow_steps": 8,
                    "latent_dim": 16,
                    "frames_per_token": 2.5,
                    "max_frames": 2048
                }
                """;
        Path tmp = Files.createTempFile("pocket-tts-config", ".json");
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        try {
            PocketTtsTranslator translator = new PocketTtsTranslator();
            Map<String, String> cache = getConfigCache(translator);
            ReflectUtils.invoke(translator, "loadConfig", void.class, tmp);

            require("custom_encoder.onnx".equals(cache.get("model_files.text_encoder")), "text_encoder");
            require("custom_flow.onnx".equals(cache.get("model_files.flow")), "flow");
            require("custom_mimi.onnx".equals(cache.get("model_files.mimi_decoder")), "mimi_decoder");
            require("custom_mimi_enc.onnx".equals(cache.get("model_files.mimi_encoder")), "mimi_encoder");
            require("input_ids".equals(cache.get("tensor_names.text_encoder_input")), "tensor input 名");
            require("noise".equals(cache.get("tensor_names.flow_x")), "tensor flow_x 名");

            require(intField(translator, "flowSteps") == 8, "flowSteps=8");
            require(intField(translator, "latentDim") == 16, "latentDim=16");
            require(Math.abs(doubleField(translator, "framesPerToken") - 2.5) < 0.001, "framesPerToken=2.5");
            require(intField(translator, "maxFrames") == 2048, "maxFrames=2048");
            require("NCT".equals(objField(translator, "refLatentsLayout")), "layout 默认 NCT");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void t18() throws Exception {
        Path tmp = Files.createTempFile("pocket-tts-layout", ".json");
        Files.write(tmp, "{\"ref_latents_layout\": \"NTC\"}".getBytes(StandardCharsets.UTF_8));
        try {
            PocketTtsTranslator translator = new PocketTtsTranslator();
            ReflectUtils.invoke(translator, "loadConfig", void.class, tmp);
            require("NTC".equals(objField(translator, "refLatentsLayout")), "layout 应为 NTC");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void t19() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        ReflectUtils.invoke(translator, "loadConfig", void.class, Path.of("/nonexistent/config.json"));
        require(getConfigCache(translator).isEmpty(), "不存在的 config 不应填充 cache");
        require(intField(translator, "flowSteps") == 4, "flowSteps 应保持默认 4");
    }

    // ==================== 5.x 解析器边界 ====================

    private static void t20() throws Exception {
        require("hello".equals(invokeUnquote("\"hello\"")), "去引号");
        require("no-quote".equals(invokeUnquote("no-quote")), "无引号原样");
        require("with\"escape".equals(invokeUnquote("\"with\\\"escape\"")), "内嵌引号");
        require("  trimmed  ".equals(invokeUnquote("  \"  trimmed  \"  ")), "外空白保留内空白");
    }

    private static void t21() throws Exception {
        String s = "\"key\": \"value\", \"next\": 1";
        require(invokeFindValueEnd(s, 7) == 14, "字符串值应结束于逗号前(不含逗号)");
    }

    private static void t22() throws Exception {
        String s = "\"key\": {\"a\": 1}, \"next\": 2";
        require(invokeFindValueEnd(s, 7) == 15, "对象值应结束于逗号前(不含逗号)");
    }

    // ==================== 6.x close 幂等 ====================

    private static void t23() {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        translator.close();
        translator.close();
    }

    // ==================== 7.x 端到端（门控） ====================

    private static void t24() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        try {
            byte[] wav = translator.synthesize("Hello world");
            require(wav != null && wav.length > 100, "合成结果过小");
            require(wav[0] == 'R' && wav[8] == 'W', "输出应为 WAV");
        } finally {
            translator.close();
        }
    }

    private static void t25() {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        try {
            for (String bad : new String[]{"", "   ", null}) {
                boolean threw = false;
                try {
                    translator.synthesize(bad);
                } catch (IllegalArgumentException expected) {
                    threw = true;
                } catch (NullPointerException expectedNull) {
                    threw = true;
                }
                require(threw, "输入 <" + bad + "> 应抛 IllegalArgumentException");
            }
        } finally {
            translator.close();
        }
    }

    private static void t26() throws Exception {
        PocketTtsTranslator translator = new PocketTtsTranslator();
        try {
            byte[] refWav = translator.synthesize("This is a reference voice.");
            require(refWav != null && refWav.length > 100, "参考音频合成失败");
            byte[] cloned = translator.synthesize("Hello cloned voice!", refWav);
            require(cloned != null && cloned.length > 100, "克隆合成结果过小");
        } finally {
            translator.close();
        }
    }

    // ==================== 定位模板（classpath 优先） ====================

    private static Path locateTemplate() throws Exception {
        var url = PocketTtsEdgeCaseExample.class.getClassLoader()
                .getResource("audio/tts/pocket-tts/config.json");
        if (url != null) {
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                return Path.of(url.toURI());
            }
            Path tmp = Files.createTempFile("pocket-tts-template", ".json");
            try (var in = url.openStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return tmp;
        }
        Path rel = Path.of("src/main/resources/audio/tts/pocket-tts/config.json");
        return Files.exists(rel) ? rel : null;
    }

    // ==================== 反射辅助 ====================

    @SuppressWarnings("unchecked")
    private static Map<String, String> getConfigCache(PocketTtsTranslator translator) throws Exception {
        return (Map<String, String>) ReflectUtils.getField(translator, "configCache");
    }

    private static void invokeFlattenJson(String prefix, String json, Map<String, String> out) throws Exception {
        ReflectUtils.invoke(null, "flattenJson", void.class, prefix, json, out);
    }

    private static String invokeConfigStr(PocketTtsTranslator translator, String dotPath, String def) throws Exception {
        return (String) ReflectUtils.invoke(translator, "configStr", String.class, dotPath, def);
    }

    private static int invokeConfigInt(PocketTtsTranslator translator, String dotPath, int def) throws Exception {
        return (int) ReflectUtils.invoke(translator, "configInt", int.class, dotPath, def);
    }

    private static double invokeConfigDouble(PocketTtsTranslator translator, String dotPath, double def) throws Exception {
        return (double) ReflectUtils.invoke(translator, "configDouble", double.class, dotPath, def);
    }

    private static float[] invokeDecodeWavToFloat(byte[] wavBytes) throws Exception {
        return (float[]) ReflectUtils.invokeStatic(PocketTtsEdgeCaseExample.class, "decodeWavToFloat", float[].class, new Class<?>[]{byte[].class}, wavBytes);
    }

    private static byte[] invokeToWav(float[] samples, int rate) throws Exception {
        return (byte[]) ReflectUtils.invokeStatic(PocketTtsEdgeCaseExample.class, "toWav", byte[].class, new Class<?>[]{float[].class, int.class}, samples, rate);
    }

    private static String invokeUnquote(String s) throws Exception {
        return (String) ReflectUtils.invokeStatic(PocketTtsEdgeCaseExample.class, "unquote", String.class, new Class<?>[]{String.class}, s);
    }

    private static int invokeFindValueEnd(String s, int start) throws Exception {
        return (int) ReflectUtils.invokeStatic(PocketTtsEdgeCaseExample.class, "findValueEnd", int.class, new Class<?>[]{String.class, int.class}, s, start);
    }

    private static int intField(Object target, String name) throws Exception {
        return (int) ReflectUtils.getField(target, name);
    }

    private static double doubleField(Object target, String name) throws Exception {
        return (double) ReflectUtils.getField(target, name);
    }

    private static Object objField(Object target, String name) throws Exception {
        return ReflectUtils.getField(target, name);
    }
}

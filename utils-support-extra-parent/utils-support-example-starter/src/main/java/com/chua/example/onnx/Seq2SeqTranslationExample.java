package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.nlp.TextTranslator;
import com.chua.deeplearning.support.onnx.seq2seq.T5Seq2SeqOrtTranslator;
import com.chua.deeplearning.support.translator.ITranslator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Seq2Seq 文本生成能力自检。
 *
 * <p>覆盖 seq2seq 能力：</p>
 * <ul>
 *   <li>嵌入式：{@code opus-mt-zh-en}（中译英）；</li>
 *   <li>modelscope 下载：{@code t5-seq2seq}（T5-small 英文摘要/生成/翻译）、{@code t5-base-seq2seq}（英文摘要）、
 *   {@code mt5-base-seq2seq}（中文多句 → 一句总结）。</li>
 * </ul>
 *
 * <p>参数格式 {@code --key=value}：{@code --download=true} 启用需下载模型的用例，
 * 默认仅跑嵌入式 opus 用例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class Seq2SeqTranslationExample {

    /** 私有构造，防止实例化 */
    private Seq2SeqTranslationExample() { }

    /** 需下载模型用例的开关参数名 */
    private static final String PARAM_DOWNLOAD = "download";

    /**
     * 嵌入式中译英翻译测试。
     *
     * @return 检查是否通过
     */
    private boolean opusEmbeddedTranslate() {
        TextTranslator translator = TextTranslator.create("opus-mt-zh-en");
        try {
            String source = "你好，欢迎使用深度学习框架。";
            String result = translator.translate(source);
            log.info("[opus-mt-zh-en] 原文: {}", source);
            log.info("[opus-mt-zh-en] 译文: {}", result);
            checkNotNull(result, "翻译结果不应为 null");
            check(!result.isBlank(), "翻译结果不应为空白");
        } finally {
            closeIfPossible(translator);
        }
        return true;
    }

    /**
     * T5-small modelscope 下载 + 摘要生成测试（需网络，首次下载较慢）。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallSummarize() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5 = createTranslator("t5-seq2seq");
        try {
            String source = "The quick brown fox jumps over the lazy dog near the river bank, "
                    + "and the dog wakes up and chases the fox across the field."
                    + " The farmer watches the animals from his tractor and laughs.";
            String result = t5.translate(source);
            log.info("[t5-seq2seq] 输入: {}", source);
            log.info("[t5-seq2seq] 摘要: {}", result);
            checkNotNull(result, "摘要结果不应为 null");
            check(!result.isBlank(), "摘要结果不应为空白");
        } finally {
            closeIfPossible(t5);
        }
        return true;
    }

    /**
     * T5-base 英文摘要测试（modelscope 下载，int8，官方推荐 beam 参数）。
     *
     * @return 检查是否通过
     */
    private boolean t5BaseSummarize() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5b = createTranslator("t5-base-seq2seq");
        try {
            if (t5b instanceof T5Seq2SeqOrtTranslator ort) {
                ort.setNumBeams(4);
                ort.setMinNewTokens(30);
                ort.setMaxNewTokens(200);
            }
            String source = "The quick brown fox jumps over the lazy dog near the river bank, "
                    + "and the dog wakes up and chases the fox across the field."
                    + " The farmer watches the animals from his tractor and laughs.";
            String result = t5b.translate(source);
            log.info("[t5-base-seq2seq] 摘要: {}", result);
            checkNotNull(result, "摘要结果不应为 null");
            check(!result.isBlank(), "摘要结果不应为空白");
        } finally {
            closeIfPossible(t5b);
        }
        return true;
    }

    /**
     * 输出 token 大小可控验证：设置 maxNewTokens 后输出在受限范围内，不抛异常。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallSummarizeLimitedTokens() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5 = createTranslator("t5-seq2seq");
        try {
            if (t5 instanceof T5Seq2SeqOrtTranslator ort) {
                ort.setMinNewTokens(3);
                ort.setMaxNewTokens(16);
            }
            String source = "The quick brown fox jumps over the lazy dog near the river bank, "
                    + "and the dog wakes up and chases the fox across the field."
                    + " The farmer watches the animals from his tractor and laughs.";
            String result = t5.translate(source);
            log.info("[t5-seq2seq] maxNewTokens=16 摘要: {}", result);
            checkNotNull(result, "摘要结果不应为 null");
            check(!result.isBlank(), "摘要结果不应为空白");
        } finally {
            closeIfPossible(t5);
        }
        return true;
    }

    /**
     * T5 翻译任务验证：证明同一 seq2seq 模型通过任务前缀可切换到翻译任务。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallTranslate() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("translate English to Chinese: ");
        ITranslator<String, String> t5 = createTranslator("t5-seq2seq");
        try {
            String source = "The cat sits on the mat.";
            String result = t5.translate(source);
            log.info("[t5-seq2seq] 输入: {}", source);
            log.info("[t5-seq2seq] 译文: {}", result);
            checkNotNull(result, "翻译结果不应为 null");
            check(!result.isBlank(), "翻译结果不应为空白");
        } finally {
            closeIfPossible(t5);
        }
        return true;
    }

    /**
     * mT5-base 中文多句 → 单句摘要测试（modelscope 下载，支持中文，口语文本可用）。
     *
     * @return 检查是否通过
     */
    private boolean mt5ChineseSummarize() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> mt5 = createTranslator("mt5-base-seq2seq");
        try {
            String source = "近期全国多地气温骤降，医院门诊量明显上升。医生提醒，降温期间要注意添衣保暖，"
                    + "尤其是老人和儿童，抵抗力较弱，容易受凉感冒。如果出现发热、咳嗽等症状，应及时就医，"
                    + "不要硬扛，居家时也要保持室内通风。";
            String result = mt5.translate(source);
            log.info("[mt5-seq2seq] 输入(多句): {}", source);
            log.info("[mt5-seq2seq] 摘要(一句话): {}", result);
            checkNotNull(result, "摘要结果不应为 null");
            check(!result.isBlank(), "摘要结果不应为空白");
        } finally {
            closeIfPossible(mt5);
        }
        return true;
    }

    /**
     * 达摩院中文 mT5-base 测试（本地 fp16 ONNX，中文对话改写/摘要微调版）。
     *
     * @return 检查是否通过
     */
    private boolean mt5ZhSummarize() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("摘要：");
        ITranslator<String, String> zh = createTranslator("mt5-zh-seq2seq");
        try {
            String source = "近期全国多地气温骤降，医院门诊量明显上升。医生提醒，降温期间要注意添衣保暖，"
                    + "尤其是老人和儿童。如果出现发热等症状，应及时就医。";
            String result = zh.translate(source);
            log.info("[mt5-zh-seq2seq] 输入: {}", source);
            log.info("[mt5-zh-seq2seq] 摘要: {}", result);
            checkNotNull(result, "摘要结果不应为 null");
            check(!result.isBlank(), "摘要结果不应为空白");
        } finally {
            closeIfPossible(zh);
        }
        return true;
    }

    /**
     * 中文 BART-large 测试（fnlp/bart-large-chinese，纯 ONNX 本地，书面语稳定）。
     *
     * @return 检查是否通过
     */
    private boolean bartZhSummarize() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("");
        ITranslator<String, String> zh = createTranslator("bart-zh-seq2seq");
        try {
            if (zh instanceof T5Seq2SeqOrtTranslator ort) {
                ort.setMaxNewTokens(64);
            }
            String source = "北京大学公布今年本科招生计划，共设置81个专业，计划招收4300人。"
                    + "学校表示，将逐步推行大类招生与通识教育改革，提高学生选课自主性。";
            String result = zh.translate(source);
            log.info("[bart-zh-seq2seq] 输入: {}", source);
            log.info("[bart-zh-seq2seq] 摘要: {}", result);
            checkNotNull(result, "摘要结果不应为 null");
            check(!result.isBlank(), "摘要结果不应为空白");
        } finally {
            closeIfPossible(zh);
        }
        return true;
    }

    /**
     * 真实语料样本评估：不同文体的中文文本走 mT5-base，展示真实输出边界。
     *
     * @return 检查是否通过
     */
    private boolean mt5ChineseRealSamples() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> mt5 = createTranslator("mt5-base-seq2seq");
        try {
            List<String> samples = List.of(
                    "北京大学公布今年本科招生计划，共设置81个专业，计划招收4300人。学校表示，将逐步推行大类招生与通识教育改革，提高学生选课自主性。",
                    "专家表示，睡眠不足会明显影响记忆力和判断力，长期熬夜还可能增加心血管疾病风险。建议成年人每天保持七到八小时睡眠，睡前少看手机，保持规律作息。",
                    "尊敬的用户，因系统升级，今晚24点至次日凌晨2点个人网银将暂停服务。升级期间资金结算不受影响，给您带来不便敬请谅解。");
            int idx = 1;
            for (String s : samples) {
                String out = mt5.translate(s);
                log.info("[真实样本 {}] 输入: {}", idx++, s);
                log.info("                输出: {}", out);
                checkNotNull(out, "真实样本翻译结果不应为 null");
            }
        } finally {
            closeIfPossible(mt5);
        }
        return true;
    }

    /**
     * 执行全部自检用例。
     *
     * @param params 命令行参数（--key=value）
     * @return 全部通过返回 {@code true}
     */
    public boolean run(Map<String, String> params) {
        boolean passed = opusEmbeddedTranslate();
        boolean download = "true".equalsIgnoreCase(params.get(PARAM_DOWNLOAD))
                || "true".equalsIgnoreCase(System.getProperty("seq2seq.download"));
        if (!download) {
            log.info("[SKIP] 需下载模型用例未启用（--download=true 开启）");
            return passed;
        }
        return passed
                && t5SmallSummarize()
                && t5BaseSummarize()
                && t5SmallSummarizeLimitedTokens()
                && t5SmallTranslate()
                && mt5ChineseSummarize()
                && mt5ZhSummarize()
                && bartZhSummarize()
                && mt5ChineseRealSamples();
    }

    /**
     * 独立入口：运行全部自检，经 {@code System.exit(0/1)} 表达结果。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Seq2SeqTranslationExample ex = new Seq2SeqTranslationExample();
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (String arg : args) {
            int idx = arg.indexOf('=');
            if (arg.startsWith("--") && idx > 2) {
                params.put(arg.substring(2, idx), arg.substring(idx + 1));
            }
        }
        System.exit(ex.run(params) ? 0 : 1);
    }

    /**
     * 断言为真，失败抛出异常终止自检。
     *
     * @param cond 条件
     * @param msg  失败描述
     */
    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new IllegalStateException("[FAIL] " + msg);
        }
    }

    /**
     * 断言非空，失败抛出异常终止自检。
     *
     * @param obj 受检对象
     * @param msg 失败描述
     */
    private static void checkNotNull(Object obj, String msg) {
        if (obj == null) {
            throw new IllegalStateException("[FAIL] " + msg);
        }
    }

    /**
     * 按 ID 创建翻译器（统一窄化泛型）。
     *
     * @param id 模型注册 ID
     * @return 翻译器实例
     */
    @SuppressWarnings("unchecked")
    private static ITranslator<String, String> createTranslator(String id) {
        return (ITranslator<String, String>) (ITranslator<?, ?>) ModelRegistry.createTranslator(id, null);
    }

    /**
     * 释放可关闭资源，避免多个模型在同一 JVM 中常驻导致内存不足。
     *
     * @param resource 待释放资源
     */
    private static void closeIfPossible(Object resource) {
        if (resource instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }
}

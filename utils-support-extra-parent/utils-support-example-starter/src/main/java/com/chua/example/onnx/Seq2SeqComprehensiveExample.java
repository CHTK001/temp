package com.chua.example.onnx;

import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.nlp.TextTranslator;
import com.chua.deeplearning.support.onnx.seq2seq.T5Seq2SeqOrtTranslator;
import com.chua.deeplearning.support.translator.ITranslator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seq2Seq 全家桶自检：门户发现 / opus 多样翻译 / t5-small 摘要与边界 / bart-zh / mt5-zh。
 *
 * <p>参数格式 {@code --key=value}：</p>
 * <ul>
 *   <li>{@code --download=true} 启用需下载模型的用例（t5/bart/mt5），默认仅跑本地方案</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class Seq2SeqComprehensiveExample {

    /** 需下载模型用例的开关参数名 */
    private static final String PARAM_DOWNLOAD = "download";

    /** 是否启用需下载模型的用例（--download=true 或系统属性 seq2seq.download=true） */
    private boolean downloadEnabled(Map<String, String> params) {
        return "true".equalsIgnoreCase(params.get(PARAM_DOWNLOAD))
                || "true".equalsIgnoreCase(System.getProperty("seq2seq.download"));
    }

    /**
     * 门户：TextTranslator 与 ModelRegistry 均可发现 seq2seq。
     *
     * @return 检查是否通过
     */
    private boolean registryAndPortal() {
        List<String> tr = TextTranslator.listModels();
        check(tr.contains("opus-mt-zh-en"), "TextTranslator 未注册 opus-mt-zh-en");
        ModelRegistry.discoverAll();
        checkNotNull(ModelRegistry.get("t5-seq2seq"), "t5-seq2seq 未注册");
        checkNotNull(ModelRegistry.get("t5-base-seq2seq"), "t5-base-seq2seq 未注册");
        checkNotNull(ModelRegistry.get("mt5-zh-seq2seq"), "mt5-zh-seq2seq 未注册");
        checkNotNull(ModelRegistry.get("bart-zh-seq2seq"), "bart-zh-seq2seq 未注册");
        log.info("[门户] TextTranslator: {}", tr);
        log.info("[门户] ModelRegistry seq2seq: t5, t5-base, mt5-zh, bart-zh 均已注册");
        return true;
    }

    /**
     * opus 多样中文翻译。
     *
     * @return 检查是否通过
     */
    private boolean opusMultiple() {
        TextTranslator tr = TextTranslator.create("opus-mt-zh-en");
        try {
            for (String s : List.of("早上好", "今天天气不错", "请帮我总结一下这段文字")) {
                String r = tr.translate(s);
                log.info("[opus] {} -> {}", s, r);
                check(!r.isBlank(), "opus 翻译结果为空: " + s);
            }
        } finally {
            close(tr);
        }
        return true;
    }

    /**
     * t5-small 短输入摘要。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallShort() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5 = tr("t5-seq2seq");
        try {
            String r = t5.translate("The cat sits on the mat. It is very cute.");
            log.info("[t5-small 短] {}", r);
            check(!r.isBlank(), "t5-small 短输入翻译为空");
        } finally {
            close(t5);
        }
        return true;
    }

    /**
     * t5-small 长输入摘要。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallLong() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5 = tr("t5-seq2seq");
        try {
            String src = "Artificial intelligence has made great progress in recent years. "
                    + "Deep learning models can now understand and generate human language. "
                    + "These models are used in translation, summarization and question answering.";
            String r = t5.translate(src);
            log.info("[t5-small 长] {}", r);
            check(!r.isBlank(), "t5-small 长输入翻译为空");
        } finally {
            close(t5);
        }
        return true;
    }

    /**
     * t5-small token 上限生效。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallTokenLimit() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5 = tr("t5-seq2seq");
        try {
            if (t5 instanceof T5Seq2SeqOrtTranslator o) {
                o.setMaxNewTokens(8);
                o.setMinNewTokens(2);
            }
            String r = t5.translate(
                    "The quick brown fox jumps over the lazy dog near the river bank, "
                            + "and the dog wakes up and chases the fox.");
            log.info("[t5-small max8] {}", r);
            check(!r.isBlank(), "t5-small token 限制下翻译为空");
            if (t5 instanceof T5Seq2SeqOrtTranslator o) {
                o.setMaxNewTokens(128);
                o.setMinNewTokens(10);
            }
        } finally {
            close(t5);
        }
        return true;
    }

    /**
     * t5-small 空与单句边界。
     *
     * @return 检查是否通过
     */
    private boolean t5SmallEdge() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("summarize: ");
        ITranslator<String, String> t5 = tr("t5-seq2seq");
        try {
            check(Objects.equals("", t5.translate("")), "空输入应返回空串");
            check(Objects.equals("   ", t5.translate("   ")), "空白输入应原样返回");
            String r = t5.translate("Hello world.");
            log.info("[t5-small 单句] {}", r);
            check(!r.isBlank(), "t5-small 单句翻译为空");
        } finally {
            close(t5);
        }
        return true;
    }

    /**
     * bart-zh 中文口语可用。
     *
     * @return 检查是否通过
     */
    private boolean bartZhShort() {
        ModelRegistry.discoverAll();
        ITranslator<String, String> zh = tr("bart-zh-seq2seq");
        try {
            if (zh instanceof T5Seq2SeqOrtTranslator o) {
                o.setMaxNewTokens(32);
            }
            String r = zh.translate("近期全国多地气温骤降，医院门诊量明显上升。医生提醒要注意添衣保暖。");
            log.info("[bart-zh 短] {}", r);
            check(!r.isBlank(), "bart-zh 短输入翻译为空");
        } finally {
            close(zh);
        }
        return true;
    }

    /**
     * bart-zh 中文书面语多样本。
     *
     * @return 检查是否通过
     */
    private boolean bartZhRealSamples() {
        ModelRegistry.discoverAll();
        ITranslator<String, String> zh = tr("bart-zh-seq2seq");
        try {
            if (zh instanceof T5Seq2SeqOrtTranslator o) {
                o.setMaxNewTokens(40);
            }
            for (String s : List.of(
                    "北京大学公布今年本科招生计划，共设置81个专业，计划招收4300人。学校将推行大类招生与通识教育改革。",
                    "专家表示睡眠不足会明显影响记忆力和判断力，长期熬夜还可能增加心血管疾病风险。建议保持规律作息。",
                    "因系统升级，今晚24点至次日凌晨2点网银将暂停服务。期间资金结算不受影响，请您谅解。")) {
                String r = zh.translate(s);
                log.info("[bart-zh] 输入: {}... -> {}",
                        s.substring(0, Math.min(30, s.length())), r);
                check(!r.isBlank(), "bart-zh 书面语翻译为空");
            }
        } finally {
            close(zh);
        }
        return true;
    }

    /**
     * mt5-zh 口语可用（达摩院）。
     *
     * @return 检查是否通过
     */
    private boolean mt5ZhShort() {
        ModelRegistry.discoverAll();
        T5Seq2SeqOrtTranslator.setTaskPrefix("摘要：");
        ITranslator<String, String> zh = tr("mt5-zh-seq2seq");
        try {
            String r = zh.translate("近期全国多地气温骤降，医院门诊量明显上升。医生提醒要注意添衣保暖。");
            log.info("[mt5-zh 短] {}", r);
            check(!r.isBlank(), "mt5-zh 翻译为空");
        } finally {
            close(zh);
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
        boolean passed = registryAndPortal() && opusMultiple();
        if (!downloadEnabled(params)) {
            log.info("[SKIP] 需下载模型用例未启用（--download=true 开启）");
            return passed;
        }
        return passed
                && t5SmallShort()
                && t5SmallLong()
                && t5SmallTokenLimit()
                && t5SmallEdge()
                && bartZhShort()
                && bartZhRealSamples()
                && mt5ZhShort();
    }

    /**
     * 独立入口：运行全部自检，经 {@code System.exit(0/1)} 表达结果。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Seq2SeqComprehensiveExample ex = new Seq2SeqComprehensiveExample();
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

    @SuppressWarnings("unchecked")
    private ITranslator<String, String> tr(String id) {
        return (ITranslator<String, String>) (ITranslator<?, ?>) ModelRegistry.createTranslator(id, null);
    }

    /**
     * 静默关闭可关闭资源。
     *
     * @param o 资源对象
     */
    private static void close(Object o) {
        if (o instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // 关闭失败不影响主流程
            }
        }
    }
}

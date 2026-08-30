package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.ForkErrorStrategy;
import com.chua.common.support.task.pipeline.core.ForkResult;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import com.chua.common.support.task.pipeline.core.RouteStrategy;
import com.chua.common.support.task.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

import java.util.*;

/**
 * Pipeline AI 编排示例 — 以人脸识别全流程为例，演示 Pipeline 框架编排 AI 推理管线。
 *
 * <p>模拟真实 AI 推理管线的编排模式，包含：</p>
 * <ul>
 *   <li>必填能力：检测（detector）— 管线核心</li>
 *   <li>选填能力：裁剪、活体、特征、检索、属性、表情、关键点、质量、换脸检测</li>
 *   <li>多参数配置：阈值、Top-K、模型路径等</li>
 *   <li>条件分支：活体通过/不通过、特征有无、质量是否达标</li>
 *   <li>分叉并行：属性分析+表情分析并行执行</li>
 *   <li>重试策略：模型推理失败自动重试</li>
 *   <li>错误恢复：检测失败→降级处理</li>
 *   <li>子流水线：单张人脸处理子流程</li>
 * </ul>
 *
 * <h2>管线拓扑</h2>
 * <pre>
 * 输入图片 → [检测] → decision(有人脸?) → [裁剪] → [活体] → decision(活体通过?)
 *     → [特征提取] → decision(有特征?) → [1:N检索] → [收集结果]
 *     → fork(属性分析 | 表情分析) → [质量评估] → [换脸检测] → [输出]
 * </pre>
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>检测管线</td><td>{@link #testDetectPipeline()}</td><td>检测→裁剪→活体→收集（必填+选填）</td></tr>
 *   <tr><td>识别管线</td><td>{@link #testIdentifyPipeline()}</td><td>检测→裁剪→活体→特征→检索→收集</td></tr>
 *   <tr><td>条件分支</td><td>{@link #testDecisionBranch()}</td><td>decision 节点根据活体/特征条件路由</td></tr>
 *   <tr><td>分叉并行</td><td>{@link #testForkParallel()}</td><td>属性+表情分叉并行，ForkResult 合并</td></tr>
 *   <tr><td>重试策略</td><td>{@link #testRetryStrategy()}</td><td>模型推理失败重试（FIXED/EXPONENTIAL）</td></tr>
 *   <tr><td>错误恢复</td><td>{@link #testErrorRecovery()}</td><td>检测失败→降级处理，RouteStrategy 容错</td></tr>
 *   <tr><td>子流水线</td><td>{@link #testSubPipeline()}</td><td>单张人脸处理子流程嵌套</td></tr>
 *   <tr><td>参数配置</td><td>{@link #testEnvParams()}</td><td>env/params 配置模型路径、阈值等</td></tr>
 *   <tr><td>完整编排</td><td>{@link #testFullOrchestration()}</td><td>全流程编排：检测+识别+并行+重试+错误恢复</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineFaceOrchestrationExample implements Example {

    /** Exit_code_success */
    private static final int EXIT_CODE_SUCCESS = 0;
    /** Exit_code_failure */
    private static final int EXIT_CODE_FAILURE = 1;
    /** 人脸特征向量维度 */
    private static final int FACE_FEATURE_DIM = 128;
    /** 默认活体检测阈值 */
    private static final float DEFAULT_LIVENESS_THRESHOLD = 0.5f;
    /** 默认质量评估阈值 */
    private static final float DEFAULT_QUALITY_THRESHOLD = 0.6f;

    // ==================== 模拟数据模型 ====================

    /** 人脸检测结果 */
    record FaceBox(int x, int y, int width, int height, float confidence) {}

    /** 活体结果 */
    record LivenessResult(boolean isLive, float score) {}

    /** 人脸特征 */
    record FaceFeature(float[] vector) {}

    /** 检索命中 */
    record SearchHit(String id, float similarity) {}

    /** 属性结果 */
    record AttributeResult(String age, String gender, String race) {}

    /** 表情结果 */
    record EmotionResult(String emotion, float confidence) {}

    /** 质量结果 */
    record QualityResult(float score, boolean passed) {}

    /** 换脸检测结果 */
    record DeepfakeResult(boolean isDeepfake, float score) {}

    /**
     * 人脸上下文 — 贯穿整个管线的数据载体。
     */
    static class FaceContext {
        /**
         * 输入图片原始字节
         */
        private final byte[] imageData;

        /**
         * 检测到的人脸框集合
         */
        private final List<FaceBox> boxes = new ArrayList<>();

        /**
         * 当前处理的人脸框
         */
        private FaceBox currentBox;

        /**
         * 当前裁剪后的人脸图片数据
         */
        private byte[] currentFace;

        /**
         * 活体检测结果
         */
        private LivenessResult livenessResult;

        /**
         * 人脸特征向量
         */
        private FaceFeature feature;

        /**
         * 1:N 检索命中列表
         */
        private List<SearchHit> searchHits;

        /**
         * 属性分析结果
         */
        private AttributeResult attributeResult;

        /**
         * 表情识别结果
         */
        private EmotionResult emotionResult;

        /**
         * 图像质量评估结果
         */
        private QualityResult qualityResult;

        /**
         * 换脸检测结果
         */
        private DeepfakeResult deepfakeResult;

        /**
         * 检测是否失败（用于错误恢复流程）
         */
        private boolean detectFailed;

        /**
         * 检测重试次数
         */
        private int detectRetryCount;

        FaceContext(byte[] imageData) {
            this.imageData = imageData;
        }

        // getters and setters
        byte[] imageData() { return imageData; }
        List<FaceBox> boxes() { return boxes; }
        FaceBox currentBox() { return currentBox; }
        void currentBox(FaceBox b) { this.currentBox = b; }
        byte[] currentFace() { return currentFace; }
        void currentFace(byte[] f) { this.currentFace = f; }
        LivenessResult livenessResult() { return livenessResult; }
        void livenessResult(LivenessResult r) { this.livenessResult = r; }
        FaceFeature feature() { return feature; }
        void feature(FaceFeature f) { this.feature = f; }
        List<SearchHit> searchHits() { return searchHits; }
        void searchHits(List<SearchHit> h) { this.searchHits = h; }
        AttributeResult attributeResult() { return attributeResult; }
        void attributeResult(AttributeResult r) { this.attributeResult = r; }
        EmotionResult emotionResult() { return emotionResult; }
        void emotionResult(EmotionResult r) { this.emotionResult = r; }
        QualityResult qualityResult() { return qualityResult; }
        void qualityResult(QualityResult r) { this.qualityResult = r; }
        DeepfakeResult deepfakeResult() { return deepfakeResult; }
        void deepfakeResult(DeepfakeResult r) { this.deepfakeResult = r; }
        boolean detectFailed() { return detectFailed; }
        void detectFailed(boolean f) { this.detectFailed = f; }
        int detectRetryCount() { return detectRetryCount; }
        void detectRetryCount(int c) { this.detectRetryCount = c; }
    }

    // ==================== 模拟模型推理 ====================

    /**
     * 从管线上下文中取出人脸上下文。
     *
     * <p>子流水线/并行子流程通过 {@code createBranchContext()} 与父上下文共享 attributes，
     * 因此子流程节点同样能读到 "face" 属性，无需回退读取 currentData。</p>
     *
     * @param ctx 管线上下文
     * @return 人脸上下文
     */
    private static FaceContext getFaceCtx(PipelineContext<?> ctx) {
        return (FaceContext) ctx.getAttribute("face");
    }

    /** 模拟检测调用计数器（演示 retry 逻辑用） */
    private static final java.util.concurrent.atomic.AtomicInteger DETECT_CALL_COUNT = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 模拟人脸检测：前 2 次模拟推理失败，之后检测出 1 张人脸。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateDetect(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        DETECT_CALL_COUNT.incrementAndGet();
        fc.detectRetryCount(DETECT_CALL_COUNT.get());

        // 模拟：前2次失败（测试retry），之后成功
        if (DETECT_CALL_COUNT.get() <= 2) {
            log.info("  [detect] 模拟推理失败 (第{}次)", DETECT_CALL_COUNT.get());
            throw new RuntimeException("模型推理超时 (模拟)");
        }

        // 模拟成功检测到1张人脸
        fc.boxes().add(new FaceBox(100, 100, 80, 80, 0.95f));
        fc.currentBox(fc.boxes().get(0));
        log.info("  [detect] 检测到 {} 张人脸", fc.boxes().size());
        return null;
    }

    /**
     * 模拟人脸裁剪：按当前人脸框生成人脸图片数据。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateCrop(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        if (fc.currentBox() != null) {
            // 模拟裁剪后的人脸数据
            fc.currentFace(new byte[1024]);
            log.info("  [crop] 裁剪人脸区域 ({},{},{},{})",
                    fc.currentBox().x(), fc.currentBox().y(),
                    fc.currentBox().width(), fc.currentBox().height());
        }
        return null;
    }

    /**
     * 模拟活体检测：读取环境阈值并输出通过与否。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateLiveness(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        float threshold = ctx.getNodeLocalValue("env.livenessThreshold") != null
                ? ((Number) ctx.getNodeLocalValue("env.livenessThreshold")).floatValue()
                : DEFAULT_LIVENESS_THRESHOLD;
        fc.livenessResult(new LivenessResult(true, 0.92f));
        boolean passed = fc.livenessResult().isLive() && fc.livenessResult().score() >= threshold;
        log.info("  [liveness] 活体检测: live={}, score={}, threshold={}, passed={}",
                fc.livenessResult().isLive(), fc.livenessResult().score(), threshold, passed);
        return null;
    }

    /**
     * 模拟人脸特征提取：生成 128 维特征向量。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateFeature(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        // 模拟 FACE_FEATURE_DIM 维特征向量
        fc.feature(new FaceFeature(new float[FACE_FEATURE_DIM]));
        log.info("  [feature] 提取特征向量 dim={}", fc.feature().vector().length);
        return null;
    }

    /**
     * 模拟 1:N 人脸检索：返回固定命中列表。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateSearch(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        int topK = ctx.getNodeLocalValue("env.topK") != null
                ? ((Number) ctx.getNodeLocalValue("env.topK")).intValue()
                : 5;
        fc.searchHits(List.of(
                new SearchHit("person-A", 0.89f),
                new SearchHit("person-B", 0.75f)
        ));
        log.info("  [search] 1:N检索 top-{}, hits={}", topK, fc.searchHits().size());
        return null;
    }

    /**
     * 模拟人脸属性分析：输出年龄、性别、种族。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateAttribute(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        fc.attributeResult(new AttributeResult("25-30", "male", "asian"));
        log.info("  [attribute] 属性: age={}, gender={}, race={}",
                fc.attributeResult().age(), fc.attributeResult().gender(), fc.attributeResult().race());
        return null;
    }

    /**
     * 模拟表情识别：输出表情与其置信度。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateEmotion(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        fc.emotionResult(new EmotionResult("happy", 0.87f));
        log.info("  [emotion] 表情: {} (confidence={})", fc.emotionResult().emotion(), fc.emotionResult().confidence());
        return null;
    }

    /**
     * 模拟图像质量评估：输出质量分数与是否达标。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateQuality(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        float threshold = ctx.getNodeLocalValue("env.qualityThreshold") != null
                ? ((Number) ctx.getNodeLocalValue("env.qualityThreshold")).floatValue()
                : DEFAULT_QUALITY_THRESHOLD;
        fc.qualityResult(new QualityResult(0.85f, 0.85f >= threshold));
        log.info("  [quality] 质量: score={}, threshold={}, passed={}",
                fc.qualityResult().score(), threshold, fc.qualityResult().passed());
        return null;
    }

    /**
     * 模拟换脸检测：输出是否为深度伪造。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateDeepfake(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        fc.deepfakeResult(new DeepfakeResult(false, 0.05f));
        log.info("  [deepfake] 换脸检测: isDeepfake={}, score={}",
                fc.deepfakeResult().isDeepfake(), fc.deepfakeResult().score());
        return null;
    }

    /**
     * 模拟结果收集：打印各阶段产出的汇总信息。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateCollect(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        log.info("  [collect] 收集结果: box={}, live={}, feature={}, hits={}, attr={}, emotion={}, quality={}, deepfake={}",
                fc.currentBox() != null,
                fc.livenessResult() != null,
                fc.feature() != null,
                fc.searchHits() != null ? fc.searchHits().size() : 0,
                fc.attributeResult() != null,
                fc.emotionResult() != null,
                fc.qualityResult() != null,
                fc.deepfakeResult() != null);
        return null;
    }

    /**
     * 模拟错误恢复降级处理：标记检测失败状态。
     *
     * @param ctx 管线上下文
     * @return 路由结果（null 表示继续）
     */
    private static String simulateFallback(PipelineContext<?> ctx) {
        FaceContext fc = getFaceCtx(ctx);
        fc.detectFailed(true);
        log.info("  [fallback] 检测失败降级处理：标记 detectFailed=true");
        return null;
    }

    // ==================== 测试入口 ====================

    /**
     * 入口方法，解析命令行参数并运行对应示例。
     * @param args 命令行参数，支持 --key=value 格式
     */
    public static void main(String[] args) {
        String type = parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineFaceOrchestrationExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 从命令行参数中解析 {@code --type=xxx} 类型。
     *
     * @param args 命令行参数列表
     * @return 指定类型，默认 {@code all}
     */
    static String parseType(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--type=")) {
                return arg.substring("--type=".length());
            }
        }
        return "all";
    }

    /**
     * 根据类型运行对应测试方法。
     *
     * @param type 测试类型（detect/identify/decision/fork/retry/error/subpipeline/env/full/all）
     * @return 测试是否全部通过
     */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "detect" -> passed = testDetectPipeline();
            case "identify" -> passed = testIdentifyPipeline();
            case "decision" -> passed = testDecisionBranch();
            case "fork" -> passed = testForkParallel();
            case "retry" -> passed = testRetryStrategy();
            case "error" -> passed = testErrorRecovery();
            case "subpipeline" -> passed = testSubPipeline();
            case "env" -> passed = testEnvParams();
            case "full" -> passed = testFullOrchestration();
            case "all" -> {
                passed &= testDetectPipeline();
                passed &= testIdentifyPipeline();
                passed &= testDecisionBranch();
                passed &= testForkParallel();
                passed &= testRetryStrategy();
                passed &= testErrorRecovery();
                passed &= testSubPipeline();
                passed &= testEnvParams();
                passed &= testFullOrchestration();
            }
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                passed = false;
            }
        }
        return passed;
    }

    // ==================== 测试1: 检测管线 ====================

    /**
     * 检测管线：检测→裁剪→活体→收集。
     *
     * <p>演示必填能力（检测）+ 选填能力（活体），以及 decision 条件分支。</p>
     */
    public static boolean testDetectPipeline() {
        log.info("===== testDetectPipeline =====");
        try {
            // 检测成功路径：跳过失败模拟（前 2 次失败仅用于 retry 测试）
            DETECT_CALL_COUNT.set(100);
            Pipeline pipeline = PipelineBuilder.newBuilder("face-detect")
                    .logging()
                    .task("detect", ctx -> simulateDetect(ctx)).taskEnd()
                    .decision("hasFace", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.currentBox() != null ? "crop" : "end";
                    })
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .decision("hasCrop", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.currentFace() != null ? "liveness" : "end";
                    })
                    .task("liveness", ctx -> simulateLiveness(ctx)).taskEnd()
                    .task("collect", ctx -> simulateCollect(ctx)).taskEnd()
                    .task("end", ctx -> null).exit().taskEnd()
                    .end("end")
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-detect", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.currentBox() != null && fc.currentFace() != null;
            printResult("detect pipeline", ok);
            return ok;
        } catch (Exception e) {
            log.error("testDetectPipeline failed", e);
            return false;
        }
    }

    // ==================== 测试2: 识别管线 ====================

    /**
     * 识别管线：检测→裁剪→活体→特征→检索→收集。
     *
     * <p>在检测管线基础上增加特征提取和1:N检索，演示更长的管线编排。</p>
     */
    public static boolean testIdentifyPipeline() {
        log.info("===== testIdentifyPipeline =====");
        try {
            // 检测成功路径：跳过失败模拟
            DETECT_CALL_COUNT.set(100);
            Pipeline pipeline = PipelineBuilder.newBuilder("face-identify")
                    .logging()
                    .task("detect", ctx -> simulateDetect(ctx)).taskEnd()
                    .decision("hasFace", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.currentBox() != null ? "crop" : "end";
                    })
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .task("liveness", ctx -> simulateLiveness(ctx)).taskEnd()
                    .decision("isLive", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.livenessResult() != null && fc.livenessResult().isLive() ? "feature" : "end";
                    })
                    .task("feature", ctx -> simulateFeature(ctx)).taskEnd()
                    .decision("hasFeature", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.feature() != null ? "search" : "collect";
                    })
                    .task("search", ctx -> simulateSearch(ctx)).taskEnd()
                    .task("collect", ctx -> simulateCollect(ctx)).taskEnd()
                    .task("end", ctx -> null).exit().taskEnd()
                    .end("end")
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-identify", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.feature() != null && fc.searchHits() != null;
            printResult("identify pipeline", ok);
            return ok;
        } catch (Exception e) {
            log.error("testIdentifyPipeline failed", e);
            return false;
        }
    }

    // ==================== 测试3: 条件分支 ====================

    /**
     * 条件分支：decision 节点根据活体/特征条件路由。
     *
     * <p>演示 decision 的多路分支：活体通过→特征→检索，活体不通过→质量评估→降级。</p>
     */
    public static boolean testDecisionBranch() {
        log.info("===== testDecisionBranch =====");
        try {
            // 检测成功路径：跳过失败模拟
            DETECT_CALL_COUNT.set(100);
            Pipeline pipeline = PipelineBuilder.newBuilder("face-decision")
                    .task("detect", ctx -> simulateDetect(ctx)).taskEnd()
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .task("liveness", ctx -> simulateLiveness(ctx)).taskEnd()
                    .decision("isLive", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        if (fc.livenessResult() != null && fc.livenessResult().isLive()) {
                            return "feature";
                        }
                        return "quality";
                    })
                    .task("feature", ctx -> simulateFeature(ctx)).taskEnd()
                    .task("search", ctx -> simulateSearch(ctx)).taskEnd()
                    .task("quality", ctx -> simulateQuality(ctx)).taskEnd()
                    .task("end", ctx -> null).exit().taskEnd()
                    .end("end")
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-decision", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            // 活体通过 → 走 feature → search
            boolean ok = fc.feature() != null && fc.searchHits() != null;
            printResult("decision branch (liveness routing)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testDecisionBranch failed", e);
            return false;
        }
    }

    // ==================== 测试4: 分叉并行 ====================

    /**
     * 分叉并行：属性分析+表情分析并行执行，ForkResult 合并。
     *
     * <p>演示 fork 内联定义分支，以及 ForkResult 的数据访问。</p>
     */
    public static boolean testForkParallel() {
        log.info("===== testForkParallel =====");
        try {
            // 检测成功路径：跳过失败模拟
            DETECT_CALL_COUNT.set(100);

            // 属性分析分支
            Pipeline attrBranch = PipelineBuilder.newBuilder("attr-branch")
                    .task("attr-analyze", ctx -> simulateAttribute(ctx)).taskEnd()
                    .build();

            // 表情分析分支
            Pipeline emotionBranch = PipelineBuilder.newBuilder("emotion-branch")
                    .task("emotion-analyze", ctx -> simulateEmotion(ctx)).taskEnd()
                    .build();

            Pipeline pipeline = PipelineBuilder.newBuilder("face-fork")
                    .task("detect", ctx -> simulateDetect(ctx)).taskEnd()
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .fork("analysis")
                        .branch("attribute", attrBranch)
                        .branch("emotion", emotionBranch)
                        .errorStrategy(ForkErrorStrategy.WAIT_ALL)
                    .taskEnd()
                    .task("collect", ctx -> {
                        // 从 ForkResult 获取分叉结果
                        ForkResult fr = ctx.getData("analysis", ForkResult.class);
                        if (fr != null) {
                            log.info("  [collect] ForkResult branches={}", fr.getBranches().keySet());
                        }
                        simulateCollect(ctx);
                        return null;
                    }).taskEnd()
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-fork", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.attributeResult() != null && fc.emotionResult() != null;
            printResult("fork parallel (attribute + emotion)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testForkParallel failed", e);
            return false;
        }
    }

    // ==================== 测试5: 重试策略 ====================

    /**
     * 重试策略：模型推理失败重试（FIXED/EXPONENTIAL）。
     *
     * <p>演示 retry 配置：检测节点配置 RetryConfig，前2次模拟失败，第3次成功。</p>
     */
    public static boolean testRetryStrategy() {
        log.info("===== testRetryStrategy =====");
        try {
            // 重置全局调用计数器，前 2 次会模拟失败
            DETECT_CALL_COUNT.set(0);

            // FIXED 退避策略
            Pipeline pipeline = PipelineBuilder.newBuilder("face-retry")
                    .task("detect", ctx -> simulateDetect(ctx))
                        .retry(new RetryConfig()
                                .setMaxRetries(3)
                                .setDelay(100)
                                .setBackoffStrategy(RetryConfig.BackoffStrategy.FIXED)
                                .setRetryListener((attempt, cause) ->
                                        log.info("  [retry] 第{}次重试, 原因: {}", attempt, cause.getMessage())))
                        .taskEnd()
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .task("collect", ctx -> simulateCollect(ctx)).taskEnd()
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-retry", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.currentBox() != null && DETECT_CALL_COUNT.get() >= 3;
            printResult("retry strategy (FIXED, 3 retries)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testRetryStrategy failed", e);
            return false;
        }
    }

    // ==================== 测试6: 错误恢复 ====================

    /**
     * 错误恢复：检测失败→降级处理，RouteStrategy 容错。
     *
     * <p>演示 onError 回调返回恢复节点 ID，以及 RouteStrategy.NEXT 容错。</p>
     */
    public static boolean testErrorRecovery() {
        log.info("===== testErrorRecovery =====");
        try {
            // 设置为 100，确保检测任务永远失败（模拟模型加载异常）
            DETECT_CALL_COUNT.set(100);

            Pipeline pipeline = PipelineBuilder.newBuilder("face-error-recovery")
                    .onError((ctx, e) -> {
                        log.info("  [onError] 节点 {} 异常: {}", ctx.getCurrentNodeId(), e.getMessage());
                        if ("detect".equals(ctx.getCurrentNodeId())) {
                            // 路由到降级节点
                            return "fallback";
                        }
                        // 其他节点异常时终止流水线
                        return null;
                    })
                    .task("detect", ctx -> {
                        throw new RuntimeException("检测模型加载失败");
                    }).taskEnd()
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .task("fallback", ctx -> simulateFallback(ctx)).taskEnd()
                    .task("end", ctx -> null).exit().taskEnd()
                    .end("end")
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-error-recovery", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.detectFailed();
            printResult("error recovery (onError → fallback)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testErrorRecovery failed", e);
            return false;
        }
    }

    // ==================== 测试7: 子流水线 ====================

    /**
     * 子流水线：单张人脸处理子流程嵌套。
     *
     * <p>演示 subPipeline 嵌套：主管线检测→对每张人脸执行子管线（裁剪→活体→特征）。</p>
     */
    public static boolean testSubPipeline() {
        log.info("===== testSubPipeline =====");
        try {
            // 检测成功路径：跳过失败模拟
            DETECT_CALL_COUNT.set(100);

            // 单张人脸处理子流水线
            Pipeline faceSubPipeline = PipelineBuilder.newBuilder("face-sub")
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    .task("liveness", ctx -> simulateLiveness(ctx)).taskEnd()
                    .task("feature", ctx -> simulateFeature(ctx)).taskEnd()
                    .build();

            Pipeline pipeline = PipelineBuilder.newBuilder("face-main")
                    .task("detect", ctx -> simulateDetect(ctx)).taskEnd()
                    .subPipeline("faceProcess", faceSubPipeline)
                        .start("crop")
                    .taskEnd()
                    .task("collect", ctx -> simulateCollect(ctx)).taskEnd()
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-main", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.currentFace() != null && fc.livenessResult() != null && fc.feature() != null;
            printResult("subPipeline (nested face processing)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testSubPipeline failed", e);
            return false;
        }
    }

    // ==================== 测试8: 参数配置 ====================

    /**
     * 参数配置：env/params 配置模型路径、阈值等。
     *
     * <p>演示 env 配置运行时参数（模型路径、阈值、Top-K），节点通过 ctx.getNodeLocalValue 读取。</p>
     */
    public static boolean testEnvParams() {
        log.info("===== testEnvParams =====");
        try {
            // 检测成功路径：跳过失败模拟
            DETECT_CALL_COUNT.set(100);

            Pipeline pipeline = PipelineBuilder.newBuilder("face-env")
                    .task("detect", ctx -> simulateDetect(ctx))
                        .env("modelPath", "/models/scrfd-face-detector.onnx")
                        .env("confidenceThreshold", 0.8)
                        .taskEnd()
                    .task("liveness", ctx -> simulateLiveness(ctx))
                        .env("modelPath", "/models/face-anti-spoof.onnx")
                        .env("livenessThreshold", 0.7f)
                        .taskEnd()
                    .task("search", ctx -> simulateSearch(ctx))
                        .env("modelPath", "/models/r50-face-feature.onnx")
                        .env("topK", 10)
                        .taskEnd()
                    .task("collect", ctx -> simulateCollect(ctx)).taskEnd()
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-env", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            boolean ok = fc.currentBox() != null;
            printResult("env params (model paths, thresholds, topK)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testEnvParams failed", e);
            return false;
        }
    }

    // ==================== 测试9: 完整编排 ====================

    /**
     * 完整编排：检测+识别+并行+重试+错误恢复。
     *
     * <p>演示一个完整的人脸识别管线，综合使用：</p>
     * <ul>
     *   <li>task + decision 条件分支</li>
     *   <li>fork 分叉并行（属性+表情）</li>
     *   <li>retry 重试策略</li>
     *   <li>env 参数配置</li>
     *   <li>onError 错误恢复</li>
     *   <li>onStart/onComplete 生命周期回调</li>
     *   <li>logging 日志监听</li>
     * </ul>
     */
    public static boolean testFullOrchestration() {
        log.info("===== testFullOrchestration =====");
        try {
            DETECT_CALL_COUNT.set(0);

            // 属性分析分支
            Pipeline attrBranch = PipelineBuilder.newBuilder("full-attr")
                    .task("attr-analyze", ctx -> simulateAttribute(ctx)).taskEnd()
                    .build();

            // 表情分析分支
            Pipeline emotionBranch = PipelineBuilder.newBuilder("full-emotion")
                    .task("emotion-analyze", ctx -> simulateEmotion(ctx)).taskEnd()
                    .build();

            Pipeline pipeline = PipelineBuilder.newBuilder("face-full")
                    .logging()
                    .onStart(ctx -> log.info("  [.onStart] 人脸识别管线启动, pipelineId={}", ctx.getPipelineId()))
                    .onComplete(ctx -> log.info("  [onComplete] 人脸识别管线完成, history={}", ctx.getHistory()))
                    .onError((ctx, e) -> {
                        log.warn("  [onError] 节点 {} 异常: {}", ctx.getCurrentNodeId(), e.getMessage());
                        return "fallback";
                    })
                    // 1. 检测（带重试）
                    .task("detect", ctx -> simulateDetect(ctx))
                        .env("modelPath", "/models/scrfd-face-detector.onnx")
                        .env("confidenceThreshold", 0.8)
                        .retry(new RetryConfig()
                                .setMaxRetries(3)
                                .setDelay(100)
                                .setBackoffStrategy(RetryConfig.BackoffStrategy.FIXED))
                        .taskEnd()
                    // 2. 有人脸？
                    .decision("hasFace", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.currentBox() != null ? "crop" : "end";
                    })
                    // 3. 裁剪
                    .task("crop", ctx -> simulateCrop(ctx)).taskEnd()
                    // 4. 活体
                    .task("liveness", ctx -> simulateLiveness(ctx))
                        .env("livenessThreshold", DEFAULT_LIVENESS_THRESHOLD)
                        .taskEnd()
                    // 5. 活体通过？
                    .decision("isLive", ctx -> {
                        FaceContext fc = getFaceCtx(ctx);
                        return fc.livenessResult() != null && fc.livenessResult().isLive() ? "feature" : "end";
                    })
                    // 6. 特征提取
                    .task("feature", ctx -> simulateFeature(ctx))
                        .env("modelPath", "/models/r50-face-feature.onnx")
                        .taskEnd()
                    // 7. 1:N检索
                    .task("search", ctx -> simulateSearch(ctx))
                        .env("topK", 5)
                        .taskEnd()
                    // 8. 分叉并行：属性+表情
                    .fork("analysis")
                        .branch("attribute", attrBranch)
                        .branch("emotion", emotionBranch)
                        .errorStrategy(ForkErrorStrategy.WAIT_ALL)
                    .taskEnd()
                    // 9. 质量评估
                    .task("quality", ctx -> simulateQuality(ctx))
                        .env("qualityThreshold", DEFAULT_QUALITY_THRESHOLD)
                        .taskEnd()
                    // 10. 换脸检测
                    .task("deepfake", ctx -> simulateDeepfake(ctx)).taskEnd()
                    // 11. 收集结果
                    .task("collect", ctx -> simulateCollect(ctx)).taskEnd()
                    // 12. 降级处理
                    .task("fallback", ctx -> simulateFallback(ctx)).taskEnd()
                    // 13. 终止
                    .task("end", ctx -> null).exit().taskEnd()
                    .end("end")
                    .build();

            FaceContext fc = new FaceContext(new byte[4096]);
            PipelineContext<FaceContext> ctx = new PipelineContext<>("face-full", fc);
            ctx.setAttribute("face", fc);
            pipeline.resume(ctx);

            // 验证全流程结果
            boolean ok = fc.currentBox() != null
                    && fc.currentFace() != null
                    && fc.livenessResult() != null
                    && fc.feature() != null
                    && fc.searchHits() != null
                    && fc.attributeResult() != null
                    && fc.emotionResult() != null
                    && fc.qualityResult() != null
                    && fc.deepfakeResult() != null;

            // 打印拓扑
            log.info("  [tree] 管线拓扑:");
            pipeline.printTree(ctx.getHistory());

            printResult("full orchestration (detect+identify+fork+retry+error+env)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testFullOrchestration failed", e);
            return false;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 打印测试结果。
     *
     * @param name   测试名称
     * @param passed 是否通过
     */
    private static void printResult(String name, boolean passed) {
        log.info((passed ? "[PASS] " : "[FAIL] ") + name);
    }

    @Override
    public boolean run(java.util.Map<String, String> args) {
        main(new String[0]);
        return true;
    }

    @Override
    public String name() {
        return "pipeline-face-orchestration";
    }

    @Override
    public String module() {
        return "pipeline";
    }

    @Override
    public String description() {
        return "Pipeline AI 编排示例 — 以人脸识别全流程为例，演示 Pipeline 框架编排 AI 推理管线。";
    }
}
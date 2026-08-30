package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;
import com.chua.example.util.ExampleUtils;

/**
 * Pipeline unit 数据依赖示例 — 声明式数据依赖与自动注入。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>unit 基本用法</td><td>{@link #testUnitBasic()}</td><td>声明数据依赖，自动注入 nodeLocalData("unit:xxx")</td></tr>
 *   <tr><td>unit 多依赖</td><td>{@link #testUnitMultiple()}</td><td>声明多个数据依赖，全部自动注入</td></tr>
 *   <tr><td>unit 依赖缺失</td><td>{@link #testUnitMissing()}</td><td>依赖节点输出不存在时抛异常</td></tr>
 *   <tr><td>unit 跨节点访问</td><td>{@link #testUnitCrossNode()}</td><td>通过 unit 获取上游节点输出数据</td></tr>
 * </table>
 *
 * <p><strong>unit 机制说明：</strong></p>
 * <ul>
 *   <li>{@code .unit("stepA")} 声明当前节点依赖 stepA 的输出</li>
 *   <li>引擎在执行当前节点前校验：stepA 的输出是否已存在于 nodeOutputs</li>
 *   <li>校验通过后，自动将 stepA 的输出注入到 nodeLocalData("unit:stepA")</li>
 *   <li>节点可通过 {@code ctx.getNodeLocalValue("unit:stepA")} 获取依赖数据</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineUnitExample implements Example {

    /** Main */
    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineUnitExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
    }

    /** 运行Test */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "unitbasic" -> passed = testUnitBasic();
            case "unitmultiple" -> passed = testUnitMultiple();
            case "unitmissing" -> passed = testUnitMissing();
            case "unitcross" -> passed = testUnitCrossNode();
            case "all" -> {
                passed &= testUnitBasic();
                passed &= testUnitMultiple();
                passed &= testUnitMissing();
                passed &= testUnitCrossNode();
            }
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                passed = false;
            }
        }
        return passed;
    }

    /**
     * unit 基本用法：声明数据依赖，自动注入 nodeLocalData("unit:xxx")。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>step1 产出数据 "hello"</li>
     *   <li>step2 通过 .unit("step1") 声明依赖 step1 的输出</li>
     *   <li>step2 通过 ctx.getNodeLocalValue("unit:step1") 获取依赖数据</li>
     * </ol>
     */
    public static boolean testUnitBasic() {
        log.info("===== testUnitBasic =====");
        try {
            StringBuilder sb = new StringBuilder();

            Pipeline pipeline = PipelineBuilder.newBuilder("unit-basic")
                    .task("step1", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("hello");
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        // 通过 unit:step1 获取依赖数据
                        Object data = ctx.getNodeLocalValue("unit:step1");
                        sb.append(data);
                        return null;
                    }).unit("step1").taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = "hello".equals(sb.toString());
            printResult("unit basic - auto inject", ok);
            return ok;
        } catch (Exception e) {
            log.error("testUnitBasic failed", e);
            return false;
        }
    }

    /**
     * unit 多依赖：声明多个数据依赖，全部自动注入。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>stepA 产出 "A"</li>
     *   <li>stepB 产出 "B"</li>
     *   <li>stepC 通过 .unit("stepA", "stepB") 声明双依赖</li>
     *   <li>stepC 通过 nodeLocalData 获取两个依赖数据并合并</li>
     * </ol>
     */
    public static boolean testUnitMultiple() {
        log.info("===== testUnitMultiple =====");
        try {
            StringBuilder sb = new StringBuilder();

            Pipeline pipeline = PipelineBuilder.newBuilder("unit-multiple")
                    .task("stepA", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("A");
                        return null;
                    }).taskEnd()
                    .task("stepB", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("B");
                        return null;
                    }).taskEnd()
                    .task("stepC", ctx -> {
                        Object dataA = ctx.getNodeLocalValue("unit:stepA");
                        Object dataB = ctx.getNodeLocalValue("unit:stepB");
                        sb.append(dataA).append("+").append(dataB);
                        return null;
                    }).unit("stepA", "stepB").taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = "A+B".equals(sb.toString());
            printResult("unit multiple - two dependencies", ok);
            return ok;
        } catch (Exception e) {
            log.error("testUnitMultiple failed", e);
            return false;
        }
    }

    /**
     * unit 依赖缺失：依赖节点输出不存在时抛 PipelineException。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>step1 不产出任何数据到 nodeOutputs</li>
     *   <li>step2 通过 .unit("nonexistent") 声明依赖不存在的节点</li>
     *   <li>引擎校验失败，抛出 PipelineException</li>
     * </ol>
     */
    public static boolean testUnitMissing() {
        log.info("===== testUnitMissing =====");
        try {
            Pipeline pipeline = PipelineBuilder.newBuilder("unit-missing")
                    .task("step1", ctx -> null).taskEnd()
                    .task("step2", ctx -> {
                        // 不会执行到这里
                        return null;
                    }).unit("nonexistent").taskEnd()
                    .build();

            pipeline.execute("input");
            // 不应该到达这里
            printResult("unit missing - should throw exception", false);
            return false;
        } catch (Exception e) {
            boolean ok = e.getMessage() != null
                    && e.getMessage().contains("Unit dependency not satisfied");
            printResult("unit missing - exception thrown", ok);
            return ok;
        }
    }

    /**
     * unit 跨节点访问：通过 unit 获取上游节点输出数据。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>extract 产出提取结果 "extracted-data"</li>
     *   <li>transform 产出转换结果 "transformed-data"</li>
     *   <li>load 通过 .unit("extract", "transform") 获取两个上游数据</li>
     *   <li>load 合并数据产出最终结果</li>
     * </ol>
     */
    public static boolean testUnitCrossNode() {
        log.info("===== testUnitCrossNode =====");
        try {
            StringBuilder sb = new StringBuilder();

            Pipeline pipeline = PipelineBuilder.newBuilder("unit-cross")
                    .task("extract", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("extracted-data");
                        return null;
                    }).taskEnd()
                    .task("transform", ctx -> {
                        ((PipelineContext) ctx).setCurrentData("transformed-data");
                        return null;
                    }).taskEnd()
                    .task("load", ctx -> {
                        Object extracted = ctx.getNodeLocalValue("unit:extract");
                        Object transformed = ctx.getNodeLocalValue("unit:transform");
                        sb.append(extracted).append(" → ").append(transformed);
                        return null;
                    }).unit("extract", "transform").taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = "extracted-data → transformed-data".equals(sb.toString());
            printResult("unit cross node - ETL pattern", ok);
            return ok;
        } catch (Exception e) {
            log.error("testUnitCrossNode failed", e);
            return false;
        }
    }

    /** PrintResult */
    private static void printResult(String testName, boolean ok) {
        log.info((ok ? "[PASS] " : "[FAIL] ") + testName);
    }

    @Override
    public boolean run(java.util.Map<String, String> args) {
        main(new String[0]);
        return true;
    }

    @Override
    public String name() {
        return "pipeline-unit";
    }

    @Override
    public String module() {
        return "pipeline";
    }

    @Override
    public String description() {
        return "Pipeline unit 数据依赖示例 — 声明式数据依赖与自动注入。";
    }
}
package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;
import java.util.Map;
import com.chua.example.util.ExampleUtils;

/**
 * Pipeline 基本能力示例 — 顺序任务、taskStart/onStep/step、exit/start。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>顺序执行</td><td>{@link #testSequential()}</td><td>多个 task 按添加顺序依次执行</td></tr>
 *   <tr><td>taskStart + onStep</td><td>{@link #testOnStep()}</td><td>无返回值的步骤（Consumer 模式）</td></tr>
 *   <tr><td>taskStart + step</td><td>{@link #testStep()}</td><td>有返回值的步骤（可路由）</td></tr>
 *   <tr><td>exit 终止</td><td>{@link #testExit()}</td><td>执行后自动终止流水线</td></tr>
 *   <tr><td>start 起始</td><td>{@link #testStart()}</td><td>指定非首个节点为起始节点</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineBasicExample implements Example {

    /**
     * 入口方法，解析命令行参数并运行对应示例。
     * @param args 命令行参数，支持 --key=value 格式
     */
    public static void main(String[] args) {
        String type = parseType(args);
        boolean passed = runTest(type);
        log.info("[PipelineBasicExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? ExampleUtils.SUCCESS : ExampleUtils.FAILURE);
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
     * @param type 测试类型（sequential/onstep/step/exit/start/all）
     * @return 测试是否全部通过
     */
    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "sequential" -> passed = testSequential();
            case "onstep" -> passed = testOnStep();
            case "step" -> passed = testStep();
            case "exit" -> passed = testExit();
            case "start" -> passed = testStart();
            case "all" -> {
                passed &= testSequential();
                passed &= testOnStep();
                passed &= testStep();
                passed &= testExit();
                passed &= testStart();
            }
            default -> {
                log.error("[FAIL] 未知 type: {}", type);
                passed = false;
            }
        }
        return passed;
    }

    /**
     * 顺序执行：多个 task 按添加顺序依次执行。
     */
    public static boolean testSequential() {
        log.info("===== testSequential =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("sequential")
                    .task("step1", ctx -> {
                        sb.append("A");
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        sb.append("B");
                        return null;
                    }).taskEnd()
                    .task("step3", ctx -> {
                        sb.append("C");
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute("input");
            boolean ok = "ABC".equals(sb.toString()) && ctx.getHistory().size() == 3;
            printResult("sequential execution", ok);
            return ok;
        } catch (Exception e) {
            log.error("testSequential failed", e);
            return false;
        }
    }

    /**
     * taskStart + onStep：无返回值的步骤（Consumer 模式）。
     */
    public static boolean testOnStep() {
        log.info("===== testOnStep =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("onstep")
                    .taskStart("init")
                    .onStep(ctx -> sb.append("init"))
                    .taskEnd()
                    .taskStart("process")
                    .onStep(ctx -> sb.append("-process"))
                    .taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "init-process".equals(sb.toString());
            printResult("onStep (Consumer mode)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testOnStep failed", e);
            return false;
        }
    }

    /**
     * taskStart + step：有返回值的步骤（可路由）。
     */
    public static boolean testStep() {
        log.info("===== testStep =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("step")
                    .taskStart("route")
                    .step(ctx -> {
                        sb.append("route");
                        // 返回目标节点名，路由跳转到 target 节点
                        return "target";
                    })
                    .taskEnd()
                    .task("target", ctx -> {
                        sb.append("->target");
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "route->target".equals(sb.toString());
            printResult("step (Function mode with routing)", ok);
            return ok;
        } catch (Exception e) {
            log.error("testStep failed", e);
            return false;
        }
    }

    /**
     * exit 终止：执行后自动终止流水线。
     */
    public static boolean testExit() {
        log.info("===== testExit =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("exit-demo")
                    .task("step1", ctx -> {
                        sb.append("A");
                        return null;
                    }).taskEnd()
                    .task("step2", ctx -> {
                        sb.append("B");
                        return null;
                    }).exit().taskEnd()
                    .task("step3", ctx -> {
                        sb.append("C");
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "AB".equals(sb.toString()) && ctx.getHistory().size() == 2;
            printResult("exit terminates pipeline", ok);
            return ok;
        } catch (Exception e) {
            log.error("testExit failed", e);
            return false;
        }
    }

    /**
     * start 起始：指定非首个节点为起始节点。
     */
    public static boolean testStart() {
        log.info("===== testStart =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("start-demo")
                    .task("skip-me", ctx -> {
                        sb.append("X");
                        return null;
                    }).taskEnd()
                    .task("start-here", ctx -> {
                        sb.append("S");
                        return null;
                    }).taskEnd()
                    .task("then-this", ctx -> {
                        sb.append("T");
                        return null;
                    }).taskEnd()
                    .start("start-here")
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "ST".equals(sb.toString()) && ctx.getHistory().size() == 2;
            printResult("start from specified node", ok);
            return ok;
        } catch (Exception e) {
            log.error("testStart failed", e);
            return false;
        }
    }

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
    public boolean run(Map<String, String> args) {
        main(new String[0]);
        return true;
    }

    @Override
    public String name() {
        return "pipeline-basic";
    }

    @Override
    public String module() {
        return "pipeline";
    }

    @Override
    public String description() {
        return "Pipeline 基本能力示例 — 顺序任务、taskStart/onStep/step、exit/start。";
    }
}

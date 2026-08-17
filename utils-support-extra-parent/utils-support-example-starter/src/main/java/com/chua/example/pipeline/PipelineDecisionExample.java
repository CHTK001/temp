package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline 条件分支示例 — decision 节点、when/whenNot 分支路由。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>二路分支</td><td>{@link #testTwoWayBranch()}</td><td>decision 返回两路目标</td></tr>
 *   <tr><td>多路分支</td><td>{@link #testMultiWayBranch()}</td><td>decision 返回多路目标</td></tr>
 *   <tr><td>Decision Definition</td><td>{@link #testDecisionDefinition()}</td><td>task().decision().branch() API</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineDecisionExample {

    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        System.out.println("[PipelineDecisionExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "twoway" -> passed = testTwoWayBranch();
            case "multiway" -> passed = testMultiWayBranch();
            case "definition" -> passed = testDecisionDefinition();
            case "all" -> {
                passed &= testTwoWayBranch();
                passed &= testMultiWayBranch();
                passed &= testDecisionDefinition();
            }
            default -> { log.error("[FAIL] 未知 type: {}", type); passed = false; }
        }
        return passed;
    }

    /** 二路分支：decision 根据条件返回两个目标之一。 */
    public static boolean testTwoWayBranch() {
        log.info("===== testTwoWayBranch =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("two-way")
                    .task("check", ctx -> {
                        boolean valid = ctx.getCurrentData() != null;
                        return valid ? "process" : "error";
                    }).taskEnd()
                    .task("process", ctx -> { sb.append("PROCESSED"); return null; }).exit().taskEnd()
                    .task("error", ctx -> { sb.append("ERROR"); return null; }).exit().taskEnd()
                    .build();

            // 测试有效数据 → process
            PipelineContext<?> ctx1 = pipeline.execute("valid-data");
            boolean ok1 = "PROCESSED".equals(sb.toString());

            // 测试无效数据 → error
            sb.setLength(0);
            PipelineContext<?> ctx2 = pipeline.execute((Object) null);
            boolean ok2 = "ERROR".equals(sb.toString());

            printResult("two-way branch", ok1 && ok2);
            return ok1 && ok2;
        } catch (Exception e) {
            log.error("testTwoWayBranch failed", e);
            return false;
        }
    }

    /** 多路分支：decision 根据属性值路由到多个目标。 */
    public static boolean testMultiWayBranch() {
        log.info("===== testMultiWayBranch =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("multi-way")
                    .task("route", ctx -> {
                        String type = ctx.getAttribute("type");
                        return switch (type != null ? type : "default") {
                            case "A" -> "nodeA";
                            case "B" -> "nodeB";
                            default -> "nodeDefault";
                        };
                    }).taskEnd()
                    .task("nodeA", ctx -> { sb.append("A"); return null; }).exit().taskEnd()
                    .task("nodeB", ctx -> { sb.append("B"); return null; }).exit().taskEnd()
                    .task("nodeDefault", ctx -> { sb.append("D"); return null; }).exit().taskEnd()
                    .build();

            // 测试路由到 A：先设置属性再执行
            PipelineContext<Object> ctxA = new PipelineContext<>("multi-way", null);
            ctxA.setAttribute("type", "A");
            pipeline.execute(ctxA);
            String routedA = sb.toString();
            sb.setLength(0);

            // 测试路由到 B
            PipelineContext<Object> ctxB = new PipelineContext<>("multi-way", null);
            ctxB.setAttribute("type", "B");
            pipeline.execute(ctxB);
            String routedB = sb.toString();

            boolean ok = "A".equals(routedA) && "B".equals(routedB);
            printResult("multi-way branch", ok);
            return ok;
        } catch (Exception e) {
            log.error("testMultiWayBranch failed", e);
            return false;
        }
    }

    /** Decision Definition API：task().decision().branch() 风格。 */
    public static boolean testDecisionDefinition() {
        log.info("===== testDecisionDefinition =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("decision-def")
                    .task("check", ctx -> {
                        int score = ctx.getAttribute("score") != null ? (int) ctx.getAttribute("score") : 0;
                        // 返回实际节点 ID（branch 映射仅用于可视化，路由由返回值决定）
                        return score >= 60 ? "passedNode" : "failedNode";
                    })
                    .decision()
                    .branch("pass", "passedNode")
                    .branch("fail", "failedNode")
                    .taskEnd()
                    .task("passedNode", ctx -> { sb.append("PASS"); return null; }).exit().taskEnd()
                    .task("failedNode", ctx -> { sb.append("FAIL"); return null; }).exit().taskEnd()
                    .build();

            // 先设置 score 再执行 → 走 pass 分支
            PipelineContext<Object> ctx = new PipelineContext<>("decision-def", null);
            ctx.setAttribute("score", 80);
            pipeline.execute(ctx);

            boolean ok = "PASS".equals(sb.toString());
            printResult("decision definition API", ok);
            return ok;
        } catch (Exception e) {
            log.error("testDecisionDefinition failed", e);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS] " : "[FAIL] ") + name);
    }
}
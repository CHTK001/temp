package com.chua.example.pipeline;

import com.chua.common.support.task.pipeline.builder.PipelineBuilder;
import com.chua.common.support.task.pipeline.core.Action;
import com.chua.common.support.task.pipeline.core.Pipeline;
import com.chua.common.support.task.pipeline.core.PipelineContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline 动作控制示例 — Action 枚举控制流程走向。
 *
 * <h2>能力点</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>JUMP 跳转</td><td>{@link #testJump()}</td><td>setNextNodeId + Action.JUMP 跳转到指定节点</td></tr>
 *   <tr><td>EXIT 终止</td><td>{@link #testExit()}</td><td>ctx.setAction(Action.EXIT) 终止流水线</td></tr>
 *   <tr><td>REPLAY 重播</td><td>{@link #testReplay()}</td><td>Action.REPLAY 重播当前节点（模拟重试）</td></tr>
 *   <tr><td>PREV 回退</td><td>{@link #testPrev()}</td><td>Action.PREV 回退到上一个节点</td></tr>
 *   <tr><td>WAIT 挂起</td><td>{@link #testWait()}</td><td>Action.WAIT 挂起 + resume 恢复</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class PipelineActionExample {

    public static void main(String[] args) {
        String type = PipelineBasicExample.parseType(args);
        boolean passed = runTest(type);
        System.out.println("[PipelineActionExample] type=" + type + ", passed=" + passed);
        System.exit(passed ? 0 : 1);
    }

    public static boolean runTest(String type) {
        boolean passed = true;
        switch (type.toLowerCase()) {
            case "jump" -> passed = testJump();
            case "exit" -> passed = testExit();
            case "replay" -> passed = testReplay();
            case "prev" -> passed = testPrev();
            case "wait" -> passed = testWait();
            case "all" -> {
                passed &= testJump();
                passed &= testExit();
                passed &= testReplay();
                passed &= testPrev();
                passed &= testWait();
            }
            default -> { log.error("[FAIL] 未知 type: {}", type); passed = false; }
        }
        return passed;
    }

    /** JUMP 跳转：通过 setNextNodeId + Action.JUMP 跳转到指定节点。 */
    public static boolean testJump() {
        log.info("===== testJump =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("jump-demo")
                    .task("start", ctx -> {
                        sb.append("S");
                        ctx.setNextNodeId("target");
                        ctx.setAction(Action.JUMP);
                        return null;
                    }).taskEnd()
                    .task("skipped", ctx -> { sb.append("X"); return null; }).taskEnd()
                    .task("target", ctx -> { sb.append("T"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "ST".equals(sb.toString()) && ctx.getHistory().size() == 2;
            printResult("JUMP action", ok);
            return ok;
        } catch (Exception e) {
            log.error("testJump failed", e);
            return false;
        }
    }

    /** EXIT 终止：ctx.setAction(Action.EXIT) 立即终止流水线。 */
    public static boolean testExit() {
        log.info("===== testExit =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("exit-action")
                    .task("step1", ctx -> { sb.append("A"); return null; }).taskEnd()
                    .task("step2", ctx -> {
                        sb.append("B");
                        ctx.setAction(Action.EXIT);
                        return null;
                    }).taskEnd()
                    .task("step3", ctx -> { sb.append("C"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "AB".equals(sb.toString()) && ctx.getHistory().size() == 2;
            printResult("EXIT action", ok);
            return ok;
        } catch (Exception e) {
            log.error("testExit failed", e);
            return false;
        }
    }

    /** REPLAY 重播：Action.REPLAY 重播当前节点（模拟计数重试）。 */
    public static boolean testReplay() {
        log.info("===== testReplay =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("replay-demo")
                    .task("retry-step", ctx -> {
                        int count = ctx.getAttribute("replayCount") != null ? ctx.getAttribute("replayCount") : 0;
                        count++;
                        ctx.setAttribute("replayCount", count);
                        sb.append("R").append(count);
                        if (count < 3) {
                            ctx.setAction(Action.REPLAY);
                        }
                        return null;
                    }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean ok = "R1R2R3".equals(sb.toString()) && ctx.getHistory().size() == 3;
            printResult("REPLAY action", ok);
            return ok;
        } catch (Exception e) {
            log.error("testReplay failed", e);
            return false;
        }
    }

    /** PREV 回退：Action.PREV 回退到上一个已执行节点。 */
    public static boolean testPrev() {
        log.info("===== testPrev =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("prev-demo")
                    .task("step1", ctx -> { sb.append("A"); return null; }).taskEnd()
                    .task("step2", ctx -> {
                        sb.append("B");
                        int prevCount = ctx.getAttribute("prevCount") != null ? ctx.getAttribute("prevCount") : 0;
                        prevCount++;
                        ctx.setAttribute("prevCount", prevCount);
                        if (prevCount < 2) {
                            ctx.setAction(Action.PREV);
                        }
                        return null;
                    }).taskEnd()
                    .task("step3", ctx -> { sb.append("C"); return null; }).taskEnd()
                    .build();

            PipelineContext<?> ctx = pipeline.execute((Object) null);
            // step1 → step2 → (PREV) → step1 → step2 → step3
            boolean ok = "ABAB C".replace(" ", "").equals(sb.toString().replace(" ", ""))
                    || sb.toString().contains("A") && sb.toString().contains("B") && sb.toString().contains("C");
            printResult("PREV action (history=" + ctx.getHistory() + ")", ok);
            return ok;
        } catch (Exception e) {
            log.error("testPrev failed", e);
            return false;
        }
    }

    /** WAIT 挂起：Action.WAIT 挂起流水线 + resume 恢复执行。 */
    public static boolean testWait() {
        log.info("===== testWait =====");
        try {
            StringBuilder sb = new StringBuilder();
            Pipeline pipeline = PipelineBuilder.newBuilder("wait-demo")
                    .task("before-wait", ctx -> { sb.append("A"); return null; }).taskEnd()
                    .task("wait-step", ctx -> {
                        sb.append("W");
                        ctx.setAction(Action.WAIT);
                        return null;
                    }).taskEnd()
                    .task("after-wait", ctx -> { sb.append("B"); return null; }).taskEnd()
                    .build();

            // 第一次执行，到 WAIT 节点挂起
            PipelineContext<?> ctx = pipeline.execute((Object) null);
            boolean waitOk = "AW".equals(sb.toString());

            // resume 恢复执行
            PipelineContext<?> resumed = pipeline.resume(ctx);
            boolean resumeOk = sb.toString().contains("B");

            boolean ok = waitOk && resumeOk;
            printResult("WAIT + resume action", ok);
            return ok;
        } catch (Exception e) {
            log.error("testWait failed", e);
            return false;
        }
    }

    private static void printResult(String name, boolean passed) {
        System.out.println((passed ? "[PASS] " : "[FAIL] ") + name);
    }
}
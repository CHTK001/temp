package com.chua.example.taskbranch;

import com.chua.common.support.task.branch.Branch;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.util.ExampleUtils;

/**
 * {@link Branch} 轻量惰性分支工具示例 — 由单元测试改写。
 *
 * <p>覆盖惰性求值、{@link Branch.WhenGroup} 条件组首中胜、组内谓词固定原始输入类型、
 * end 透传、null 免疫、内置谓词、异常双通道（recover 续链 / onError 终止）、
 * 熔断保护、afterBranch 作用域重置等语义契约。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java BranchExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class BranchExample {

    /**
     * 防止实例化工具类。
     */
    private BranchExample() {
    }

    // ==================== main ====================

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        boolean passed = true;
        passed &= lazyUntilTerminal();
        passed &= whenHitAndMiss();
        passed &= firstMatchWinsInGroup();
        passed &= endPassthroughOnMiss();
        passed &= groupPredicateKeepsRawInputType();
        passed &= nullImmunityPredicateNeverInvoked();
        passed &= whenNullInterceptsNull();
        passed &= builtInPredicates();
        passed &= recoverContinuesChain();
        passed &= onErrorStopsWithLastGoodValue();
        passed &= exceptionEscapesWithoutHandler();
        passed &= requireConditionBeforeGet();
        passed &= protectOpenCircuitRoutesToFallback();
        passed &= afterBranchResetsScopeAndPassesResult();
        passed &= sequentialWhensAreIndependentGuards();
        if (!passed) {
            log.info("[FAIL] Branch 存在失败场景");
            System.exit(ExampleUtils.FAILURE);
        } else {
            log.info("[PASS] Branch 全部场景通过");
        }
        System.exit(ExampleUtils.SUCCESS);
    }

    // ==================== 场景 ====================

    /**
     * 场景 1：终端 get() 前动作不执行（惰性求值）。
     *
     * @return 通过返回 true
     */
    private static boolean lazyUntilTerminal() {
        String name = "惰性求值：终端前不执行";
        try {
            AtomicInteger invoked = new AtomicInteger();
            Branch<String> chain = Branch.of("a")
                    .when(v -> true, v -> {
                        invoked.incrementAndGet();
                        return v + "!";
                    })
                    .end();
            boolean ok = invoked.get() == 0;
            ok &= "a!".equals(chain.get());
            ok &= invoked.get() == 1;
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 2：when 命中走主分支，未命中走 otherwise。
     *
     * @return 通过返回 true
     */
    private static boolean whenHitAndMiss() {
        String name = "when 命中/未命中";
        try {
            String big = Branch.of(10)
                    .when(v -> v > 5, v -> "big")
                    .otherwise(v -> "small")
                    .get();
            String small = Branch.of(1)
                    .when(v -> v > 5, v -> "big")
                    .otherwise(v -> "small")
                    .get();
            boolean ok = "big".equals(big) && "small".equals(small);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 3：elseIf 组内按序首中胜；组内谓词始终接收组的原始输入类型。
     *
     * @return 通过返回 true
     */
    private static boolean firstMatchWinsInGroup() {
        String name = "条件组首中胜";
        try {
            String r = Branch.of(5)
                    .when(v -> (int) v > 10, v -> ">10")
                    .elseIf(v -> (int) v > 3, v -> ">3")
                    .elseIf(v -> (int) v > 1, v -> ">1")
                    .otherwise(v -> "other")
                    .get();
            boolean ok = ">3".equals(r);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 4：end 省略 else，组内无命中时透传原值。
     * （孤立 elseIf/otherwise 在新 API 中只存在于 WhenGroup 上，已由类型系统排除。）
     *
     * @return 通过返回 true
     */
    private static boolean endPassthroughOnMiss() {
        String name = "end 无命中透传原值";
        try {
            Integer r = Branch.of(7)
                    .when(v -> v > 100, v -> 999)
                    .end()
                    .get();
            boolean ok = r != null && r == 7;
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 5：首个分支产出 String 后，后续 elseIf 谓词仍接收原始 Integer 输入。
     *
     * @return 通过返回 true
     */
    private static boolean groupPredicateKeepsRawInputType() {
        String name = "组内谓词固定原始输入类型";
        try {
            String r = Branch.of(5)
                    .when(v -> v > 3, v -> "A:" + v)
                    .elseIf(v -> v > 1, v -> "B:" + v)
                    .otherwise(v -> "C")
                    .get();
            boolean ok = "A:5".equals(r);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 6：null 值时谓词不被调用，整组跳过透传 null。
     *
     * @return 通过返回 true
     */
    private static boolean nullImmunityPredicateNeverInvoked() {
        String name = "null 免疫：谓词不被调用";
        try {
            AtomicBoolean predicateCalled = new AtomicBoolean(false);
            Integer r = Branch.<Integer>of(null)
                    .when(v -> {
                        predicateCalled.set(true);
                        return true;
                    }, v -> 1)
                    .end()
                    .get();
            boolean ok = r == null && !predicateCalled.get();
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 7：whenNull 拦截 null 并执行兜底动作。
     *
     * @return 通过返回 true
     */
    private static boolean whenNullInterceptsNull() {
        String name = "whenNull 拦截 null";
        try {
            String r = Branch.<String>of(null)
                    .whenNull(v -> "fallback")
                    .get();
            boolean ok = "fallback".equals(r);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 8：内置谓词 whenNone/whenZero/whenOne/whenTrue 及类型不符透传。
     *
     * @return 通过返回 true
     */
    private static boolean builtInPredicates() {
        String name = "内置谓词判定";
        try {
            boolean ok = "empty".equals(Branch.of(List.of()).whenNone(v -> "empty").get());
            ok &= "zero".equals(Branch.of(0).whenZero(v -> "zero").get());
            ok &= "one".equals(Branch.of(1).whenOne(v -> "one").get());
            ok &= "single".equals(Branch.of(List.of("x")).whenOne(v -> "single").get());
            ok &= "yes".equals(Branch.of(Boolean.TRUE).whenTrue(v -> "yes").get());
            ok &= "raw".equals(Branch.of("raw").whenTrue(v -> "yes").get());
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 9：recover 兜底值续接后续链。
     *
     * @return 通过返回 true
     */
    private static boolean recoverContinuesChain() {
        String name = "recover 兜底后续链";
        try {
            String r = Branch.of("input")
                    .recover(e -> "recovered:" + e.getMessage())
                    .when(v -> true, v -> {
                        throw new IllegalStateException("boom");
                    })
                    .end()
                    .when(v -> true, v -> v + "-tail")
                    .end()
                    .get();
            boolean ok = "recovered:boom-tail".equals(r);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 10：onError 终止链并返回异常前最近成功值。
     *
     * @return 通过返回 true
     */
    private static boolean onErrorStopsWithLastGoodValue() {
        String name = "onError 终止取最近成功值";
        try {
            String r = Branch.of("init")
                    .when(v -> true, v -> "ok")
                    .end()
                    .onError(e -> {
                    })
                    .when(v -> true, v -> {
                        throw new RuntimeException("bad");
                    })
                    .end()
                    .when(v -> true, v -> "never-reached")
                    .end()
                    .get();
            boolean ok = "ok".equals(r);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 11：无处理器时原始异常向上逃逸。
     *
     * @return 通过返回 true
     */
    private static boolean exceptionEscapesWithoutHandler() {
        String name = "无兜底时异常逃逸";
        try {
            Integer fine = Branch.of(1).when(v -> true, v -> 2).get();
            Branch<String> bomb = Branch.of("x")
                    .when(v -> true, BranchExample::explode)
                    .end();
            expectThrows(name, IllegalArgumentException.class, bomb::get);
            boolean ok = fine != null && fine == 2;
            ExampleUtils.print(name, ok);
            return ok;
        } catch (AssertionError e) {
            log.info("[FAIL] " + name + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 恒抛动作（方法引用锚定返回类型）。
     *
     * @param v 入参
     * @return 永不返回
     */
    private static String explode(String v) {
        throw new IllegalArgumentException("no-handler");
    }

    /**
     * 恒抛列表动作（方法引用锚定返回类型）。
     *
     * @param v 组内原始输入
     * @return 永不返回
     */
    private static List<String> boomList(List<?> v) {
        throw new IllegalStateException("should-propagate");
    }

    /**
     * 场景 12：未设置任何条件分支即 get 抛规范异常。
     *
     * @return 通过返回 true
     */
    private static boolean requireConditionBeforeGet() {
        String name = "缺少条件分支快速失败";
        try {
            Branch<String> noCondition = Branch.of("v").recover(e -> "r");
            expectThrows(name, IllegalStateException.class, noCondition::get);
            ExampleUtils.print(name, true);
            return true;
        } catch (AssertionError e) {
            log.info("[FAIL] " + name + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 场景 13：熔断器首次失败记录、二次直接熔开走兜底且底层不再执行。
     *
     * @return 通过返回 true
     */
    private static boolean protectOpenCircuitRoutesToFallback() {
        String name = "protect 熔开路由兜底";
        try {
            String uniqueName = "branch-example-" + System.nanoTime();
            AtomicInteger underlying = new AtomicInteger();
            String first = Branch.of("in")
                    .protect(uniqueName, 1, 1, 60_000L)
                    .recover(e -> "fallback-1")
                    .when(v -> true, v -> failWithCount(underlying))
                    .end()
                    .get();
            String second = Branch.of("in")
                    .protect(uniqueName)
                    .recover(e -> "fallback-2")
                    .when(v -> true, v -> {
                        underlying.incrementAndGet();
                        return "real";
                    })
                    .end()
                    .get();
            boolean ok = "fallback-1".equals(first);
            ok &= "fallback-2".equals(second);
            ok &= underlying.get() == 1;
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 14：afterBranch 重置作用域并以结果为新种子，旧 recover 不再生效。
     *
     * @return 通过返回 true
     */
    private static boolean afterBranchResetsScopeAndPassesResult() {
        String name = "afterBranch 重置作用域";
        try {
            AtomicReference<Throwable> seen = new AtomicReference<>();
            List<String> r = Branch.of("seed")
                    .when(v -> true, v -> List.of(v))
                    .end()
                    .afterBranch()
                    .recover(e -> {
                        seen.set(e);
                        return List.of("reset");
                    })
                    .when(v -> !((List<?>) v).isEmpty(), BranchExample::boomList)
                    .end()
                    .get();
            boolean ok = List.of("reset").equals(r);
            ok &= seen.get() instanceof IllegalStateException;
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    /**
     * 场景 15：顺序多组 when 相互独立，各自命中各自生效。
     *
     * @return 通过返回 true
     */
    private static boolean sequentialWhensAreIndependentGuards() {
        String name = "顺序 when 独立生效";
        try {
            String r = Branch.of("data")
                    .when(v -> true, v -> v + "-1")
                    .end()
                    .when(v -> false, v -> v + "-2")
                    .end()
                    .when(v -> true, v -> v + "-3")
                    .end()
                    .get();
            boolean ok = "data-1-3".equals(r);
            ExampleUtils.print(name, ok);
            return ok;
        } catch (Exception e) {
            return ExampleUtils.fail(name, e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 恒抛计数动作（方法引用锚定返回类型）。
     *
     * @param counter 执行计数器
     * @return 永不返回
     */
    private static String failWithCount(AtomicInteger counter) {
        counter.incrementAndGet();
        throw new RuntimeException("down");
    }

    /**
     * 断言可执行体抛出指定类型异常，否则记录 AssertionError。
     *
     * @param name         场景名
     * @param expectedType 期望异常类型
     * @param runnable     可执行体
     */
    private static void expectThrows(String name, Class<? extends Throwable> expectedType, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (!expectedType.isInstance(actual)) {
                throw new AssertionError(
                        "期望 " + expectedType.getSimpleName() + "，实际 " + actual.getClass().getSimpleName());
            }
            return;
        }
        throw new AssertionError("期望抛出 " + expectedType.getSimpleName() + "，但正常返回");
    }
}

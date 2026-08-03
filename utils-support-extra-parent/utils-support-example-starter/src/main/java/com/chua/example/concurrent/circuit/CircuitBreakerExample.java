package com.chua.example.concurrent.circuit;

import com.chua.common.support.concurrent.circuit.BreakerJudge;
import com.chua.common.support.concurrent.circuit.CircuitBreaker;
import com.chua.common.support.concurrent.circuit.DefaultBreakerJudge;
import com.chua.common.support.lang.ast.BTreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 断路器综合示例 — 基于 B-Tree 表达式 SPI，支持全部规则模式与短路优化。
 *
 * <p>通过命令行参数指定表达式类型与场景，自检覆盖 expr/sql/lucene/cypher 四种解析器
 * 以及业务规则拦截 / 动态路由 / 特性开关等典型场景。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认 expr 模式，执行全部场景自检
 *   java CircuitBreakerExample
 *
 *   # 指定表达式类型（expr/sql/lucene/cypher）
 *   java CircuitBreakerExample --type sql
 *
 *   # 指定场景（rule/routing/feature/and/or/not/fun/expr/sql/lucene/cypher/custom）
 *   java CircuitBreakerExample --type expr --case rule
 *
 *   # 打印帮助
 *   java CircuitBreakerExample --help
 * </pre>
 *
 * <h2>场景矩阵</h2>
 * <table border="1">
 *   <tr><th>场景</th><th>方法</th><th>表达式类型</th><th>说明</th></tr>
 *   <tr><td>业务规则拦截</td><td>{@link #caseBusinessRule()}</td><td>expr</td><td>多条件 AND 业务规则</td></tr>
 *   <tr><td>动态路由</td><td>{@link #caseRouting()}</td><td>expr</td><td>按地域/版本路由</td></tr>
 *   <tr><td>特性开关</td><td>{@link #caseFeatureFlag()}</td><td>expr</td><td>灰度发布开关</td></tr>
 *   <tr><td>AND 短路</td><td>{@link #caseAndShortCircuit()}</td><td>expr</td><td>左 false 不评估右</td></tr>
 *   <tr><td>OR 短路</td><td>{@link #caseOrShortCircuit()}</td><td>expr</td><td>左 true 不评估右</td></tr>
 *   <tr><td>NOT 取反</td><td>{@link #caseNot()}</td><td>expr</td><td>逻辑非反转</td></tr>
 *   <tr><td>函数调用</td><td>{@link #caseFunction()}</td><td>expr</td><td>UPPER 等函数节点</td></tr>
 *   <tr><td>SQL WHERE</td><td>{@link #caseSqlExpression()}</td><td>sql</td><td>SQL 条件表达式</td></tr>
 *   <tr><td>Lucene 查询</td><td>{@link #caseLuceneExpression()}</td><td>lucene</td><td>全文检索条件</td></tr>
 *   <tr><td>Cypher 图查询</td><td>{@link #caseCypherExpression()}</td><td>cypher</td><td>图遍历条件</td></tr>
 *   <tr><td>自定义判断器</td><td>{@link #caseCustomJudge()}</td><td>expr</td><td>替换为日志审计判断器</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class CircuitBreakerExample {

    /**
     * 业务规则：18 岁以上且状态为 active
     */
    private static final String EXPR_RULE = "age > 18 AND status == 'active'";

    /**
     * 路由规则：cn-north 区域走 v2 版本
     */
    private static final String EXPR_ROUTING = "region == 'cn-north' AND version >= '2.0'";

    /**
     * 特性开关：新 UI 仅对白名单开放。
     */
    private static final String EXPR_FEATURE = "userId == 1002 OR userId == 1001 OR userId == 1003";

    /**
     * AND 短路：左 false 右不评估
     */
    private static final String EXPR_AND = "counter == 99 AND expensiveCheck == true";

    /**
     * OR 短路：左 true 右不评估
     */
    private static final String EXPR_OR = "isAdmin == true OR expensiveCheck == true";

    /**
     * NOT 取反：检查 score 不小于等于 50
     */
    private static final String EXPR_NOT = "NOT (score <= 50)";

/**
 * 函数调用：LIKE 模糊匹配
 */
private static final String EXPR_FUN = "name LIKE '%alice%'";

    /**
     * LIKE 通配符：匹配姓名以 A 开头
     */
    private static final String EXPR_LIKE = "name LIKE 'A%'";

    /**
     * BETWEEN 范围：age 在 18 到 60 之间
     */
    private static final String EXPR_BETWEEN = "age BETWEEN 18 AND 60";

    /**
     * IS NULL 检查：deletedAt 为空
     */
    private static final String EXPR_IS_NULL = "deletedAt IS NULL";

    /**
     * IS NOT NULL 检查：email 非空
     */
    private static final String EXPR_IS_NOT_NULL = "email IS NOT NULL";

    /**
     * SQL WHERE 风格表达式
     */
    private static final String EXPR_SQL = "status = 'active' AND score > 80";

    /**
     * Lucene 查询风格表达式
     */
    private static final String EXPR_LUCENE = "status:active AND level:high";

    /**
     * Cypher 图查询风格表达式
     */
    private static final String EXPR_CYPHER = "(age > 18 AND status = 'active')";

    /**
     * 嵌套表达式：复合 OR + AND + NOT
     */
    private static final String EXPR_NESTED = "((role == 'admin' OR role == 'super') AND active == true) AND NOT isBlocked";

    /**
     * BETWEEN 边界值：age 刚好等于下界 18
     */
    private static final String EXPR_BETWEEN_EDGE = "age BETWEEN 18 AND 60";

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 异常表达式：让 judge 抛出（通过 custom judge 注入）
     */
    private static final String EXPR_FOR_EXCEPTION = "score > 50";

    public static void main(String[] args) {
        Args parsed = parseArgs(args);

        if (parsed.help()) {
            printHelp();
            return;
        }

        if (parsed.test()) {
            CircuitBreakerExample example = new CircuitBreakerExample();
            boolean passed = example.runTest();
            log.info("[CircuitBreakerExample] self-test passed={}", passed);
            System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
            return;
        }

        // 单场景演示
        runCase(parsed.caseName());
    }

    /**
     * 自检入口：执行全部场景并汇总测试结果。
     *
     * @return true 表示所有场景自检通过
     */
    public boolean runTest() {
        log.info("===== CircuitBreakerExample --test =====");

        boolean allPassed = true;
        allPassed &= expectTrue("业务规则(满足条件)", caseBusinessRule());
        allPassed &= expectTrue("动态路由(命中 v2)", caseRouting());
        allPassed &= expectTrue("特性开关(白名单用户)", caseFeatureFlag());
        allPassed &= expectFalse("AND 短路(左 false)", caseAndShortCircuit());
        allPassed &= expectTrue("OR 短路(左 true)", caseOrShortCircuit());
        allPassed &= expectTrue("NOT 取反(未封禁)", caseNot());
        allPassed &= expectTrue("函数调用(LIKE)", caseFunction());
        allPassed &= expectTrue("LIKE 通配符", caseLike());
        allPassed &= expectTrue("BETWEEN 范围", caseBetween());
        allPassed &= expectTrue("IS NULL 检查", caseIsNull());
        allPassed &= expectTrue("IS NOT NULL 检查", caseIsNotNull());
        allPassed &= expectTrue("SQL 表达式", caseSqlExpression());
        allPassed &= expectTrue("Lucene 表达式", caseLuceneExpression());
        allPassed &= expectTrue("Cypher 表达式", caseCypherExpression());
        allPassed &= expectTrue("嵌套表达式", caseNestedExpression());
        allPassed &= expectTrue("BETWEEN 边界值", caseBetweenEdge());
        allPassed &= expectTrue("Boolean 比较", caseBooleanComparison());
        allPassed &= expectFalse("异常保护(judge 抛错时 evaluate 返回 false)", caseExceptionSafety());
        allPassed &= expectTrue("便捷方法 evaluate(expr, ctx)", caseConvenientMethod());
        allPassed &= expectTrue("自定义判断器(委托默认)", caseCustomJudge());

        return allPassed;
    }

    // ==================== 单场景演示 ====================

    private static void runCase(String caseName) {
        if ("rule".equalsIgnoreCase(caseName)) {
            printResult("业务规则", caseBusinessRule());
        } else if ("routing".equalsIgnoreCase(caseName)) {
            printResult("动态路由", caseRouting());
        } else if ("feature".equalsIgnoreCase(caseName)) {
            printResult("特性开关", caseFeatureFlag());
        } else if ("short".equalsIgnoreCase(caseName) || "and".equalsIgnoreCase(caseName)) {
            printResult("AND 短路", caseAndShortCircuit());
        } else if ("or".equalsIgnoreCase(caseName)) {
            printResult("OR 短路", caseOrShortCircuit());
        } else if ("not".equalsIgnoreCase(caseName)) {
            printResult("NOT 取反", caseNot());
        } else if ("fun".equalsIgnoreCase(caseName)) {
            printResult("函数调用", caseFunction());
        } else if ("like".equalsIgnoreCase(caseName)) {
            printResult("LIKE 模糊匹配", caseLike());
        } else if ("between".equalsIgnoreCase(caseName)) {
            printResult("BETWEEN 范围", caseBetween());
        } else if ("isnull".equalsIgnoreCase(caseName) || "is-null".equalsIgnoreCase(caseName)) {
            printResult("IS NULL 检查", caseIsNull());
        } else if ("isnotnull".equalsIgnoreCase(caseName) || "is-not-null".equalsIgnoreCase(caseName)) {
            printResult("IS NOT NULL 检查", caseIsNotNull());
        } else if ("sql".equalsIgnoreCase(caseName)) {
            printResult("SQL 表达式", caseSqlExpression());
        } else if ("lucene".equalsIgnoreCase(caseName)) {
            printResult("Lucene 表达式", caseLuceneExpression());
        } else if ("cypher".equalsIgnoreCase(caseName)) {
            printResult("Cypher 表达式", caseCypherExpression());
        } else if ("nested".equalsIgnoreCase(caseName)) {
            printResult("嵌套表达式", caseNestedExpression());
        } else if ("between-edge".equalsIgnoreCase(caseName)) {
            printResult("BETWEEN 边界值", caseBetweenEdge());
        } else if ("boolean".equalsIgnoreCase(caseName)) {
            printResult("Boolean 比较", caseBooleanComparison());
        } else if ("exception".equalsIgnoreCase(caseName)) {
            printResult("异常保护", caseExceptionSafety());
        } else if ("convenient".equalsIgnoreCase(caseName)) {
            printResult("便捷方法", caseConvenientMethod());
        } else if ("expr".equalsIgnoreCase(caseName)) {
            printResult("通用表达式", caseBusinessRule());
        } else if ("custom".equalsIgnoreCase(caseName)) {
            printResult("自定义判断器", caseCustomJudge());
        } else {
            log.error("[ERROR] 未知场景: {}", caseName);
            printHelp();
        }
    }

    // ==================== 场景实现 ====================

    /**
     * 业务规则：18 岁以上且状态为 active。
     *
     * <p>使用默认判断器 {@link DefaultBreakerJudge}，自动注入。</p>
     *
     * @return true=允许, false=拒绝
     */
    private static boolean caseBusinessRule() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("age", 25);
        ctx.put("status", "active");

        return CircuitBreaker.builder()
                .expression(EXPR_RULE)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * 动态路由：按 region + version 选择目标。
     *
     * @return true=命中 cn-north v2 路由
     */
    private static boolean caseRouting() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("region", "cn-north");
        ctx.put("version", "2.5");

        return CircuitBreaker.builder()
                .expression(EXPR_ROUTING)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * 特性开关：新 UI 仅对白名单开放。
     *
     * @return true=启用新 UI
     */
    private static boolean caseFeatureFlag() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("userId", 1002);

        BreakerJudge audit = (node, c) -> {
            boolean result = new DefaultBreakerJudge().judge(node, c);
            if (node.getType() == BTreeNode.Type.COMPARE) {
                String column = node.getLeft().getOperator();
                String op = node.getOperator();
                log.info("[audit] {} {} {} (actual={}) -> {}",
                        column, op, node.getRight().getValue(), ctx.get(column), result);
            }
            return result;
        };

        return CircuitBreaker.builder()
                .expression(EXPR_FEATURE)
                .context(ctx)
                .judge(audit)
                .build()
                .evaluate();
    }

    /**
     * AND 短路：左 false 直接返回 false（右侧不评估）。
     *
     * @return false
     */
    private static boolean caseAndShortCircuit() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("counter", 0);
        ctx.put("expensiveCheck", true);

        return CircuitBreaker.builder()
                .expression(EXPR_AND)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * OR 短路：左 true 直接返回 true（右侧不评估）。
     *
     * @return true
     */
    private static boolean caseOrShortCircuit() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("isAdmin", true);
        ctx.put("expensiveCheck", false);

        return CircuitBreaker.builder()
                .expression(EXPR_OR)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * NOT 取反：score 不小于等于 50
     *
     * @return true（score=75 满足 not (≤50)）
     */
    private static boolean caseNot() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("score", 75);

        return CircuitBreaker.builder()
                .expression(EXPR_NOT)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * 函数调用：name 不为空。
     *
     * @return true
     */
    private static boolean caseFunction() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("name", "alice");

        return CircuitBreaker.builder()
                .expression(EXPR_FUN)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * LIKE 通配符：姓名以 A 开头
     *
     * @return true
     */
    private static boolean caseLike() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("name", "Alice");

        return CircuitBreaker.builder()
                .expression(EXPR_LIKE)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * BETWEEN 范围：age 在 18-60 之间
     *
     * @return true
     */
    private static boolean caseBetween() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("age", 30);

        return CircuitBreaker.builder()
                .expression(EXPR_BETWEEN)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * IS NULL 检查：deletedAt 为空
     *
     * @return true
     */
    private static boolean caseIsNull() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        // 不放 deletedAt 字段

        return CircuitBreaker.builder()
                .expression(EXPR_IS_NULL)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * IS NOT NULL 检查：email 非空
     *
     * @return true
     */
    private static boolean caseIsNotNull() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("email", "alice@example.com");

        return CircuitBreaker.builder()
                .expression(EXPR_IS_NOT_NULL)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * SQL WHERE 表达式解析。
     *
     * @return true
     */
    private static boolean caseSqlExpression() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("status", "active");
        ctx.put("score", 90);

        return CircuitBreaker.builder()
                .expression(EXPR_SQL)
                .expressionType("sql")
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * Lucene 表达式解析。
     *
     * @return true
     */
    private static boolean caseLuceneExpression() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("status", "active");
        ctx.put("level", "high");

        return CircuitBreaker.builder()
                .expression(EXPR_LUCENE)
                .expressionType("lucene")
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * Cypher 表达式解析。
     *
     * @return true
     */
    private static boolean caseCypherExpression() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("age", 25);
        ctx.put("status", "active");

        return CircuitBreaker.builder()
                .expression(EXPR_CYPHER)
                .expressionType("cypher")
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * 嵌套表达式：复合 OR + AND + NOT
     *
     * <p>展示多层括号嵌套的处理：先算内层 (admin OR super) → 短路 AND → NOT 取反。</p>
     *
     * @return true
     */
    private static boolean caseNestedExpression() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("role", "admin");
        ctx.put("active", true);
        ctx.put("isBlocked", false);

        return CircuitBreaker.builder()
                .expression(EXPR_NESTED)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * BETWEEN 边界值：age 等于下界 18
     *
     * @return true（18 在 [18, 60] 闭区间内）
     */
    private static boolean caseBetweenEdge() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("age", 18);

        return CircuitBreaker.builder()
                .expression(EXPR_BETWEEN_EDGE)
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * Boolean 类型比较：flag == true（实际 Boolean 值）
     *
     * @return true
     */
    private static boolean caseBooleanComparison() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("flag", Boolean.TRUE);

        return CircuitBreaker.builder()
                .expression("flag == true")
                .context(ctx)
                .build()
                .evaluate();
    }

    /**
     * 异常保护：自定义 judge 抛 RuntimeException，evaluate 返回 false 而不冒泡
     *
     * @return false（异常被捕获，断路）
     */
    private static boolean caseExceptionSafety() {
        BreakerJudge throwingJudge = (node, ctx) -> {
            throw new RuntimeException("模拟 judge 实现 bug");
        };
        return CircuitBreaker.builder()
                .expression(EXPR_FOR_EXCEPTION)
                .context(new LinkedHashMap<>())
                .judge(throwingJudge)
                .build()
                .evaluate();
    }

    /**
     * 便捷方法：CircuitBreaker.evaluate(expr, ctx)
     *
     * @return true
     */
    private static boolean caseConvenientMethod() {
        return CircuitBreaker.evaluate("status == 'active'", Map.of("status", "active"));
    }

    /**
     * 自定义判断器：委托给默认判断器并打印审计日志。
     *
     * <p>展示如何替换默认判断器（{@link BreakerJudge}），可叠加审计/埋点逻辑。</p>
     *
     * @return true
     */
    private static boolean caseCustomJudge() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("age", 25);
        ctx.put("status", "active");

        BreakerJudge auditJudge = (node, c) -> {
            boolean result = new DefaultBreakerJudge().judge(node, c);
            if (node.getType() == BTreeNode.Type.COMPARE) {
                String column = node.getLeft().getOperator();
                String op = node.getOperator();
                log.info("[audit] judge {} {} {} -> {}", column, op,
                        node.getRight().getValue(), result);
            }
            return result;
        };

        return CircuitBreaker.builder()
                .expression(EXPR_RULE)
                .context(ctx)
                .judge(auditJudge)
                .build()
                .evaluate();
    }

    // ==================== 辅助方法 ====================

    private static void printResult(String name, boolean passed) {
        log.info("{}{} {} -> {}", (passed ? "[PASS]" : "[FAIL]"), name, (passed ? "通过" : "断路"));
    }

    private static boolean expectTrue(String name, boolean actual) {
        boolean ok = actual;
        log.info("{}{}", (ok ? "[PASS]" : "[FAIL]"), name);
        return ok;
    }

    private static boolean expectFalse(String name, boolean actual) {
        boolean ok = !actual;
        log.info("{}{}", (ok ? "[PASS]" : "[FAIL]"), name);
        return ok;
    }

    // ==================== 参数解析 ====================

    private static Args parseArgs(String[] args) {
        Args result = new Args();
        int index = 0;
        while (index < args.length) {
            switch (args[index]) {
                case "--type", "-t" -> {
                    if (index + 1 < args.length) {
                        result = result.withType(args[++index]);
                    }
                }
                case "--case", "-c" -> {
                    if (index + 1 < args.length) {
                        result = result.withCaseName(args[++index]);
                    }
                }
                case "--test" -> result = result.withTest(true);
                case "--help", "-h" -> result = result.withHelp(true);
                default -> log.warn("[WARN] 未知参数: {}", args[index]);
            }
            index++;
        }
        return result;
    }

    private static void printHelp() {
        log.info("CircuitBreaker 综合示例 — 基于 CircuitBreaker + ExpressionParser SPI");
        log.info("");
        log.info("用法: java CircuitBreakerExample [选项]");
        log.info("");
        log.info("选项:");
        log.info("  --type,    -t <key>    表达式类型（expr/sql/lucene/cypher）");
        log.info("  --case,    -c <name>   场景名（rule/routing/feature/and/or/not/fun/like/between/isnull/isnotnull/nested/between-edge/boolean/exception/convenient/sql/lucene/cypher/custom）");
        log.info("  --test                 执行全部场景自检并退出");
        log.info("  --help,  -h            显示此帮助");
    }

    // ==================== 参数容器 ====================

    /**
     * 命令行参数容器。
     *
     * @param type     表达式类型
     * @param caseName 场景名
     * @param test     是否自检模式
     * @param help     是否打印帮助
     * @author CH
     * @since 4.0.0.43
     */
    private record Args(
            String type,
            String caseName,
            boolean test,
            boolean help
    ) {
        /**
         * 带默认值的空参构造。
         */
        Args() {
            this("expr", "rule", false, false);
        }

        /**
         * 替换 type 字段，返回新实例。
         */
        public Args withType(String type) {
            return new Args(type, caseName, test, help);
        }

        /**
         * 替换 caseName 字段，返回新实例。
         */
        public Args withCaseName(String caseName) {
            return new Args(type, caseName, test, help);
        }

        /**
         * 替换 test 字段，返回新实例。
         */
        public Args withTest(boolean test) {
            return new Args(type, caseName, test, help);
        }

        /**
         * 替换 help 字段，返回新实例。
         */
        public Args withHelp(boolean help) {
            return new Args(type, caseName, test, help);
        }
    }
}
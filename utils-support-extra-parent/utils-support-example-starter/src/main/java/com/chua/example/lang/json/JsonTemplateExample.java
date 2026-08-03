package com.chua.example.lang.json;

import com.chua.common.support.lang.json.JsonTemplateExtractor;
import com.chua.common.support.lang.template.TemplateExtractResult;
import com.chua.common.support.lang.template.TemplateVar;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON 模板匹配提取综合示例（可独立运行的 main 测试，非 JUnit）。
 *
 * <p>演示 {@link JsonTemplateExtractor}（与 {@code JsonPath} 同属 JSON 能力家族）的两种使用方式：</p>
 * <ul>
 *     <li>通过 SPI 入口 {@link JsonTemplateExtractor#getInstance()} 解耦具体实现；</li>
 *     <li>通过 {@link JsonTemplateExtractor#create()} 流式构建器做更细粒度控制。</li>
 * </ul>
 *
 * <p>本示例针对“输入顺序/格式不确定”的场景：输入字段顺序被打乱、含注释与单引号时仍能正确抽取，
 * 并覆盖缺失感知、内嵌占位符、宽松格式、POJO 转换、完整性校验、有序变量列表、部分提取、
 * 数组单元素全匹配策略与类型容忍等能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class JsonTemplateExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 模板声明待抽取槽位 {name} / {age}；输入顺序故意与模板相反，用于验证顺序无关
     */
    private static final String DEFAULT_TEMPLATE = "{\"xx\":\"{name}\",\"yy\":\"{age}\"}";

    /**
     * 默认测试输入
     */
    private static final String DEFAULT_INPUT = "{\"yy\":25,\"xx\":\"Alice\"}";

    /**
     * 程序入口：依次演示各能力并做断言校验，任意断言失败即以 AssertionError 暴露问题。
     *
     * @param args 命令行参数（本示例未使用）
     */
    public static void main(String[] args) {
        log.info("========== JSON 模板匹配提取示例 ==========\n");

        // 模板声明待抽取槽位 {name} / {age}；输入顺序故意与模板相反，用于验证顺序无关
        String template = DEFAULT_TEMPLATE;
        String input = DEFAULT_INPUT;

        demoInterfaceEntry(template, input);
        demoBuilderWithMissing();
        demoEmbeddedPlaceholder();
        demoLenientFormat();
        demoExtractAsPojo(template, input);
        demoMatches(template, input);
        demoExtractVars();
        demoPartialExtraction();
        demoArrayStrategy();
        demoTolerantType();

        log.info("\n========== 全部示例通过 ==========");
    }

    /**
     * 断言校验：条件不成立时抛出 AssertionError 终止示例，成立时打印通过信息。
     *
     * @param condition 待校验的布尔条件
     * @param message   校验项描述（用于通过 / 失败时的输出）
     */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(String.format("示例断言失败: %s", message));
        }
        log.info("  [OK] {}", message);
    }

    /**
     * 演示通过 SPI 接口入口完成一次性提取（推荐用法，调用方不依赖具体实现类）。
     *
     * @param template 模板 JSON
     * @param input    输入 JSON
     */
    private static void demoInterfaceEntry(String template, String input) {
        log.info("\n【1】SPI 接口入口 JsonTemplateExtractor.getInstance()");
        JsonTemplateExtractor extractor = JsonTemplateExtractor.getInstance();
        Map<String, Object> result = extractor.extract(template, input);

        require("Alice".equals(result.get("name")),
                String.format("顺序无关的整值抽取: name = %s", result.get("name")));
        require(Integer.valueOf(25).equals(result.get("age")),
                String.format("顺序无关的整值抽取: age = %s", result.get("age")));
    }

    /**
     * 演示流式构建器 + 缺失变量感知：模板声明但输入缺失时给出缺失列表，已抽到的变量仍生效。
     */
    private static void demoBuilderWithMissing() {
        log.info("\n【2】流式构建器 + 缺失变量感知");
        TemplateExtractResult result = JsonTemplateExtractor.create()
                .template("{\"a\":\"{name}\",\"b\":\"{missing}\"}")
                .extract("{\"a\":\"Bob\"}");

        require(!result.isSuccess(), "存在缺失变量时 isSuccess() = false");
        require(result.missing().contains("missing"),
                String.format("缺失变量列表包含 missing: %s", result.missing()));
        require("Bob".equals(result.get("name")),
                String.format("已抽取到的变量仍生效: name = %s", result.get("name")));
    }

    /**
     * 演示内嵌占位符：模板值在普通文本中间嵌入 {@code {变量名}}，仅截取变量部分。
     */
    private static void demoEmbeddedPlaceholder() {
        log.info("\n【3】内嵌占位符");
        String template = "{\"greeting\":\"hello {name}!\"}";
        String input = "{\"greeting\":\"hello 世界!\"}";

        Map<String, Object> result = JsonTemplateExtractor.getInstance().extract(template, input);
        require("世界".equals(result.get("name")),
                String.format("内嵌占位符截取: name = %s", result.get("name")));
    }

    /**
     * 演示宽松格式：输入含 JSON5 注释与单引号键名，仍可正确解析抽取。
     */
    private static void demoLenientFormat() {
        log.info("\n【4】宽松格式（注释 + 单引号）");
        String template = "{\"greeting\":\"hi {name}\"}";
        String input = "{ /* 这是一段注释 */ 'greeting' : 'hi 张三' }";

        Map<String, Object> result = JsonTemplateExtractor.getInstance().extract(template, input);
        require("张三".equals(result.get("name")),
                String.format("宽容解析后抽取: name = %s", result.get("name")));
    }

    /**
     * 演示 extractAs 直接转换为目标强类型 POJO（保留原始类型）。
     *
     * @param template 模板 JSON
     * @param input    输入 JSON
     */
    private static void demoExtractAsPojo(String template, String input) {
        log.info("\n【5】extractAs 直转 POJO");
        User user = JsonTemplateExtractor.getInstance().extractAs(template, input, User.class);
        require("Alice".equals(user.name()),
                String.format("POJO.name = %s", user.name()));
        require(user.age() == 25,
                String.format("POJO.age = %s", user.age()));
    }

    /**
     * 演示 matches：判断输入是否完整覆盖模板所有槽位。
     *
     * @param template 模板 JSON
     * @param input    输入 JSON
     */
    private static void demoMatches(String template, String input) {
        log.info("\n【6】matches 完整性校验");
        JsonTemplateExtractor extractor = JsonTemplateExtractor.getInstance();
        require(extractor.matches(template, input), "完整输入 matches() = true");
        require(!extractor.matches("{\"a\":\"{name}\",\"b\":\"{lost}\"}", "{\"a\":\"X\"}"),
                "缺失槽位时 matches() = false");
    }

    /**
     * 目标 POJO（record 形式），用于演示 extractAs 直接转换为强类型对象。
     *
     * @param name 抽取得到的用户名
     * @param age  抽取得到的用户年龄
     * @author CH
     * @since 4.0.0.42
     */
    public static record User(
        String name,
        int age
    ) {}

    /**
     * 演示 extractVars：返回有序的 {@link TemplateVar} 列表，每项携带变量名、值与在输入中的 JSON 路径，
     * 适合需要顺序或溯源的消费场景。
     */
    private static void demoExtractVars() {
        log.info("\n【7】extractVars 有序变量列表（含路径）");
        String template = "{\"user\":{\"name\":\"{name}\",\"age\":25},\"tag\":\"v_{ver}\"}";
        String input = "{\"user\":{\"name\":\"Carol\",\"age\":25},\"tag\":\"v_2.0\"}";

        List<TemplateVar> vars = JsonTemplateExtractor.getInstance().extractVars(template, input);
        require(vars.size() == 2,
                String.format("成功提取的变量数量 = %s", vars.size()));
        require("name".equals(vars.get(0).name()) && "Carol".equals(vars.get(0).value())
                && "user.name".equals(vars.get(0).path()), "首个变量 name=Carol 路径=user.name");
        require("ver".equals(vars.get(1).name()) && "2.0".equals(vars.get(1).value())
                && "tag".equals(vars.get(1).path()), "第二个变量 ver=2.0 路径=tag");

        // 变量名支持字母、数字、下划线（含数字开头）
        String tpl2 = "{\"id\":\"{v1_2}\"}";
        String in2 = "{\"id\":\"X9\"}";
        List<TemplateVar> vars2 = JsonTemplateExtractor.getInstance().extractVars(tpl2, in2);
        require(vars2.size() == 1 && "v1_2".equals(vars2.get(0).name()),
                String.format("变量名支持字母数字下划线: %s", vars2.get(0).name()));
    }

    /**
     * 演示部分提取：模板仅声明占位符槽位，字面量文本仅作定位锚点。
     * 示例：模板 {"msg":"用户{name}登录"} 从输入 {"msg":"用户张三登录"} 中抽取 name。
     */
    private static void demoPartialExtraction() {
        log.info("\n【8】部分提取（字面量仅作锚点）");
        String template = "{\"msg\":\"用户{name}登录\"}";
        String input = "{\"msg\":\"用户张三登录\"}";

        Map<String, Object> result = JsonTemplateExtractor.getInstance().extract(template, input);
        require("张三".equals(result.get("name")),
                String.format("仅抽取占位符部分: name = %s", result.get("name")));
        require(result.size() == 1,
                String.format("字面量锚点不参与输出，仅 1 个变量: %s", result.size()));
    }

    /**
     * 演示数组“单元素模板匹配全部”策略：模板数组仅一个元素，套用到输入数组每个元素。
     */
    private static void demoArrayStrategy() {
        log.info("\n【9】数组：单元素模板匹配全部");
        String template = "{\"list\":[{\"name\":\"{name}\",\"score\":\"{score}\"}]}";
        String input = "{\"list\":[{\"name\":\"A\",\"score\":90},{\"name\":\"B\",\"score\":85}]}";

        List<TemplateVar> vars = JsonTemplateExtractor.create()
                .arrayStrategy(JsonTemplateExtractor.ArrayMatchStrategy.SINGLE_MATCHES_ALL)
                .template(template)
                .extractVars(input);
        require(vars.size() == 4,
                String.format("两个元素各提取 name/score，共 4 个变量: %s", vars.size()));
        require("list[0].name".equals(vars.get(0).path()) && "A".equals(vars.get(0).value()),
                "首个元素 name=A 路径=list[0].name");
        require("list[1].name".equals(vars.get(2).path()) && "B".equals(vars.get(2).value()),
                "第二个元素 name=B 路径=list[1].name");

        // 默认策略（按索引逐元素）下，单元素模板只能匹配输入数组的第 0 个元素
        List<TemplateVar> varsDefault = JsonTemplateExtractor.getInstance().extractVars(template, input);
        require(varsDefault.size() == 2,
                String.format("默认策略仅匹配输入第 0 个元素: %s", varsDefault.size()));
    }

    /**
     * 演示容忍类型差异：内嵌占位符对应的输入为非文本（数字）时，开启 tolerantType 仍可抽取。
     */
    private static void demoTolerantType() {
        log.info("\n【10】容忍类型差异");
        String template = "{\"code\":\"x{name}y\"}";
        String input = "{\"code\":9988}";

        // 不开启：非文本输入 + 内嵌占位符 => 判为缺失
        List<TemplateVar> none = JsonTemplateExtractor.getInstance().extractVars(template, input);
        require(none.isEmpty(), "默认不容忍：变量缺失，提取为空");

        // 开启：非文本输入转文本兜底取值
        List<TemplateVar> vars = JsonTemplateExtractor.create()
                .tolerantType(true)
                .template(template)
                .extractVars(input);
        require(vars.size() == 1 && "9988".equals(vars.get(0).value()),
                String.format("开启容忍后，非文本输入转为文本截取: %s", vars.get(0).value()));
    }
}

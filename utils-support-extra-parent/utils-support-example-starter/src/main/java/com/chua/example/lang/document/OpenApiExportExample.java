package com.chua.example.lang.document;

import lombok.extern.slf4j.Slf4j;
import com.chua.common.support.lang.document.OpenApiDocumentData;
import com.chua.common.support.lang.document.OpenApiDocumentProvider;
import com.chua.common.support.lang.document.OpenApiSection;
import com.chua.example.spi.Example;
import com.chua.starter.swagger.support.export.OpenApiConverter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenApiDocumentProvider 烟雾测试 Example。
 *
 * <p>构造样例 OpenAPI → OpenApiConverter → OpenApiHtmlProvider.export() 三步走,
 * 输出到 {@code ./target/openapi-export-test.html}, 并对生成的 HTML 做结构断言。</p>
 *
 * <h2>使用方式</h2>
 * <pre>
 *   java RunnerExample --example=openapi-export-test
 * </pre>
 *
 * <h2>验证项</h2>
 * <ul>
 *     <li>SPI 加载 ({@link OpenApiDocumentProvider#create})</li>
 *     <li>模型转换 (3 endpoints / 2 tags / 1 section)</li>
 *     <li>HTML 渲染 (12 项关键结构断言)</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OpenApiExportExample implements Example {

    /** 私有构造，防止实例化 */
    private OpenApiExportExample() { }

    @Override
    /** Name */
    public String name() {
        return "openapi-export-test";
    }

    @Override
    /** Module */
    public String module() {
        return "lang-document";
    }

    @Override
    /** Description */
    public String description() {
        return "OpenApiDocumentProvider SPI 烟雾测试 (Sample OpenAPI → 泛微E10风格HTML)";
    }

    @Override
    /** 运行 */
    public boolean run(Map<String, String> args) {
        try {
            log.info("=== OpenApiDocumentProvider 烟雾测试 ===");

            // 1) 构造样例 OpenAPI
            OpenAPI openAPI = buildSampleOpenApi();

            // 2) 转换
            OpenApiDocumentData data = OpenApiConverter.fromOpenApi(openAPI);
            data.setTitle("示例 OpenAPI 接口文档");
            data.setVersion("1.0.0-TEST");
            data.getSections().add(buildIntroSection());

            log.info("  转换完成: title=" + data.getTitle() + " tags=" + data.getTags().size()
                    + " endpoints=" + data.getEndpoints().size() + " sections=" + data.getSections().size());

            // 3) 通过 SPI 创建 provider
            OpenApiDocumentProvider provider = OpenApiDocumentProvider.create("html");
            if (provider == null) {
                System.err.println("  ✗ 未找到 OpenApiDocumentProvider (type=html) SPI");
                return false;
            }
            log.info("  SPI Provider: " + provider.getClass().getName() + " extensions="
                    + java.util.Arrays.toString(provider.getExtensions()));

            // 4) 导出
            File out = new File("./target/openapi-export-test.html");
            if (out.getParentFile() != null) {
                out.getParentFile().mkdirs();
            }
            provider.export(data, out);
            log.info("  已导出: " + out.getAbsolutePath() + " size=" + out.length() + " bytes");

            // 5) 结构断言
            String html = new String(java.nio.file.Files.readAllBytes(out.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertContains(html, "<!DOCTYPE html>", "doctype");
            assertContains(html, "示例 OpenAPI 接口文档", "title 文字");
            assertContains(html, "id=\"apiTree\"", "sidebar tree 容器");
            assertContains(html, "class=\"folder-link\">用户管理", "tag 折叠分组");
            assertContains(html, "/api/v1/users", "endpoint path");
            assertContains(html, ">GET<", "method GET");
            assertContains(html, ">POST<", "method POST");
            assertContains(html, "请求参数", "请求参数表");
            assertContains(html, "响应参数", "响应参数表");
            assertContains(html, "class=\"api-table\"", "api-table 样式");
            assertContains(html, "class=\"code-block\"", "code-block 示例");
            assertContains(html, "id=\"navSearch\"", "搜索框");

            // 6) 数据模型断言
            if (data.getEndpoints().size() < 2) {
                System.err.println("  ✗ 应至少 2 个 endpoint, 实际: " + data.getEndpoints().size());
                return false;
            }
            if (data.getTags().isEmpty()) {
                System.err.println("  ✗ 应至少 1 个 tag");
                return false;
            }

            log.info("  全部断言通过 ✓");
            return true;
        } catch (Exception e) {
            System.err.println("  ✗ 测试异常: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    /**
     * 构造样例 OpenAPI: 2 个 tag / 3 个 endpoint (用户管理 GET+POST, 订单管理 GET)。
     *
     * @return OpenAPI 实例
     */
    private static OpenAPI buildSampleOpenApi() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new Info()
                .title("示例 OpenAPI 接口文档")
                .version("1.0.0-TEST")
                .description("自动验证 OpenApiDocumentProvider"));

        // tags
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("用户管理").description("用户 CRUD"));
        tags.add(new Tag().name("订单管理").description("订单 CRUD"));
        openAPI.setTags(tags);

        // /api/v1/users
        Operation getUsers = new Operation()
                .summary("查询用户列表")
                .description("分页查询用户列表, 支持按名称/邮箱模糊匹配")
                .addTagsItem("用户管理");
        getUsers.addParametersItem(new Parameter()
                .name("page").in("query").required(false)
                .description("页码, 从 1 开始")
                .schema(new Schema<>().type("integer").example(1)));
        getUsers.addParametersItem(new Parameter()
                .name("size").in("query").required(false)
                .description("每页条数")
                .schema(new Schema<>().type("integer").example(20)));
        getUsers.addParametersItem(new Parameter()
                .name("keyword").in("query").required(false)
                .description("关键字 (姓名 / 邮箱)")
                .schema(new Schema<>().type("string").example("张三")));
        getUsers.setResponses(buildResponses());

        Operation createUser = new Operation()
                .summary("创建用户")
                .description("传入 user 对象, 创建一个新用户")
                .addTagsItem("用户管理");
        Map<String, Schema> userProps = new LinkedHashMap<>();
        userProps.put("id", new Schema<>().type("integer"));
        userProps.put("name", new Schema<>().type("string"));
        userProps.put("email", new Schema<>().type("string"));
        userProps.put("age", new Schema<>().type("integer"));
        Schema userSchema = new Schema<>().type("object").properties(userProps);
        createUser.requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                .required(true)
                .description("用户对象")
                .content(new Content().addMediaType("application/json", new MediaType().schema(userSchema))));
        createUser.setResponses(buildResponses());

        openAPI.path("/api/v1/users", new PathItem()
                .get(getUsers)
                .post(createUser));

        // /api/v1/orders
        Operation getOrders = new Operation()
                .summary("查询订单列表")
                .addTagsItem("订单管理");
        getOrders.addParametersItem(new Parameter()
                .name("userId").in("query").required(true)
                .description("用户 ID")
                .schema(new Schema<>().type("integer").example(1001)));
        getOrders.setResponses(buildResponses());

        openAPI.path("/api/v1/orders", new PathItem().get(getOrders));

        return openAPI;
    }

    /**
     * 构造样例 ApiResponses (200 / 404)。
     *
     * @return ApiResponses 实例
     */
    private static ApiResponses buildResponses() {
        Map<String, Schema> userProps = new LinkedHashMap<>();
        userProps.put("id", new Schema<>().type("integer"));
        userProps.put("name", new Schema<>().type("string"));
        userProps.put("email", new Schema<>().type("string"));
        Schema userSchema = new Schema<>().type("object").properties(userProps);

        return new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .description("成功")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(userSchema))))
                .addApiResponse("404", new ApiResponse().description("资源不存在"));
    }

    /**
     * 构造快速入门 section。
     *
     * @return OpenApiSection 实例
     */
    private static OpenApiSection buildIntroSection() {
        OpenApiSection sec = new OpenApiSection();
        sec.setTitle("快速入门");
        sec.setContent("""
                <p>本文档由 <code>OpenApiDocumentProvider</code> 渲染, 模仿泛微 E10 OpenAPI 接口文档样式。</p>
                <h3>使用方式</h3>
                <ul>
                    <li><strong>本地下载</strong>: 访问 <code>GET /chua/swagger/export</code></li>
                    <li><strong>在线预览</strong>: 访问 <code>GET /chua/swagger/export/preview</code></li>
                </ul>
                """);
        return sec;
    }

    /**
     * HTML 内容断言。
     *
     * @param html     HTML 字符串
     * @param expected 期望包含的子串
     * @param label    断言标签 (用于日志)
     * @throws AssertionError 断言失败
     */
    private static void assertContains(String html, String expected, String label) {
        if (!html.contains(expected)) {
            throw new AssertionError("断言失败 [" + label + "]: HTML 中应包含 " + expected
                    + "\n--- 实际开头 500 字符 ---\n"
                    + html.substring(0, Math.min(500, html.length())));
        }
        log.info("  ✓ " + label);
    }

    public static void main(String[] args) {
        new OpenApiExportExample().run(java.util.Arrays.stream(args).collect(java.util.stream.Collectors.toMap(a -> a.split("=")[0], a -> a.split("=")[1])));
    }

}

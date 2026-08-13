package com.chua.example.lang.document;

import com.chua.common.support.lang.document.OpenApiDocumentData;
import com.chua.common.support.lang.document.OpenApiDocumentProvider;
import com.chua.common.support.lang.document.OpenApiSection;
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

public class OpenApiExportSmokeTest {

    public static void main(String[] args) {
        try {
            System.out.println("=== OpenApiDocumentProvider Smoke Test ===");

            OpenAPI openAPI = buildSampleOpenApi();
            OpenApiDocumentData data = OpenApiConverter.fromOpenApi(openAPI);
            data.setTitle("示例 OpenAPI 接口文档");
            data.setVersion("1.0.0-TEST");
            data.getSections().add(buildIntroSection());

            System.out.println("  转换完成: title=" + data.getTitle() + " tags=" + data.getTags().size()
                    + " endpoints=" + data.getEndpoints().size() + " sections=" + data.getSections().size());

            // 直接使用 SPI 加载 (绕过日志初始化冲突)
            OpenApiDocumentProvider provider = null;
            try {
                provider = OpenApiDocumentProvider.create("html");
            } catch (Throwable t) {
                System.err.println("  SPI 加载异常: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                try {
                    Class<?> clazz = Class.forName("com.chua.common.support.lang.document.OpenApiHtmlProvider");
                    provider = (OpenApiDocumentProvider) clazz.getDeclaredConstructor().newInstance();
                    System.out.println("  直接实例化 SPI Provider 成功: " + clazz.getName());
                } catch (Exception e2) {
                    System.err.println("  ✗ 直接实例化也失败: " + e2.getMessage());
                    e2.printStackTrace(System.err);
                    System.exit(1);
                    return;
                }
            }
            System.out.println("  Provider: " + provider.getClass().getName());

            File out = new File("./target/openapi-export-smoke-test.html");
            out.getParentFile().mkdirs();
            provider.export(data, out);
            System.out.println("  已导出: " + out.getAbsolutePath() + " size=" + out.length() + " bytes");

            String html = new String(java.nio.file.Files.readAllBytes(out.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);

            int passed = 0;
            int total = 12;
            if (assertContains(html, "<!DOCTYPE html>", "doctype")) passed++;
            if (assertContains(html, "示例 OpenAPI 接口文档", "title 文字")) passed++;
            if (assertContains(html, "id=\"apiTree\"", "sidebar tree 容器")) passed++;
            if (assertContains(html, "class=\"folder-link\">用户管理", "tag 折叠分组")) passed++;
            if (assertContains(html, "/api/v1/users", "endpoint path")) passed++;
            if (assertContains(html, ">GET<", "method GET")) passed++;
            if (assertContains(html, ">POST<", "method POST")) passed++;
            if (assertContains(html, "请求参数", "请求参数表")) passed++;
            if (assertContains(html, "响应参数", "响应参数表")) passed++;
            if (assertContains(html, "class=\"api-table\"", "api-table 样式")) passed++;
            if (assertContains(html, "class=\"code-block\"", "code-block 示例")) passed++;
            if (assertContains(html, "id=\"navSearch\"", "搜索框")) passed++;

            System.out.println();
            System.out.println("  结果: " + passed + "/" + total + " 通过");
            System.exit(passed == total ? 0 : 1);
        } catch (Exception e) {
            System.err.println("  ✗ 测试异常: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static OpenAPI buildSampleOpenApi() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new Info().title("示例 OpenAPI 接口文档").version("1.0.0-TEST")
                .description("自动验证 OpenApiDocumentProvider"));
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("用户管理").description("用户 CRUD"));
        tags.add(new Tag().name("订单管理").description("订单 CRUD"));
        openAPI.setTags(tags);

        Operation getUsers = new Operation().summary("查询用户列表").description("分页查询")
                .addTagsItem("用户管理");
        getUsers.addParametersItem(new Parameter().name("page").in("query").required(false)
                .description("页码").schema(new Schema<>().type("integer").example(1)));
        getUsers.setResponses(buildResponses());

        Operation createUser = new Operation().summary("创建用户").description("创建用户")
                .addTagsItem("用户管理");
        Map<String, Schema> userProps = new LinkedHashMap<>();
        userProps.put("id", new Schema<>().type("integer"));
        userProps.put("name", new Schema<>().type("string"));
        Schema userSchema = new Schema<>().type("object").properties(userProps);
        createUser.requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                .required(true).content(new Content().addMediaType("application/json",
                        new MediaType().schema(userSchema))));
        createUser.setResponses(buildResponses());

        openAPI.path("/api/v1/users", new PathItem().get(getUsers).post(createUser));

        Operation getOrders = new Operation().summary("查询订单列表").addTagsItem("订单管理");
        getOrders.addParametersItem(new Parameter().name("userId").in("query").required(true)
                .description("用户 ID").schema(new Schema<>().type("integer").example(1001)));
        getOrders.setResponses(buildResponses());
        openAPI.path("/api/v1/orders", new PathItem().get(getOrders));

        return openAPI;
    }

    private static ApiResponses buildResponses() {
        Map<String, Schema> userProps = new LinkedHashMap<>();
        userProps.put("id", new Schema<>().type("integer"));
        userProps.put("name", new Schema<>().type("string"));
        Schema userSchema = new Schema<>().type("object").properties(userProps);
        return new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("成功")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(userSchema))))
                .addApiResponse("404", new ApiResponse().description("资源不存在"));
    }

    private static OpenApiSection buildIntroSection() {
        OpenApiSection sec = new OpenApiSection();
        sec.setTitle("快速入门");
        sec.setContent("<p>OpenApiDocumentProvider 渲染</p>");
        return sec;
    }

    private static boolean assertContains(String html, String expected, String label) {
        if (!html.contains(expected)) {
            System.err.println("  ✗ 断言失败 [" + label + "]: 未找到 " + expected);
            return false;
        }
        System.out.println("  ✓ " + label);
        return true;
    }
}

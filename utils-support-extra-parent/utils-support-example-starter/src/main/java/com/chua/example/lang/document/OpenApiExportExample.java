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

import static java.util.Arrays.stream;
import static java.util.Arrays.toString;
import java.util.stream.Collectors;

/**
 * OpenApiDocumentProvider 鐑熼浘娴嬭瘯 Example銆? *
 * <p>鏋勯€犳牱渚?OpenAPI 鈫?OpenApiConverter 鈫?OpenApiHtmlProvider.export() 涓夋璧?
 * 杈撳嚭鍒?{@code ./target/openapi-export-test.html}, 骞跺鐢熸垚鐨?HTML 鍋氱粨鏋勬柇瑷€銆?/p>
 *
 * <h2>浣跨敤鏂瑰紡</h2>
 * <pre>
 *   java RunnerExample --example=openapi-export-test
 * </pre>
 *
 * <h2>楠岃瘉椤?/h2>
 * <ul>
 *     <li>SPI 鍔犺浇 ({@link OpenApiDocumentProvider#create})</li>
 *     <li>妯″瀷杞崲 (3 endpoints / 2 tags / 1 section)</li>
 *     <li>HTML 娓叉煋 (12 椤瑰叧閿粨鏋勬柇瑷€)</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OpenApiExportExample implements Example {

    /** 绉佹湁鏋勯€狅紝闃叉瀹炰緥鍖?*/
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
        return "OpenApiDocumentProvider SPI 鐑熼浘娴嬭瘯 (Sample OpenAPI 鈫?娉涘井E10椋庢牸HTML)";
    }

    @Override
    /** 杩愯 */
    public boolean run(Map<String, String> args) {
        try {
            log.info("=== OpenApiDocumentProvider 鐑熼浘娴嬭瘯 ===");

            // 1) 鏋勯€犳牱渚?OpenAPI
            OpenAPI openAPI = buildSampleOpenApi();

            // 2) 杞崲
            OpenApiDocumentData data = OpenApiConverter.fromOpenApi(openAPI);
            data.setTitle("绀轰緥 OpenAPI 鎺ュ彛鏂囨。");
            data.setVersion("1.0.0-TEST");
            data.getSections().add(buildIntroSection());

            log.info("  杞崲瀹屾垚: title=" + data.getTitle() + " tags=" + data.getTags().size()
                    + " endpoints=" + data.getEndpoints().size() + " sections=" + data.getSections().size());

            // 3) 閫氳繃 SPI 鍒涘缓 provider
            OpenApiDocumentProvider provider = OpenApiDocumentProvider.create("html");
            if (provider == null) {
                System.err.println("  鉁?鏈壘鍒?OpenApiDocumentProvider (type=html) SPI");
                return false;
            }
            log.info("  SPI Provider: " + provider.getClass().getName() + " extensions="
                    + toString(provider.getExtensions()));

            // 4) 瀵煎嚭
            File out = new File("./target/openapi-export-test.html");
            if (out.getParentFile() != null) {
                out.getParentFile().mkdirs();
            }
            provider.export(data, out);
            log.info("  宸插鍑? " + out.getAbsolutePath() + " size=" + out.length() + " bytes");

            // 5) 缁撴瀯鏂█
            String html = new String(java.nio.file.Files.readAllBytes(out.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertContains(html, "<!DOCTYPE html>", "doctype");
            assertContains(html, "绀轰緥 OpenAPI 鎺ュ彛鏂囨。", "title 鏂囧瓧");
            assertContains(html, "id=\"apiTree\"", "sidebar tree 瀹瑰櫒");
            assertContains(html, "class=\"folder-link\">鐢ㄦ埛绠＄悊", "tag 鎶樺彔鍒嗙粍");
            assertContains(html, "/api/v1/users", "endpoint path");
            assertContains(html, ">GET<", "method GET");
            assertContains(html, ">POST<", "method POST");
            assertContains(html, "璇锋眰鍙傛暟", "璇锋眰鍙傛暟琛?);
            assertContains(html, "鍝嶅簲鍙傛暟", "鍝嶅簲鍙傛暟琛?);
            assertContains(html, "class=\"api-table\"", "api-table 鏍峰紡");
            assertContains(html, "class=\"code-block\"", "code-block 绀轰緥");
            assertContains(html, "id=\"navSearch\"", "鎼滅储妗?);

            // 6) 鏁版嵁妯″瀷鏂█
            if (data.getEndpoints().size() < 2) {
                System.err.println("  鉁?搴旇嚦灏?2 涓?endpoint, 瀹為檯: " + data.getEndpoints().size());
                return false;
            }
            if (data.getTags().isEmpty()) {
                System.err.println("  鉁?搴旇嚦灏?1 涓?tag");
                return false;
            }

            log.info("  鍏ㄩ儴鏂█閫氳繃 鉁?);
            return true;
        } catch (Exception e) {
            System.err.println("  鉁?娴嬭瘯寮傚父: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    /**
     * 鏋勯€犳牱渚?OpenAPI: 2 涓?tag / 3 涓?endpoint (鐢ㄦ埛绠＄悊 GET+POST, 璁㈠崟绠＄悊 GET)銆?     *
     * @return OpenAPI 瀹炰緥
     */
    private static OpenAPI buildSampleOpenApi() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new Info()
                .title("绀轰緥 OpenAPI 鎺ュ彛鏂囨。")
                .version("1.0.0-TEST")
                .description("鑷姩楠岃瘉 OpenApiDocumentProvider"));

        // tags
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("鐢ㄦ埛绠＄悊").description("鐢ㄦ埛 CRUD"));
        tags.add(new Tag().name("璁㈠崟绠＄悊").description("璁㈠崟 CRUD"));
        openAPI.setTags(tags);

        // /api/v1/users
        Operation getUsers = new Operation()
                .summary("鏌ヨ鐢ㄦ埛鍒楄〃")
                .description("鍒嗛〉鏌ヨ鐢ㄦ埛鍒楄〃, 鏀寔鎸夊悕绉?閭妯＄硦鍖归厤")
                .addTagsItem("鐢ㄦ埛绠＄悊");
        getUsers.addParametersItem(new Parameter()
                .name("page").in("query").required(false)
                .description("椤电爜, 浠?1 寮€濮?)
                .schema(new Schema<>().type("integer").example(1)));
        getUsers.addParametersItem(new Parameter()
                .name("size").in("query").required(false)
                .description("姣忛〉鏉℃暟")
                .schema(new Schema<>().type("integer").example(20)));
        getUsers.addParametersItem(new Parameter()
                .name("keyword").in("query").required(false)
                .description("鍏抽敭瀛?(濮撳悕 / 閭)")
                .schema(new Schema<>().type("string").example("寮犱笁")));
        getUsers.setResponses(buildResponses());

        Operation createUser = new Operation()
                .summary("鍒涘缓鐢ㄦ埛")
                .description("浼犲叆 user 瀵硅薄, 鍒涘缓涓€涓柊鐢ㄦ埛")
                .addTagsItem("鐢ㄦ埛绠＄悊");
        Map<String, Schema> userProps = new LinkedHashMap<>();
        userProps.put("id", new Schema<>().type("integer"));
        userProps.put("name", new Schema<>().type("string"));
        userProps.put("email", new Schema<>().type("string"));
        userProps.put("age", new Schema<>().type("integer"));
        Schema userSchema = new Schema<>().type("object").properties(userProps);
        createUser.requestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                .required(true)
                .description("鐢ㄦ埛瀵硅薄")
                .content(new Content().addMediaType("application/json", new MediaType().schema(userSchema))));
        createUser.setResponses(buildResponses());

        openAPI.path("/api/v1/users", new PathItem()
                .get(getUsers)
                .post(createUser));

        // /api/v1/orders
        Operation getOrders = new Operation()
                .summary("鏌ヨ璁㈠崟鍒楄〃")
                .addTagsItem("璁㈠崟绠＄悊");
        getOrders.addParametersItem(new Parameter()
                .name("userId").in("query").required(true)
                .description("鐢ㄦ埛 ID")
                .schema(new Schema<>().type("integer").example(1001)));
        getOrders.setResponses(buildResponses());

        openAPI.path("/api/v1/orders", new PathItem().get(getOrders));

        return openAPI;
    }

    /**
     * 鏋勯€犳牱渚?ApiResponses (200 / 404)銆?     *
     * @return ApiResponses 瀹炰緥
     */
    private static ApiResponses buildResponses() {
        Map<String, Schema> userProps = new LinkedHashMap<>();
        userProps.put("id", new Schema<>().type("integer"));
        userProps.put("name", new Schema<>().type("string"));
        userProps.put("email", new Schema<>().type("string"));
        Schema userSchema = new Schema<>().type("object").properties(userProps);

        return new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .description("鎴愬姛")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(userSchema))))
                .addApiResponse("404", new ApiResponse().description("璧勬簮涓嶅瓨鍦?));
    }

    /**
     * 鏋勯€犲揩閫熷叆闂?section銆?     *
     * @return OpenApiSection 瀹炰緥
     */
    private static OpenApiSection buildIntroSection() {
        OpenApiSection sec = new OpenApiSection();
        sec.setTitle("蹇€熷叆闂?);
        sec.setContent("""
                <p>鏈枃妗ｇ敱 <code>OpenApiDocumentProvider</code> 娓叉煋, 妯′豢娉涘井 E10 OpenAPI 鎺ュ彛鏂囨。鏍峰紡銆?/p>
                <h3>浣跨敤鏂瑰紡</h3>
                <ul>
                    <li><strong>鏈湴涓嬭浇</strong>: 璁块棶 <code>GET /chua/swagger/export</code></li>
                    <li><strong>鍦ㄧ嚎棰勮</strong>: 璁块棶 <code>GET /chua/swagger/export/preview</code></li>
                </ul>
                """);
        return sec;
    }

    /**
     * HTML 鍐呭鏂█銆?     *
     * @param html     HTML 瀛楃涓?     * @param expected 鏈熸湜鍖呭惈鐨勫瓙涓?     * @param label    鏂█鏍囩 (鐢ㄤ簬鏃ュ織)
     * @throws AssertionError 鏂█澶辫触
     */
    private static void assertContains(String html, String expected, String label) {
        if (!html.contains(expected)) {
            throw new AssertionError("鏂█澶辫触 [" + label + "]: HTML 涓簲鍖呭惈 " + expected
                    + "\n--- 瀹為檯寮€澶?500 瀛楃 ---\n"
                    + html.substring(0, Math.min(500, html.length())));
        }
        log.info("  鉁?" + label);
    }

    public static void main(String[] args) {
        new OpenApiExportExample().run(stream(args).collect(Collectors.toMap(a -> a.split("=")[0], a -> a.split("=")[1])));
    }

}

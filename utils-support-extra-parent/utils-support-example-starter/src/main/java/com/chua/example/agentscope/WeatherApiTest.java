package com.chua.example.agentscope;

import com.chua.common.support.network.client.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单天气查询测试 — 直接调用 wttr.in 验证网络连通性和数据解析。
 */
@Slf4j
public class WeatherApiTest {
    public static void main(String[] args) throws Exception {
        String city = args.length > 0 ? args[0] : "北京";
        String url = "https://wttr.in/" + city + "?format=j1";

        log.info(">>> 查询 {} 天气: {}", city, url);

        String json = HttpClientFactory.of(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126")
                .get()
                .getBodyString();

        if (json == null || json.isEmpty()) {
            log.error("[ERROR] 无响应");
            return;
        }

        // 解析关键字段
        int tempC = parseJsonInt(json, "temp_C");
        int humidity = parseJsonInt(json, "humidity");
        int feelsLike = parseJsonInt(json, "FeelsLikeC");
        String desc = extractJsonValue(json, "value");

        log.info("=== {} 实时天气 ===", city);
        log.info("气温:   {}°C", tempC);
        log.info("体感:   {}°C", feelsLike);
        log.info("湿度:   {}%", humidity);
        log.info("天气:   {}", desc);
        log.info("响应长度: {} bytes", json.length());
        log.info("状态:   成功");
    }

    private static int parseJsonInt(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"?(\\d+)\"?";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception e) {}
        return -1;
    }

    private static String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception e) {}
        return "?";
    }
}

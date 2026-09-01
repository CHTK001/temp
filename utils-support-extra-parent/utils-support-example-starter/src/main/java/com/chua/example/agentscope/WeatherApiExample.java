package com.chua.example.agentscope;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.network.client.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单天气查询示例 — 直接调用 wttr.in 验证网络连通性和数据解析。
 * 演示使用 {@link Json} 工具类安全解析 JSON 响应，而非手写正则。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class WeatherApiExample {

    private WeatherApiExample() {}

    /**
     * 入口方法：查询指定城市的实时天气。
     *
     * @param args 第一个参数为城市名（默认"北京"）
     */
    public static void main(String[] args) throws Exception {
        String city = args != null && args.length > 0 && args[0] != null ? args[0] : "北京";
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

        // 使用 Json 工具类解析关键字段
        int tempC = getJsonInt(json, "temp_C");
        int humidity = getJsonInt(json, "humidity");
        int feelsLike = getJsonInt(json, "FeelsLikeC");
        String desc = getJsonStr(json, "value");

        log.info("=== {} 实时天气 ===", city);
        log.info("气温:   {}°C", tempC);
        log.info("体感:   {}°C", feelsLike);
        log.info("湿度:   {}%", humidity);
        log.info("天气:   {}", desc);
        log.info("响应长度: {} bytes", json.length());
        log.info("状态:   成功");
    }

    /**
     * 从 JSON 字符串中提取指定整数字段值，未找到返回 -1。
     *
     * @param json JSON 字符串，不能为 null 或空
     * @param key  字段名，不能为 null 或空
     * @return 字段整数值，解析失败返回 -1
     */
    private static int getJsonInt(String json, String key) {
        try {
            return Json.parse(json).get(key).toIntValue(-1);
        } catch (Exception e) {
            log.warn("[WARN] parse failed for key='{}': {}", key, e.getMessage());
            return -1;
        }
    }

    /**
     * 从 JSON 字符串中提取指定字符串字段值，未找到返回 "?"。
     *
     * @param json JSON 字符串，不能为 null 或空
     * @param key  字段名，不能为 null 或空
     * @return 字段字符串值，解析失败返回 "?"
     */
    private static String getJsonStr(String json, String key) {
        try {
            return Json.parse(json).get(key).toStringValue("?");
        } catch (Exception e) {
            log.warn("[WARN] parse failed for key='{}': {}", key, e.getMessage());
            return "?";
        }
    }
}

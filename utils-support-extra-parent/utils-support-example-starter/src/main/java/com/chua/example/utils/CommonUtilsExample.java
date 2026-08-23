package com.chua.example.utils;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.DateUtils;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.PrivacyUtils;
import com.chua.common.support.utils.RandomUtils;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 公共工具类综合示例，演示 IdUtils / DigestUtils / StringUtils / CollectionUtils /
 * MapUtils / DateUtils / PrivacyUtils / RandomUtils / ReflectUtils 的核心用法并自检。
 *
 * <p>数学工具请参考同包 {@link MathUtilsExample}。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java com.chua.example.utils.CommonUtilsExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class CommonUtilsExample {

    /**
     * 主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        int pass = 0;
        int fail = 0;

        // 1) UUID 格式自检：8-4-4-4-12 十六进制
        if (check("IdUtils.uuid", () ->
                IdUtils.uuid().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
            log.info("  uuid = {}", IdUtils.uuid());
            pass++;
        } else {
            fail++;
        }

        // 2) 雪花 ID 自检：返回 long 且大于 0
        if (check("IdUtils.createSnowflakeId", () -> IdUtils.createSnowflakeId() > 0)) {
            log.info("  snowflake = {}", IdUtils.createSnowflakeId());
            pass++;
        } else {
            fail++;
        }

        // 3) MD5 已知值自检："hello" 的标准摘要
        if (check("IdUtils.createMd5(hello)", () ->
                "5d41402abc4b2a76b9719d911017c592".equals(IdUtils.createMd5("hello")))) {
            pass++;
        } else {
            fail++;
        }

        // 4) 反射加载自检
        if (check("ReflectUtils.forName", () ->
                ReflectUtils.forName("com.chua.common.support.utils.MathUtils") != null)) {
            pass++;
        } else {
            fail++;
        }

        // 5) 摘要算法自检："abc" 的标准测试向量
        if (check("DigestUtils.md5Hex(abc)", () ->
                "900150983cd24fb0d6963f7d28e17f72".equals(DigestUtils.md5Hex("abc")))) {
            pass++;
        } else {
            fail++;
        }

        // 6) SHA-256 已知值自检
        if (check("DigestUtils.sha256(abc)", () ->
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(DigestUtils.sha256("abc")))) {
            pass++;
        } else {
            fail++;
        }

        // 7) HMAC-SHA256 输出为 64 位十六进制
        if (check("DigestUtils.hmacSha256", () ->
                DigestUtils.hmacSha256("abc", "key").matches("[0-9a-f]{64}"))) {
            pass++;
        } else {
            fail++;
        }

        // 8) 字符串工具自检：空白判断与首字母大写
        if (check("StringUtils.isBlank/capitalize", () ->
                StringUtils.isBlank(" ") && !StringUtils.isBlank("hi")
                        && "Hi".equals(StringUtils.capitalize("hi")))) {
            pass++;
        } else {
            fail++;
        }

        // 9) 集合工具自检：判空 / 包含 / 排序
        if (check("CollectionUtils", () -> {
            List<String> list = new ArrayList<>();
            Collections.addAll(list, "b", "a", "c");
            return CollectionUtils.size(list) == 3
                    && CollectionUtils.contains(list, Collections.singleton("a"))
                    && CollectionUtils.sort(list, String::compareTo).toString().equals("[a, b, c]");
        })) {
            pass++;
        } else {
            fail++;
        }

        // 10) Map 工具自检：判空 / 取值
        if (check("MapUtils", () -> {
            Map<String, Object> map = Map.of("k", "v");
            return !MapUtils.isEmpty(map)
                    && "v".equals(MapUtils.getString(map, "k"));
        })) {
            pass++;
        } else {
            fail++;
        }

        // 11) 日期工具自检：格式化与解析往返一致
        if (check("DateUtils round-trip", () -> {
            String now = DateUtils.currentString();
            return now.equals(DateUtils.format(DateUtils.parseDateSafe(now)));
        })) {
            log.info("  now = {}", DateUtils.currentString());
            pass++;
        } else {
            fail++;
        }

        // 12) 隐私脱敏自检：手机号中间四位打码
        if (check("PrivacyUtils.hidePhone", () ->
                "138****8000".equals(PrivacyUtils.hidePhone("13800138000")))) {
            log.info("  hideCard = {}", PrivacyUtils.hideCard("6222021234567890123"));
            pass++;
        } else {
            fail++;
        }

        // 13) 随机数自检：落在闭区间 [1, 100]
        if (check("RandomUtils.randomInt(1,100)", () -> {
            int value = RandomUtils.randomInt(1, 100);
            return value >= 1 && value <= 100;
        })) {
            pass++;
        } else {
            fail++;
        }

        // 汇总输出
        log.info("===== CommonUtilsExample: {} 通过 / {} 失败 =====", pass, fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    /**
     * 执行单条自检并打印结果。
     *
     * @param name      自检项名称
     * @param condition 断言条件
     * @return 全部通过返回 true
     */
    private static boolean check(String name, BooleanSupplier condition) {
        boolean result = condition.getAsBoolean();
        log.info("[{}] {}", result ? "PASS" : "FAIL", name);
        return result;
    }
}

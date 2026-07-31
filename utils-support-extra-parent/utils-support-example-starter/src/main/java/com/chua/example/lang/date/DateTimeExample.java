package com.chua.example.lang.date;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.date.DateTimeRange;
import com.chua.common.support.lang.date.enums.ZoneIdEnum;
import com.chua.common.support.lang.date.unit.DateUnit;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * 日期时间工具综合示例 — 演示 {@link DateTime} 的能力矩阵。
 *
 * <p>本示例覆盖当前时间、解析、格式化、类型转换、时间计算、时间差、
 * 时间比较、时间区间、时区处理等 9 大能力点，并提供自检流程用于
 * 验证 {@code com.chua.common.support.lang.date} 包下的工具类可用性。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 默认运行模式：执行全部能力点自检
 *   java DateTimeExample
 *
 *   # 指定能力点测试（now / parse / format / convert / plus / between / compare / range / zone）
 *   java DateTimeExample --type now
 *
 *   # 打印帮助
 *   java DateTimeExample --help
 * </pre>
 *
 * <h2>能力点矩阵</h2>
 * <table border="1">
 *   <tr><th>能力</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>当前时间</td><td>{@link #nowExample()}</td><td>获取当前时间、毫秒时间戳、指定时区时间</td></tr>
 *   <tr><td>解析</td><td>{@link #parseExample()}</td><td>字符串/时间戳/Date 转 LocalDateTime</td></tr>
 *   <tr><td>格式化</td><td>{@link #formatExample()}</td><td>默认格式与自定义格式</td></tr>
 *   <tr><td>类型转换</td><td>{@link #convertExample()}</td><td>Date/LocalDateTime/ZonedDateTime 互转</td></tr>
 *   <tr><td>时间计算</td><td>{@link #plusExample()}</td><td>加减年/月/日/时/分/秒</td></tr>
 *   <tr><td>时间差</td><td>{@link #betweenExample()}</td><td>计算天数/小时/分钟/秒/毫秒差</td></tr>
 *   <tr><td>时间比较</td><td>{@link #compareExample()}</td><td>区间判断、是否今天</td></tr>
 *   <tr><td>时间区间</td><td>{@link #rangeExample()}</td><td>创建并使用 DateTimeRange</td></tr>
 *   <tr><td>时区处理</td><td>{@link #zoneExample()}</td><td>时区转换与时区枚举</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.43
 */
public class DateTimeExample {

    /**
     * 测试用时间字符串（ISO 格式）
     */
    private static final String ISO_TIME_STRING = "2026-07-27T10:00:00";

    /**
     * 测试用时间字符串（自定义格式）
     */
    private static final String CUSTOM_TIME_STRING = "2026-07-27 10:00:00";

    /**
     * 自定义时间格式
     */
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 上海时区
     */
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 测试用毫秒时间戳（2026-07-27 10:00:00 UTC+8 的毫秒值）
     */
    private static final long TEST_EPOCH_MILLIS = 1785242400000L;

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 主入口：根据命令行参数运行指定能力点自检。
     *
     * @param args 命令行参数，args[0]=能力点类型，默认执行全部
     */
    public static void main(String[] args) {
        String type = (args != null && args.length > 0 && args[0] != null && !args[0].isEmpty())
                ? args[0].toLowerCase()
                : "all";

        if ("--help".equals(type) || "-h".equals(type)) {
            printHelp();
            return;
        }

        DateTimeExample example = new DateTimeExample();
        boolean passed = example.runTest(type);
        System.out.println("[DateTimeExample] self-test type=" + type + ", passed=" + passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 启动自检流程：根据能力点类型分发到对应的测试方法。
     *
     * @param type 能力点类型
     * @return true 表示所选能力点自检通过
     */
    public boolean runTest(String type) {
        if (type == null || type.isEmpty()) {
            type = "all";
        }
        switch (type.toLowerCase()) {
            case "now" -> {
                return nowExample();
            }
            case "parse" -> {
                return parseExample();
            }
            case "format" -> {
                return formatExample();
            }
            case "convert" -> {
                return convertExample();
            }
            case "plus" -> {
                return plusExample();
            }
            case "between" -> {
                return betweenExample();
            }
            case "compare" -> {
                return compareExample();
            }
            case "range" -> {
                return rangeExample();
            }
            case "zone" -> {
                return zoneExample();
            }
            case "all" -> {
                return nowExample()
                        && parseExample()
                        && formatExample()
                        && convertExample()
                        && plusExample()
                        && betweenExample()
                        && compareExample()
                        && rangeExample()
                        && zoneExample();
            }
            default -> {
                System.err.println("[DateTimeExample] 未知能力点: " + type);
                return false;
            }
        }
    }

    /**
     * 当前时间示例：获取系统默认时区与指定时区的当前时间。
     *
     * @return true 表示自检通过
     */
    public boolean nowExample() {
        System.out.println("===== [now] 当前时间示例 =====");
        try {
            LocalDateTime now = DateTime.now();
            long nowMillis = DateTime.nowMillis();
            LocalDateTime shanghaiNow = DateTime.now(SHANGHAI_ZONE);

            System.out.println("  系统默认时区当前时间: " + DateTime.format(now));
            System.out.println("  当前毫秒时间戳: " + nowMillis);
            System.out.println("  上海时区当前时间: " + DateTime.format(shanghaiNow));

            boolean passed = now != null && nowMillis > 0 && shanghaiNow != null;
            System.out.println("  [now] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] now failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 解析示例：字符串、时间戳、Date 转 LocalDateTime。
     *
     * @return true 表示自检通过
     */
    public boolean parseExample() {
        System.out.println("===== [parse] 解析示例 =====");
        try {
            LocalDateTime parsedIso = DateTime.parse(ISO_TIME_STRING);
            LocalDateTime parsedCustom = DateTime.parse(CUSTOM_TIME_STRING, PATTERN);
            LocalDateTime parsedMillis = DateTime.parse(TEST_EPOCH_MILLIS);
            Date date = new Date(TEST_EPOCH_MILLIS);
            LocalDateTime parsedDate = DateTime.parse(date);

            System.out.println("  ISO 解析: " + parsedIso);
            System.out.println("  自定义格式解析: " + parsedCustom);
            System.out.println("  毫秒时间戳解析: " + parsedMillis);
            System.out.println("  Date 解析: " + parsedDate);

            boolean passed = parsedIso != null
                    && parsedCustom != null
                    && parsedMillis != null
                    && parsedDate != null;
            System.out.println("  [parse] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] parse failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 格式化示例：默认格式与自定义格式。
     *
     * @return true 表示自检通过
     */
    public boolean formatExample() {
        System.out.println("===== [format] 格式化示例 =====");
        try {
            LocalDateTime dateTime = DateTime.parse(ISO_TIME_STRING);
            String defaultFormat = DateTime.format(dateTime);
            String customFormat = DateTime.format(dateTime, "yyyy/MM/dd HH:mm");

            System.out.println("  默认格式化: " + defaultFormat);
            System.out.println("  自定义格式化: " + customFormat);

            boolean passed = defaultFormat.contains("2026-07-27")
                    && customFormat.contains("2026/07/27");
            System.out.println("  [format] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] format failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 类型转换示例：LocalDateTime、Date、毫秒时间戳互转。
     *
     * @return true 表示自检通过
     */
    public boolean convertExample() {
        System.out.println("===== [convert] 类型转换示例 =====");
        try {
            LocalDateTime dateTime = DateTime.parse(ISO_TIME_STRING);
            long millis = DateTime.toMillis(dateTime);
            Date date = DateTime.toDate(dateTime);

            System.out.println("  LocalDateTime -> millis: " + millis);
            System.out.println("  LocalDateTime -> Date: " + date);

            boolean passed = millis > 0 && date != null;
            System.out.println("  [convert] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] convert failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 时间计算示例：加减年/月/日/时/分/秒。
     *
     * @return true 表示自检通过
     */
    public boolean plusExample() {
        System.out.println("===== [plus] 时间计算示例 =====");
        try {
            LocalDateTime now = DateTime.now();
            LocalDateTime nextYear = DateTime.plusYears(1);
            LocalDateTime nextMonth = DateTime.plusMonths(1);
            LocalDateTime nextDay = DateTime.plusDays(1);
            LocalDateTime nextHour = DateTime.plusHours(1);
            LocalDateTime plusCustom = DateTime.plus(now, 30, DateUnit.MINUTE);

            System.out.println("  当前时间: " + DateTime.format(now));
            System.out.println("  +1 年: " + DateTime.format(nextYear));
            System.out.println("  +1 月: " + DateTime.format(nextMonth));
            System.out.println("  +1 日: " + DateTime.format(nextDay));
            System.out.println("  +1 时: " + DateTime.format(nextHour));
            System.out.println("  +30 分: " + DateTime.format(plusCustom));

            boolean passed = nextYear.isAfter(now)
                    && nextMonth.isAfter(now)
                    && nextDay.isAfter(now)
                    && nextHour.isAfter(now)
                    && plusCustom.isAfter(now);
            System.out.println("  [plus] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] plus failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 时间差示例：计算两个时间的天/小时/分钟/秒/毫秒差。
     *
     * @return true 表示自检通过
     */
    public boolean betweenExample() {
        System.out.println("===== [between] 时间差示例 =====");
        try {
            LocalDateTime start = DateTime.parse(ISO_TIME_STRING);
            LocalDateTime end = start.plusDays(2).plusHours(3).plusMinutes(30);

            long days = DateTime.betweenDays(start, end);
            long hours = DateTime.betweenHours(start, end);
            long minutes = DateTime.betweenMinutes(start, end);
            long seconds = DateTime.betweenSeconds(start, end);
            long millis = DateTime.betweenMillis(start, end);

            System.out.println("  start: " + start);
            System.out.println("  end: " + end);
            System.out.println("  相差天数: " + days);
            System.out.println("  相差小时: " + hours);
            System.out.println("  相差分钟: " + minutes);
            System.out.println("  相差秒数: " + seconds);
            System.out.println("  相差毫秒: " + millis);

            boolean passed = days == 2 && hours == 51 && minutes == 3090;
            System.out.println("  [between] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] between failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 时间比较示例：区间判断、是否今天。
     *
     * @return true 表示自检通过
     */
    public boolean compareExample() {
        System.out.println("===== [compare] 时间比较示例 =====");
        try {
            LocalDateTime start = DateTime.parse(ISO_TIME_STRING);
            LocalDateTime mid = start.plusHours(5);
            LocalDateTime end = start.plusDays(1);

            boolean midIn = DateTime.isBetween(mid, start, end);
            boolean endIn = DateTime.isBetween(end, start, end);
            LocalDateTime now = DateTime.now();
            boolean today = DateTime.isToday(now);
            boolean notToday = DateTime.isToday(start);

            System.out.println("  start: " + start);
            System.out.println("  mid  (+5h): " + mid);
            System.out.println("  end  (+1d): " + end);
            System.out.println("  mid 在 [start, end] 内: " + midIn);
            System.out.println("  end 在 [start, end] 内: " + endIn);
            System.out.println("  当前时间是今天: " + today);
            System.out.println("  start 是今天: " + notToday);

            boolean passed = midIn && endIn && today && !notToday;
            System.out.println("  [compare] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] compare failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 时间区间示例：创建并使用 DateTimeRange。
     *
     * @return true 表示自检通过
     */
    public boolean rangeExample() {
        System.out.println("===== [range] 时间区间示例 =====");
        try {
            LocalDateTime start = DateTime.parse(ISO_TIME_STRING);
            DateTimeRange range1 = DateTime.range(start, start.plusDays(7));
            DateTimeRange range2 = DateTime.range(start, 7);

            System.out.println("  range1: " + range1);
            System.out.println("  range2: " + range2);

            boolean passed = range1 != null && range2 != null;
            System.out.println("  [range] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] range failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 时区处理示例：时区转换与时区枚举。
     *
     * @return true 表示自检通过
     */
    public boolean zoneExample() {
        System.out.println("===== [zone] 时区处理示例 =====");
        try {
            LocalDateTime dateTime = DateTime.parse(ISO_TIME_STRING);
            ZonedDateTime shanghaiZoned = DateTime.withZone(dateTime, SHANGHAI_ZONE);
            long shanghaiMillis = DateTime.toEpochMilli(dateTime, SHANGHAI_ZONE);
            String shanghaiFormatted = DateTime.format(dateTime, SHANGHAI_ZONE, PATTERN);
            ZonedDateTime fromEnum = DateTime.ofZone(dateTime, ZoneIdEnum.CTT);

            System.out.println("  上海时区 ZonedDateTime: " + shanghaiZoned);
            System.out.println("  上海时区毫秒时间戳: " + shanghaiMillis);
            System.out.println("  上海时区格式化: " + shanghaiFormatted);
            System.out.println("  CTT 枚举转 ZonedDateTime: " + fromEnum);

            boolean passed = shanghaiZoned != null
                    && shanghaiMillis > 0
                    && shanghaiFormatted.contains("2026-07-27")
                    && fromEnum != null
                    && fromEnum.getZone().equals(SHANGHAI_ZONE);
            System.out.println("  [zone] passed=" + passed);
            return passed;
        } catch (Exception e) {
            System.err.println("[DateTimeExample] zone failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 打印帮助信息。
     */
    private static void printHelp() {
        System.out.println("DateTimeExample — 日期时间工具示例");
        System.out.println();
        System.out.println("用法: java DateTimeExample [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  now       当前时间能力点");
        System.out.println("  parse     解析能力点");
        System.out.println("  format    格式化能力点");
        System.out.println("  convert   类型转换能力点");
        System.out.println("  plus      时间计算能力点");
        System.out.println("  between   时间差能力点");
        System.out.println("  compare   时间比较能力点");
        System.out.println("  range     时间区间能力点");
        System.out.println("  zone      时区处理能力点");
        System.out.println("  all       测试全部能力点（默认）");
        System.out.println("  --help    显示此帮助");
    }
}

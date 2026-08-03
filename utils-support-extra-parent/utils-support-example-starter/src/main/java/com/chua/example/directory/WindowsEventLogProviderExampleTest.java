package com.chua.example.directory;

import com.chua.filesystem.log.support.model.LogEntry;
import com.chua.filesystem.log.support.model.LogLevel;
import com.chua.filesystem.log.support.spi.impl.WindowsEventLogProvider;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * WindowsEventLogProvider 单元测试入口。
 *
 * <p>绕过 FFM 调用，直接构造 {@code EVENTLOGRECORD} 二进制布局的 buffer，
 * 验证 {@link WindowsEventLogProvider#parseEventLogRecords} 字段解析、
 * UTF-16 LE 字符串解码、EventType 级别映射、源字段透传等行为。</p>
 *
 * <p>EVENTLOGRECORD 字段偏移（参考 MSDN）：</p>
 * <pre>
 *   0  Length             DWORD
 *   4  Reserved           DWORD
 *   8  RecordNumber       DWORD
 *  12  TimeGenerated      DWORD
 *  16  TimeWritten        DWORD
 *  20  EventID            DWORD
 *  24  EventType          WORD   (1=ERROR, 2=WARNING, 4=INFO, 16=ERROR)
 *  26  NumStrings         WORD
 *  28  EventCategory      WORD
 *  30  ReservedFlags      WORD
 *  32  ClosingRecordNumber DWORD
 *  36  StringOffset       DWORD
 *  40  UserSidLength      DWORD
 *  44  UserSidOffset      DWORD
 *  48  DataLength         DWORD
 *  52  DataOffset         DWORD
 *  56+ Strings            UTF-16 LE, double-null terminated
 * </pre>
 *
 * <p>运行：</p>
 * <pre>
 * mvn exec:java@windows-event-log-test -pl utils-support-example-starter
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
public class WindowsEventLogProviderExampleTest {

    /**
     * 手造单条 EVENTLOGRECORD。
     *
     * @param eventType   1=ERROR / 2=WARNING / 4=INFO
     * @param epochSec    TimeGenerated
     * @param messageUtf16 字符串（已 UTF-16 LE 编码）
     * @return MemorySegment + bytesRead
     */
    private static byte[] buildRecord(int eventType, long epochSec, String message) {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_16LE);
        int msgLen = msgBytes.length;
        // header(56) + 双 null 终止符(4)
        int totalLen = 56 + msgLen + 4;
        byte[] rec = new byte[totalLen];

        // DWORD/Word 用小端写入
        writeInt(rec, 0, totalLen);                       // Length
        writeInt(rec, 4, 0);                              // Reserved
        writeInt(rec, 8, 1);                              // RecordNumber
        writeInt(rec, 12, (int) (epochSec & 0xFFFFFFFFL));// TimeGenerated
        writeInt(rec, 16, (int) (epochSec & 0xFFFFFFFFL));// TimeWritten
        writeInt(rec, 20, 1000);                          // EventID
        writeShort(rec, 24, (short) eventType);           // EventType
        writeShort(rec, 26, (short) 1);                  // NumStrings
        writeShort(rec, 28, (short) 0);                  // EventCategory
        writeShort(rec, 30, (short) 0);                  // ReservedFlags
        writeInt(rec, 32, 1);                             // ClosingRecordNumber
        writeInt(rec, 36, 56);                            // StringOffset = header size
        writeInt(rec, 40, 0);                             // UserSidLength
        writeInt(rec, 44, 0);                             // UserSidOffset
        writeInt(rec, 48, 0);                             // DataLength
        writeInt(rec, 52, 0);                             // DataOffset

        // 字符串区
        System.arraycopy(msgBytes, 0, rec, 56, msgLen);
        // 终止符：\u0000\u0000
        writeShort(rec, 56 + msgLen, (short) 0);
        writeShort(rec, 56 + msgLen + 2, (short) 0);

        return rec;
    }

    private static void writeInt(byte[] buf, int off, int v) {
        buf[off]     = (byte) (v & 0xFF);
        buf[off + 1] = (byte) ((v >>> 8) & 0xFF);
        buf[off + 2] = (byte) ((v >>> 16) & 0xFF);
        buf[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    private static void writeShort(byte[] buf, int off, short v) {
        buf[off]     = (byte) (v & 0xFF);
        buf[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }

    /**
     * 将多条 record 拼接成一个 buffer。
     */
    private static MemorySegment wrap(byte[]... records) {
        int total = 0;
        for (byte[] r : records) {
            total += r.length;
        }
        byte[] all = new byte[total];
        int off = 0;
        for (byte[] r : records) {
            System.arraycopy(r, 0, all, off, r.length);
            off += r.length;
        }
        return MemorySegment.ofArray(all);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " ==> expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private static final class Result {
        final String name;
        int total = 0;
        int pass = 0;
        final List<String> failures = new ArrayList<>();

        Result(String name) {
            this.name = name;
        }

        void run(String caseName, Runnable body) {
            total++;
            try {
                body.run();
                pass++;
                System.out.println("  [PASS] " + caseName);
            } catch (Throwable t) {
                failures.add(caseName + " -> " + t.getMessage());
                System.out.println("  [FAIL] " + caseName + " -> " + t.getMessage());
            }
        }

        void summary() {
            System.out.println("[" + name + "] pass=" + pass + "/" + total
                    + (failures.isEmpty() ? "" : ", failures=" + failures));
        }
    }

    public static void main(String[] args) {
        Result r = new Result("WindowsEventLogProvider");
        r.run("decodeUtf16Message", WindowsEventLogProviderExampleTest::t1);
        r.run("eventTypeMapping", WindowsEventLogProviderExampleTest::t2);
        r.run("sourceFieldPassedThrough", WindowsEventLogProviderExampleTest::t3);
        r.run("multipleRecordsParsed", WindowsEventLogProviderExampleTest::t4);
        r.run("patternFilterApplied", WindowsEventLogProviderExampleTest::t5);
        r.run("minLevelFilterDrops", WindowsEventLogProviderExampleTest::t6);
        r.run("noMessageFallback", WindowsEventLogProviderExampleTest::t7);
        r.run("truncatedRecordStopsParsing", WindowsEventLogProviderExampleTest::t8);
        r.run("emptyBufferReturnsNothing", WindowsEventLogProviderExampleTest::t9);
        r.run("corruptLengthZeroStopsLoop", WindowsEventLogProviderExampleTest::t10);
        r.run("corruptLengthOverflowsStopsLoop", WindowsEventLogProviderExampleTest::t11);
        r.run("remainingLimitStopsParsing", WindowsEventLogProviderExampleTest::t12);
        r.summary();
        if (!r.failures.isEmpty()) {
            System.err.println("FAILURES:");
            for (String f : r.failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // ---------- 用例 ----------

    /**
     * UTF-16 LE 字符串应正确解码（不是单字节截断的乱码）。
     */
    private static void t1() {
        byte[] rec = buildRecord((short) 4, 1700000000L, "hello-world");
        MemorySegment buf = wrap(rec);
        List<LogEntry> out = new ArrayList<>();
        WindowsEventLogProvider.parseEventLogRecords(buf, rec.length, "System",
                null, null, 100, out);

        assertEquals(1, out.size(), "应解析 1 条");
        assertEquals("hello-world", out.get(0).message(), "UTF-16 消息解码正确");
    }

    /**
     * EventType 1/2/4/16 应映射到 ERROR/WARNING/INFO/ERROR。
     * 修复前 EventType 偏移错误读取了 EventID 低位，导致大量条目错判。
     */
    private static void t2() {
        List<LogEntry> out = new ArrayList<>();
        byte[] r1 = buildRecord((short) 1, 1700000001L, "err");
        byte[] r2 = buildRecord((short) 2, 1700000002L, "warn");
        byte[] r3 = buildRecord((short) 4, 1700000003L, "info");
        byte[] r4 = buildRecord((short) 16, 1700000004L, "audit-fail");
        MemorySegment buf = wrap(r1, r2, r3, r4);
        int total = r1.length + r2.length + r3.length + r4.length;
        WindowsEventLogProvider.parseEventLogRecords(buf, total, "System",
                null, null, 100, out);

        assertEquals(4, out.size(), "应解析 4 条");
        assertEquals(LogLevel.ERROR, out.get(0).level(), "EventType=1 -> ERROR");
        assertEquals(LogLevel.WARNING, out.get(1).level(), "EventType=2 -> WARNING");
        assertEquals(LogLevel.INFO, out.get(2).level(), "EventType=4 -> INFO");
        assertEquals(LogLevel.ERROR, out.get(3).level(), "EventType=16 -> ERROR");
    }

    /**
     * LogEntry.source 必须等于调用方传入的 source（不能硬编码为 "System"）。
     */
    private static void t3() {
        byte[] rec = buildRecord((short) 4, 1700000010L, "x");
        MemorySegment buf = wrap(rec);
        List<LogEntry> out = new ArrayList<>();

        WindowsEventLogProvider.parseEventLogRecords(buf, rec.length, "Application",
                null, null, 100, out);
        assertEquals("Application", out.get(0).source(), "Application channel 应透传");

        out.clear();
        WindowsEventLogProvider.parseEventLogRecords(buf, rec.length, "Security",
                null, null, 100, out);
        assertEquals("Security", out.get(0).source(), "Security channel 应透传");
    }

    /**
     * 多条 record 应顺序全部解析。
     */
    private static void t4() {
        byte[] r1 = buildRecord((short) 4, 1700000100L, "first");
        byte[] r2 = buildRecord((short) 4, 1700000200L, "second");
        byte[] r3 = buildRecord((short) 4, 1700000300L, "third");
        MemorySegment buf = wrap(r1, r2, r3);
        List<LogEntry> out = new ArrayList<>();
        int total = r1.length + r2.length + r3.length;
        WindowsEventLogProvider.parseEventLogRecords(buf, total, "System",
                null, null, 100, out);

        assertEquals(3, out.size(), "应解析 3 条");
        assertEquals("first", out.get(0).message(), "顺序正确");
        assertEquals("second", out.get(1).message(), "顺序正确");
        assertEquals("third", out.get(2).message(), "顺序正确");
    }

    /**
     * pattern glob 过滤：不匹配的消息被丢弃。
     */
    private static void t5() {
        byte[] r1 = buildRecord((short) 4, 1700000200L, "disk-failure");
        byte[] r2 = buildRecord((short) 4, 1700000201L, "network-timeout");
        MemorySegment buf = wrap(r1, r2);
        List<LogEntry> out = new ArrayList<>();
        int total = r1.length + r2.length;
        // glob "*disk*" → regex ".*disk.*"
        WindowsEventLogProvider.parseEventLogRecords(buf, total, "System",
                Pattern.compile(".*disk.*", Pattern.CASE_INSENSITIVE),
                null, 100, out);

        assertEquals(1, out.size(), "应过滤掉 network-timeout");
        assertEquals("disk-failure", out.get(0).message(), "仅保留匹配项");
    }

    /**
     * minLevel=ERROR 时 WARNING/INFO 被丢弃。
     */
    private static void t6() {
        byte[] r1 = buildRecord((short) 4, 1700000300L, "info");
        byte[] r2 = buildRecord((short) 2, 1700000301L, "warn");
        byte[] r3 = buildRecord((short) 1, 1700000302L, "err");
        MemorySegment buf = wrap(r1, r2, r3);
        List<LogEntry> out = new ArrayList<>();
        int total = r1.length + r2.length + r3.length;
        WindowsEventLogProvider.parseEventLogRecords(buf, total, "System",
                null, LogLevel.ERROR, 100, out);

        assertEquals(1, out.size(), "应仅保留 ERROR");
        assertEquals("err", out.get(0).message(), "ERROR 条目");
    }

    /**
     * 字符串区为空时回退为 "(no message)"。
     */
    private static void t7() {
        byte[] rec = buildRecord((short) 4, 1700000400L, "");
        MemorySegment buf = wrap(rec);
        List<LogEntry> out = new ArrayList<>();
        WindowsEventLogProvider.parseEventLogRecords(buf, rec.length, "System",
                null, null, 100, out);

        assertEquals(1, out.size(), "应解析 1 条");
        assertEquals("(no message)", out.get(0).message(), "空消息回退");
    }

    /**
     * 当声明的 length 超出 buffer 时停止解析（不抛异常）。
     */
    private static void t8() {
        byte[] rec = buildRecord((short) 4, 1700000500L, "abc");
        MemorySegment buf = wrap(rec);
        List<LogEntry> out = new ArrayList<>();
        // 截断到只包含部分记录
        WindowsEventLogProvider.parseEventLogRecords(buf, rec.length - 10, "System",
                null, null, 100, out);

        assertEquals(0, out.size(), "截断 buffer 不应解析出任何记录");
    }

    /**
     * 空 buffer 不抛异常，返回空列表。
     */
    private static void t9() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(ValueLayout.JAVA_BYTE, 64);
            List<LogEntry> out = new ArrayList<>();
            WindowsEventLogProvider.parseEventLogRecords(buf, 0, "System",
                    null, null, 100, out);
            assertEquals(0, out.size(), "空 buffer 返回 0 条");
        }
    }

    /**
     * length=0 的损坏记录应停止解析，不抛异常。
     */
    private static void t10() {
        byte[] rec1 = buildRecord(4, 1700000600L, "good");
        byte[] rec2 = buildRecord(4, 1700000601L, "after-zero");
        // 强制把 rec2 的 length 字段清零
        byte[] zeroLen = rec2.clone();
        writeInt(zeroLen, 0, 0);
        MemorySegment buf = wrap(rec1, zeroLen);
        List<LogEntry> out = new ArrayList<>();
        int total = rec1.length + zeroLen.length;
        WindowsEventLogProvider.parseEventLogRecords(buf, total, "System",
                null, null, 100, out);

        assertEquals(1, out.size(), "length=0 应停止解析");
        assertEquals("good", out.get(0).message(), "应保留 length=0 之前的记录");
    }

    /**
     * length 超出 buffer 剩余空间应停止解析（不读越界、不抛异常）。
     */
    private static void t11() {
        byte[] rec1 = buildRecord(4, 1700000700L, "good");
        // 篡改 rec1 的 length 字段为巨大值
        byte[] badLen = rec1.clone();
        writeInt(badLen, 0, 0x7FFFFFFF);
        MemorySegment buf = wrap(badLen);
        List<LogEntry> out = new ArrayList<>();
        WindowsEventLogProvider.parseEventLogRecords(buf, badLen.length, "System",
                null, null, 100, out);

        assertEquals(0, out.size(), "length 越界应放弃解析");
    }

    /**
     * remaining 限制：到达上限后停止解析，不再读后续 record。
     */
    private static void t12() {
        byte[] r1 = buildRecord(4, 1700000800L, "first");
        byte[] r2 = buildRecord(4, 1700000801L, "second");
        byte[] r3 = buildRecord(4, 1700000802L, "third");
        MemorySegment buf = wrap(r1, r2, r3);
        List<LogEntry> out = new ArrayList<>();
        int total = r1.length + r2.length + r3.length;
        // 只允许解析 2 条
        WindowsEventLogProvider.parseEventLogRecords(buf, total, "System",
                null, null, 2, out);

        assertEquals(2, out.size(), "应只解析 2 条");
        assertEquals("first", out.get(0).message(), "顺序正确");
        assertEquals("second", out.get(1).message(), "顺序正确");
    }
}
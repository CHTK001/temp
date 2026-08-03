package com.chua.example.directory;

import com.chua.common.support.lang.directory.EventObserver;
import com.chua.common.support.lang.directory.PolledListener;
import com.chua.common.support.lang.directory.WatcherEvent;
import com.chua.filesystem.log.support.SystemLogService;
import com.chua.filesystem.log.support.model.LogEntry;
import com.chua.filesystem.log.support.model.LogLevel;
import com.chua.filesystem.log.support.model.LogQuery;
import com.chua.filesystem.log.support.polling.SyslogPolledDirectory;
import com.chua.filesystem.log.support.spi.SystemLogProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * SyslogPolledDirectory 单元测试入口。
 *
 * <p>通过 {@code main} 方法以断言形式覆盖 10 个核心场景，所有测试
 * 使用内存 mock provider（{@link FakeProvider}），不依赖任何平台 FFM。</p>
 *
 * <p>运行方式：</p>
 * <pre>
 * mvn exec:java@polled-directory-test -pl utils-support-example-starter
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
public class SyslogPolledDirectoryExampleTest {

    /**
     * 可在测试中替换日志快照的最小 mock provider。
     */
    private static final class FakeProvider implements SystemLogProvider {
        private final List<LogEntry> snapshot;
        private final List<String> sources;

        FakeProvider(List<String> sources, List<LogEntry> snapshot) {
            this.sources = sources;
            this.snapshot = new ArrayList<>(snapshot);
        }

        void setSnapshot(List<LogEntry> entries) {
            this.snapshot.clear();
            this.snapshot.addAll(entries);
        }

        @Override
        public boolean isPlatformSupported() {
            return true;
        }

        @Override
        public List<String> getSources() {
            return sources;
        }

        @Override
        public List<LogEntry> search(LogQuery query) {
            List<LogEntry> out = new ArrayList<>();
            for (LogEntry e : snapshot) {
                if (query.source() != null && !query.source().equals(e.source())) {
                    continue;
                }
                if (query.minLevel() != null && !e.level().meetsMinimum(query.minLevel())) {
                    continue;
                }
                out.add(e);
                if (out.size() >= query.maxResults()) {
                    break;
                }
            }
            return out;
        }
    }

    private static SystemLogService serviceWith(FakeProvider provider) {
        return new SystemLogService() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<LogEntry> search(LogQuery query) {
                return provider.search(query);
            }
        };
    }

    private static PolledListener listener(BiConsumer<WatcherEvent, EventObserver> onModify) {
        return new PolledListener() {
            @Override
            public void onModify(WatcherEvent event, EventObserver observer) {
                onModify.accept(event, observer);
            }
        };
    }

    private static LogEntry entry(String ts, LogLevel lvl, String src, String msg) {
        return new LogEntry(ts, lvl, src, msg, "test", null);
    }

    /**
     * 测试结果累加器：total / pass / fail。
     */
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

    private static void assertDoesNotThrow(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            throw new AssertionError("expected no throw but got: " + t);
        }
    }

    /**
     * 入口：依次执行 10 个测试用例，结束打印统计。
     * 任何一个失败都会让退出码非 0，便于 CI 接入。
     */
    public static void main(String[] args) {
        Result r = new Result("SyslogPolledDirectory");
        r.run("firstUpgrade_onlyAdvancesCursor_noEvents", SyslogPolledDirectoryExampleTest::t1);
        r.run("secondUpgrade_dispatchesNewEntries_withCorrectObserverFields", SyslogPolledDirectoryExampleTest::t2);
        r.run("sameTimestampEntry_notRedispatched", SyslogPolledDirectoryExampleTest::t3);
        r.run("minLevelFilter_respectsQuery", SyslogPolledDirectoryExampleTest::t4);
        r.run("sourceAndPatternPassedToQuery", SyslogPolledDirectoryExampleTest::t5);
        r.run("multipleListeners_allReceiveSameEvent", SyslogPolledDirectoryExampleTest::t6);
        r.run("listenerException_doesNotBreakOthersOrNextPoll", SyslogPolledDirectoryExampleTest::t7);
        r.run("closeStopsPolling", SyslogPolledDirectoryExampleTest::t8);
        r.run("unavailableService_upgradeIsNoop", SyslogPolledDirectoryExampleTest::t9);
        r.run("isDelegatedOperatingSystem_returnsFalse", SyslogPolledDirectoryExampleTest::t10);
        r.run("concurrent_upgradeAndClose_safeAndConsistent", SyslogPolledDirectoryExampleTest::t11);
        r.run("concurrent_addListenerAndUpgrade_noLostCallbacks", SyslogPolledDirectoryExampleTest::t12);
        r.summary();
        if (!r.failures.isEmpty()) {
            System.err.println("FAILURES:");
            for (String f : r.failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }
    }

    // ---------- 测试用例 ----------

    private static void t1() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                List.of(
                        entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old-1"),
                        entry("2026-08-03T00:00:02.000", LogLevel.INFO, "System", "old-2"),
                        entry("2026-08-03T00:00:03.000", LogLevel.WARNING, "System", "old-3")
                )
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        List<LogEntry> received = new CopyOnWriteArrayList<>();
        watcher.addListener(listener((e, o) -> received.add((LogEntry) o.getSource())));

        watcher.upgrade();

        assertEquals(0, received.size(), "首次轮询不应推送任何条目");
        assertEquals("2026-08-03T00:00:03.000", watcher.getLastTimestamp(), "游标应推进到最新条目时间戳");
    }

    private static void t2() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                List.of(
                        entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old-1"),
                        entry("2026-08-03T00:00:02.000", LogLevel.INFO, "System", "old-2")
                )
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .source("System")
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        List<EventObserver> observers = new CopyOnWriteArrayList<>();
        watcher.addListener(listener((e, o) -> observers.add(o)));

        watcher.upgrade();
        assertTrue(observers.isEmpty(), "首次 upgrade 不应触发 onModify");

        provider.setSnapshot(List.of(
                entry("2026-08-03T00:00:03.000", LogLevel.WARNING, "System", "new-1"),
                entry("2026-08-03T00:00:04.000", LogLevel.ERROR, "System", "new-2")
        ));

        watcher.upgrade();

        assertEquals(2, observers.size(), "应分发 2 条新条目");
        EventObserver first = observers.get(0);
        assertEquals(WatcherEvent.MODIFY, first.getEventType(), "eventType 应为 MODIFY");
        assertEquals("System", first.getCurrentPath(), "currentPath 应等于配置的 source");
        assertEquals("System-WARNING", first.getTriggerFile(), "triggerFile 应为 source + '-' + level");
        assertTrue(first.getSource() instanceof LogEntry, "source 必须是 LogEntry 实例");

        LogEntry payload0 = (LogEntry) first.getSource();
        assertEquals("2026-08-03T00:00:03.000", payload0.timestamp(), "首条时间戳");
        assertEquals(LogLevel.WARNING, payload0.level(), "首条级别");
        assertEquals("System", payload0.source(), "LogEntry.source 应为真实打开的 channel");
        assertEquals("new-1", payload0.message(), "首条消息");

        EventObserver second = observers.get(1);
        LogEntry payload1 = (LogEntry) second.getSource();
        assertEquals("System-ERROR", second.getTriggerFile(), "第二条 triggerFile");
        assertEquals("new-2", payload1.message(), "第二条消息");
        assertEquals("2026-08-03T00:00:04.000", watcher.getLastTimestamp(), "游标应推进到最后一条");
    }

    private static void t3() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                List.of(entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "x"))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        AtomicInteger count = new AtomicInteger();
        watcher.addListener(listener((e, o) -> count.incrementAndGet()));

        watcher.upgrade();
        watcher.upgrade();
        watcher.upgrade();

        assertEquals(0, count.get(), "时间戳等于游标的条目不应再次分发");
    }

    private static void t4() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                new ArrayList<>(List.of(
                        entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "i1"),
                        entry("2026-08-03T00:00:02.000", LogLevel.WARNING, "System", "w1")
                ))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .minLevel(LogLevel.WARNING)
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        List<LogEntry> out = new CopyOnWriteArrayList<>();
        watcher.addListener(listener((e, o) -> out.add((LogEntry) o.getSource())));

        watcher.upgrade();
        provider.setSnapshot(List.of(
                entry("2026-08-03T00:00:03.000", LogLevel.INFO, "System", "i2"),
                entry("2026-08-03T00:00:04.000", LogLevel.ERROR, "System", "e1")
        ));
        watcher.upgrade();

        assertEquals(1, out.size(), "仅 ERROR 应被分发");
        assertEquals(LogLevel.ERROR, out.get(0).level(), "分发级别应为 ERROR");
    }

    private static void t5() {
        FakeProvider provider = new FakeProvider(
                List.of("Application"),
                Collections.emptyList()
        );
        AtomicReference<LogQuery> captured = new AtomicReference<>();
        SystemLogService service = new SystemLogService() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<LogEntry> search(LogQuery query) {
                captured.set(query);
                return provider.search(query);
            }
        };

        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .source("Application")
                .pattern("*disk*")
                .service(service)
                .build();
        watcher.upgrade();

        LogQuery q = captured.get();
        assertTrue(q != null, "LogQuery 必须被传入 service.search");
        assertEquals("Application", q.source(), "source 透传");
        assertEquals("*disk*", q.pattern(), "pattern 透传");
        assertEquals(LogQuery.ORDER_ASC, q.order(), "order 必须为 ASC");
    }

    private static void t6() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                List.of(entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old"))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        AtomicInteger c = new AtomicInteger();
        watcher.addListener(listener((e, o) -> a.incrementAndGet()));
        watcher.addListener(listener((e, o) -> b.incrementAndGet()));
        watcher.addListener(listener((e, o) -> c.incrementAndGet()));

        watcher.upgrade();
        provider.setSnapshot(List.of(
                entry("2026-08-03T00:00:02.000", LogLevel.INFO, "System", "new")
        ));
        watcher.upgrade();

        assertEquals(1, a.get(), "listener a 计数");
        assertEquals(1, b.get(), "listener b 计数");
        assertEquals(1, c.get(), "listener c 计数");
    }

    private static void t7() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                List.of(entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old"))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        AtomicInteger okCount = new AtomicInteger();
        watcher.addListener(listener((e, o) -> {
            throw new RuntimeException("boom");
        }));
        watcher.addListener(listener((e, o) -> okCount.incrementAndGet()));

        watcher.upgrade();
        provider.setSnapshot(List.of(
                entry("2026-08-03T00:00:02.000", LogLevel.INFO, "System", "new")
        ));
        watcher.upgrade();

        assertEquals(1, okCount.get(), "异常监听器不应阻断其他监听器");
    }

    private static void t8() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                List.of(entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old"))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        AtomicInteger count = new AtomicInteger();
        watcher.addListener(listener((e, o) -> count.incrementAndGet()));

        watcher.upgrade();
        watcher.close();
        provider.setSnapshot(List.of(
                entry("2026-08-03T00:00:02.000", LogLevel.INFO, "System", "new")
        ));
        watcher.upgrade();

        assertEquals(0, count.get(), "close 后 upgrade 不应再触发监听器");
        assertEquals(null, watcher.getLastTimestamp(), "close 应清空游标");
    }

    private static void t9() {
        SystemLogService unavailable = new SystemLogService() {
            @Override
            public boolean isAvailable() {
                return false;
            }
        };
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .service(unavailable)
                .build();
        assertDoesNotThrow(watcher::upgrade);
        assertEquals(null, watcher.getLastTimestamp(), "服务不可用时游标应保持 null");
    }

    private static void t10() {
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder().build();
        assertEquals(false, watcher.isDelegatedOperatingSystem(), "应使用定时轮询而非 OS 事件");
    }

    /**
     * 并发：一个线程持续 upgrade，另一个线程调用 close，最终 close 必须生效，
     * 且不会触发任何后续 listener、cursor 必须被清空。
     */
    private static void t11() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                new ArrayList<>(List.of(entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old")))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        AtomicInteger count = new AtomicInteger();
        watcher.addListener(listener((e, o) -> count.incrementAndGet()));

        // 启动 cursor
        watcher.upgrade();

        try {
            Thread upgradeThread = new Thread(() -> {
                for (int i = 0; i < 500; i++) {
                    watcher.upgrade();
                }
            }, "upgrader");
            Thread closeThread = new Thread(watcher::close, "closer");
            upgradeThread.start();
            Thread.sleep(5);
            closeThread.start();
            upgradeThread.join();
            closeThread.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AssertionError("并发测试被中断: " + ie.getMessage());
        }

        assertEquals(null, watcher.getLastTimestamp(), "close 后游标必须为 null");
        // close 后 listener 列表被清空，所以即便 upgrade 仍跑也无处触发
        int afterClose = count.get();
        provider.setSnapshot(List.of(entry("2026-08-03T00:00:99.000", LogLevel.INFO, "System", "new")));
        for (int i = 0; i < 10; i++) {
            watcher.upgrade();
        }
        assertEquals(afterClose, count.get(), "close 后不应再分发任何事件");
    }

    /**
     * 并发：upgrade 在跑时 addListener 持续注册新 listener，每个 listener 都应被通知到。
     * 由于 listeners 是 CopyOnWriteArrayList，遍历快照保证一致性。
     */
    private static void t12() {
        FakeProvider provider = new FakeProvider(
                List.of("System"),
                new ArrayList<>(List.of(entry("2026-08-03T00:00:01.000", LogLevel.INFO, "System", "old")))
        );
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(1)
                .service(serviceWith(provider))
                .build();
        watcher.upgrade();

        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 50; i++) {
            final int idx = i;
            watcher.addListener(listener((e, o) -> seen.add("L" + idx + ":" + ((LogEntry) o.getSource()).message())));
            String ts = String.format("2026-08-03T00:00:%02d.000", i + 2);
            provider.setSnapshot(List.of(entry(ts, LogLevel.INFO, "System", "msg-" + i)));
            watcher.upgrade();
        }

        assertTrue(seen.size() > 0, "至少应有一次回调被分发, seen=" + seen.size());
        for (int i = 0; i < 50; i++) {
            String msg = "msg-" + i;
            assertTrue(seen.stream().anyMatch(s -> s.endsWith(":" + msg)),
                    "消息 " + msg + " 应至少被某个 listener 收到");
        }
    }
}
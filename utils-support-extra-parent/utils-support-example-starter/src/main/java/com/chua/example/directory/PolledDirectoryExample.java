package com.chua.example.directory;

import com.chua.common.support.lang.directory.environment.DirectoryPollerEnvironment;
import com.chua.common.support.lang.directory.EventObserver;
import com.chua.common.support.lang.directory.PolledListener;
import com.chua.common.support.lang.directory.WatcherEvent;
import com.chua.filesystem.log.support.SystemLogService;
import com.chua.filesystem.log.support.model.LogEntry;
import com.chua.filesystem.log.support.model.LogLevel;
import com.chua.filesystem.log.support.model.LogQuery;
import com.chua.filesystem.log.support.polling.SyslogPolledDirectory;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轮询目录示例 — 演示 SyslogPolledDirectory 实时监听系统日志。
 *
 * <p>通过 {@link SyslogPolledDirectory} 实现跨平台系统日志实时监控，
 * 支持按日志级别、来源、消息模式过滤，替代 tail -f 效果。</p>
 *
 * <h2>用法</h2>
 * <pre>
 * # 默认：监听所有级别系统日志，运行 30 秒
 * java PolledDirectoryExample
 *
 * # 指定运行时长（秒）
 * java PolledDirectoryExample --duration 60
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class PolledDirectoryExample {

    private static final int DEFAULT_DURATION_SECONDS = 30;

    public static void main(String[] args) throws Exception {
        int duration = parseDuration(args);
        System.out.println("=== PolledDirectory 系统日志监听示例 ===\n");

        dumpAllLogs();
        testAllLogs(duration);
        testErrorOnly(duration);
        testFiltered(duration);

        System.out.println("\n=== 全部示例运行完成 ===");
    }

    /**
     * 直接调用 SystemLogService 拉取系统日志条目并打印到控制台
     */
    private static void dumpAllLogs() {
        System.out.println("【直查】SystemLogService.getInstance().search(...) ===");
        SystemLogService service = SystemLogService.getInstance();
        if (!service.isAvailable()) {
            System.out.println("系统日志服务不可用");
            return;
        }
        List<LogEntry> entries = service.search(LogQuery.builder().maxResults(20).order(LogQuery.ORDER_DESC).build());
        if (entries == null || entries.isEmpty()) {
            System.out.println("无日志条目");
            return;
        }
        System.out.println("共获取 " + entries.size() + " 条日志：");
        for (LogEntry entry : entries) {
            System.out.printf("[%s] [%s] [%s] %s%n",
                    entry.timestamp(), entry.level(), entry.source(), entry.message());
        }
        System.out.println();
    }

    /**
     * 解析命令行参数中的运行时长
     */
    private static int parseDuration(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--duration".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.err.println("无效的 --duration 参数: " + args[i + 1]);
                }
            }
        }
        return DEFAULT_DURATION_SECONDS;
    }

    /**
     * 测试监听所有级别系统日志
     */
    private static void testAllLogs(int duration) throws Exception {
        System.out.println("【测试1】监听所有级别系统日志 (运行 " + duration + " 秒)...");
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(3)
                .build();

        watcher.addListener(new PolledListener() {
            @Override
            public void onModify(WatcherEvent event, EventObserver observer) {
                LogEntry entry = (LogEntry) observer.getSource();
                System.out.printf("[%s] [%s] [%s] %s%n",
                        entry.timestamp(), entry.level(), entry.source(), entry.message());
            }
        });

        watcher.start(new DirectoryPollerEnvironment(
                Collections.singleton(WatcherEvent.MODIFY), 3, TimeUnit.SECONDS));

        Thread.sleep(duration * 1000L);
        watcher.close();
        System.out.println("【测试1】完成\n");
    }

    /**
     * 测试仅监听 ERROR 及以上级别日志
     */
    private static void testErrorOnly(int duration) throws Exception {
        System.out.println("【测试2】仅监听 ERROR 及以上级别日志 (运行 " + duration + " 秒)...");
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .minLevel(LogLevel.ERROR)
                .pollIntervalSeconds(2)
                .build();

        watcher.addListener(new PolledListener() {
            @Override
            public void onModify(WatcherEvent event, EventObserver observer) {
                LogEntry entry = (LogEntry) observer.getSource();
                System.out.printf("[ERROR] [%s] [%s] %s%n",
                        entry.timestamp(), entry.source(), entry.message());
            }
        });

        watcher.start(new DirectoryPollerEnvironment(
                Collections.singleton(WatcherEvent.MODIFY), 2, TimeUnit.SECONDS));

        Thread.sleep(duration * 1000L);
        watcher.close();
        System.out.println("【测试2】完成\n");
    }

    /**
     * 测试带过滤条件的系统日志监听
     */
    private static void testFiltered(int duration) throws Exception {
        System.out.println("【测试3】按来源和消息模式过滤 (运行 " + duration + " 秒)...");
        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .source("System")
                .pattern("*disk*")
                .minLevel(LogLevel.WARNING)
                .pollIntervalSeconds(5)
                .build();

        watcher.addListener(new PolledListener() {
            @Override
            public void onModify(WatcherEvent event, EventObserver observer) {
                LogEntry entry = (LogEntry) observer.getSource();
                System.out.printf("[FILTERED] [%s] [%s] %s%n",
                        entry.timestamp(), entry.source(), entry.message());
            }
        });

        watcher.start(new DirectoryPollerEnvironment(
                Collections.singleton(WatcherEvent.MODIFY), 5, TimeUnit.SECONDS));

        Thread.sleep(duration * 1000L);
        watcher.close();
        System.out.println("【测试3】完成\n");
    }
}
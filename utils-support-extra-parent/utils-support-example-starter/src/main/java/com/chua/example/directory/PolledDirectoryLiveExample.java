package com.chua.example.directory;

import com.chua.common.support.lang.directory.environment.DirectoryPollerEnvironment;
import com.chua.common.support.lang.directory.EventObserver;
import com.chua.common.support.lang.directory.PolledListener;
import com.chua.common.support.lang.directory.WatcherEvent;
import com.chua.filesystem.log.support.polling.SyslogPolledDirectory;
import com.chua.filesystem.log.support.model.LogEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 实时演示示例：在轮询监听期间通过 {@code eventcreate} 持续向
 * Windows Application 日志写入条目，以触发 {@code onModify} 回调。
 *
 * <p>本示例仅在 Windows 上有效（依赖 {@code eventcreate.exe}）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 * java PolledDirectoryLiveExample            # 默认 30 秒
 * java PolledDirectoryLiveExample --duration 60
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class PolledDirectoryLiveExample {

    /**
     * 默认运行时长（秒）
     */
    private static final int DEFAULT_DURATION_SECONDS = 30;

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 事件生成间隔（秒）
     */
    private static final long EVENT_GENERATE_INTERVAL_SECONDS = 2L;

    /**
     * eventcreate 超时时间（秒）
     */
    private static final long EVENTCREATE_TIMEOUT_SECONDS = 2L;

    public static void main(String[] args) throws Exception {
        int duration = parseDuration(args);
        PolledDirectoryLiveExample example = new PolledDirectoryLiveExample();
        boolean passed = example.runTest(duration);
        log.info("[PolledDirectoryLiveExample] self-test passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：启动 eventcreate 后台写日志线程与轮询监听，运行指定时长后退出。
     *
     * @param duration 运行时长（秒）
     * @return true 表示自检通过（事件生成器运行期间未发生异常）
     */
    public boolean runTest(int duration) {
        log.info("=== PolledDirectory 实时触发示例（{}s）===\n", duration);
        boolean passed = true;
        try {
            SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                    .source("Application")
                    .minLevel(com.chua.filesystem.log.support.model.LogLevel.WARNING)
                    .pollIntervalSeconds(2)
                    .build();
            watcher.addListener(new PolledListener() {
                @Override
                public void onModify(WatcherEvent event, EventObserver observer) {
                    LogEntry entry = (LogEntry) observer.getSource();
                    log.info("[EVENT] [{}] [{}] [{}] {}",
                            entry.timestamp(), entry.level(), entry.source(), entry.message());
                }
            });
            watcher.start(new DirectoryPollerEnvironment(
                    Collections.singleton(WatcherEvent.MODIFY), 2, TimeUnit.SECONDS));

            ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "event-generator");
                t.setDaemon(true);
                return t;
            });
            final int[] counter = {0};
            exec.scheduleAtFixedRate(() -> {
                counter[0]++;
                String message = "live-demo-event-" + counter[0] + "-disk-test";
                try {
                    Process p = new ProcessBuilder("eventcreate",
                            "/ID", "1000",
                            "/T", "WARNING",
                            "/L", "APPLICATION",
                            "/SO", "PolledDirectoryDemo",
                            "/D", message).inheritIO().start();
                    p.waitFor(EVENTCREATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("eventcreate 失败: {}", e.getMessage());
                }
            }, 1, EVENT_GENERATE_INTERVAL_SECONDS, TimeUnit.SECONDS);

            Thread.sleep(duration * 1000L);
            exec.shutdownNow();
            watcher.close();
            log.info("\n=== 实时示例结束，共生成 {} 条事件 ===", counter[0]);
        } catch (Exception e) {
            log.error("[PolledDirectoryLiveExample] 自检异常: {}", e.getMessage(), e);
            passed = false;
        }
        return passed;
    }

    private static int parseDuration(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--duration".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    log.error("无效的 --duration 参数: {}", args[i + 1]);
                }
            }
        }
        return DEFAULT_DURATION_SECONDS;
    }
}
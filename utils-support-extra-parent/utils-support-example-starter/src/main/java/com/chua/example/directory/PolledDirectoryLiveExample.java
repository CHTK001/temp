package com.chua.example.directory;

import com.chua.common.support.lang.directory.environment.DirectoryPollerEnvironment;
import com.chua.common.support.lang.directory.EventObserver;
import com.chua.common.support.lang.directory.PolledListener;
import com.chua.common.support.lang.directory.WatcherEvent;
import com.chua.filesystem.log.support.polling.SyslogPolledDirectory;
import com.chua.filesystem.log.support.model.LogEntry;

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
 * @since 4.0.0
 */
public class PolledDirectoryLiveExample {

    private static final int DEFAULT_DURATION_SECONDS = 30;

    public static void main(String[] args) throws Exception {
        int duration = parseDuration(args);
        System.out.println("=== PolledDirectory 实时触发示例（" + duration + "s）===\n");

        SyslogPolledDirectory watcher = SyslogPolledDirectory.builder()
                .pollIntervalSeconds(2)
                .build();
        watcher.addListener(new PolledListener() {
            @Override
            public void onModify(WatcherEvent event, EventObserver observer) {
                LogEntry entry = (LogEntry) observer.getSource();
                System.out.printf("[EVENT] [%s] [%s] [%s] %s%n",
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
                p.waitFor(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("eventcreate 失败: " + e.getMessage());
            }
        }, 1, 2, TimeUnit.SECONDS);

        Thread.sleep(duration * 1000L);
        exec.shutdownNow();
        watcher.close();
        System.out.println("\n=== 实时示例结束，共生成 " + counter[0] + " 条事件 ===");
    }

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
}
package com.chua.example.lang.cmd;

import com.chua.common.support.lang.cmd.CmdExecutors;
import com.chua.common.support.lang.cmd.LineCallback;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 命令同步执行示例（基于 ConPTY），演示 LineCallback 实时行级回调与 进度条覆盖帧捕获。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class CmdSyncExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    public static void main(String[] args) throws Exception {
        CmdSyncExample example = new CmdSyncExample();
        boolean passed = example.runTest();
        log.info("[CmdSyncExample] 自检结果: passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：依次演示 ping 实时回调、curl 进度条、winget 卸载/安装的逐行回调。
     *
     * @return true 表示四个同步命令全部执行成功
     */
    public boolean runTest() {
        boolean passed = true;
        log.info("========================================");
        log.info("  CmdSyncExample - LineCallback 实时回调");
        log.info("========================================");
        try {
            // 1. ping：每行 Reply 即时回调（已验证实时）
            printStep("1. ping", "ping 127.0.0.1 -n 6 -w 1000");
            CmdExecutors.executeWithOutput("ping 127.0.0.1 -n 6 -w 1000", 30, TimeUnit.SECONDS, new LineCallback() {
                @Override
                public void onLine(String line) {
                    log.info("  [ping] {}", line);
                }

                @Override
                public void onComplete(int exitCode) {
                    log.info("  [ping] done, exitCode={}", exitCode);
                }
            });
            log.info("");

            // 2. curl 进度条（\r 实时覆盖帧 → onLine 逐帧回调）
            //  ConPTY 使子进程认为它有真实控制台，从而输出 \r 进度条
            printStep("2. curl 进度条", "curl --progress-bar http://speedtest.tele2.net/1MB.zip -o NUL --max-time 10");
            CmdExecutors.executeWithOutput("curl --progress-bar http://speedtest.tele2.net/1MB.zip -o NUL --max-time 10", 30, TimeUnit.SECONDS, new LineCallback() {
                @Override
                public void onLine(String line) {
                    log.info("  [curl] {}", line);
                }

                @Override
                public void onComplete(int exitCode) {
                    log.info("  [curl] done, exitCode={}", exitCode);
                }
            });
            log.info("");

            // 3. 卸载 Redis → 安装 Redis（全程逐行回调）
            printStep("3. 卸载 Redis", "winget uninstall Redis.Redis");
            CmdExecutors.executeWithOutput("winget uninstall Redis.Redis", 60, TimeUnit.SECONDS, new LineCallback() {
                @Override
                public void onLine(String line) {
                    log.info("  [uninstall] {}", line);
                }

                @Override
                public void onComplete(int exitCode) {
                    log.info("  [uninstall] done, exitCode={}", exitCode);
                }
            });
            log.info("");

            printStep("4. 安装 Redis", "winget install --id Redis.Redis --accept-package-agreements");
            CmdExecutors.executeWithOutput("winget install --id Redis.Redis --accept-package-agreements", 600, TimeUnit.SECONDS, new LineCallback() {
                @Override
                public void onLine(String line) {
                    log.info("  [install] {}", line);
                }

                @Override
                public void onComplete(int exitCode) {
                    log.info("  [install] done, exitCode={}", exitCode);
                }
            });
            log.info("");

            log.info("========================================");
            log.info("  ALL DONE");
            log.info("========================================");
        } catch (Exception e) {
            log.error("[CmdSyncExample] 异常: {}", e.getMessage(), e);
            passed = false;
        }
        return passed;
    }

    private static void printStep(String label, String value) {
        log.info("[CmdSyncExample] {}: {}", label, value);
    }
}

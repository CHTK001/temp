package com.chua.example.lang.cmd;

import com.chua.common.support.lang.cmd.CmdCallback;
import com.chua.common.support.lang.cmd.CmdExecutors;
import com.chua.common.support.lang.cmd.CmdResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 命令异步执行示例：演示 CmdExecutors.executeAsync 的回调与 CompletableFuture 两种异步模式。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class CmdAsyncExample {

    /**
     * 程序退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 程序退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 第一个异步任务等待时长（毫秒）
     */
    private static final long FIRST_ASYNC_WAIT_MS = 2000L;

    /**
     * CompletableFuture 任务等待时长（毫秒）
     */
    private static final long SECOND_ASYNC_WAIT_MS = 1000L;

    /**
     * winget 异步任务超时时间（秒）
     */
    private static final long WINGET_TIMEOUT_SECONDS = 5L;

    public static void main(String[] args) throws Exception {
        CmdAsyncExample example = new CmdAsyncExample();
        boolean passed = example.runTest();
        log.info("[CmdAsyncExample] 自检结果: passed={}", passed);
        System.exit(passed ? EXIT_CODE_SUCCESS : EXIT_CODE_FAILURE);
    }

    /**
     * 自检入口：依次演示 CmdCallback 异步回调、CompletableFuture 链式、winget 异步检测三种场景。
     *
     * @return true 表示三种异步模式都能正常执行
     */
    public boolean runTest() {
        boolean passed = true;
        log.info("========================================");
        log.info("  CmdAsyncExample - Async Execution");
        log.info("========================================");
        try {
            // 1. 异步执行 + CmdCallback 生命周期回调
            printStep("1. 异步回调", "systeminfo | findstr /C:\"OS Name\"");
            CmdExecutors.executeAsync("systeminfo | findstr /C:\"OS Name\"", 10, TimeUnit.SECONDS, new CmdCallback() {
                @Override
                public void onStart(String command) {
                    log.info("  [callback] start: {}", command);
                }

                @Override
                public void onComplete(CmdResult result) {
                    log.info("  [callback] complete: exitCode={}", result.getExitCode());
                    log.info("  [callback] stdout={}", result.getStdout().trim());
                }

                @Override
                public void onTimeout(String command, long timeout, TimeUnit unit) {
                    log.info("  [callback] timeout: {}", command);
                }

                @Override
                public void onError(String command, Throwable throwable) {
                    log.info("  [callback] error: {}", throwable.getMessage());
                }
            });
            Thread.sleep(FIRST_ASYNC_WAIT_MS);
            log.info("");

            // 2. CompletableFuture 链式异步
            printStep("2. CompletableFuture", "echo hello from async");
            CompletableFuture<CmdResult> future = CmdExecutors.executeAsync("echo hello from async", WINGET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            future.thenAccept(result -> {
                log.info("  [future] exitCode={}", result.getExitCode());
                log.info("  [future] stdout={}", result.getStdout().trim());
            }).exceptionally(ex -> {
                log.info("  [future] error: {}", ex.getMessage());
                return null;
            });
            Thread.sleep(SECOND_ASYNC_WAIT_MS);
            log.info("");

            // 3. winget 异步检测
            printStep("3. winget version", "winget --version");
            CompletableFuture<CmdResult> wv = CmdExecutors.executeAsync("winget --version", WINGET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            wv.thenAccept(result -> {
                if (result.getExitCode() == 0) {
                    log.info("  [winget] version={}", result.getStdout().trim());
                } else {
                    log.info("  [winget] not available");
                }
            }).get();

            log.info("");
            log.info("========================================");
            log.info("  ALL DONE");
            log.info("========================================");
        } catch (Exception e) {
            log.error("[CmdAsyncExample] 异常: {}", e.getMessage(), e);
            passed = false;
        }
        return passed;
    }

    private static void printStep(String label, String value) {
        log.info("[CmdAsyncExample] {}: {}", label, value);
    }
}

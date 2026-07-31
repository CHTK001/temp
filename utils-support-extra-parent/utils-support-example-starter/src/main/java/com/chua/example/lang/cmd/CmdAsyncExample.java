package com.chua.example.lang.cmd;

import com.chua.common.support.lang.cmd.CmdCallback;
import com.chua.common.support.lang.cmd.CmdExecutors;
import com.chua.common.support.lang.cmd.CmdResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CmdAsyncExample {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  CmdAsyncExample - Async Execution");
        System.out.println("========================================");

        // 1. 异步执行 + CmdCallback 生命周期回调
        printStep("1. 异步回调", "systeminfo | findstr /C:\"OS Name\"");
        CmdExecutors.executeAsync("systeminfo | findstr /C:\"OS Name\"", 10, TimeUnit.SECONDS, new CmdCallback() {
            @Override
            public void onStart(String command) {
                System.out.println("  [callback] start: " + command);
            }

            @Override
            public void onComplete(CmdResult result) {
                System.out.println("  [callback] complete: exitCode=" + result.getExitCode());
                System.out.println("  [callback] stdout=" + result.getStdout().trim());
            }

            @Override
            public void onTimeout(String command, long timeout, TimeUnit unit) {
                System.out.println("  [callback] timeout: " + command);
            }

            @Override
            public void onError(String command, Throwable throwable) {
                System.out.println("  [callback] error: " + throwable.getMessage());
            }
        });
        Thread.sleep(2000);
        System.out.println();

        // 2. CompletableFuture 链式异步
        printStep("2. CompletableFuture", "echo hello from async");
        CompletableFuture<CmdResult> future = CmdExecutors.executeAsync("echo hello from async", 5, TimeUnit.SECONDS);
        future.thenAccept(result -> {
            System.out.println("  [future] exitCode=" + result.getExitCode());
            System.out.println("  [future] stdout=" + result.getStdout().trim());
        }).exceptionally(ex -> {
            System.out.println("  [future] error: " + ex.getMessage());
            return null;
        });
        Thread.sleep(1000);
        System.out.println();

        // 3. winget 异步检测
        printStep("3. winget version", "winget --version");
        CompletableFuture<CmdResult> wv = CmdExecutors.executeAsync("winget --version", 5, TimeUnit.SECONDS);
        wv.thenAccept(result -> {
            if (result.getExitCode() == 0) {
                System.out.println("  [winget] version=" + result.getStdout().trim());
            } else {
                System.out.println("  [winget] not available");
            }
        }).get();

        System.out.println();
        System.out.println("========================================");
        System.out.println("  ALL DONE");
        System.out.println("========================================");
    }

    private static void printStep(String label, String value) {
        System.out.println("[CmdAsyncExample] " + label + ": " + value);
    }
}

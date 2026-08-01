package com.chua.example.lang.cmd;

import com.chua.common.support.lang.cmd.CmdExecutors;
import com.chua.common.support.lang.cmd.LineCallback;

import java.util.concurrent.TimeUnit;

/**
 * 命令同步执行示例（基于 ConPTY），演示 LineCallback 实时行级回调与 进度条覆盖帧捕获。
 *
 * @author CH
 * @since 4.0.0
 */
public class CmdSyncExample {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  CmdSyncExample - LineCallback 实时回调");
        System.out.println("========================================");

        // 1. ping：每行 Reply 即时回调（已验证实时）
        printStep("1. ping", "ping 127.0.0.1 -n 6 -w 1000");
        CmdExecutors.executeWithOutput("ping 127.0.0.1 -n 6 -w 1000", 30, TimeUnit.SECONDS, new LineCallback() {
            @Override
            public void onLine(String line) {
                System.out.println("  [ping] " + line);
            }
            @Override
            public void onComplete(int exitCode) {
                System.out.println("  [ping] done, exitCode=" + exitCode);
            }
        });
        System.out.println();

        // 2. curl 进度条（\r 实时覆盖帧 → onLine 逐帧回调）
        //  ConPTY 使子进程认为它有真实控制台，从而输出 \r 进度条
        printStep("2. curl 进度条", "curl --progress-bar http://speedtest.tele2.net/1MB.zip -o NUL --max-time 10");
        CmdExecutors.executeWithOutput("curl --progress-bar http://speedtest.tele2.net/1MB.zip -o NUL --max-time 10", 30, TimeUnit.SECONDS, new LineCallback() {
            @Override
            public void onLine(String line) {
                System.out.println("  [curl] " + line);
            }
            @Override
            public void onComplete(int exitCode) {
                System.out.println("  [curl] done, exitCode=" + exitCode);
            }
        });
        System.out.println();

        // 3. 卸载 Redis → 安装 Redis（全程逐行回调）
        printStep("3. 卸载 Redis", "winget uninstall Redis.Redis");
        CmdExecutors.executeWithOutput("winget uninstall Redis.Redis", 60, TimeUnit.SECONDS, new LineCallback() {
            @Override
            public void onLine(String line) {
                System.out.println("  [uninstall] " + line);
            }
            @Override
            public void onComplete(int exitCode) {
                System.out.println("  [uninstall] done, exitCode=" + exitCode);
            }
        });
        System.out.println();

        printStep("4. 安装 Redis", "winget install --id Redis.Redis --accept-package-agreements");
        CmdExecutors.executeWithOutput("winget install --id Redis.Redis --accept-package-agreements", 600, TimeUnit.SECONDS, new LineCallback() {
            @Override
            public void onLine(String line) {
                System.out.println("  [install] " + line);
            }
            @Override
            public void onComplete(int exitCode) {
                System.out.println("  [install] done, exitCode=" + exitCode);
            }
        });
        System.out.println();

        System.out.println("========================================");
        System.out.println("  ALL DONE");
        System.out.println("========================================");
    }

    private static void printStep(String label, String value) {
        System.out.println("[CmdSyncExample] " + label + ": " + value);
    }
}

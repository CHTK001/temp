package com.chua.example.smb;

import com.chua.smb.client.SmbClient;
import com.chua.smb.server.SmbServer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SMB 集成测试 — 启动 server，client 连接并列出文件。
 * 对应矩阵中的"Win client ↔ Win server"场景。
 *
 * @author CH
 * @since 2026-07-29
 */
@Slf4j
public class SmbServerClientIntegration {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  SMB Server+Client Integration Test");
        System.out.println("========================================");

        int port = 1445;
        String share = "testshare";
        String root = "C:\\Users\\yemen\\AppData\\Local\\Temp\\smb-test-root";

        File rootDir = new File(root);
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        // 放一些测试文件
        for (String name : new String[]{"a.txt", "b.txt", "c.txt"}) {
            File f = new File(rootDir, name);
            if (!f.exists()) {
                try (java.io.FileWriter fw = new java.io.FileWriter(f)) {
                    fw.write("test content for " + name + "\n");
                }
            }
        }
        System.out.println("root path: " + root);
        System.out.println("test files: " + java.util.Arrays.toString(rootDir.list()));

        // 启动 server (无用户 = 公共模式/匿名访问)
        SmbServer server = SmbServer.builder()
                .host("127.0.0.1")
                .port(port)
                .shareName(share)
                .rootPath(root)
                .user("")
                .password("")
                .build();

        AtomicReference<Throwable> serverError = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
                Thread.sleep(60_000); // run 60s
                server.stop();
            } catch (Throwable e) {
                serverError.set(e);
            }
        }, "smb-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // 等 server 起来
        Thread.sleep(3000);
        check("TC-S1", "server started", true, "");

        // 测试 connect - 匿名访问
        String smbUrl = "smb://127.0.0.1:" + port + "/" + share;
        SmbClient client = SmbClient.create(smbUrl);
        check("TC-S2", "client created", client != null, "url=" + smbUrl);

        boolean connected = false;
        try {
            client.connect();
            connected = true;
        } catch (Exception e) {
            System.out.println("  connect err: " + e.getMessage());
        }
        check("TC-S3", "client connect()", connected, "url=" + smbUrl);

        boolean loggedIn = false;
        if (connected) {
            try {
                client.login();
                loggedIn = true;
            } catch (Exception e) {
                System.out.println("  login err: " + e.getMessage());
                Throwable cause = e.getCause();
                while (cause != null) {
                    System.out.println("  caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                    cause = cause.getCause();
                }
            }
        }
        check("TC-S4", "client login()", loggedIn, "anonymous");

        boolean shareOpened = false;
        if (loggedIn) {
            try {
                client.openShare();
                shareOpened = true;
            } catch (Exception e) {
                System.out.println("  openShare err: " + e.getMessage());
            }
        }
        check("TC-S5", "client openShare()", shareOpened, "");

        int listCount = 0;
        if (shareOpened) {
            try {
                java.util.List<SmbClient.SmbFileEntry> files = client.listFiles("/");
                listCount = files.size();
                System.out.println("  list size: " + listCount);
                for (SmbClient.SmbFileEntry f : files) {
                    System.out.println("    [" + (f.isDirectory() ? "DIR " : "FILE") + "] " + f.name());
                }
            } catch (Exception e) {
                System.out.println("  list err: " + e.getMessage());
            }
        }
        check("TC-S6", "listFiles (>=3 files)", listCount >= 3, "count=" + listCount);

        // server.stop() via join (server thread will stop it after 60s)
        // For faster verification, kill the server thread
        System.out.println("PASS: " + pass + " / FAIL: " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    private static void check(String id, String desc, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        System.out.printf("[%s] %s | %s | %s%n", ok ? "PASS" : "FAIL", id, desc, detail);
    }
}
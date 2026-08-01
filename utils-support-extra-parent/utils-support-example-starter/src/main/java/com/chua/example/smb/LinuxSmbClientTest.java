package com.chua.example.smb;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.common.SmbPath;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-platform test: Win client → Linux server (172.16.0.40:1447).
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class LinuxSmbClientTest {
    static int pass = 0, fail = 0;

    public static void main(String[] args) throws Exception {
        String host = "172.16.0.40";
        int port = 1450;
        String share = "testshare";

        System.out.println("========================================");
        System.out.println("  Win Client -> Linux Server (Cross-Platform)");
        System.out.println("========================================");
        System.out.println("host: " + host + ":" + port + "/" + share);

        SMBClient client;
        try {
            client = new SMBClient();
            pass("X1 SMBClient created");
        } catch (Exception e) {
            fail("X1 SMBClient create", e); return;
        }

        Connection conn;
        try {
            conn = client.connect(host, port);
            pass("X2 client.connect(" + host + ":" + port + ")");
        } catch (Exception e) {
            fail("X2 client.connect", e);
            client.close();
            return;
        }

        Session session;
        try {
            AuthenticationContext ac = AuthenticationContext.anonymous();
            session = conn.authenticate(ac);
            pass("X3 anonymous login");
        } catch (Exception e) {
            fail("X3 anonymous login", e);
            conn.close();
            client.close();
            return;
        }

        DiskShare shareObj;
        try {
            shareObj = (DiskShare) session.connectShare(share);
            pass("X4 connectShare(" + share + ")");
        } catch (Exception e) {
            fail("X4 connectShare", e);
            session.close(); conn.close(); client.close();
            return;
        }

        com.chua.smb.client.SmbClient c2 = com.chua.smb.client.SmbClient.create("smb://" + host + ":" + port + "/" + share);
        try {
            c2.connect();
            pass("X2a high-level connect()");
        } catch (Exception e) {
            fail("X2a high-level connect", e);
        }

        try {
            List<com.chua.smb.client.SmbClient.SmbFileEntry> files = c2.listFiles("/");
            System.out.println("  list size: " + files.size());
            for (com.chua.smb.client.SmbClient.SmbFileEntry f : files) {
                System.out.println("    [FILE] " + f.name());
            }
            if (files.size() >= 3) {
                pass("X5 listFiles (>=3) count=" + files.size());
            } else {
                fail("X5 listFiles (expected >=3 got " + files.size() + ")", null);
            }
        } catch (Exception e) {
            fail("X5 listFiles", e);
        }

        try {
            c2.close();
            pass("X6 close");
        } catch (Exception e) {
            fail("X6 close", e);
        }

        shareObj.close();
        session.close();
        conn.close();
        client.close();

        System.out.println();
        System.out.println("PASS: " + pass + " / FAIL: " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    static void pass(String n) { pass++; System.out.println("[PASS] " + n + " | "); }
    static void fail(String n, Throwable t) {
        fail++;
        System.out.println("[FAIL] " + n);
        if (t != null) { System.out.println("  err: " + t.getClass().getSimpleName() + ": " + t.getMessage()); }
    }
}
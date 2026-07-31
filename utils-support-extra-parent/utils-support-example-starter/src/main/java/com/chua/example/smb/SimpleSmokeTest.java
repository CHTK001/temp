package com.chua.example.smb;
import com.chua.smb.client.SmbClient;
import java.util.List;
public class SimpleSmokeTest {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 1445;
        String share = args.length > 2 ? args[2] : "testshare";
        SmbClient c = SmbClient.create("smb://" + host + ":" + port + "/" + share);
        try {
            c.connect();
            System.out.println("connect OK");
            List<SmbClient.SmbFileEntry> files = c.listFiles("/");
            System.out.println("list size=" + files.size());
            for (SmbClient.SmbFileEntry f : files) System.out.println("  " + f.name());
            c.close();
            System.out.println("PASS");
            System.exit(0);
        } catch (Exception e) {
            System.out.println("FAIL: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
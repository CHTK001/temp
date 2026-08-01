package com.chua.example.smb;

import com.chua.smb.server.SmbServer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * Long-running SMB server on a configurable port.
 * Usage: java -Dsmb.host=0.0.0.0 -Dsmb.port=1448 -Dsmb.share=testshare -Dsmb.root=PATH com.chua.example.smb.SmbServerLong
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class SmbServerLong {
    public static void main(String[] args) throws Exception {
        String root = System.getProperty("smb.root",
                System.getProperty("java.io.tmpdir") + File.separator + "smb-test-root");
        String share = System.getProperty("smb.share", "example-share");
        String host = System.getProperty("smb.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("smb.port", "1445"));
        long duration = Long.parseLong(System.getProperty("smb.duration", "2000"));

        File rootDir = new File(root);
        if (!rootDir.exists()) rootDir.mkdirs();

        log.info("SMB server starting host={} port={} share={} root={} duration={}ms",
                host, port, share, root, duration);

        SmbServer server = SmbServer.builder()
                .host(host).port(port).shareName(share)
                .rootPath(root).user("").password("").build();

        server.start();
        log.info("SMB server started, sleeping {} ms", duration);
        Thread.sleep(duration);
        log.info("Stopping server");
        server.stop();
    }
}
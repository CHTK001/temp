package com.chua.example.smb;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.ProcessBuilder;

/**
 * Win SMB server launcher with full classpath from cp.txt.
 *
 * @author CH
 * @since 4.0.0
 */
public class WinSmbServerLauncher {
    public static void main(String[] args) throws Exception {
        String root = System.getProperty("smb.root",
                System.getProperty("java.io.tmpdir") + File.separator + "smb-test-root");
        String share = System.getProperty("smb.share", "example-share");
        String host = System.getProperty("smb.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("smb.port", "1445"));
        long duration = Long.parseLong(System.getProperty("smb.duration", "2000"));

        File rootDir = new File(root);
        if (!rootDir.exists()) rootDir.mkdirs();

        String workdir = "G:\\work\\utils-support-parent-starter\\utils-support-extra-parent\\utils-support-example-starter";
        String cpTxt;
        try (Stream<String> lines = Files.lines(new File(workdir, "cp.txt").toPath())) {
            cpTxt = lines.collect(Collectors.joining());
        }
        String cp = workdir + ";" + workdir + "\\target\\classes;"
                + "G:\\work\\utils-support-parent-starter\\utils-support-network-parent\\utils-support-smb-starter\\target\\classes;"
                + "G:\\repo\\com\\hierynomus\\smbj\\0.14.0\\smbj-0.14.0.jar;"
                + "G:\\repo\\com\\hierynomus\\asn-one\\0.6.0\\asn-one-0.6.0.jar;"
                + "G:\\repo\\net\\engio\\mbassador\\1.3.0\\mbassador-1.3.0.jar;"
                + "G:\\repo\\org\\bouncycastle\\bcprov-jdk18on\\1.78\\bcprov-jdk18on-1.78.jar;"
                + cpTxt;

        String[] cmd = new String[]{
                "java",
                "--enable-native-access=ALL-UNNAMED",
                "-Dsmb.host=" + host,
                "-Dsmb.host=0.0.0.0",
                "-Dsmb.port=1448",
                "-Dsmb.share=testshare",
                "-Dsmb.root=C:\\Users\\yemen\\AppData\\Local\\Temp\\smb-test-root-win",
                "-Dsmb.duration=600000",
                "-cp", cp,
                "com.chua.example.smb.SmbServerLong"
        };

        System.out.println("Spawning: " + String.join(" ", cmd).substring(0, Math.min(300, String.join(" ", cmd).length())));
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectOutput(new File("C:\\Users\\yemen\\AppData\\Local\\Temp\\smb_win_child.log"))
                .redirectError(new File("C:\\Users\\yemen\\AppData\\Local\\Temp\\smb_win_child_err.log"));
        pb.environment().put("PATH", System.getenv("PATH"));
        Process p = pb.start();
        System.out.println("Child PID: " + p.pid());

        // Wait for the server to come up (or fail)
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000);
            if (p.isAlive()) {
                // Check if 1448 is listening via netstat
                Process np = new ProcessBuilder("netstat", "-an").redirectOutput(new File("C:\\Users\\yemen\\AppData\\Local\\Temp\\netstat.txt")).start();
                np.waitFor();
                String ns = new String(Files.readAllBytes(new File("C:\\Users\\yemen\\AppData\\Local\\Temp\\netstat.txt").toPath()));
                if (ns.contains(":" + port + " ") && ns.contains("LISTENING")) {
                    System.out.println("Port " + port + " is LISTENING");
                    break;
                }
            } else {
                System.out.println("Child process exited");
                break;
            }
        }

        // Sleep a bit and then check final status
        Thread.sleep(2000);
        System.out.println("Child alive: " + p.isAlive());
    }
}
package com.chua.example.network.ftp;

import com.chua.common.support.network.ftp.FtpConfig;
import com.chua.common.support.network.ftp.FtpServer;
import com.chua.common.support.network.ftp.FtpsServer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * FTP / FTPS 服务器综合示例 — 基于 FtpServer / FtpsServer。
 *
 * <p>演示明文 FTP 与隐式 FTPS 两种服务器启动方式，涵盖匿名访问、目录权限、
 * 被动模式数据端口范围等核心配置。示例直接常驻运行，支持 Ctrl+C 优雅退出。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   # 启动明文 FTP 服务器（端口 2121，匿名只读，根目录 ./ftp-home）
 *   java FtpServerExample --mode ftp
 *
 *   # 启动隐式 FTPS 服务器（端口 990，自签名证书，根目录 ./ftps-home）
 *   java FtpServerExample --mode ftps
 *
 *   # 自定义端口与根目录
 *   java FtpServerExample --mode ftp --port 2121 --home ./myftp
 * </pre>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public class FtpServerExample {

    /** 私有构造，防止实例化 */
    private FtpServerExample() { }

    /**
     * 默认 FTP 控制端口
     */
    private static final int DEFAULT_FTP_PORT = 2121;

    /**
     * 默认 FTPS 控制端口（隐式）
     */
    private static final int DEFAULT_FTPS_PORT = 990;

    /**
     * 常驻锁存器，阻止主线程退出
     */
    private static final CountDownLatch STOP_LATCH = new CountDownLatch(1);

    /**
     * 程序入口。
     *
     * @param args 命令行参数（--mode / --port / --home）
     */
    public static void main(String[] args) {
        String mode = getArg(args, "mode", "ftp");
        int port = Integer.parseInt(getArg(args, "port", mode.equalsIgnoreCase("ftps")
                ? String.valueOf(DEFAULT_FTPS_PORT)
                : String.valueOf(DEFAULT_FTP_PORT)));
        String home = getArg(args, "home", mode.equalsIgnoreCase("ftps") ? "ftps-home" : "ftp-home");

        Runtime.getRuntime().addShutdownHook(new Thread(STOP_LATCH::countDown, "ftp-shutdown-hook"));

        // 构建 FTP 配置：匿名只读，被动模式端口范围 49152~65535
        FtpConfig config = FtpConfig.builder()
                .controlPort(port)
                .host("0.0.0.0")
                .homeDirectory(new File(home))
                .anonymousEnabled(true)
                .anonymousWriteEnabled(false)
                .passivePortRange(49152, 65535)
                .build();

        if ("ftps".equalsIgnoreCase(mode)) {
            // 隐式 FTPS：自动生成自签名证书
            config.setSslEnabled(true);
            config.setSelfSignedAuto(true);
            FtpsServer server = new FtpsServer(config);
            server.start();
            log.info("[FTPS] 服务器就绪: 0.0.0.0:{} 根目录={} 匿名只读", port, home);
        } else {
            FtpServer server = new FtpServer(config);
            server.start();
            log.info("[FTP] 服务器就绪: 0.0.0.0:{} 根目录={} 匿名只读", port, home);
        }

        try {
            STOP_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析 --key=value 形式的命令行参数。
     *
     * @param args 命令行参数数组
     * @param key  参数键
     * @param def  默认值
     * @return 参数值或默认值
     */
    private static String getArg(String[] args, String key, String def) {
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                int eq = arg.indexOf('=');
                if (key.equals(arg.substring(2, eq))) {
                    return arg.substring(eq + 1);
                }
            }
        }
        return def;
    }
}

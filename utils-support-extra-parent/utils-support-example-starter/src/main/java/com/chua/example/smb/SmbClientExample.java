package com.chua.example.smb;

import com.chua.smb.client.SmbClient;
import lombok.extern.slf4j.Slf4j;

/**
 * SMB 客户端示例 — 验证 SMBJ 客户端连接与目录浏览。
 *
 * <p>默认连接本地 Windows 共享。可通过系统属性覆盖目标。</p>
 *
 * <pre>
 * # 默认 (本地 C$)
 * mvn exec:java -pl utils-support-extra-parent/utils-support-example-starter \
 *     -Dexec.mainClass=com.chua.example.smb.SmbClientExample
 *
 * # 远程服务器
 * mvn exec:java -pl utils-support-extra-parent/utils-support-example-starter \
 *     -Dexec.mainClass=com.chua.example.smb.SmbClientExample \
 *     -Dsmb.url=smb://admin:pass@192.168.1.100:445/data
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SmbClientExample {

    public static void main(String[] args) {
        String smbUrl = System.getProperty("smb.url",
                "smb://" + System.getProperty("user.name", "guest")
                        + "@127.0.0.1:445/C$");

        log.info("========== SMB 客户端示例 ==========");
        log.info("目标: {}", smbUrl);

        // 1. 创建并连接
        SmbClient client = SmbClient.create(smbUrl);
        log.info(">>> SmbClient 创建完成");

        try {
            client.connect();
            log.info("    ✅ connect() 成功");
        } catch (Exception e) {
            log.error("    ❌ connect() 失败: {}", e.getMessage());
            log.info("----------------------------------------");
            log.info("提示: 需要有效的 SMB 服务器");
            log.info("  - Windows: 确保 SMB 共享开启 (net share)");
            log.info("  - Linux:   可启动 SmbServerExample 服务端");
            log.info("  - 配置:    -Dsmb.url=smb://user:pass@host:port/share");
            log.info("----------------------------------------");
            System.exit(1);
        }

        try {
            client.login();
            log.info("    ✅ login() 成功");
        } catch (Exception e) {
            log.warn("    ⚠ login() 认证失败: {}", e.getMessage());
        }

        try {
            client.openShare();
            log.info("    ✅ openShare() 成功");

            // 列目录
            java.util.List<SmbClient.SmbFileEntry> files = client.listFiles("/");
            log.info(">>> 根目录: {} 项", files.size());
            int maxShow = 15;
            int count = 0;
            for (SmbClient.SmbFileEntry f : files) {
                if (count++ >= maxShow) {
                    log.info("    ... 省略 {} 项", files.size() - maxShow);
                    break;
                }
                log.info("    [{}] {}  ({} bytes, {})",
                        f.isDirectory() ? "DIR " : "FILE",
                        f.name(),
                        f.size(),
                        new java.util.Date(f.lastModified()));
            }
        } catch (Exception e) {
            log.warn("    ⚠ openShare/listFiles 失败: {}", e.getMessage());
        }

        log.info("========== SMB 客户端示例完成 ==========");
    }
}

package com.chua.example.smb;

import com.chua.smb.server.SmbServer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * SMB 服务器示例 — 验证 Rust 原生库加载与服务启停。
 *
 * <p>注意：
 * <ul>
 *   <li>SMB 原生库 (rust_smb_server) 依赖 Unix 系统调用，仅在 Linux/macOS 上可用。</li>
 *   <li>SmbServer 继承 AbstractServer，需要 Spring 上下文，此处仅验证构建。</li>
 *   <li>Windows 平台上 native DLL 不可用，启动会抛出 UnsatisfiedLinkError。</li>
 * </ul>
 *
 * <pre>
 * mvn exec:java -pl utils-support-extra-parent/utils-support-example-starter \
 *     -Dexec.mainClass=com.chua.example.smb.SmbServerExample
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SmbServerExample {

    public static void main(String[] args) {
        String root = System.getProperty("smb.root",
                System.getProperty("java.io.tmpdir") + File.separator + "smb-test-root");
        String share = System.getProperty("smb.share", "example-share");
        String host = System.getProperty("smb.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("smb.port", "1445"));
        long duration = Long.parseLong(System.getProperty("smb.duration", "2000"));

        // 确保 root 目录存在
        File rootDir = new File(root);
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }

        log.info("========== SMB 服务器示例 ==========");
        log.info("配置: host={}, port={}, share={}, root={}", host, port, share, root);

        try {
            SmbServer server = SmbServer.builder()
                    .host(host)
                    .port(port)
                    .shareName(share)
                    .rootPath(root)
                    .user("")
                    .password("")
                    .build();

            log.info(">>> SmbServer build 成功");

            try {
                server.start();
                log.info(">>> 服务器启动成功 (仅 Linux/macOS)");
                Thread.sleep(duration);
                server.stop();
                log.info(">>> 服务器正常停止");
            } catch (UnsatisfiedLinkError e) {
                log.warn(">>> Windows 平台: native DLL 不可用: {}", e.getMessage());
                log.info(">>> 提示: 请在 Linux/macOS 环境测试完整 SMB 服务功能");
            } catch (Exception e) {
                log.warn(">>> 启动异常 (平台限制): {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error(">>> SmbServer 构建失败: {}", e.getMessage());
            log.info(">>> 提示: SmbServer 需要 Spring 上下文");
            log.info(">>> 当前为 standalone 测试，构建失败属于预期行为");
        }

        log.info("========== SMB 服务器示例完成 ==========");
    }
}

package com.chua.example.onnx;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WebP 解码探测示例：验证当前 JVM 的 ImageIO 是否能解码 WebP。
 *
 * <h2>用法</h2>
 * <pre>
 *   java WebpDecodeExample G:/images/1.webp G:/images/2.webp
 * </pre>
 *
 * <p>退出码：{@code 0}=全部文件解码成功，{@code 1}=存在解码失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class WebpDecodeExample {

    private WebpDecodeExample() {
    }

    /**
     * 入口。
     *
     * @param args 待探测的图片路径列表
     * @throws Exception 文件读取失败
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法: java WebpDecodeExample <file1> [file2] ...");
            System.exit(1);
        }
        int ok = 0;
        for (String n : args) {
            byte[] b = Files.readAllBytes(Path.of(n));
            try {
                var img = ImageIO.read(new ByteArrayInputStream(b));
                System.out.println(n + " ImageIO: "
                        + (img == null ? "null" : img.getWidth() + "x" + img.getHeight()));
                if (img != null) {
                    ok++;
                } else {
                    System.err.println("[FAIL] 解码为 null: " + n);
                }
            } catch (Exception e) {
                System.err.println("[FAIL] " + n + " ImageIO ERROR: " + e.getMessage());
            }
        }
        if (ok != args.length) {
            System.exit(1);
        }
        System.out.println("[PASS] " + ok + "/" + args.length + " 解码成功");
        System.exit(0);
    }
}

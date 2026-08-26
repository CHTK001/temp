package com.chua.example.ai.rag;

import com.chua.common.support.ai.rag.LocalFileUploadProvider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link LocalFileUploadProvider} 本地文件上传提供者示例 — 由同名单元测试改写。
 *
 * <p>覆盖 upload/read/delete 全链路：往返读写、不存在读取、删除清理、
 * 重复删除、多文档隔离。全部走本地临时目录，无外部依赖。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java LocalFileUploadProviderExample            # 运行全部自检
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public final class LocalFileUploadProviderExample {

    /**
     * 临时根目录：java.io.tmpdir/test-output/common-misc
     */
    private static final String TEMP_ROOT = "test-output" + File.separator + "common-misc";

    /**
     * 退出码：成功
     */
    private static final int EXIT_CODE_SUCCESS = 0;

    /**
     * 退出码：失败
     */
    private static final int EXIT_CODE_FAILURE = 1;

    /**
     * 防止实例化工具类。
     */
    private LocalFileUploadProviderExample() {
    }

    // ==================== main ====================

    /**
     * 独立入口：运行全部场景，任一失败以退出码 1 结束。
     *
     * @param args 命令行参数（未使用）
     * @throws IOException 临时目录创建失败
     */
    public static void main(String[] args) throws IOException {
        Path tempDir = createTempDir("upload-provider-example-");
        boolean passed;
        try {
            LocalFileUploadProvider provider = new LocalFileUploadProvider(tempDir.toString());
            passed = testUploadAndRead(provider);
            passed &= testReadNonExistent(provider);
            passed &= testDelete(provider);
            passed &= testDeleteNonExistent(provider);
            passed &= testMultipleUploads(provider);
            provider.delete("test-doc");
        } finally {
            deleteRecursively(tempDir.toFile());
        }
        if (!passed) {
            System.out.println("[FAIL] LocalFileUploadProvider 存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        } else {
            System.out.println("[PASS] LocalFileUploadProvider 全部场景通过");
        }
        System.exit(EXIT_CODE_SUCCESS);
    }

    // ==================== 场景 ====================

    /**
     * 场景 1：上传后按 fileId 读回，内容逐字节一致。
     *
     * @param provider 上传提供者
     * @return 通过返回 true
     */
    private static boolean testUploadAndRead(LocalFileUploadProvider provider) {
        byte[] data = "Hello RAG document content".getBytes(StandardCharsets.UTF_8);
        try {
            String fileId = provider.upload("doc-001", "test.txt", data);
            boolean ok = "doc-001".equals(fileId);
            byte[] readBack = provider.read("doc-001");
            ok &= readBack != null && Arrays.equals(data, readBack);
            print("upload/read 往返一致", ok);
            return ok;
        } catch (Exception e) {
            return fail("upload/read 往返一致", e);
        }
    }

    /**
     * 场景 2：读取不存在的 fileId 返回 null。
     *
     * @param provider 上传提供者
     * @return 通过返回 true
     */
    private static boolean testReadNonExistent(LocalFileUploadProvider provider) {
        boolean ok = provider.read("non-existent") == null;
        print("read 不存在返回 null", ok);
        return ok;
    }

    /**
     * 场景 3：删除已上传文档后不可再读。
     *
     * @param provider 上传提供者
     * @return 通过返回 true
     */
    private static boolean testDelete(LocalFileUploadProvider provider) {
        byte[] data = "test content".getBytes(StandardCharsets.UTF_8);
        try {
            provider.upload("doc-delete", "test.txt", data);
            boolean removed = provider.delete("doc-delete");
            boolean ok = removed && provider.read("doc-delete") == null;
            print("delete 后不可读", ok);
            return ok;
        } catch (Exception e) {
            return fail("delete 后不可读", e);
        }
    }

    /**
     * 场景 4：删除不存在的 fileId 返回 false。
     *
     * @param provider 上传提供者
     * @return 通过返回 true
     */
    private static boolean testDeleteNonExistent(LocalFileUploadProvider provider) {
        boolean ok = !provider.delete("non-existent");
        print("delete 不存在返回 false", ok);
        return ok;
    }

    /**
     * 场景 5：多文档上传互不干扰，删除其一不影响其余。
     *
     * @param provider 上传提供者
     * @return 通过返回 true
     */
    private static boolean testMultipleUploads(LocalFileUploadProvider provider) {
        try {
            provider.upload("doc-a", "a.txt", "content-a".getBytes(StandardCharsets.UTF_8));
            provider.upload("doc-b", "b.txt", "content-b".getBytes(StandardCharsets.UTF_8));
            provider.upload("doc-c", "c.txt", "content-c".getBytes(StandardCharsets.UTF_8));
            boolean ok = Arrays.equals("content-a".getBytes(StandardCharsets.UTF_8), provider.read("doc-a"));
            ok &= Arrays.equals("content-b".getBytes(StandardCharsets.UTF_8), provider.read("doc-b"));
            ok &= Arrays.equals("content-c".getBytes(StandardCharsets.UTF_8), provider.read("doc-c"));
            provider.delete("doc-b");
            ok &= provider.read("doc-b") == null;
            ok &= provider.read("doc-a") != null;
            print("多文档隔离与选择性删除", ok);
            return ok;
        } catch (Exception e) {
            return fail("多文档隔离与选择性删除", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 输出单场景通过结果。
     *
     * @param name 场景名
     * @param ok   是否通过
     */
    private static void print(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    /**
     * 输出单场景异常失败结果。
     *
     * @param name 场景名
     * @param e    异常
     * @return 恒为 false
     */
    private static boolean fail(String name, Exception e) {
        System.out.println("[FAIL] " + name + ": " + e.getMessage());
        return false;
    }

    /**
     * 在 java.io.tmpdir/test-output/common-misc 下创建唯一临时目录。
     *
     * @param prefix 目录名前缀
     * @return 已创建的目录路径
     * @throws IOException 创建失败
     */
    private static Path createTempDir(String prefix) throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), TEMP_ROOT);
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix);
    }

    /**
     * 递归删除文件或目录。
     *
     * @param file 目标文件/目录
     */
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

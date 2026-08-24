package com.chua.example.ai.rag;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.splitter.TextSplitter;
import com.chua.common.support.ai.splitter.TextSplitterProvider;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link RagClient} 文档管理链路本地示例 — 由集成测试改写。
 *
 * <p>覆盖 uploadDocument / deleteDocument / updateDocument / listDocuments /
 * readDocumentContent 全链路，默认全走 memory（本地/内存）实现，零外部依赖。</p>
 *
 * <p>问答链路依赖外部 LLM 服务：<b>默认跳过</b>并打印 {@code [SKIP] remote-disabled}，
 * 追加 {@code --remote} 开关才走远端（需 {@code --key}，可选 {@code --base-url} / {@code --model}）。</p>
 *
 * <h2>用法</h2>
 * <pre>
 *   java RagClientLocalExample                     # 仅本地/内存路径
 *   java RagClientLocalExample --remote --key=sk-xxx
 *   java RagClientLocalExample --remote --key=sk-xxx --base-url=https://api.deepseek.com --model=deepseek-chat
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class RagClientLocalExample {

    /**
     * 临时根目录：java.io.tmpdir/test-output/common-misc
     */
    private static final String TEMP_ROOT = "test-output" + File.separator + "common-misc";

    /**
     * 分块大小
     */
    private static final int CHUNK_SIZE = 500;

    /**
     * 分块重叠
     */
    private static final int CHUNK_OVERLAP = 50;

    /**
     * 检索 Top-K
     */
    private static final int TOP_K = 3;

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
    private RagClientLocalExample() {
    }

    // ==================== main ====================

    /**
     * 独立入口：先跑本地场景，再按 --remote 决定是否跑远端问答。
     *
     * @param args 命令行参数：--remote --key=xxx --base-url=xxx --model=xxx
     * @throws IOException 临时目录创建失败
     */
    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        Path tempDir = createTempDir("rag-local-example-");
        boolean passed;
        try {
            passed = testUploadReturnsFileId(tempDir, options);
            passed &= testDeleteRemovesVectorAndFile(tempDir, options);
            passed &= testUpdateDocument(tempDir, options);
            passed &= testListAndCount(tempDir, options);
            passed &= testReadDocumentContent(tempDir, options);
            passed &= testRemoteChatQuery(tempDir, options);
        } finally {
            deleteRecursively(tempDir.toFile());
        }
        if (!passed) {
            System.out.println("[FAIL] RagClient 本地链路存在失败场景");
            System.exit(EXIT_CODE_FAILURE);
        } else {
            System.out.println("[PASS] RagClient 本地链路全部场景通过");
        }
        System.exit(EXIT_CODE_SUCCESS);
    }

    // ==================== 场景 ====================

    /**
     * 场景 1：上传返回非空 fileId，状态 READY 且已分块。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return 通过返回 true
     */
    private static boolean testUploadReturnsFileId(Path tempDir, Options options) {
        byte[] data = "这是一份测试文档，关于RAG检索增强生成的介绍".getBytes(StandardCharsets.UTF_8);
        try {
            RagClient client = createClient(tempDir, options);
            try {
                RagDocument doc = client.uploadDocument("test.txt", data);
                boolean ok = doc.id() != null;
                ok &= "test.txt".equals(doc.fileName());
                ok &= "READY".equals(doc.status());
                ok &= doc.chunkCount() > 0;
                print("uploadDocument 返回 READY 文档", ok);
                return ok;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            return fail("uploadDocument 返回 READY 文档", e);
        }
    }

    /**
     * 场景 2：删除后文档消失且落盘文件被清理。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return 通过返回 true
     */
    private static boolean testDeleteRemovesVectorAndFile(Path tempDir, Options options) {
        byte[] data = "删除测试文档内容".getBytes(StandardCharsets.UTF_8);
        try {
            RagClient client = createClient(tempDir, options);
            try {
                String docId = client.uploadDocument("delete-test.txt", data).id();
                boolean removed = client.deleteDocument(docId);
                boolean gone = client.documentCount() == 0;
                gone &= client.listDocuments(1, 10).stream().noneMatch(d -> d.id().equals(docId));
                boolean fileCleaned = true;
                Path filesDir = tempDir.resolve("files");
                if (Files.exists(filesDir)) {
                    try (var stream = Files.list(filesDir)) {
                        fileCleaned = stream.noneMatch(p -> p.getFileName().toString().startsWith(docId));
                    }
                }
                boolean ok = removed && gone && fileCleaned;
                print("deleteDocument 清理向量与文件", ok);
                return ok;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            return fail("deleteDocument 清理向量与文件", e);
        }
    }

    /**
     * 场景 3：更新文档保持 docId 不变且状态 READY。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return 通过返回 true
     */
    private static boolean testUpdateDocument(Path tempDir, Options options) {
        byte[] oldData = "这是旧版本的内容，需要被替换".getBytes(StandardCharsets.UTF_8);
        byte[] newData = "这是新版本的内容，更加详细和完整".getBytes(StandardCharsets.UTF_8);
        try {
            RagClient client = createClient(tempDir, options);
            try {
                String docId = client.uploadDocument("update-test.txt", oldData).id();
                RagDocument newDoc = client.updateDocument(docId, "update-test.txt", newData);
                boolean ok = newDoc != null;
                ok &= docId.equals(newDoc.id());
                ok &= "READY".equals(newDoc.status());
                print("updateDocument 原位替换", ok);
                return ok;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            return fail("updateDocument 原位替换", e);
        }
    }

    /**
     * 场景 4：documentCount 与 listDocuments 分页一致。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return 通过返回 true
     */
    private static boolean testListAndCount(Path tempDir, Options options) {
        try {
            RagClient client = createClient(tempDir, options);
            try {
                client.uploadDocument("doc1.txt", "文档一的内容".getBytes(StandardCharsets.UTF_8));
                client.uploadDocument("doc2.txt", "文档二的内容".getBytes(StandardCharsets.UTF_8));
                client.uploadDocument("doc3.txt", "文档三的内容".getBytes(StandardCharsets.UTF_8));
                boolean ok = client.documentCount() == 3;
                List<RagDocument> page1 = client.listDocuments(1, 2);
                List<RagDocument> page2 = client.listDocuments(2, 2);
                List<RagDocument> page3 = client.listDocuments(3, 2);
                ok &= page1.size() == 2;
                ok &= page2.size() == 1;
                ok &= page3.isEmpty();
                print("listDocuments 分页正确", ok);
                return ok;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            return fail("listDocuments 分页正确", e);
        }
    }

    /**
     * 场景 5：读取内容包含上传原文。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return 通过返回 true
     */
    private static boolean testReadDocumentContent(Path tempDir, Options options) {
        byte[] data = "读取测试文档的原始内容".getBytes(StandardCharsets.UTF_8);
        try {
            RagClient client = createClient(tempDir, options);
            try {
                String docId = client.uploadDocument("read-test.txt", data).id();
                String readBack = client.readDocumentContent(docId);
                boolean ok = readBack != null && readBack.contains("读取测试");
                print("readDocumentContent 读回原文", ok);
                return ok;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            return fail("readDocumentContent 读回原文", e);
        }
    }

    /**
     * 场景 6：远端 LLM 问答。默认跳过；加 --remote 才真实调用外部服务。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return 通过（或按预期跳过）返回 true
     */
    private static boolean testRemoteChatQuery(Path tempDir, Options options) {
        if (!options.remote()) {
            System.out.println("[SKIP] remote-disabled 远端问答依赖外部 LLM 服务，加 --remote 启用");
            return true;
        }
        try {
            RagClient client = createClient(tempDir, options);
            try {
                client.uploadDocument("qa.txt", "RAG 检索增强生成用于结合检索与生成能力"
                        .getBytes(StandardCharsets.UTF_8));
                var response = client.query("什么是RAG？");
                boolean ok = response != null && response.answer() != null && !response.answer().isBlank();
                print("远端问答返回非空回答", ok);
                return ok;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            return fail("远端问答返回非空回答", e);
        }
    }

    // ==================== 构建辅助 ====================

    /**
     * 创建 memory 存储的 RAG 客户端；--remote 时 chat 走外部 LLM，其余仍为本地实现。
     *
     * @param tempDir 上传根目录
     * @param options 命令行选项
     * @return RAG 客户端
     */
    private static RagClient createClient(Path tempDir, Options options) {
        EmbeddingClient embedding = EmbeddingClient.create(
                EmbeddingClientSetting.builder().provider("memory").build());
        TextSplitter splitter = TextSplitterProvider.create("sentence", CHUNK_SIZE, CHUNK_OVERLAP);
        VectorStorage storage = VectorStorageBuilder.newBuilder()
                .dimension(embedding.embedding("test").length)
                .build();
        ChatClient chat = buildChatClient(options);
        RagClientSetting setting = RagClientSetting.builder()
                .chatClient(chat)
                .embeddingClient(embedding)
                .textSplitter(splitter)
                .vectorStorage(storage)
                .uploadDir(tempDir.toString())
                .topK(TOP_K)
                .similarityThreshold(0.0)
                .build();
        return RagClient.create("memory", setting);
    }

    /**
     * 构建 ChatClient：默认 memory 空实现；--remote 时按 provider/key/baseUrl/model 构建。
     *
     * @param options 命令行选项
     * @return 聊天客户端
     */
    private static ChatClient buildChatClient(Options options) {
        if (!options.remote()) {
            return ChatClient.create(ChatClientSetting.builder().provider("memory").build());
        }
        if (options.key() == null || options.key().isBlank()) {
            throw new IllegalArgumentException("--remote 需要指定 --key");
        }
        ChatClientSetting setting = ChatClientSetting.builder()
                .provider(options.provider())
                .appKey(options.key())
                .baseUrl(options.baseUrl())
                .model(options.model())
                .build();
        return ChatClient.create(setting);
    }

    /**
     * 命令行选项。
     *
     * @param remote   是否启用远端问答
     * @param provider 远端服务商（openai/deepseek/siliconflow）
     * @param key      API Key
     * @param baseUrl  可选基础地址
     * @param model    可选模型名
     */
    private record Options(boolean remote, String provider, String key, String baseUrl, String model) {

        /**
         * 解析 --key=value 形式参数。
         *
         * @param args 命令行参数
         * @return 选项对象
         */
        private static Options parse(String[] args) {
            boolean remote = false;
            String provider = "openai";
            String key = null;
            String baseUrl = null;
            String model = null;
            for (String arg : args) {
                if ("--remote".equals(arg)) {
                    remote = true;
                } else if (arg.startsWith("--provider=")) {
                    provider = arg.substring("--provider=".length());
                } else if (arg.startsWith("--key=")) {
                    key = arg.substring("--key=".length());
                } else if (arg.startsWith("--base-url=")) {
                    baseUrl = arg.substring("--base-url=".length());
                } else if (arg.startsWith("--model=")) {
                    model = arg.substring("--model=".length());
                }
            }
            return new Options(remote, provider, key, baseUrl, model);
        }
    }

    // ==================== 通用辅助 ====================

    /**
     * 输出单场景结果。
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

package com.chua.example.ai.rag;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.common.support.ai.embedding.EmbeddingClient;
import com.chua.common.support.ai.embedding.EmbeddingClientSetting;
import com.chua.common.support.ai.rag.RagClient;
import com.chua.common.support.ai.rag.RagClientSetting;
import com.chua.common.support.ai.rag.RagDocument;
import com.chua.common.support.ai.rag.RagResponse;
import com.chua.common.support.ai.splitter.SentenceTextSplitter;
import com.chua.common.support.ai.splitter.TextSplitter;
import com.chua.common.support.file.txtractor.TextExtractor;
import com.chua.common.support.utils.CommandLine;
import com.chua.common.support.vector.Vector;
import com.chua.common.support.vector.VectorCompareAlgorithm;
import com.chua.common.support.vector.VectorStorage;
import com.chua.common.support.vector.VectorStorageProvider;
import com.chua.jvector.support.configuration.JVectorStorageProperties;
import com.chua.openai.support.OpenAiChatClient;
import com.chua.openai.support.OpenAiEmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import com.chua.example.spi.Example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RAG 文档问答示例。
 * <p>
 * 默认走零依赖 {@code memory} provider（伪向量 + 伪回答），可切换为 OpenAI 兼容接口。
 * 向量后端默认 {@code jvector}（本地磁盘图）
 * </p>
 *
 * <h2>子命令</h2>
 * <pre>
 *   # 上传文档（自动识别 .pdf / .docx / .txt / .md / .xlsx / .csv）
 *   java RagChatExample upload --provider=memory --file=manual.pdf
 *   java RagChatExample upload --provider=openai --key=sk-xxx --file=guide.pdf
 *
 *   # 文档问答
 *   java RagChatExample query --provider=openai --key=sk-xxx --q="如何使用 XX 功能？"
 *   java RagChatExample query --provider=memory --q="如何使用 XX 功能？" --topK=3
 *
 *   # 仅向量召回（不调 LLM）
 *   java RagChatExample search --q="分布式锁"
 *
 *   # 文档管理
 *   java RagChatExample list
 *   java RagChatExample delete --id=xxx
 *   java RagChatExample reindex
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class RagChatExample implements Example {

    /**
     * 默认工作目录：./
     */
    private static final String DEFAULT_UPLOAD_DIR = "./rag-chat-data";

    /**
     * 默认 embedding 维度（memory 伪向量）
     */
    private static final int DEFAULT_DIMENSIONS = 1536;

    /**
     * 默认分块大小
     */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /**
     * 默认 Top-K
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * OpenAI 默认 embedding 维度
     */
    private static final int OPENAI_DEFAULT_DIMENSIONS = 1536;

    /**
     * 支持的子命令
     */
    private static final List<String> COMMANDS = List.of("upload", "query", "search", "list", "delete", "reindex");

    /**
     * SPI 路由名。
     *
     * @return 示例名称
     */
    @Override
    public String name() {
        return "rag-chat";
    }

    /**
     * 所属模块。
     *
     * @return 模块标识
     */
    @Override
    public String module() {
        return "rag";
    }

    /**
     * 展示描述。
     *
     * @return 一行说明
     */
    @Override
    public String description() {
        return "RAG 对话示例：upload/query/search/list/delete/reindex";
    }

    /**
     * Runner 自检入口：将参数转换为命令行后委托 main。
     *
     * @param args 参数键值对
     * @return 执行完成即视为通过
     */
    @Override
    public boolean run(Map<String, String> args) {
        String[] cli = args.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        main(cli);
        return true;
    }

    /** Main */
    public static void main(String[] args) {
        CommandLine cli = CommandLine.parse(args)
                .program("RagChatExample")
                .register("command", "c", "子命令: " + COMMANDS, null)
                .register("provider", "p", "AI 服务商：memory / openai（默认 memory）", "memory")
                .register("key", "k", "API Key（provider=openai 时必填）")
                .register("base-url", "u", "API 基础地址（可选，OpenAI 兼容地址）")
                .register("model", "m", "Chat 模型（如 gpt-4 / deepseek-chat）", "gpt-4o-mini")
                .register("embed-model", "em", "Embedding 模型（如 text-embedding-3-small）", "text-embedding-3-small")
                .register("dimensions", "d", "Embedding 维度", String.valueOf(DEFAULT_DIMENSIONS))
                .register("file", "f", "upload 子命令：待上传文件路径")
                .register("q", "query 子命令：问题文本 / search 子命令：查询文本")
                .register("id", "delete 子命令：文档 ID")
                .register("topk", "查询 Top-K", String.valueOf(DEFAULT_TOP_K))
                .register("threshold", "相似度阈值 (0~1)", "0.1")
                .register("chunk-size", "分块大小", String.valueOf(DEFAULT_CHUNK_SIZE))
                .register("chunk-overlap", "分块重叠", "50")
                .register("upload-dir", "RAG 文件目录", DEFAULT_UPLOAD_DIR)
                .register("extractor", "TextExtractor SPI（pdf/tika/docx/auto）", "auto")
                .register("vector", "向量后端：memory / jvector", "jvector")
                .register("help", "h", "显示帮助");

        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String command = cli.get("command");
        if (command == null || command.isBlank()) {
            log.error("[ERROR] 必须指定子命令 --command, 可选: {}", COMMANDS);
            cli.help();
            System.exit(1);
            return;
        }

        try (RagChatHarness harness = newHarness(cli)) {
            switch (command.toLowerCase(Locale.ROOT)) {
                case "upload" -> doUpload(harness, cli);
                case "query" -> doQuery(harness, cli);
                case "search" -> doSearch(harness, cli);
                case "list" -> doList(harness);
                case "delete" -> doDelete(harness, cli);
                case "reindex" -> doReindex(harness);
                default -> {
                    log.error("[ERROR] 未知子命令: {}, 可选: {}", command, COMMANDS);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            log.error("[ERROR] 执行失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * 构建 RagChat 工具实例。
     *
     * @param cli 命令行
     * @return harness 实例
     */
    private static RagChatHarness newHarness(CommandLine cli) {
        String provider = cli.get("provider", "memory").toLowerCase(Locale.ROOT);
        String apiKey = cli.get("key");
        String baseUrl = cli.get("base-url");
        String chatModel = cli.get("model", "gpt-4o-mini");
        String embedModel = cli.get("embed-model", "text-embedding-3-small");
        int dimensions = cli.getInt("dimensions", DEFAULT_DIMENSIONS);
        String uploadDir = cli.get("upload-dir", DEFAULT_UPLOAD_DIR);
        int chunkSize = cli.getInt("chunk-size", DEFAULT_CHUNK_SIZE);
        int chunkOverlap = cli.getInt("chunk-overlap", 50);
        int topK = cli.getInt("topk", DEFAULT_TOP_K);
        double threshold = Double.parseDouble(cli.get("threshold", "0.1"));
        String extractorType = cli.get("extractor", "auto");
        String vectorType = cli.get("vector", "jvector");

        ChatClient chatClient = buildChatClient(provider, apiKey, baseUrl, chatModel);
        EmbeddingClient embeddingClient = buildEmbeddingClient(provider, apiKey, baseUrl, embedModel, dimensions);
        TextExtractor extractor = buildTextExtractor(extractorType);
        VectorStorage vectorStorage = buildVectorStorage(vectorType, dimensions, uploadDir);
        TextSplitter splitter = new SentenceTextSplitter(chunkSize, chunkOverlap);

        RagClientSetting setting = RagClientSetting.builder()
                .chatClient(chatClient)
                .embeddingClient(embeddingClient)
                .textExtractor(extractor)
                .textSplitter(splitter)
                .vectorStorage(vectorStorage)
                .uploadDir(uploadDir)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        RagClient ragClient = RagClient.create("memory", setting);
        ragClient.topK(topK).similarityThreshold(threshold);

        log.info("[RagChat] provider={}, model={}, vector={}, dimensions={}, topK={}, threshold={}",
                provider, chatModel, vectorType, dimensions, topK, threshold);

        return new RagChatHarness(ragClient, embeddingClient, vectorStorage, setting);
    }

    /**
     * 构建 ChatClient。
     * <p>
     * provider=memory 时使用默认 ChatClient（项目内可能未提供 memory SPI，自动降级），
     * 通过 setting.provider 强制走空实现；provider=openai 时需 key。
     * </p>
     */
    private static ChatClient buildChatClient(String provider, String apiKey, String baseUrl, String model) {
        if ("openai".equals(provider) || "siliconflow".equals(provider) || "deepseek".equals(provider)) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("provider=" + provider + " 时必须指定 --key");
            }
            ChatClientSetting setting = ChatClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .model(model)
                    .build();
            return ChatClient.create(setting);
        }
        if ("memory".equals(provider)) {
            return ChatClient.create(ChatClientSetting.builder().provider("memory").build());
        }
        throw new IllegalArgumentException("不支持的 provider: " + provider);
    }

    /**
     * 构建 EmbeddingClient。
     */
    private static EmbeddingClient buildEmbeddingClient(String provider, String apiKey, String baseUrl,
                                                       String model, int dimensions) {
        if ("openai".equals(provider) || "siliconflow".equals(provider) || "deepseek".equals(provider)) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("provider=" + provider + " 时必须指定 --key");
            }
            EmbeddingClientSetting setting = EmbeddingClientSetting.builder()
                    .provider(provider)
                    .appKey(apiKey)
                    .baseUrl(baseUrl)
                    .model(model)
                    .dimensions("openai".equals(provider) ? OPENAI_DEFAULT_DIMENSIONS : dimensions)
                    .build();
            return EmbeddingClient.create(setting);
        }
        if ("memory".equals(provider)) {
            return EmbeddingClient.create("memory", "memory-memory-memory")
                    .model("memory")
                    .dimensions(dimensions);
        }
        throw new IllegalArgumentException("不支持的 provider: " + provider);
    }

    /**
     * 构建 TextExtractor。
     * <p>
     * 默认 {@code auto} 会按后缀匹配；指定具体类型如 pdf/tika/docx/excel/txt。
     * </p>
     */
    private static TextExtractor buildTextExtractor(String type) {
        if (type == null || type.isBlank() || "auto".equalsIgnoreCase(type)) {
            return null;
        }
        try {
            return TextExtractor.create(type.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            log.warn("[RagChat] TextExtractor={} 加载失败, 降级为 null: {}", type, e.getMessage());
            return null;
        }
    }

    /**
     * 构建 VectorStorage。
     */
    private static VectorStorage buildVectorStorage(String type, int dimensions, String uploadDir) {
        if (type == null || type.isBlank()) {
            type = "jvector";
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "memory" -> VectorStorageProvider.of("memory").dimension(dimensions)
                    .algorithm(VectorCompareAlgorithm.cosine()).build();
            case "jvector" -> {
                JVectorStorageProperties props = new JVectorStorageProperties();
                props.setMode(JVectorStorageProperties.Mode.MEMORY);
                props.setIndexPath(Path.of(uploadDir, "jvector-index").toString());
                yield VectorStorageProvider.of("jvector").dimension(dimensions)
                        .algorithm(VectorCompareAlgorithm.cosine())
                        .properties(props).build();
            }
            default -> throw new IllegalArgumentException("不支持的 vector: " + type);
        };
    }

    // ==================== 子命令实现 ====================

    /** DoUpload */
    private static void doUpload(RagChatHarness harness, CommandLine cli) throws Exception {
        String filePath = cli.get("file");
        if (filePath == null || filePath.isBlank()) {
            log.error("[ERROR] upload 子命令必须指定 --file");
            System.exit(1);
            return;
        }
        Path file = Path.of(filePath);
        if (!Files.exists(file)) {
            log.error("[ERROR] 文件不存在: {}", filePath);
            System.exit(1);
            return;
        }
        byte[] data = Files.readAllBytes(file);
        RagDocument doc = harness.ragClient.uploadDocument(file.getFileName().toString(), data);
        log.info("[upload] id={} fileName={} status={} chunkCount={} error={}",
                doc.id(), doc.fileName(), doc.status(), doc.chunkCount(), doc.errorMessage());
        if ("FAILED".equals(doc.status())) {
            System.exit(1);
        }
    }

    /** Do查询 */
    private static void doQuery(RagChatHarness harness, CommandLine cli) {
        String question = cli.get("q");
        if (question == null || question.isBlank()) {
            log.error("[ERROR] query 子命令必须指定 --q");
            System.exit(1);
            return;
        }
        RagResponse response = harness.ragClient.query(question);
        log.info("[query] answer={}", response.answer());
        log.info("[query] sources={}", response.sources().size());
        int i = 1;
        for (RagResponse.Source s : response.sources()) {
            Object score = s.metadata() != null ? s.metadata().get("chunkIndex") : null;
            log.info("  [{}] docId={} chunkIndex={} content={}",
                    i++, s.documentId(), score, truncate(s.content(), 120));
        }
    }

    /** Do搜索 */
    private static void doSearch(RagChatHarness harness, CommandLine cli) {
        String question = cli.get("q");
        if (question == null || question.isBlank()) {
            log.error("[ERROR] search 子命令必须指定 --q");
            System.exit(1);
            return;
        }
        float[] queryVector = harness.embeddingClient.embedding(question);
        List<Vector> results = harness.vectorStorage.search(queryVector, cli.getInt("topk", DEFAULT_TOP_K));
        log.info("[search] query={} returned={}", question, results.size());
        int i = 1;
        for (Vector v : results) {
            log.info("  [{}] id={} metadata={}", i++, v.id(), v.metadata());
        }
    }

    /** DoList */
    private static void doList(RagChatHarness harness) {
        int total = harness.ragClient.documentCount();
        log.info("[list] total documents={}", total);
        List<RagDocument> docs = harness.ragClient.listDocuments(1, Math.max(1, total));
        for (RagDocument d : docs) {
            log.info("  id={} fileName={} fileType={} size={} chunks={} status={} created={}",
                    d.id(), d.fileName(), d.fileType(), d.fileSize(), d.chunkCount(), d.status(), d.createTime());
        }
    }

    /** Do删除 */
    private static void doDelete(RagChatHarness harness, CommandLine cli) {
        String id = cli.get("id");
        if (id == null || id.isBlank()) {
            log.error("[ERROR] delete 子命令必须指定 --id");
            System.exit(1);
            return;
        }
        boolean ok = harness.ragClient.deleteDocument(id);
        log.info("[delete] id={} ok={}", id, ok);
    }

    /** DoReindex */
    private static void doReindex(RagChatHarness harness) {
        int count = harness.ragClient.reindex();
        log.info("[reindex] ok docs={}", count);
    }

    /** Truncate */
    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /**
     * 资源句柄聚合。
     */
    private static class RagChatHarness implements AutoCloseable {
        final RagClient ragClient;
        final EmbeddingClient embeddingClient;
        final VectorStorage vectorStorage;
        final RagClientSetting setting;

        RagChatHarness(RagClient ragClient, EmbeddingClient embeddingClient,
                      VectorStorage vectorStorage, RagClientSetting setting) {
            this.ragClient = ragClient;
            this.embeddingClient = embeddingClient;
            this.vectorStorage = vectorStorage;
            this.setting = setting;
        }

        @Override
        /** 关闭 */
        public void close() {
            try {
                ragClient.close();
            } catch (Exception ignored) {
            }
            try {
                embeddingClient.close();
            } catch (Exception ignored) {
            }
            try {
                vectorStorage.close();
            } catch (Exception ignored) {
            }
        }
    }
}

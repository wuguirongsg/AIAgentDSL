package com.agentdsl.memory.hypergraph;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.embedding.EmbeddingGeneratorFactory;
import com.agentdsl.memory.hypergraph.embedding.TextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.engine.ConsolidationEngine;
import com.agentdsl.memory.hypergraph.engine.DeepRecallEngine;
import com.agentdsl.memory.hypergraph.engine.HeuristicImportanceScorer;
import com.agentdsl.memory.hypergraph.engine.ImportanceScorer;
import com.agentdsl.memory.hypergraph.graph.MemoryGraphExtractor;
import com.agentdsl.memory.hypergraph.engine.MemoryReconstructor;
import com.agentdsl.memory.hypergraph.engine.SummaryCompressor;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.store.EmbeddingStoreVectorArchiveStore;
import com.agentdsl.memory.hypergraph.store.InMemoryStmStore;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.PersistentFileVectorArchiveStore;
import com.agentdsl.memory.hypergraph.store.SQLiteLtmStore;
import com.agentdsl.memory.hypergraph.store.StmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 插件内部的存储协调器。
 * 宿主只看到 ChatMemory，STM/LTM/归档的协同逻辑完全封装在插件内。
 */
public class HypergraphMemoryStore {

    private final HypergraphMemoryConfig config;
    private final StmStore stmStore;
    private final LtmStore ltmStore;
    private final VectorArchiveStore archiveStore;
    private final ImportanceScorer importanceScorer;
    private final ConsolidationEngine consolidationEngine;
    private final DeepRecallEngine deepRecallEngine;
    private final MemoryGraphExtractor graphExtractor;

    public HypergraphMemoryStore(HypergraphMemoryConfig config) {
        this(config,
                DefaultComponents.create(config, EmbeddingGeneratorFactory.create(config.vector())),
                new HeuristicImportanceScorer(),
                null,
                null);
    }

    public HypergraphMemoryStore(HypergraphMemoryConfig config,
            SummaryCompressor summaryCompressor,
            MemoryReconstructor reconstructor,
            TextEmbeddingGenerator embeddingGenerator) {
        this(config,
                DefaultComponents.create(config, embeddingGenerator),
                new HeuristicImportanceScorer(),
                summaryCompressor,
                reconstructor);
    }

    private HypergraphMemoryStore(HypergraphMemoryConfig config, DefaultComponents components) {
        this(config, components, new HeuristicImportanceScorer(), null, null);
    }

    private HypergraphMemoryStore(HypergraphMemoryConfig config,
            DefaultComponents components,
            ImportanceScorer importanceScorer,
            SummaryCompressor summaryCompressor,
            MemoryReconstructor reconstructor) {
        this(config,
                components.stmStore(),
                components.ltmStore(),
                components.archiveStore(),
                importanceScorer,
                components.embeddingGenerator(),
                summaryCompressor,
                reconstructor);
    }

    public HypergraphMemoryStore(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore) {
        this(config,
                stmStore,
                ltmStore,
                archiveStore,
                new HeuristicImportanceScorer(),
                EmbeddingGeneratorFactory.create(config.vector()),
                null,
                null);
    }

    public HypergraphMemoryStore(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            ImportanceScorer importanceScorer) {
        this(config,
                stmStore,
                ltmStore,
                archiveStore,
                importanceScorer,
                EmbeddingGeneratorFactory.create(config.vector()),
                null,
                null);
    }

    public HypergraphMemoryStore(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            ImportanceScorer importanceScorer,
            TextEmbeddingGenerator embeddingGenerator) {
        this(config, stmStore, ltmStore, archiveStore, importanceScorer, embeddingGenerator, null, null);
    }

    public HypergraphMemoryStore(HypergraphMemoryConfig config,
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            ImportanceScorer importanceScorer,
            TextEmbeddingGenerator embeddingGenerator,
            SummaryCompressor summaryCompressor,
            MemoryReconstructor reconstructor) {
        this.config = config;
        this.stmStore = stmStore;
        this.ltmStore = ltmStore;
        this.archiveStore = archiveStore;
        this.importanceScorer = importanceScorer;
        this.graphExtractor = new MemoryGraphExtractor();
        this.consolidationEngine = new ConsolidationEngine(
                config,
                stmStore,
                ltmStore,
                archiveStore,
                new com.agentdsl.memory.hypergraph.engine.DecayEngine(
                        config.decay() != null ? config.decay() : com.agentdsl.memory.hypergraph.config.DecayConfig.defaults()),
                summaryCompressor != null
                        ? summaryCompressor
                        : com.agentdsl.memory.hypergraph.engine.SummaryCompressorFactory.create(config.ltm()));
        this.deepRecallEngine = new DeepRecallEngine(
                config,
                ltmStore,
                archiveStore,
                embeddingGenerator,
                reconstructor != null ? reconstructor : new com.agentdsl.memory.hypergraph.engine.HeuristicMemoryReconstructor());
        if (config.consolidation().autoStart() && config.consolidation().intervalHours() > 0) {
            this.consolidationEngine.start(config.consolidation().intervalHours(), java.util.concurrent.TimeUnit.HOURS);
        }
    }

    public void add(ChatMessage message) {
        HyperEdge edge = toEdge(message);
        stmStore.add(edge);
        consolidationEngine.onAfterAdd();
    }

    public List<ChatMessage> messages() {
        return stmStore.snapshot().stream()
                .map(this::toChatMessage)
                .toList();
    }

    public void set(Iterable<ChatMessage> messages) {
        clear();
        for (ChatMessage message : messages) {
            add(message);
        }
    }

    public void clear() {
        stmStore.clear();
        ltmStore.clear(config.memoryId());
        archiveStore.clear();
    }

    public void consolidate() {
        consolidationEngine.consolidate();
    }

    public Optional<String> recall(String query) {
        return deepRecallEngine.recall(query);
    }

    private HyperEdge toEdge(ChatMessage message) {
        Instant now = Instant.now();
        String content = switch (message.type()) {
            case USER -> ((UserMessage) message).singleText();
            case AI -> aiMessageText((AiMessage) message);
            case SYSTEM -> ((SystemMessage) message).text();
            case TOOL_EXECUTION_RESULT -> ((ToolExecutionResultMessage) message).text();
            default -> null;
        };
        // STM 入边时就先抽取 nodes/tags/emotion，避免只有在压缩落库时才开始补图信息。
        MemoryGraphExtractor.GraphSnapshot snapshot = graphExtractor.extract(config.memoryId(), content, now);
        return switch (message.type()) {
            case USER -> HyperEdge.forStm(
                    UUID.randomUUID().toString(), config.memoryId(), ChatMessageType.USER.name(),
                    content, null, null,
                    score(((UserMessage) message).singleText()), false,
                    snapshot.emotionTag(), snapshot.contextTags(), snapshot.nodeIds());
            case AI -> HyperEdge.forStm(
                    UUID.randomUUID().toString(), config.memoryId(), ChatMessageType.AI.name(),
                    content, null, null,
                    score(aiMessageText((AiMessage) message)), false,
                    snapshot.emotionTag(), snapshot.contextTags(), snapshot.nodeIds());
            case SYSTEM -> HyperEdge.forStm(
                    UUID.randomUUID().toString(), config.memoryId(), ChatMessageType.SYSTEM.name(),
                    content, null, null,
                    0.95, true,
                    snapshot.emotionTag(), snapshot.contextTags(), snapshot.nodeIds());
            case TOOL_EXECUTION_RESULT -> {
                ToolExecutionResultMessage toolMessage = (ToolExecutionResultMessage) message;
                yield HyperEdge.forStm(
                        UUID.randomUUID().toString(), config.memoryId(),
                        ChatMessageType.TOOL_EXECUTION_RESULT.name(),
                        content, toolMessage.id(), toolMessage.toolName(),
                        score(toolMessage.text()), false,
                        snapshot.emotionTag(), snapshot.contextTags(), snapshot.nodeIds());
            }
            default -> throw new IllegalArgumentException("暂不支持的 ChatMessage 类型: " + message.type());
        };
    }

    private ChatMessage toChatMessage(HyperEdge edge) {
        ChatMessageType type = ChatMessageType.valueOf(edge.messageType());
        return switch (type) {
            case USER -> UserMessage.from(edge.messageText());
            case AI -> AiMessage.from(edge.messageText());
            case SYSTEM -> SystemMessage.from(edge.messageText());
            case TOOL_EXECUTION_RESULT -> ToolExecutionResultMessage.from(
                    edge.toolRequestId(),
                    edge.toolName(),
                    edge.messageText());
            default -> throw new IllegalArgumentException("暂不支持的 HyperEdge 类型: " + edge.messageType());
        };
    }

    private double score(String text) {
        return importanceScorer.score(text);
    }

    private String aiMessageText(AiMessage aiMessage) {
        String text = aiMessage.text();
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (aiMessage.hasToolExecutionRequests()) {
            String toolNames = aiMessage.toolExecutionRequests().stream()
                    .map(request -> request.name() != null ? request.name() : "unknown")
                    .distinct()
                    .reduce((left, right) -> left + "," + right)
                    .orElse("tool");
            return "[tool-call] " + toolNames;
        }
        return "[empty-ai-message]";
    }

    private static LtmStore createLtmStore(HypergraphMemoryConfig config) {
        String backend = normalize(config.ltm().backend(), "sqlite");
        return switch (backend) {
            case "sqlite" -> new SQLiteLtmStore(config.ltm().jdbcUrl());
            case "neo4j" -> throw new IllegalArgumentException("当前 hypergraph memory 暂不支持 ltm.backend=neo4j");
            default -> throw new IllegalArgumentException("未知的 ltm.backend: " + config.ltm().backend());
        };
    }

    private static VectorArchiveStore createArchiveStore(HypergraphMemoryConfig config,
            TextEmbeddingGenerator embeddingGenerator) {
        String store = normalize(config.vector().store(), "file-local");
        return switch (store) {
            case "in-memory", "inmemory" -> new EmbeddingStoreVectorArchiveStore(embeddingGenerator);
            case "file-local", "local-file", "embedded-file" ->
                new PersistentFileVectorArchiveStore(config.vector().path(), embeddingGenerator);
            case "chroma", "qdrant" ->
                throw new IllegalArgumentException("当前 hypergraph memory 暂不支持 vector.store=" + config.vector().store());
            default -> throw new IllegalArgumentException("未知的 vector.store: " + config.vector().store());
        };
    }

    private static String normalize(String value, String defaultValue) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toLowerCase(Locale.ROOT);
        return normalized.replace('_', '-');
    }

    private record DefaultComponents(
            StmStore stmStore,
            LtmStore ltmStore,
            VectorArchiveStore archiveStore,
            TextEmbeddingGenerator embeddingGenerator) {

        private static DefaultComponents create(HypergraphMemoryConfig config, TextEmbeddingGenerator embeddingGenerator) {
            return new DefaultComponents(
                    new InMemoryStmStore(),
                    createLtmStore(config),
                    createArchiveStore(config, embeddingGenerator),
                    embeddingGenerator);
        }
    }
}

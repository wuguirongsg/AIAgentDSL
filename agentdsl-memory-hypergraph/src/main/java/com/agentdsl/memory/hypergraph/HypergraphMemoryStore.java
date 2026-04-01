package com.agentdsl.memory.hypergraph;

import com.agentdsl.memory.hypergraph.async.GraphUpdateWorker;
import com.agentdsl.memory.hypergraph.async.IngestQueue;
import com.agentdsl.memory.hypergraph.async.ScoringBatchWorker;
import com.agentdsl.memory.hypergraph.async.SummaryPrecomputer;
import com.agentdsl.memory.hypergraph.config.AsyncConfig;
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
import com.agentdsl.memory.hypergraph.store.LtmGraphIndex;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 超图记忆系统的存储协调器，封装 STM / LTM / 归档三层存储的读写与整合逻辑。
 *
 * <h3>架构定位</h3>
 * <p>这是插件内部的"大脑"，协调以下组件：</p>
 * <ul>
 *   <li>{@link StmStore} — 短期记忆，ConcurrentHashMap 内存存储，TTL 自动过期</li>
 *   <li>{@link LtmStore} — 长期记忆，SQLite 持久化，存储压缩摘要</li>
 *   <li>{@link VectorArchiveStore} — 向量冷库，存储原始细节碎片，支持语义检索</li>
 *   <li>{@link ConsolidationEngine} — 后台整合引擎，定时压缩 STM → LTM</li>
 *   <li>{@link DeepRecallEngine} — 深度回忆引擎，从冷库还原并重建记忆</li>
 *   <li>{@link MemoryGraphExtractor} — 超图关系抽取，提取节点和标签</li>
 * </ul>
 *
 * <h3>数据流</h3>
 * <pre>
 *   用户消息 → toEdge() → graphExtractor → STM
 *                                    ↓ (consolidation)
 *                                 LTM + Archive
 *                                    ↓ (deep recall)
 *                               重建上下文
 * </pre>
 *
 * <h3>构造器选择</h3>
 * <p>提供多个构造器以适配不同场景：</p>
 * <ul>
 *   <li>仅传 config — 自动创建所有默认组件（生产场景）</li>
 *   <li>传入自定义 SummaryCompressor/MemoryReconstructor — 注入真实 LLM（需要压缩质量时）</li>
 *   <li>传入自定义 StmStore/LtmStore/ArchiveStore — 单元测试或自定义后端</li>
 * </ul>
 *
 * @see HypergraphChatMemory
 * @see ConsolidationEngine
 * @see DeepRecallEngine
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

    // v1.2 异步层组件
    private final LtmGraphIndex ltmGraphIndex;
    private final IngestQueue ingestQueue;
    private final ScoringBatchWorker scoringBatchWorker;
    private final GraphUpdateWorker graphUpdateWorker;
    private final SummaryPrecomputer summaryPrecomputer;

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

        // v1.2 异步层初始化
        AsyncConfig asyncConfig = AsyncConfig.defaults();
        this.ltmGraphIndex = new LtmGraphIndex();
        this.ingestQueue = new IngestQueue(asyncConfig.ingestQueueCapacity());
        this.graphUpdateWorker = new GraphUpdateWorker(ltmGraphIndex);
        this.scoringBatchWorker = new ScoringBatchWorker(
                ingestQueue, stmStore, importanceScorer, graphUpdateWorker, asyncConfig);
        this.summaryPrecomputer = new SummaryPrecomputer(
                config.memoryId(), ltmStore, ltmGraphIndex, archiveStore, embeddingGenerator, asyncConfig);

        this.consolidationEngine = new ConsolidationEngine(
                config,
                stmStore,
                ltmStore,
                ltmGraphIndex,
                archiveStore,
                new com.agentdsl.memory.hypergraph.engine.DecayEngine(
                        config.decay() != null ? config.decay() : com.agentdsl.memory.hypergraph.config.DecayConfig.defaults()),
                summaryCompressor != null
                        ? summaryCompressor
                        : com.agentdsl.memory.hypergraph.engine.SummaryCompressorFactory.create(config.ltm()),
                new MemoryGraphExtractor());
        this.deepRecallEngine = new DeepRecallEngine(
                config,
                ltmStore,
                archiveStore,
                embeddingGenerator,
                reconstructor != null ? reconstructor : new com.agentdsl.memory.hypergraph.engine.HeuristicMemoryReconstructor());
        if (config.consolidation().autoStart() && config.consolidation().intervalHours() > 0) {
            this.consolidationEngine.start(config.consolidation().intervalHours(), java.util.concurrent.TimeUnit.HOURS);
        }
        graphUpdateWorker.start();
        scoringBatchWorker.start();
        summaryPrecomputer.start();
    }

    /**
     * 写入一条新的聊天消息到记忆系统（v1.2 异步化）。
     *
     * <p>对话路径目标 &lt; 50ms：</p>
     * <ol>
     *   <li>规则快速预评分（&lt; 1ms）</li>
     *   <li>写入 STM，立即对对话路径可见</li>
     *   <li>触发即时整合检查（过期淘汰 + 容量溢出）</li>
     *   <li>入队 IngestQueue 供后台精确重评分（非阻塞，立即返回）</li>
     * </ol>
     *
     * @param message 聊天消息（USER / AI / SYSTEM / TOOL_EXECUTION_RESULT）
     */
    public void add(ChatMessage message) {
        HyperEdge edge = toEdge(message);
        stmStore.add(edge);
        consolidationEngine.onAfterAdd();
        // 异步精确重评分（非阻塞）
        ingestQueue.offer(edge, edge.rawContent() != null ? edge.rawContent() : "");
        // 高重要度消息（个人身份、偏好等关键事实）立即双写到 LTM
        // 防止应用重启后 InMemoryStmStore 丢失导致跨会话记忆消失
        if (edge.importance() >= config.immediateFlushThreshold()) {
            immediateWriteToLtm(edge);
        }
    }

    /**
     * 将高重要度消息立即写入 LTM（不等待整合定时器）。
     *
     * <p>消息同时保留在 STM 中，供当前会话上下文使用。
     * LTM 中使用原始消息文本作为摘要（不压缩），保留完整事实供后续检索。
     * 当整合引擎后续将此消息正式迁移到 LTM 时，会用带归档指针的版本覆盖（INSERT OR REPLACE）。</p>
     */
    private void immediateWriteToLtm(HyperEdge stmEdge) {
        com.agentdsl.memory.hypergraph.model.HyperEdge ltmEdge = new com.agentdsl.memory.hypergraph.model.HyperEdge(
                stmEdge.id(), stmEdge.memoryId(), stmEdge.messageType(), stmEdge.messageText(),
                stmEdge.toolRequestId(), stmEdge.toolName(),
                stmEdge.weight(), stmEdge.importance(),
                com.agentdsl.memory.hypergraph.model.MemoryTier.LTM,
                1,
                stmEdge.messageText(), // summary = 原始文本，不压缩，保留完整事实
                List.of(),             // archivePointers 由正式整合时写入
                stmEdge.nodeIds(),
                stmEdge.accessCount(), stmEdge.anchor(),
                stmEdge.createdAt(), stmEdge.createdAt(),
                stmEdge.emotionTag(), stmEdge.contextTags(),
                List.of(), null);
        ltmStore.save(ltmEdge);
    }

    /**
     * 获取当前 STM 中的活跃消息列表，并自动注入相关 LTM 记忆（主动回忆）。
     *
     * <p>主动回忆流程：取 STM 中最近一条 USER 消息作为 query，在 LTM 中语义检索；
     * 若命中（相似度 ≥ proactiveRecallThreshold），将结果以 SystemMessage 形式
     * 预置在消息列表最前，使 LLM 在生成答复时能感知长期记忆。</p>
     *
     * <p>此机制解决了"你知道我是谁吗"类抽象问题：即使用户名字已从 STM 整合到 LTM，
     * LLM 仍能在无需显式调用 deep_recall 工具的情况下获取相关背景。</p>
     *
     * @return 消息列表（可能包含主动注入的长期记忆 SystemMessage）
     */
    public List<ChatMessage> messages() {
        List<HyperEdge> stmEdges = stmStore.snapshot();
        List<ChatMessage> result = new ArrayList<>(stmEdges.stream()
                .map(this::toChatMessage)
                .toList());

        if (config.proactiveRecallEnabled()) {
            stmEdges.stream()
                    .filter(e -> "USER".equals(e.messageType()))
                    .reduce((a, b) -> b) // 取最近一条用户消息
                    .map(HyperEdge::messageText)
                    .filter(q -> q != null && !q.isBlank())
                    .flatMap(q -> deepRecallEngine.recall(q, config.proactiveRecallThreshold()))
                    .ifPresent(recalled ->
                            result.add(0, SystemMessage.from("[长期记忆]\n" + recalled)));
        }

        return result;
    }

    /**
     * 替换所有记忆为给定消息列表。
     * <p>先清空全部数据（STM + LTM + Archive），再逐条写入新消息。</p>
     *
     * @param messages 新的消息列表
     */
    public void set(Iterable<ChatMessage> messages) {
        clear();
        for (ChatMessage message : messages) {
            add(message);
        }
    }

    /**
     * 清空所有记忆数据。
     * <p>包括 STM 内存、LTM SQLite 数据和归档冷库，不可恢复。</p>
     */
    public void clear() {
        stmStore.clear();
        ltmStore.clear(config.memoryId());
        archiveStore.clear();
    }

    /**
     * 手动触发一次完整整合。
     * <p>执行 STM → LTM 压缩（对低权重超边生成摘要）和 LTM → Archive 归档。
     * 整合完成后触发预计算摘要索引更新。</p>
     */
    public void consolidate() {
        consolidationEngine.consolidate();
        summaryPrecomputer.triggerPrecompute();
    }

    /**
     * 触发预计算摘要索引更新（异步，非阻塞）。
     */
    public void triggerSummaryPrecompute() {
        summaryPrecomputer.triggerPrecompute();
    }

    /**
     * 对指定查询执行深度回忆。
     * <p>在 LTM 摘要中语义检索，若匹配度超过阈值则从冷库还原原始碎片，
     * 使用 MemoryReconstructor 重建完整记忆情境。</p>
     *
     * @param query 回忆查询文本（如"昨天讨论的结论"）
     * @return 重建后的记忆内容；如果未触发深度回忆则返回 empty
     */
    public Optional<String> recall(String query) {
        return deepRecallEngine.recall(query);
    }

    /**
     * 将 LangChain4j ChatMessage 转换为超图记忆的 HyperEdge。
     * <p>核心转换逻辑，同时执行以下操作：</p>
     * <ol>
     *   <li>提取消息文本内容</li>
     *   <li>调用 ImportanceScorer 评分（USER/AI/TOOL 消息）</li>
     *   <li>调用 MemoryGraphExtractor 抽取节点、标签和情绪（入边时即抽取，避免压缩时重复计算）</li>
     *   <li>构建 STM 级别的 HyperEdge（weight=1.0, archivePointers=[]）</li>
     * </ol>
     *
     * @param message LangChain4j 消息
     * @return STM 级别的 HyperEdge
     * @throws IllegalArgumentException 如果消息类型不支持
     */
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

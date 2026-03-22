package com.agentdsl.memory.hypergraph;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.config.ConsolidationConfig;
import com.agentdsl.memory.hypergraph.config.DecayConfig;
import com.agentdsl.memory.hypergraph.config.LtmConfig;
import com.agentdsl.memory.hypergraph.config.StmConfig;
import com.agentdsl.memory.hypergraph.config.VectorConfig;
import com.agentdsl.memory.hypergraph.embedding.PseudoTextEmbeddingGenerator;
import com.agentdsl.memory.hypergraph.model.HyperEdge;
import com.agentdsl.memory.hypergraph.model.EmotionTag;
import com.agentdsl.memory.hypergraph.model.MemoryTier;
import com.agentdsl.memory.hypergraph.store.EmbeddingStoreVectorArchiveStore;
import com.agentdsl.memory.hypergraph.store.InMemoryStmStore;
import com.agentdsl.memory.hypergraph.store.LtmStore;
import com.agentdsl.memory.hypergraph.store.SQLiteLtmStore;
import com.agentdsl.memory.hypergraph.store.StmStore;
import com.agentdsl.memory.hypergraph.store.VectorArchiveStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypergraphChatMemoryTest {

    @Test
    @DisplayName("STM 超限后应压缩到 LTM，并可触发 recall")
    void shouldConsolidateAndRecall() throws Exception {
        Path tempDir = Files.createTempDirectory("hypergraph-memory-test");
        HypergraphMemoryConfig config = config(tempDir, 2, 48, "sqlite", "in-memory");
        HypergraphChatMemory memory = new HypergraphChatMemory(config);

        memory.add(UserMessage.from("用户偏好：喜欢黑咖啡，不加糖。"));
        memory.add(AiMessage.from("已记录你的咖啡偏好。"));
        memory.add(UserMessage.from("请记住我下周三要发布 AgentDSL memory 插件。"));

        assertEquals(2, memory.messages().size(), "STM 只应保留最近 2 条");

        String recalled = memory.recall("咖啡偏好").orElse("");
        assertTrue(recalled.contains("黑咖啡"), "应能从 LTM/Archive 回忆出被压缩的旧消息");
    }

    @Test
    @DisplayName("file-local 向量冷库应在重启后保留 archive 碎片")
    void shouldPersistArchiveAcrossRestart() throws Exception {
        Path tempDir = Files.createTempDirectory("hypergraph-memory-persist-test");
        HypergraphMemoryConfig config = config(tempDir, 2, 48, "sqlite", "file-local");

        HypergraphChatMemory first = new HypergraphChatMemory(config);
        first.add(UserMessage.from("用户偏好：喜欢黑咖啡，不加糖。"));
        first.add(AiMessage.from("已记录你的咖啡偏好。"));
        first.add(UserMessage.from("请记住我总是点黑咖啡。"));

        HypergraphChatMemory reloaded = new HypergraphChatMemory(config);
        String recalled = reloaded.recall("咖啡偏好").orElse("");

        assertTrue(recalled.contains("黑咖啡"), "重启后仍应能从持久化 archive 恢复细节");
        assertTrue(Files.exists(archivePath(tempDir)), "应生成本地持久化向量库文件");
    }

    @Test
    @DisplayName("TTL 到期的 STM 应在整合时转入 LTM")
    void shouldMoveExpiredStmEntriesIntoLtm() throws Exception {
        Path tempDir = Files.createTempDirectory("hypergraph-memory-ttl-test");
        HypergraphMemoryConfig config = config(tempDir, 8, 1, "sqlite", "in-memory");
        StmStore stmStore = new InMemoryStmStore();
        LtmStore ltmStore = new SQLiteLtmStore(config.ltm().jdbcUrl());
        VectorArchiveStore archiveStore = new EmbeddingStoreVectorArchiveStore(
                new PseudoTextEmbeddingGenerator(32, "pseudo"));
        HypergraphMemoryStore store = new HypergraphMemoryStore(config, stmStore, ltmStore, archiveStore);

        Instant expiredAt = Instant.now().minus(2, ChronoUnit.HOURS);
        stmStore.add(new HyperEdge(
                UUID.randomUUID().toString(),
                config.memoryId(),
                "USER",
                "过期的短期记忆",
                null,
                null,
                1.0,
                0.8,
                MemoryTier.STM,
                null,
                List.of(),
                List.of(),
                1,
                false,
                expiredAt,
                expiredAt,
                EmotionTag.NEUTRAL,
                List.of(),
                List.of()));

        store.consolidate();

        assertTrue(store.messages().isEmpty(), "过期 STM 应被移出工作记忆");
        assertEquals(1, ltmStore.findAll(config.memoryId()).size(), "过期 STM 应落入 LTM");
    }

    @Test
    @DisplayName("不支持的 LTM backend 应立即失败")
    void shouldRejectUnsupportedLtmBackend() throws Exception {
        Path tempDir = Files.createTempDirectory("hypergraph-memory-invalid-ltm");
        HypergraphMemoryConfig config = config(tempDir, 2, 48, "neo4j", "in-memory");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HypergraphChatMemory(config));

        assertTrue(error.getMessage().contains("ltm.backend=neo4j"));
    }

    @Test
    @DisplayName("不支持的向量存储后端应立即失败")
    void shouldRejectUnsupportedVectorStore() throws Exception {
        Path tempDir = Files.createTempDirectory("hypergraph-memory-invalid-vector");
        HypergraphMemoryConfig config = config(tempDir, 2, 48, "sqlite", "qdrant");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HypergraphChatMemory(config));

        assertTrue(error.getMessage().contains("vector.store=qdrant"));
    }

    @Test
    @DisplayName("超图长期记忆应保存节点、上下文标签和关联边")
    void shouldPersistHypergraphStructureIntoLtm() throws Exception {
        Path tempDir = Files.createTempDirectory("hypergraph-memory-graph-test");
        HypergraphMemoryConfig config = config(tempDir, 1, 48, "sqlite", "file-local");
        HypergraphChatMemory memory = new HypergraphChatMemory(config);
        SQLiteLtmStore ltmStore = new SQLiteLtmStore(config.ltm().jdbcUrl());

        memory.add(UserMessage.from("请记住 AgentDSL memory 插件将在下周发布。"));
        memory.add(UserMessage.from("继续跟踪 AgentDSL memory 插件发布计划。"));
        memory.add(UserMessage.from("发布前提醒我检查 AgentDSL memory 插件文档。"));

        List<HyperEdge> edges = ltmStore.findAll(config.memoryId());
        assertTrue(edges.size() >= 2, "应至少有两条 LTM 记忆边");
        assertTrue(edges.stream().anyMatch(edge -> edge.nodeIds() != null && !edge.nodeIds().isEmpty()),
                "LTM 记忆边应持久化 nodeIds");
        assertTrue(edges.stream().anyMatch(edge -> edge.contextTags() != null && !edge.contextTags().isEmpty()),
                "LTM 记忆边应持久化 contextTags");
        assertTrue(edges.stream().anyMatch(edge -> edge.linkedEdgeIds() != null && !edge.linkedEdgeIds().isEmpty()),
                "相近主题的记忆边应建立 linkedEdgeIds 关联");
    }

    private HypergraphMemoryConfig config(Path tempDir,
            int maxEdges,
            int ttlHours,
            String ltmBackend,
            String vectorStore) {
        Path dbPath = tempDir.resolve("memory.db");
        return new HypergraphMemoryConfig(
                "memory-test",
                new StmConfig(maxEdges, ttlHours),
                new LtmConfig(ltmBackend, dbPath.toString(), null, "jdbc:sqlite:" + dbPath),
                new VectorConfig(vectorStore, null, archivePath(tempDir).toString(), 32, 3),
                DecayConfig.defaults(),
                new ConsolidationConfig(6, false),
                64,
                0.5);
    }

    private Path archivePath(Path tempDir) {
        return tempDir.resolve("memory.archive-store.json");
    }
}

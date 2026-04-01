# agentdsl-memory-hypergraph

> **超图长期记忆模块** — AIAgentDSL 的分层记忆实现，支持短期记忆、长期记忆、深度回忆和超图关系网络。

---

## 模块定位

`agentdsl-memory-hypergraph` 是 AIAgentDSL v1.5 新增的 **SPI 插件模块**，实现基于超图数据结构的分层记忆系统。

**核心特性**：
- **三层记忆架构** — STM（短期）→ LTM（长期）→ Archive（冷库），模拟人脑的记忆分层
- **两级 LTM 超图** — Level-1 摘要超边 + Level-2 元超边（主题聚类），构建记忆主题网络
- **超图关系网络** — 多元关系超边，支持节点、标签、情绪和边间关联（JGraphT）
- **智能衰减策略** — 基于艾宾浩斯遗忘曲线，重要度越高抗衰减越强
- **深度回忆** — 从冷库还原原始碎片，LLM 重建完整记忆情境
- **后台整合** — 模拟"睡眠整合"机制，定时压缩迁移
- **快慢分离异步层** — 对话路径 < 50ms，重评分/图更新/摘要预计算全部异步

---

## 架构模式

### 插件化边界

```
┌─────────────────────────────────────────────────────────────┐
│                    AIAgentDSL Runtime                        │
│                                                              │
│   SPI (ServiceLoader) → HypergraphMemoryPlugin               │
│          │                                                   │
│          ▼                                                   │
│   HypergraphChatMemory (实现 LangChain4j ChatMemory 接口)    │
│          │                                                   │
│          └──→ HypergraphMemoryStore (内部协调器，宿主不可见)   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**关键约束**：
- 宿主通过 `META-INF/services/com.agentdsl.core.plugin.AgentDslPlugin` 自动发现
- 宿主只依赖 `dev.langchain4j.memory.ChatMemory` 接口
- 插件不依赖 `agentdsl-runtime`、`SkillSpec`、`ToolSpec` 等内部类型
- 可独立拆仓库、单独版本化

### 三层存储架构

```
┌─────────────────────────────────────────────────────────────────┐
│                   HypergraphMemoryStore                          │
│                                                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ STM Store (ConcurrentHashMap + ConcurrentLinkedDeque)    │   │
│   │   - 最近 N 条超边，全量细节，O(1) 读写                      │   │
│   │   - TTL 自动过期（默认 24-48 小时）                         │   │
│   │   - 容量溢出 FIFO 淘汰 → 触发压缩到 LTM                     │   │
│   │   ⚠ 注意：进程重启会丢失未压缩的 STM 数据                    │   │
│   │     生产场景建议实现 SqliteStmStore（参见"扩展开发"章节）      │   │
│   └─────────────────────────────────────────────────────────┘   │
│                          │                                       │
│          ┌───────────────┼───────────────────────┐              │
│          ▼               ▼                       ▼              │
│   ┌────────────┐  ┌─────────────┐  ┌─────────────────────┐     │
│   │ LTM Store  │  │ DecayEngine │  │ ConsolidationEngine  │     │
│   │ (SQLite)   │  │ (衰减计算)   │  │ (后台整合，定时6小时)  │     │
│   │ Level-1    │  │             │  │ + LtmGraphIndex      │     │
│   │ 摘要超边   │  └─────────────┘  └─────────────────────┘     │
│   │ Level-2    │                                                 │
│   │ 元超边主题  │                                                 │
│   └────────────┘                                                 │
│          │                                                       │
│          ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ Archive Store (VectorArchiveStore)                       │   │
│   │   - 原始细节碎片持久化                                      │   │
│   │   - 支持语义向量检索（upsertSummary 预计算索引）              │   │
│   │   - file-local 持久化 / in-memory 临时                     │   │
│   └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 快慢分离异步层（v1.2 新增）

```
对话路径 (< 50ms)                 后台异步路径
─────────────────────          ──────────────────────────────────
add(message)
  ├─ 规则预评分 (< 1ms)
  ├─ 写入 STM
  ├─ 即时整合检查               ScoringBatchWorker (每 5 分钟)
  └─ offer(IngestQueue) ──→     ├─ 批量精确重评分
                                └─ 差值 > 0.1 则更新 STM
                                         │
                                         ▼
                                GraphUpdateWorker (单后台线程)
                                └─ 更新 LtmGraphIndex 边权重

consolidate()                   SummaryPrecomputer (每 30 分钟)
  └─ triggerPrecompute() ──→    └─ N-hop 邻居聚合 enrichedSummary
                                   └─ upsertSummary → VectorArchiveStore
```

---

## 两级 LTM 结构（v1.1 新增）

```
Level-1（摘要超边，ltmLevel=1）
  ← ConsolidationEngine.moveToLongTermMemory() 产出
  ← 存储压缩摘要 + archivePointers + linkedEdgeIds
  ← LtmGraphIndex.level1Graph 维护边权关联

Level-2（元超边，ltmLevel=2 / MetaHyperEdge）
  ← AbstractionEngine.abstractClusters() 产出
  ← 对 Level-1 超边按 contextTags Jaccard 相似度贪心聚类
  ← 存储主题摘要 + memberEdgeIds + linkedMetaEdgeIds
  ← LtmGraphIndex.level2Graph 维护元超边间关联
```

---

## 目录结构

```
agentdsl-memory-hypergraph/
├── src/main/java/com/agentdsl/memory/hypergraph/
│   ├── HypergraphChatMemory.java          # ChatMemory 接口实现（宿主入口）
│   ├── HypergraphMemoryStore.java         # 内部存储协调器
│   ├── HypergraphMemoryProvider.java      # SPI Provider（反射发现入口）
│   ├── HypergraphMemoryPlugin.java        # AgentDslPlugin SPI 声明（v1.5）
│   │
│   ├── model/                             # 数据模型
│   │   ├── HyperEdge.java                 # 超边（记忆片段核心，含 ltmLevel/metaEdgeId）
│   │   ├── MetaHyperEdge.java             # 元超边（Level-2 主题聚类，v1.1 新增）
│   │   ├── MemoryNode.java                # 节点（实体/事件/概念/情绪）
│   │   ├── MemoryTier.java                # 记忆层级枚举（STM/LTM/ARCHIVE）
│   │   └── EmotionTag.java                # 情绪标签枚举
│   │
│   ├── engine/                            # 核心引擎
│   │   ├── DecayEngine.java               # 权重衰减计算（艾宾浩斯曲线）
│   │   ├── ConsolidationEngine.java       # 后台整合（STM→LTM，含 migrateLinks）
│   │   ├── AbstractionEngine.java         # 主题抽象（Level-1→Level-2，v1.1 新增）
│   │   ├── DeepRecallEngine.java          # 深度回忆（语义检索+重建）
│   │   ├── ImportanceScorer.java          # 重要度评分接口
│   │   ├── HeuristicImportanceScorer.java # 启发式评分实现
│   │   ├── SummaryCompressor.java         # 摘要压缩接口
│   │   ├── SummaryCompressorFactory.java  # 压缩器工厂
│   │   ├── HeuristicSummaryCompressor.java# 启发式压缩实现
│   │   ├── ExtractiveSummaryCompressor.java # 抽取式压缩实现
│   │   ├── ChatModelSummaryCompressor.java# LLM 驱动压缩实现
│   │   ├── MemoryReconstructor.java       # 记忆重建接口
│   │   ├── HeuristicMemoryReconstructor.java# 启发式重建实现
│   │   ├── ChatModelMemoryReconstructor.java# LLM 驱动重建实现
│   │   └── ChatModelPromptRunner.java     # LLM 调用辅助
│   │
│   ├── store/                             # 存储层
│   │   ├── StmStore.java                  # 短期记忆接口
│   │   ├── InMemoryStmStore.java          # 内存实现（ConcurrentHashMap，默认）
│   │   ├── LtmStore.java                  # 长期记忆接口（含 v1.1 元超边方法）
│   │   ├── SQLiteLtmStore.java            # SQLite 实现（Level-1/2 两级支持）
│   │   ├── LtmGraphIndex.java             # JGraphT 图索引（Level-1/2，v1.1 新增）
│   │   ├── VectorArchiveStore.java        # 向量冷库接口（含 upsertSummary）
│   │   ├── EmbeddingStoreVectorArchiveStore.java  # 内存实现
│   │   └── PersistentFileVectorArchiveStore.java  # 文件持久化实现
│   │
│   ├── async/                             # 异步层（v1.2 新增）
│   │   ├── IngestQueue.java               # 非阻塞摄取队列（ArrayBlockingQueue）
│   │   ├── ScoringBatchWorker.java        # 后台批量精确重评分
│   │   ├── GraphUpdateWorker.java         # 后台图索引更新
│   │   └── SummaryPrecomputer.java        # 预计算摘要索引（N-hop 邻居聚合）
│   │
│   ├── embedding/                         # 向量生成
│   │   ├── TextEmbeddingGenerator.java    # 文本嵌入接口
│   │   ├── LangChain4jEmbeddingGenerator.java     # LangChain4j 实现
│   │   ├── PseudoTextEmbeddingGenerator.java      # 伪嵌入实现（测试用）
│   │   └── EmbeddingGeneratorFactory.java         # 工厂
│   │
│   ├── graph/                             # 超图关系
│   │   └── MemoryGraphExtractor.java      # 节点/标签/情绪抽取
│   │
│   ├── capability/                        # 宿主桥接能力
│   │   ├── MemoryCapability.java          # 能力接口
│   │   ├── DeepRecallCapability.java      # 深度回忆桥接
│   │   └── ConsolidateMemoryCapability.java # 整合桥接
│   │
│   └── config/                            # 配置模型
│       ├── HypergraphMemoryConfig.java    # 总配置
│       ├── StmConfig.java                 # STM 配置
│       ├── LtmConfig.java                 # LTM 配置
│       ├── VectorConfig.java              # 向量配置
│       ├── DecayConfig.java               # 衰减参数
│       ├── ConsolidationConfig.java       # 整合调度配置
│       └── AsyncConfig.java               # 异步层配置（v1.2 新增）
│
└── src/main/resources/
    ├── db/schema.sql                      # SQLite DDL（含 Level-2 元超边表）
    └── META-INF/services/
        └── com.agentdsl.core.plugin.AgentDslPlugin  # SPI 声明
```

---

## 核心流程

### 消息写入流程（v1.2 快慢分离）

```
用户消息 / AI 回复
      │
      ▼
HypergraphChatMemory.add(message)
      │
      ▼
HypergraphMemoryStore.add(message)                    ← 对话路径目标 < 50ms
      │
      ├──→ toEdge(message)
      │         ├── ImportanceScorer.score()           ← 规则预评分 (< 1ms)
      │         └── MemoryGraphExtractor.extract()     ← 抽取节点/标签/情绪
      │
      ├──→ stmStore.add(edge)                          ← 写入 STM (< 1ms)
      │
      ├──→ consolidationEngine.onAfterAdd()            ← 即时整合检查
      │         ├── drainExpiredStm()                  ← 淘汰过期 → LTM
      │         └── evictOverflow(maxEdges)            ← 溢出淘汰 → LTM
      │
      └──→ ingestQueue.offer(edge)                     ← 非阻塞入队，立即返回
                    │
                    ▼（后台，不阻塞对话）
            ScoringBatchWorker（每 5 分钟批量）
                    ├── scorer.score(rawText)          ← 精确重评分
                    ├── 差值 > 0.1 → 更新 STM 超边
                    └── graphUpdateWorker.enqueue()
                                │
                                ▼
                        GraphUpdateWorker（单后台线程）
                        └── ltmGraphIndex.updateEdgeWeight()
```

### 后台整合流程（含 v1.1 图关联迁移）

```
ConsolidationEngine.consolidate()  (定时 6 小时 / 手动触发)
      │
      ├── Step 1: 淘汰过期 STM → moveToLongTermMemory(edge)
      │
      ├── Step 2: 压缩低权重 STM（weight < compressionThreshold）
      │     │
      │     └── moveToLongTermMemory(edge):
      │           ├── archiveStore.archive()           ← 原始碎片 → 冷库
      │           ├── graphExtractor.extractNodes()    ← 提取 MemoryNode
      │           ├── ltmStore.saveNodes()             ← 持久化节点
      │           ├── decayEngine.computeWeight()      ← 计算当前权重
      │           ├── ltmStore.findRelatedEdges()      ← 查找关联边（超图构建）
      │           ├── summaryCompressor.compress()     ← 生成摘要（ltmLevel=1）
      │           ├── ltmStore.save(ltmEdge)           ← 写入 LTM
      │           ├── ltmGraphIndex.addEdge(ltmEdge)   ← 更新图索引（v1.1）
      │           ├── 更新关联边的 linkedEdgeIds        ← 双向链接
      │           └── migrateLinks()                   ← 迁移 STM→LTM 权重（v1.1）
      │
      ├── Step 3: 归档极低权重 LTM（weight < archiveThreshold）
      │     ltmStore.findAll().filter(shouldArchive) → markArchived()
      │
      └── Step 4: 触发预计算摘要（v1.2）
            summaryPrecomputer.triggerPrecompute()
```

### 主题抽象流程（Level-2，v1.1 新增）

```
AbstractionEngine.abstractClusters()
      │
      ├── ltmStore.findByLtmLevel(1)              ← 找未归属元超边的 Level-1 超边
      │
      ├── greedyCluster()                         ← 贪心聚类（Jaccard 相似度）
      │     for each edge:
      │       找到第一个相似度 ≥ threshold 的已有簇 → 加入
      │       否则 → 新建簇
      │
      ├── buildMetaHyperEdge(cluster):
      │     ├── themeSummary = "【主题摘要】" + 各摘要拼接
      │     ├── cohesion = 平均 Jaccard 相似度
      │     └── contextTags = 簇内最高频标签
      │
      ├── ltmStore.saveMetaEdge(metaEdge)         ← 写入 hypergraph_meta_edges
      │
      ├── ltmStore.updateMetaEdgeId(edgeId, ...)  ← 更新 Level-1 的归属
      │
      ├── ltmGraphIndex.addMetaEdge(metaEdge)     ← 加入 Level-2 图
      │
      └── 关联相似元超边: ltmStore.linkMetaEdges()  ← hypergraph_meta_edge_links
```

### 深度回忆流程

```
用户查询 query
      │
      ▼
DeepRecallEngine.recall(query)
      │
      ├── Step 1: 语义检索
      │     ltmStore.semanticSearch(memoryId, query, k) → 候选超边
      │
      ├── Step 2: 相似度计算
      │     similarity(query, edge):
      │       ├── cosineSimilarity  ← 向量余弦（优先，需要 embeddingGenerator）
      │       └── lexicalSimilarity ← 字符集交集（兜底）
      │     取较大值
      │
      ├── Step 3: 阈值判断
      │     similarity < deepRecallThreshold → 返回 empty
      │
      ├── Step 4: 碎片还原
      │     archiveStore.retrieve(archivePointers) → 原始碎片列表
      │
      └── Step 5: 情境重建
            reconstructor.reconstruct(query, summary, fragments) → 重建文本
```

### 预计算摘要索引流程（v1.2 新增）

```
SummaryPrecomputer.precompute()  (后台每 30 分钟 / consolidate 后触发)
      │
      ├── 处理 Level-1 超边:
      │     for each ltm edge (ltmLevel=1):
      │       ├── ltmGraphIndex.findNeighbors(edgeId, neighborHops=2)
      │       ├── 拼接邻居摘要 → enrichedSummary
      │       ├── embeddingGenerator.embed(enrichedSummary)
      │       └── vectorIndex.upsertSummary(edgeId, enrichedSummary, embedding)
      │
      └── 处理 Level-2 元超边:
            for each metaEdge:
              ├── 拼接 memberEdge 摘要 → enrichedSummary
              ├── embeddingGenerator.embed(enrichedSummary)
              └── vectorIndex.upsertSummary(metaEdge.id(), ...)
```

---

## DSL 配置

```groovy
memory {
    type "hypergraph"

    stm {
        maxEdges  100          // 最大超边数量（溢出时 FIFO 淘汰到 LTM）
        ttlHours  24           // 短期记忆存活时间（过期自动压缩到 LTM）
    }

    ltm {
        backend "sqlite"       // 存储后端（当前支持 sqlite）
        path    "./data/memory.db"
        compressionModel "gemini-2.5-flash"  // 压缩摘要模型（可选，不填则启发式压缩）
    }

    vector {
        store "file-local"     // file-local（持久化）| in-memory（测试用）
        embeddingModel "bge-m3"  // 嵌入模型（可选，不填则用伪嵌入）
        path   "./data/memory.archive.json"
    }

    decay {
        baseRate          0.1  // 基础衰减率
        importanceBoost   5.0  // 重要度压制系数（越高重要记忆越不易衰减）
        compressionThreshold 0.35  // STM→LTM 压缩阈值
        archiveThreshold   0.1     // LTM→Archive 归档阈值
    }

    consolidation {
        intervalHours 6        // 后台整合间隔
        autoStart     true     // 是否自动启动后台任务
    }

    deepRecallThreshold 0.85   // 深度回忆触发阈值
}
```

---

## STM 持久化说明

当前默认的 `InMemoryStmStore` 使用 `ConcurrentHashMap + ConcurrentLinkedDeque`，**进程重启会丢失未压缩到 LTM 的 STM 数据**。

**丢失窗口**：写入 STM → 下次 consolidation 之间（若 `maxEdges=1` 则几乎为零；若 `maxEdges=100` 则最多 100 条）。

**影响评估**：

| 场景 | 影响程度 | 建议 |
|------|----------|------|
| 交互式对话 Agent（用户在线） | 低 — 用户重启后重新提问即可恢复上下文 | 默认实现即可 |
| 无人值守后台 Agent | 高 — 可能丢失批量处理进度 | 实现 `SqliteStmStore` |
| `maxEdges=1` 极小窗口 | 极低 — 每条消息几乎即时压缩到 LTM | 默认实现即可 |

**推荐扩展：SQLite-backed STM**（零新依赖，参见"扩展开发"章节）。

---

## SQLite Schema（v1.1 更新）

```sql
-- Level-1/0 超边（STM 溢出后压缩至此，ltm_level=1）
hypergraph_ltm (id, memory_id, ..., ltm_level, ..., meta_edge_id)

-- Level-2 元超边（AbstractionEngine 聚类生成）
hypergraph_meta_edges (id, member_edge_ids, theme_summary, ...)

-- 元超边间关联（多对多）
hypergraph_meta_edge_links (meta_id_a, meta_id_b)

-- 记忆节点（实体/事件/概念）
hypergraph_nodes (id, memory_id, content, node_type, embedding_json, ...)
```

---

## 衰减公式

```
weight(t) = max(0.01, currentWeight) × e^(-λ × Δt)

其中：
  λ = baseDecayRate / (1 + importance × importanceBoost)
  Δt = 距上次访问的小时数
```

**参数说明**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `baseDecayRate` | 0.1 | 基础衰减率，越大衰减越快 |
| `importanceBoost` | 5.0 | 重要度压制系数，重要度越高 λ 越小 |
| `compressionThreshold` | 0.35 | 低于此权重触发 STM→LTM |
| `archiveThreshold` | 0.1 | 低于此权重触发 LTM→Archive |

**锚点记忆**：`anchor=true` 的超边权重恒为 1.0，不受衰减影响（如 systemPrompt）。

---

## 扩展开发指南

### 1. 新增 STM 持久化（SQLite-backed STM）

适用于无人值守后台 Agent，防止进程崩溃导致 STM 数据丢失：

```java
// 实现 StmStore 接口，复用 hypergraph_ltm 表的 tier='STM' 行
public class SqliteStmStore implements StmStore {

    private final String jdbcUrl;

    @Override
    public void add(HyperEdge edge) {
        // INSERT OR REPLACE into hypergraph_ltm with tier='STM'
        // 建议用 SQLite WAL 模式：PRAGMA journal_mode=WAL
    }

    @Override
    public List<HyperEdge> findAll() {
        // SELECT * FROM hypergraph_ltm WHERE tier='STM' ORDER BY created_at
    }

    // 启动时从 SQLite 重建内存索引（可选，用于提升读取性能）
    public void warmup() {
        // 加载 tier='STM' 的记录到内存缓存
    }
}

// 注入方式：
HypergraphMemoryStore store = new HypergraphMemoryStore(
    config, new SqliteStmStore(jdbcUrl), ltmStore, archiveStore);
```

### 2. 新增 LTM 后端（如 Neo4j）

```java
// 1. 实现 LtmStore 接口
public class Neo4jLtmStore implements LtmStore {
    @Override
    public void save(HyperEdge edge) { /* Neo4j 写入 */ }

    @Override
    public List<HyperEdge> semanticSearch(String memoryId, String query, int limit) {
        /* 向量检索 */
    }
}

// 2. 在 HypergraphMemoryStore.createLtmStore() 添加分支
case "neo4j" -> new Neo4jLtmStore(config);
```

### 3. 新增向量后端（如 Chroma / Qdrant）

```java
// 1. 实现 VectorArchiveStore 接口
public class ChromaVectorArchiveStore implements VectorArchiveStore {
    @Override
    public List<String> archive(HyperEdge edge) { /* Chroma 写入 */ }

    @Override
    public List<String> retrieve(List<String> archivePointers) { /* Chroma 查询 */ }

    @Override
    public void upsertSummary(String edgeId, String enrichedSummary, List<Float> embedding) {
        /* 写入预计算摘要索引，供 SummaryPrecomputer 使用 */
    }
}

// 2. 在 HypergraphMemoryStore.createArchiveStore() 添加分支
case "chroma" -> new ChromaVectorArchiveStore(config);
```

### 4. 自定义摘要压缩策略

```java
// 实现 SummaryCompressor 接口
public class MySummaryCompressor implements SummaryCompressor {
    @Override
    public String compress(String content, int maxLength) {
        return myCompressLogic(content, maxLength);
    }
}

// 通过构造器注入
new HypergraphMemoryStore(config, myCompressor, null, embeddingGenerator);
```

### 5. 自定义重要度评分

```java
// 实现 ImportanceScorer 接口（可调用 LLM 精确评分）
public class LlmImportanceScorer implements ImportanceScorer {
    @Override
    public double score(String text) {
        // 调用 LLM 评估重要度（0.0 ~ 1.0）
        return llmScore(text);
    }
}

// 通过构造器注入
new HypergraphMemoryStore(config, stmStore, ltmStore, archiveStore, myScorer);
```

### 6. 手动触发主题抽象

```java
// AbstractionEngine 可独立使用
AbstractionEngine abstractionEngine = new AbstractionEngine(
    memoryId, ltmStore, ltmGraphIndex, 0.3);  // 0.3 = 聚类相似度阈值
abstractionEngine.abstractClusters();
```

---

## 接口契约

### HypergraphChatMemory（LangChain4j 适配）

标准 LangChain4j 接口 + 扩展方法：

```java
ChatMemory memory = new HypergraphChatMemory(config);

// 写入消息（< 50ms 返回，重评分异步进行）
memory.add(UserMessage.from("你好"));
memory.add(AiMessage.from("你好！有什么可以帮助你的吗？"));

// 读取活跃 STM 消息（组装 LLM 上下文）
List<ChatMessage> messages = memory.messages();

// 深度回忆（扩展方法，从 LTM + Archive 还原）
Optional<String> recalled = ((HypergraphChatMemory) memory).recall("昨天讨论的结论");

// 手动触发整合（扩展方法）
((HypergraphChatMemory) memory).consolidate();
```

### SPI 注册（Java ServiceLoader）

```
META-INF/services/com.agentdsl.core.plugin.AgentDslPlugin
  → com.agentdsl.memory.hypergraph.HypergraphMemoryPlugin
```

`HypergraphMemoryPlugin.register()` 负责：
1. 注册 `"hypergraph"` MemoryFactory → `HypergraphMemoryProvider::create`
2. 注册内置 Skill `"deep_recall"`

---

## 测试

```bash
# 运行单元测试
./gradlew :agentdsl-memory-hypergraph:test

# 运行指定测试类
./gradlew :agentdsl-memory-hypergraph:test --tests "HypergraphChatMemoryTest"

# 运行示例（需要设置 GEMINI_API_KEY）
./shell/agentdsl.sh run examples/long-memory-hypergraph.agent.groovy \
    --chat "请记住我喜欢黑咖啡，不加糖。"
```

---

## 后续优化方向

1. **STM 持久化** — 实现 `SqliteStmStore`，防止进程崩溃丢失未压缩 STM 数据
2. **AbstractionEngine 定时触发** — 将主题聚类接入异步层，每 24 小时自动运行
3. **LLM 驱动抽取** — 替换启发式抽取为模型驱动的图谱抽取，提升 contextTags 质量
4. **真实向量聚类** — AbstractionEngine 用 embedding 相似度替代 Jaccard，提升聚类精度
5. **扩展后端** — 实现 Neo4j / Chroma / Qdrant 适配器
6. **节点级向量索引** — 为 MemoryNode 建立独立的向量索引
7. **多模态支持** — 图片、音频等非文本记忆的存储与检索
8. **分布式部署** — 支持多 Agent 共享记忆网络

### 存储层规模化说明

在实现上述后端扩展之前，需了解当前各层的实际工作方式和真实瓶颈：

#### LTM 检索（真正的性能瓶颈）

`SQLiteLtmStore.semanticSearch()` 当前实现是**字面关键词匹配**，不是向量搜索：

```
semanticSearch()
  └── findAll(memoryId)        ← 全表拉回 Java 内存（O(n) 全表扫描）
        └── 字面 token 重叠打分  ← 无向量计算
```

向量余弦相似度实际发生在 `DeepRecallEngine.similarity()` 对 topK 候选的**重排阶段**，而非检索阶段。

**规模化优先级**（按影响程度排序）：

| 方案 | 适用场景 | 实现复杂度 |
|------|---------|-----------|
| SQLite FTS5 全文索引 | 单机、百万条内，改造成本低 | 低（一行 DDL + 查询改写） |
| Neo4j（已规划） | 图关系复杂、多 Agent 共享 | 高 |
| Chroma / Qdrant（已规划） | 真实 ANN 向量检索 | 中（需独立服务） |

> 当前 LTM 规模（单用户单 Agent）通常在百至千条量级，全表扫描耗时可忽略。
> 超过 **1 万条**时建议引入 FTS5；超过 **10 万条**时建议引入向量检索后端。

#### Archive 冷库（当前瓶颈是序列化，不是检索）

Archive 在深度回忆链路中只做**按 ID 点查**（`retrieve(archivePointers)`），不参与向量搜索：

```
DeepRecallEngine.recall()
  ├── ltmStore.semanticSearch()       ← 向量/关键词搜索在 LTM 侧
  ├── similarity() 阈值过滤
  └── archiveStore.retrieve(ids)      ← Archive 只做 ID 点查，冷路径
```

`upsertSummary()` 的向量能力仅由 `SummaryPrecomputer` 异步调用，不在对话热路径上。

`PersistentFileVectorArchiveStore` 当前问题：每次写入触发全量 JSON 序列化。
推荐替换为 `SQLiteVectorArchiveStore`（文本存 SQLite 表，无需引入向量引擎）：

```sql
-- 推荐 Archive 表结构
CREATE TABLE archive_texts (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE TABLE archive_summaries (
    edge_id TEXT PRIMARY KEY,
    summary TEXT NOT NULL,
    embedding BLOB,        -- float[] 序列化，按需加载做余弦相似度
    updated_at INTEGER NOT NULL
);
```

若日后 `upsertSummary` 的语义检索性能成为瓶颈（Archive 数据量超过 **10 万条**），
再考虑引入 Chroma / Qdrant 作为 `VectorArchiveStore` 的独立向量后端。

---

*本文档对应 AIAgentDSL v1.5.0，超图记忆 v1.2 版本。*

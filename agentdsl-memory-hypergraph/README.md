# agentdsl-memory-hypergraph

> **超图长期记忆模块** — AIAgentDSL 的分层记忆实现，支持短期记忆、长期记忆、深度回忆和超图关系网络。

---

## 模块定位

`agentdsl-memory-hypergraph` 是 AIAgentDSL v1.5 新增的 **SPI 插件模块**，实现基于超图数据结构的分层记忆系统。

**核心特性**：
- **三层记忆架构** — STM（短期）→ LTM（长期）→ Archive（冷库），模拟人脑的记忆分层
- **超图关系网络** — 多元关系超边，支持节点、标签、情绪和边间关联
- **智能衰减策略** — 基于艾宾浩斯遗忘曲线，重要度越高抗衰减越强
- **深度回忆** — 从冷库还原原始碎片，LLM 重建完整记忆情境
- **后台整合** — 模拟"睡眠整合"机制，定时压缩迁移

---

## 架构模式

### 插件化边界

```
┌─────────────────────────────────────────────────────────────┐
│                    AIAgentDSL Runtime                        │
│                                                              │
│   宿主通过 SPI 发现 → HypergraphMemoryProvider               │
│          │                                                   │
│          ▼                                                   │
│   HypergraphChatMemory (实现 LangChain4j ChatMemory 接口)    │
│          │                                                   │
│          └──→ HypergraphMemoryStore (内部协调器，宿主不可见)   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**关键约束**：
- 宿主只依赖 `dev.langchain4j.memory.ChatMemory` 接口
- 插件不依赖 `agentdsl-runtime`、`SkillSpec`、`ToolSpec` 等内部类型
- 可独立拆仓库、单独版本化

### 三层存储架构

```
┌─────────────────────────────────────────────────────────────────┐
│                   HypergraphMemoryStore                          │
│                                                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ STM Store (ConcurrentHashMap)                            │   │
│   │   - 最近 N 条超边，全量细节                                 │   │
│   │   - TTL 自动过期（默认 24-48 小时）                         │   │
│   │   - 容量溢出 FIFO 淘汰                                    │   │
│   └─────────────────────────────────────────────────────────┘   │
│                          │                                       │
│          ┌───────────────┼───────────────┐                       │
│          ▼               ▼               ▼                       │
│   ┌────────────┐  ┌────────────┐  ┌────────────┐                │
│   │ LTM Store  │  │ DecayEngine│  │ Consolidate│                │
│   │ (SQLite)   │  │ (衰减计算)  │  │ Engine     │                │
│   │ 压缩摘要   │  │            │  │ (后台整合)  │                │
│   └────────────┘  └────────────┘  └────────────┘                │
│          │                                                       │
│          ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │ Archive Store (VectorArchiveStore)                       │   │
│   │   - 原始细节碎片                                           │   │
│   │   - 支持语义检索                                           │   │
│   │   - file-local 持久化 / in-memory 临时                     │   │
│   └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 目录结构

```
agentdsl-memory-hypergraph/
├── src/main/java/com/agentdsl/memory/hypergraph/
│   ├── HypergraphChatMemory.java          # ChatMemory 接口实现（宿主入口）
│   ├── HypergraphMemoryStore.java         # 内部存储协调器
│   ├── HypergraphMemoryProvider.java      # SPI Provider（宿主反射发现）
│   │
│   ├── model/                             # 数据模型
│   │   ├── HyperEdge.java                 # 超边（记忆片段的核心单元）
│   │   ├── MemoryNode.java                # 节点（实体/事件/概念/情绪）
│   │   ├── MemoryTier.java                # 记忆层级枚举
│   │   └── EmotionTag.java                # 情绪标签枚举
│   │
│   ├── engine/                            # 核心引擎
│   │   ├── DecayEngine.java               # 权重衰减计算
│   │   ├── ConsolidationEngine.java       # 后台整合（STM→LTM 压缩）
│   │   ├── DeepRecallEngine.java          # 深度回忆（语义检索+重建）
│   │   ├── ImportanceScorer.java          # 重要度评分接口
│   │   ├── HeuristicImportanceScorer.java # 启发式评分实现
│   │   ├── SummaryCompressor.java         # 摘要压缩接口
│   │   ├── HeuristicSummaryCompressor.java# 启发式压缩实现
│   │   ├── ChatModelSummaryCompressor.java# LLM 驱动压缩实现
│   │   ├── MemoryReconstructor.java       # 记忆重建接口
│   │   ├── HeuristicMemoryReconstructor.java# 启发式重建实现
│   │   └── ChatModelMemoryReconstructor.java# LLM 驱动重建实现
│   │
│   ├── store/                             # 存储层
│   │   ├── StmStore.java                  # 短期记忆接口
│   │   ├── InMemoryStmStore.java          # 内存实现（ConcurrentHashMap）
│   │   ├── LtmStore.java                  # 长期记忆接口
│   │   ├── SQLiteLtmStore.java            # SQLite 实现
│   │   ├── VectorArchiveStore.java        # 向量冷库接口
│   │   ├── EmbeddingStoreVectorArchiveStore.java  # 内存实现
│   │   └── PersistentFileVectorArchiveStore.java  # 文件持久化实现
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
│       └── ConsolidationConfig.java       # 整合调度配置
│
└── src/main/resources/
    └── db/schema.sql                      # SQLite DDL
```

---

## 核心流程

### 消息写入流程

```
用户消息 / AI 回复
      │
      ▼
HypergraphChatMemory.add(message)
      │
      ▼
HypergraphMemoryStore.add(message)
      │
      ├──→ toEdge(message) ──→ ChatMessage → HyperEdge
      │         │
      │         ├── ImportanceScorer.score()  // 评分
      │         └── MemoryGraphExtractor.extract()  // 抽取节点/标签/情绪
      │
      ├──→ stmStore.add(edge)  // 写入 STM
      │
      └──→ consolidationEngine.onAfterAdd()  // 即时整合检查
                │
                ├── drainExpiredStm()  // 淘汰过期
                └── evictOverflow()    // 容量溢出淘汰
```

### 后台整合流程

```
ConsolidationEngine.consolidate()  (定时 6 小时 / 手动触发)
      │
      ├── Step 1: 淘汰过期 STM
      │     drainExpiredStm(now, ttl) → 超边列表
      │
      ├── Step 2: 压缩低权重 STM
      │     stmStore.findAll().filter(shouldCompress) → 候选列表
      │     │
      │     └── moveToLongTermMemory(edge):
      │           ├── archiveStore.archive()  // 原始碎片 → 冷库
      │           ├── graphExtractor.extractNodes()  // 提取节点
      │           ├── ltmStore.saveNodes()  // 持久化节点
      │           ├── decayEngine.computeWeight()  // 计算权重
      │           ├── ltmStore.findRelatedEdges()  // 查找关联边
      │           ├── summaryCompressor.compress()  // 生成摘要
      │           ├── ltmStore.save(ltmEdge)  // 写入 LTM
      │           └── 更新关联边的 linkedEdgeIds  // 双向链接
      │
      └── Step 3: 归档极低权重 LTM
            ltmStore.findAll().filter(shouldArchive) → markArchived()
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
      │       ├── cosineSimilarity  // 向量余弦（优先）
      │       └── lexicalSimilarity // 字符集交集（兜底）
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

---

## DSL 配置

```groovy
memory {
    type "hypergraph"

    stm {
        maxEdges  100          // 最大超边数量
        ttlHours  24           // 短期记忆存活时间
    }

    ltm {
        backend "sqlite"       // 存储后端（当前仅支持 sqlite）
        path    "./data/memory.db"
        compressionModel "qwen3:4b"  // 压缩摘要模型（可选）
    }

    vector {
        store "file-local"     // file-local | in-memory
        embeddingModel "bge-m3"  // 嵌入模型
    }

    decay {
        baseRate          0.1  // 基础衰减率
        importanceBoost   5.0  // 重要度压制系数
        accessBonus       0.05 // 访问频次奖励
        compressionThreshold 0.35  // STM→LTM 压缩阈值
        archiveThreshold   0.1     // LTM→Archive 归档阈值
    }

    consolidation {
        intervalHours 6        // 后台整合间隔
        autoStart     true     // 是否自动启动
    }

    deepRecallThreshold 0.85   // 深度回忆触发阈值
}
```

---

## 扩展开发指南

### 1. 新增 LTM 后端（如 Neo4j）

```java
// 1. 实现 LtmStore 接口
public class Neo4jLtmStore implements LtmStore {
    @Override
    public void save(HyperEdge edge) { /* Neo4j 写入 */ }

    @Override
    public List<HyperEdge> semanticSearch(String memoryId, String query, int limit) {
        /* 向量检索 */
    }

    // ... 其他方法
}

// 2. 在 HypergraphMemoryStore.createLtmStore() 添加分支
case "neo4j" -> new Neo4jLtmStore(config);

// 3. 更新 DSL 验证器，允许 ltm.backend = "neo4j"
```

### 2. 新增向量后端（如 Chroma / Qdrant）

```java
// 1. 实现 VectorArchiveStore 接口
public class ChromaVectorArchiveStore implements VectorArchiveStore {
    @Override
    public List<String> archive(HyperEdge edge) { /* Chroma 写入 */ }

    @Override
    public List<String> retrieve(List<String> archivePointers) { /* Chroma 查询 */ }
}

// 2. 在 HypergraphMemoryStore.createArchiveStore() 添加分支
case "chroma" -> new ChromaVectorArchiveStore(config);

// 3. 更新 DSL 验证器
```

### 3. 自定义摘要压缩策略

```java
// 实现 SummaryCompressor 接口
public class MySummaryCompressor implements SummaryCompressor {
    @Override
    public String compress(String content, int maxLength) {
        // 自定义压缩逻辑
        return myCompressLogic(content, maxLength);
    }
}

// 通过 HypergraphMemoryStore 构造器注入
new HypergraphMemoryStore(config, myCompressor, null, embeddingGenerator);
```

### 4. 自定义记忆重建策略

```java
// 实现 MemoryReconstructor 接口
public class MyMemoryReconstructor implements MemoryReconstructor {
    @Override
    public String reconstruct(String query, String summary, List<String> fragments) {
        // 自定义重建逻辑
        return myReconstructLogic(query, summary, fragments);
    }
}

// 通过 HypergraphMemoryStore 构造器注入
new HypergraphMemoryStore(config, null, myReconstructor, embeddingGenerator);
```

### 5. 自定义重要度评分

```java
// 实现 ImportanceScorer 接口
public class MyImportanceScorer implements ImportanceScorer {
    @Override
    public double score(String text) {
        // 自定义评分逻辑（可调用 LLM）
        return myScoreLogic(text);
    }
}

// 通过 HypergraphMemoryStore 构造器注入
new HypergraphMemoryStore(config, stmStore, ltmStore, archiveStore, myScorer);
```

### 6. 新增记忆层级

当前支持 `WORKING / STM / LTM / ARCHIVE` 四级，如需新增：

```java
// 1. 在 MemoryTier 枚举添加新值
public enum MemoryTier {
    WORKING, STM, LTM, ARCHIVE,
    FROZEN  // 新增：冻结记忆
}

// 2. 在 DecayEngine 添加对应的判断逻辑
public boolean shouldFreeze(HyperEdge edge, Instant now) {
    return computeWeight(edge, now) < config.freezeThreshold();
}

// 3. 在 ConsolidationEngine 添加冻结流程
```

---

## 衰减公式

```
weight(t) = max(0.01, currentWeight) × e^(-λ × Δt) + accessCount × α

其中：
  λ = baseDecayRate / (1 + importance × importanceBoost)
  Δt = 距上次访问的小时数
  α = accessBonus（访问频次奖励系数）
```

**参数说明**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `baseDecayRate` | 0.1 | 基础衰减率，越大衰减越快 |
| `importanceBoost` | 5.0 | 重要度压制系数，重要度越高 λ 越小 |
| `accessBonus` | 0.05 | 每次访问的权重奖励 |
| `compressionThreshold` | 0.35 | 低于此权重触发 STM→LTM |
| `archiveThreshold` | 0.1 | 低于此权重触发 LTM→Archive |

**锚点记忆**：`anchor=true` 的超边权重恒为 1.0，不受衰减影响（如 systemPrompt）。

---

## 接口契约

### HypergraphMemoryProvider（SPI 发现）

宿主通过反射调用：

```java
// 1. 发现 provider
Class<?> providerClass = Class.forName("com.agentdsl.memory.hypergraph.HypergraphMemoryProvider");
Object provider = providerClass.getDeclaredConstructor().newInstance();

// 2. 获取类型标识
String type = (String) providerClass.getMethod("getType").invoke(provider);  // "hypergraph"

// 3. 创建 ChatMemory
Map<String, Object> config = /* 从 DSL 配置构建 */;
ChatMemory memory = (ChatMemory) providerClass.getMethod("create", Map.class).invoke(provider, config);

// 4. 发现能力（可选）
List<Object> capabilities = (List<Object>) memory.getClass().getMethod("getCapabilities").invoke(memory);
```

### HypergraphChatMemory（LangChain4j 适配）

标准 LangChain4j 接口：

```java
ChatMemory memory = new HypergraphChatMemory(config);

// 写入消息
memory.add(UserMessage.from("你好"));
memory.add(AiMessage.from("你好！有什么可以帮助你的吗？"));

// 读取消息
List<ChatMessage> messages = memory.messages();

// 深度回忆（扩展方法）
Optional<String> recalled = ((HypergraphChatMemory) memory).recall("昨天讨论的结论");

// 手动整合（扩展方法）
((HypergraphChatMemory) memory).consolidate();
```

---

## 测试

```bash
# 运行单元测试
./gradlew :agentdsl-memory-hypergraph:test

# 运行指定测试类
./gradlew :agentdsl-memory-hypergraph:test --tests "HypergraphChatMemoryTest"
```

---

## 后续优化方向

1. **扩展后端** — 实现 Neo4j / Chroma / Qdrant 适配器
2. **图查询增强** — 支持超图遍历、路径查询、社区发现
3. **LLM 驱动抽取** — 替换启发式抽取为模型驱动的图谱抽取
4. **节点级向量索引** — 为 MemoryNode 建立独立的向量索引
5. **多模态支持** — 图片、音频等非文本记忆的存储与检索
6. **分布式部署** — 支持多 Agent 共享记忆网络

---

*本文档对应 AIAgentDSL v1.5.0 版本。*

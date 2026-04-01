package com.agentdsl.memory.hypergraph;

import com.agentdsl.memory.hypergraph.config.HypergraphMemoryConfig;
import com.agentdsl.memory.hypergraph.capability.ConsolidateMemoryCapability;
import com.agentdsl.memory.hypergraph.capability.DeepRecallCapability;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LangChain4j ChatMemory 接口的超图记忆实现。
 *
 * <p>这是插件对宿主暴露的唯一入口。宿主（agentdsl-runtime）通过 LangChain4j 的
 * {@link ChatMemory} 接口与记忆系统交互，完全不需要了解底层的 STM/LTM/归档分层。</p>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>持有 {@link HypergraphMemoryStore}，将所有读写操作委托给它</li>
 *   <li>对外暴露 {@link #getCapabilities()}，让宿主发现并桥接 deep_recall / consolidate 能力</li>
 *   <li>不直接操作任何存储或引擎，保持薄适配层定位</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 1. 通过配置创建
 * HypergraphChatMemory memory = new HypergraphChatMemory(config);
 *
 * // 2. 注入到 LangChain4j 对话流程
 * memory.add(UserMessage.from("你好"));
 * List<ChatMessage> messages = memory.messages();
 *
 * // 3. 触发深度回忆
 * memory.recall("昨天讨论的结论").ifPresent(System.out::println);
 * }</pre>
 *
 * @see HypergraphMemoryStore
 * @see dev.langchain4j.memory.ChatMemory
 */
public class HypergraphChatMemory implements ChatMemory {

    private static final int PRECOMPUTE_TRIGGER_INTERVAL = 10;

    private final HypergraphMemoryConfig config;
    private final HypergraphMemoryStore store;
    private final AtomicLong addCounter = new AtomicLong(0);

    /**
     * 使用默认依赖创建超图记忆实例。
     * <p>内部会根据配置自动创建 STM、LTM、归档存储和所有引擎组件。</p>
     *
     * @param config 超图记忆配置（来自 DSL memory {} 块）
     */
    public HypergraphChatMemory(HypergraphMemoryConfig config) {
        this(config, new HypergraphMemoryStore(config));
    }

    /**
     * 使用外部注入的存储协调器创建实例。
     * <p>适用于需要自定义组件（如注入真实 LLM 压缩模型）的场景。</p>
     *
     * @param config 配置
     * @param store  已初始化的存储协调器
     */
    public HypergraphChatMemory(HypergraphMemoryConfig config, HypergraphMemoryStore store) {
        this.config = config;
        this.store = store;
    }

    /** {@inheritDoc} 返回 memoryId 作为唯一标识。 */
    @Override
    public Object id() {
        return config.memoryId();
    }

    /**
     * {@inheritDoc}
     * <p>将消息写入 STM，并触发即时整合检查（过期淘汰 + 溢出压缩）。</p>
     */
    @Override
    public void add(ChatMessage message) {
        store.add(message);
        // 每隔 N 条消息懒触发一次预计算摘要索引更新（异步非阻塞）
        if (addCounter.incrementAndGet() % PRECOMPUTE_TRIGGER_INTERVAL == 0) {
            store.triggerSummaryPrecompute();
        }
    }

    /**
     * {@inheritDoc}
     * <p>先清空当前所有记忆，再逐条写入新消息。用于对话重置场景。</p>
     */
    @Override
    public void set(Iterable<ChatMessage> messages) {
        store.set(messages);
    }

    /**
     * {@inheritDoc}
     * <p>从 STM 获取当前活跃的记忆消息列表（按时间顺序）。</p>
     */
    @Override
    public List<ChatMessage> messages() {
        return store.messages();
    }

    /**
     * {@inheritDoc}
     * <p>清空 STM、LTM 和归档存储中的所有数据。不可恢复。</p>
     */
    @Override
    public void clear() {
        store.clear();
    }

    /**
     * 手动触发一次记忆整合（STM → LTM 压缩 + LTM → Archive 归档）。
     * <p>通常由后台调度自动执行，此方法供外部按需调用。</p>
     */
    public void consolidate() {
        store.consolidate();
    }

    /**
     * 对指定查询执行深度回忆。
     * <p>在 LTM 摘要中语义检索，若相似度超过阈值则从归档冷库还原原始碎片并重建情境。</p>
     *
     * @param query 回忆查询文本
     * @return 重建的记忆内容；如果未触发深度回忆则返回 empty
     */
    public Optional<String> recall(String query) {
        return store.recall(query);
    }

    /**
     * 获取此记忆实例暴露的能力列表。
     * <p>宿主通过反射调用此方法，发现并桥接 DSL 内置 skill（如 deep_recall、consolidate_memory）。</p>
     *
     * @return 能力对象列表（DeepRecallCapability、ConsolidateMemoryCapability）
     */
    public List<Object> getCapabilities() {
        return List.of(
                new DeepRecallCapability(this),
                new ConsolidateMemoryCapability(this));
    }
}

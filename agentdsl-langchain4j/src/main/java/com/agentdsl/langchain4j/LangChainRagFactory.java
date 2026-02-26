package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.ContentRetrieverSpec;
import com.agentdsl.core.spec.RagSpec;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangChain4j RAG 工厂。
 * 根据 RagSpec 创建 ContentRetriever 实例。
 */
public class LangChainRagFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainRagFactory.class);

    /**
     * 根据 RagSpec 创建 ContentRetriever。
     *
     * @param ragSpec RAG 配置规范
     * @return ContentRetriever 实例，如果 ragSpec 为 null 返回 null
     */
    public ContentRetriever create(RagSpec ragSpec) {
        if (ragSpec == null || ragSpec.getContentRetriever() == null) {
            return null;
        }

        ContentRetrieverSpec retrieverSpec = ragSpec.getContentRetriever();
        log.info("创建 ContentRetriever: type={}, embeddingModel={}, maxResults={}, minScore={}",
                retrieverSpec.getType(), retrieverSpec.getEmbeddingModel(),
                retrieverSpec.getMaxResults(), retrieverSpec.getMinScore());

        // 创建嵌入模型
        EmbeddingModel embeddingModel = createEmbeddingModel(retrieverSpec.getEmbeddingModel());

        // 创建嵌入存储（默认使用内存存储）
        EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(retrieverSpec.getType());

        // 构建 ContentRetriever
        EmbeddingStoreContentRetriever.EmbeddingStoreContentRetrieverBuilder builder = EmbeddingStoreContentRetriever
                .builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel);

        if (retrieverSpec.getMaxResults() != null) {
            builder.maxResults(retrieverSpec.getMaxResults());
        }

        if (retrieverSpec.getMinScore() != null && retrieverSpec.getMinScore() > 0.0) {
            builder.minScore(retrieverSpec.getMinScore());
        }

        ContentRetriever retriever = builder.build();
        log.info("ContentRetriever 创建成功");
        return retriever;
    }

    /**
     * 创建嵌入模型。
     * 目前使用内置的 AllMiniLmL6V2 模型作为默认实现。
     * 未来可扩展支持 OpenAI、DashScope 等远程嵌入模型。
     */
    private EmbeddingModel createEmbeddingModel(String modelName) {
        // 默认使用内置的 AllMiniLmL6V2 轻量级嵌入模型
        // 它是一个纯 Java ONNX 模型，无需外部 API 调用
        log.info("使用内置嵌入模型: AllMiniLmL6V2 (配置值: {})", modelName);
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * 创建嵌入存储。
     * 目前仅支持内存存储，后续可扩展 Chroma、Milvus 等外部存储。
     */
    private EmbeddingStore<TextSegment> createEmbeddingStore(String type) {
        log.info("使用内存嵌入存储 (配置类型: {})", type);
        return new InMemoryEmbeddingStore<>();
    }
}

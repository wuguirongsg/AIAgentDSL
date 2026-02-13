package com.agentdsl.core.spec;

/**
 * RAG 配置规范。
 * 对应 DSL 中的 rag { ... } 块。
 */
public class RagSpec {

    private ContentRetrieverSpec contentRetriever;

    public ContentRetrieverSpec getContentRetriever() {
        return contentRetriever;
    }

    public void setContentRetriever(ContentRetrieverSpec contentRetriever) {
        this.contentRetriever = contentRetriever;
    }

    @Override
    public String toString() {
        return "RagSpec{contentRetriever=" + contentRetriever + '}';
    }
}

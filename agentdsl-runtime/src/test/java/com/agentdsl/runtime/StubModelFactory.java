package com.agentdsl.runtime;

import com.agentdsl.core.spec.ModelSpec;
import com.agentdsl.langchain4j.LangChainModelFactory;
import dev.langchain4j.model.chat.ChatModel;

/**
 * 用于测试的 ModelFactory 子类。
 * 覆盖 create() 方法返回预设的 StubChatModel，绕过真实 API key 校验。
 */
public class StubModelFactory extends LangChainModelFactory {

    private final StubChatModel stubModel;

    public StubModelFactory(StubChatModel stubModel) {
        this.stubModel = stubModel;
    }

    @Override
    public ChatModel create(ModelSpec spec) {
        return stubModel;
    }
}

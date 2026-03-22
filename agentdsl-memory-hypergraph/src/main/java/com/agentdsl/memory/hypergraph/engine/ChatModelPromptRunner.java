package com.agentdsl.memory.hypergraph.engine;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

final class ChatModelPromptRunner {

    private ChatModelPromptRunner() {
    }

    static String generate(ChatModel model, String prompt) {
        if (model == null || prompt == null || prompt.isBlank()) {
            return "";
        }
        var response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build());
        if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
            return "";
        }
        return response.aiMessage().text().trim();
    }
}

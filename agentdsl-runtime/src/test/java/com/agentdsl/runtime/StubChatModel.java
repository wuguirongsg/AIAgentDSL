package com.agentdsl.runtime;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 用于测试的 ChatModel 桩实现。
 * 可预设一系列回复，每次 chat() 调用依次返回。
 */
public class StubChatModel implements ChatModel {

    private final Queue<ChatResponse> responses = new LinkedList<>();
    private final List<ChatRequest> receivedRequests = new LinkedList<>();

    /**
     * 添加一条固定文本回复。
     */
    public StubChatModel addTextResponse(String text) {
        responses.add(ChatResponse.builder()
                .aiMessage(new AiMessage(text))
                .build());
        return this;
    }

    /**
     * 添加一条工具调用回复。
     */
    public StubChatModel addToolCallResponse(String toolName, String argumentsJson) {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call-" + toolName)
                .name(toolName)
                .arguments(argumentsJson)
                .build();
        responses.add(ChatResponse.builder()
                .aiMessage(new AiMessage(List.of(toolRequest)))
                .build());
        return this;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        receivedRequests.add(chatRequest);
        if (responses.isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("default stub response"))
                    .build();
        }
        return responses.poll();
    }

    /**
     * 获取收到的所有请求（用于断言）。
     */
    public List<ChatRequest> getReceivedRequests() {
        return receivedRequests;
    }
}

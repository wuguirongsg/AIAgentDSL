package com.agentdsl.runtime;

import com.agentdsl.core.exception.DslRuntimeException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 执行引擎。
 * 负责调用 LangChain4j 模型，处理工具调用循环。
 */
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final AgentRegistry registry;

    public AgentExecutor(AgentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 向指定 Agent 发送消息并获取回复。
     *
     * @param agentName   Agent 名称
     * @param userMessage 用户消息
     * @return Agent 回复文本
     */
    public String chat(String agentName, String userMessage) {
        AgentInstance instance = registry.get(agentName);
        return execute(instance, userMessage);
    }

    /**
     * 执行一次完整的对话交互，包含工具调用循环。
     */
    private String execute(AgentInstance instance, String userMessage) {
        ChatModel model = instance.getModel();
        String agentName = instance.getName();

        log.info("[{}] 收到消息: {}", agentName, truncate(userMessage, 100));

        // 1. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();

        // SystemPrompt
        String systemPrompt = instance.getSpec().getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt));
        }

        // 从记忆中恢复历史消息
        if (instance.getMemory() != null) {
            messages.addAll(instance.getMemory().messages());
        }

        // 添加用户消息（可能经过 RAG 增强）
        String augmentedMessage = augmentWithRag(instance, userMessage);
        UserMessage userMsg = UserMessage.from(augmentedMessage);
        messages.add(userMsg);

        // 保存到记忆
        if (instance.getMemory() != null) {
            instance.getMemory().add(userMsg);
        }

        // 2. 工具调用循环
        List<ToolSpecification> tools = instance.hasTools()
                ? instance.getToolSpecifications()
                : null;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            ChatResponse response;

            try {
                ChatRequest.Builder requestBuilder = ChatRequest.builder()
                        .messages(messages);
                if (tools != null && !tools.isEmpty()) {
                    requestBuilder.toolSpecifications(tools);
                }
                response = model.chat(requestBuilder.build());
            } catch (Exception e) {
                throw new DslRuntimeException("ADSL-020",
                        "Agent '" + instance.getName() + "' 模型调用失败: " + e.getMessage(), e);
            }

            AiMessage aiMessage = response.aiMessage();

            // 保存 AI 消息到记忆
            if (instance.getMemory() != null) {
                instance.getMemory().add(aiMessage);
            }
            messages.add(aiMessage);

            // 3. 检查是否有工具调用
            if (!aiMessage.hasToolExecutionRequests()) {
                // 没有工具调用，直接返回文本回复
                String text = aiMessage.text();
                log.info("[{}] 回复: {}", agentName, truncate(text, 100));
                return text;
            }

            // 4. 执行工具调用
            log.debug("[{}] 工具调用请求: {} 个", agentName,
                    aiMessage.toolExecutionRequests().size());

            for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolRequest.name();
                ToolExecutor executor = instance.getToolExecutors().get(toolName);

                if (executor == null) {
                    log.warn("[{}] 未找到工具执行器: {}", agentName, toolName);
                    String errorResult = "Error: Tool '" + toolName + "' not found";
                    ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(
                            toolRequest, errorResult);
                    messages.add(resultMsg);
                    if (instance.getMemory() != null) {
                        instance.getMemory().add(resultMsg);
                    }
                    continue;
                }

                String result = executor.execute(toolRequest, agentName);
                log.debug("[{}] 工具 '{}' 返回: {}", agentName, toolName, truncate(result, 200));

                ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(
                        toolRequest, result);
                messages.add(resultMsg);
                if (instance.getMemory() != null) {
                    instance.getMemory().add(resultMsg);
                }
            }
        }

        throw new DslRuntimeException("ADSL-020",
                "Agent '" + instance.getName() + "' 工具调用循环超过最大次数: " + MAX_TOOL_ITERATIONS);
    }

    /**
     * 如果 Agent 配置了 RAG，检索相关内容并增强用户消息。
     */
    private String augmentWithRag(AgentInstance instance, String userMessage) {
        ContentRetriever retriever = instance.getContentRetriever();
        if (retriever == null) {
            return userMessage;
        }

        try {
            List<Content> contents = retriever.retrieve(Query.from(userMessage));
            if (contents == null || contents.isEmpty()) {
                log.debug("[{}] RAG 未检索到相关内容", instance.getName());
                return userMessage;
            }

            String context = contents.stream()
                    .map(c -> c.textSegment().text())
                    .collect(Collectors.joining("\n\n"));

            log.debug("[{}] RAG 检索到 {} 条相关内容", instance.getName(), contents.size());

            return """
                    以下是与问题相关的参考信息：
                    ---
                    %s
                    ---

                    用户问题：%s""".formatted(context, userMessage);
        } catch (Exception e) {
            log.warn("[{}] RAG 检索失败，回退为原始消息: {}", instance.getName(), e.getMessage());
            return userMessage;
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null)
            return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

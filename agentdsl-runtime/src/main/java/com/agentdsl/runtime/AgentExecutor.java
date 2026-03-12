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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.agentdsl.core.metrics.DebugEvent;
import com.agentdsl.core.metrics.DebugTracer;

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

        if (DebugTracer.isEnabled()) {
            Map<String, Object> details = new HashMap<>();
            details.put("modelProvider", instance.getSpec().getModel().getProvider());
            details.put("modelName", instance.getSpec().getModel().getModelName());
            details.put("systemPrompt", instance.getSpec().getSystemPrompt());
            details.put("userMessage", userMessage);
            DebugTracer.record(DebugEvent.Type.AGENT_START, agentName, details);
            DebugTracer.enter();
        }

        try {
            int dynamicDiscoveryAttempts = 0;
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

            // 2. 收集工具
            List<ToolSpecification> tools = instance.hasTools()
                    ? new ArrayList<>(instance.getToolSpecifications())
                    : new ArrayList<>();
            Map<String, ToolExecutor> allToolExecutors = instance.hasTools()
                    ? new HashMap<>(instance.getToolExecutors())
                    : new HashMap<>();

            // 2.1 主动发现：若 auto_discover_mcp=true 且当前没有任何工具，
            //     在第一次模型调用前基于用户消息主动搜索并挂载 MCP 工具。
            if (tools.isEmpty() && instance.getSpec().isAutoDiscoverMcp()) {
                log.info("[{}] 当前无工具，触发主动 MCP 发现, userMessage={}", agentName,
                        truncate(userMessage, 60));
                boolean discovered = registry.tryAutoDiscoverAndAttachTool(instance, "", userMessage);
                if (discovered) {
                    tools = new ArrayList<>(instance.getToolSpecifications());
                    allToolExecutors.putAll(instance.getToolExecutors());
                    dynamicDiscoveryAttempts++;
                    log.info("[{}] 主动 MCP 发现完成，加载了 {} 个工具", agentName, tools.size());
                } else {
                    log.warn("[{}] 主动 MCP 发现未找到合适工具，继续执行（无工具）", agentName);
                }
            }

            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                ChatResponse response;

                try {
                    ChatRequest.Builder requestBuilder = ChatRequest.builder()
                            .messages(messages);

                    if (DebugTracer.isEnabled()) {
                        Map<String, Object> reqDetails = new HashMap<>();
                        reqDetails.put("iteration", iteration);
                        List<Map<String, String>> msgList = messages.stream().map(m -> {
                            String cText = "(tool call/result)";
                            if (m instanceof dev.langchain4j.data.message.UserMessage um) {
                                cText = um.contents().stream()
                                        .filter(c -> c instanceof dev.langchain4j.data.message.TextContent)
                                        .map(c -> ((dev.langchain4j.data.message.TextContent) c).text())
                                        .collect(Collectors.joining("\\n"));
                            } else if (m instanceof dev.langchain4j.data.message.AiMessage aim) {
                                cText = aim.text() != null ? aim.text() : cText;
                            } else if (m instanceof dev.langchain4j.data.message.SystemMessage sm) {
                                cText = sm.text();
                            } else if (m instanceof dev.langchain4j.data.message.ToolExecutionResultMessage trm) {
                                cText = trm.text();
                            }
                            return Map.of("type", m.type().name(), "text", cText);
                        }).collect(Collectors.toList());
                        reqDetails.put("messages", msgList);
                        if (!tools.isEmpty()) {
                            reqDetails.put("tools",
                                    tools.stream().map(ToolSpecification::name).collect(Collectors.toList()));
                        }
                        DebugTracer.record(DebugEvent.Type.MODEL_REQUEST, agentName, reqDetails);
                    }

                    if (!tools.isEmpty()) {
                        requestBuilder.toolSpecifications(tools);
                    }

                    long modelStartTime = System.currentTimeMillis();
                    response = model.chat(requestBuilder.build());
                    long modelDuration = System.currentTimeMillis() - modelStartTime;

                    if (DebugTracer.isEnabled()) {
                        Map<String, Object> resDetails = new HashMap<>();
                        resDetails.put("durationMs", modelDuration);
                        resDetails.put("text", response.aiMessage().text());
                        resDetails.put("hasToolCalls", response.aiMessage().hasToolExecutionRequests());
                        DebugTracer.record(DebugEvent.Type.MODEL_RESPONSE, agentName, resDetails);
                    }
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
                    // 某些模型（如 Gemini）在工具调用完成后可能返回 null text
                    // 此时说明任务已完成，返回最后一条工具执行结果的摘要
                    if (text == null || text.isBlank()) {
                        // 从 messages 里找最后一条 ToolExecutionResultMessage 的内容作为回复
                        String lastToolResult = findLastToolResult(messages);
                        text = lastToolResult != null
                                ? lastToolResult
                                : "✅ 任务已完成（模型无额外回复）";
                    }
                    log.info("[{}] 回复: {}", agentName, truncate(text, 100));

                    if (DebugTracer.isEnabled()) {
                        DebugTracer.exit();
                        DebugTracer.record(DebugEvent.Type.AGENT_END, agentName,
                                Map.of("reply", text, "iterations", iteration + 1));
                    }
                    return text;
                }

                // 4. 执行工具调用
                log.debug("[{}] 工具调用请求: {} 个", agentName,
                        aiMessage.toolExecutionRequests().size());

                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    String toolName = toolRequest.name();

                    if (DebugTracer.isEnabled()) {
                        Map<String, Object> toolReqDetails = new HashMap<>();
                        toolReqDetails.put("toolName", toolName);
                        toolReqDetails.put("arguments", toolRequest.arguments());
                        DebugTracer.record(DebugEvent.Type.TOOL_CALL_REQUEST, agentName, toolReqDetails);
                    }

                    ToolExecutor executor = allToolExecutors.get(toolName);

                    if (executor == null) {
                        boolean discoverySucceeded = false;
                        if (instance.getSpec().isAutoDiscoverMcp() && dynamicDiscoveryAttempts < 1) {
                            dynamicDiscoveryAttempts++;
                            discoverySucceeded = registry.tryAutoDiscoverAndAttachTool(instance, toolName, userMessage);
                            if (discoverySucceeded) {
                                // 刷新本轮可用工具列表，确保后续模型请求可见新工具。
                                tools = new ArrayList<>(instance.getToolSpecifications());
                                allToolExecutors.putAll(instance.getToolExecutors());
                                executor = allToolExecutors.get(toolName);
                                log.info("[{}] 工具 '{}' 已通过动态 MCP 发现挂载", agentName, toolName);
                            }
                        }
                    }

                    if (executor == null) {
                        log.warn("[{}] 未找到工具执行器: {}", agentName, toolName);
                        String errorResult = "Error: Tool '" + toolName + "' not found";

                        if (DebugTracer.isEnabled()) {
                            DebugTracer.record(DebugEvent.Type.TOOL_CALL_RESULT, agentName,
                                    Map.of("toolName", toolName, "result", errorResult, "error", true));
                        }

                        ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(
                                toolRequest, errorResult);
                        messages.add(resultMsg);
                        if (instance.getMemory() != null) {
                            instance.getMemory().add(resultMsg);
                        }
                        continue;
                    }

                    long toolStartTime = System.currentTimeMillis();
                    String result;
                    try {
                        result = executor.execute(toolRequest, agentName);
                    } catch (Exception e) {
                        result = "Error executing tool: " + e.getMessage();
                        log.error("[{}] 工具 '{}' 执行异常: {}", agentName, toolName, e.getMessage());
                    }
                    long toolDuration = System.currentTimeMillis() - toolStartTime;

                    if (DebugTracer.isEnabled()) {
                        DebugTracer.record(DebugEvent.Type.TOOL_CALL_RESULT, agentName,
                                Map.of("toolName", toolName, "result", result, "durationMs", toolDuration));
                    }

                    log.info("[{}] 工具 '{}' 执行完成: {}", agentName, toolName, truncate(result, 200));

                    ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(
                            toolRequest, result);
                    messages.add(resultMsg);
                    if (instance.getMemory() != null) {
                        instance.getMemory().add(resultMsg);
                    }
                }

                // 5. 所有工具执行完后，所有工具结果都已加入 messages
                // 继续循环：再次调用模型，让模型基于工具结果生成最终回复
                // （模型可能再次调用工具，或产生最终文本回复）

            }

            if (DebugTracer.isEnabled()) {
                DebugTracer.exit();
                DebugTracer.record(DebugEvent.Type.AGENT_END, agentName,
                        Map.of("error", "Max tool iterations reached"));
            }
            throw new DslRuntimeException("ADSL-020",
                    "Agent '" + instance.getName() + "' 工具调用循环超过最大次数: " + MAX_TOOL_ITERATIONS);
        } catch (Exception e) {
            if (DebugTracer.isEnabled()) {
                DebugTracer.exit();
                DebugTracer.record(DebugEvent.Type.AGENT_END, agentName, Map.of("error", e.getMessage()));
            }
            throw e;
        }
    }

    /**
     * 从消息历史中找最后一条 ToolExecutionResultMessage 的内容。
     * 用于 Gemini 等模型在工具调用完成后不返回文本时的降级回复。
     */
    private static String findLastToolResult(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolExecutionResultMessage t) {
                return t.text();
            }
        }
        return null;
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

package com.agentdsl.runtime;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * LLM 调用监听器接口。
 *
 * <p>当需要观察每次 LLM 调用的原始输入/输出时（例如 {@code --verbose} 模式），
 * 实现此接口并注入到 {@link AgentExecutor} 或
 * {@link com.agentdsl.runtime.autonomous.AutonomousExecutor} 中。
 *
 * <p>该接口定义在 {@code agentdsl-runtime} 模块，
 * 具体渲染逻辑（例如终端彩色打印）由 {@code agentdsl-cli} 模块实现，
 * 避免反向依赖。
 */
public interface LlmCallListener {

    /**
     * 在模型调用发出之前触发。
     *
     * @param messages 本次发送的消息列表（含 system / user / assistant / tool 历史）
     * @param tools    本次可用的工具列表，无工具时为空列表
     */
    void onRequest(List<ChatMessage> messages, List<ToolSpecification> tools);

    /**
     * 在模型调用返回之后触发。
     *
     * @param aiMessage 模型返回的 AI 消息（文本或工具调用请求）
     * @param elapsedMs 本次调用耗时（毫秒）
     */
    void onResponse(AiMessage aiMessage, long elapsedMs);
}

package com.example.demo;

import com.agentdsl.runtime.AgentDslEngine;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 示例服务：展示如何在业务代码中注入和使用 AgentDslEngine。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final AgentDslEngine engine;

    public DemoController(AgentDslEngine engine) {
        this.engine = engine;
    }

    /**
     * 与指定 Agent 进行对话。
     * 
     * @param agentName Agent 名称
     * @param request 请求体，包含 message 字段
     * @return Agent 的回复
     */
    @PostMapping("/chat/{agentName}")
    public Map<String, Object> chat(
            @PathVariable String agentName,
            @RequestBody Map<String, String> request) {
        
        String message = request.getOrDefault("message", "Hello");
        String response = engine.chat(agentName, message);
        
        return Map.of(
            "agent", agentName,
            "message", message,
            "response", response
        );
    }

    /**
     * 执行指定工作流。
     * 
     * @param workflowName 工作流名称
     * @param request 请求体，包含 input 字段
     * @return 工作流执行结果
     */
    @PostMapping("/workflow/{workflowName}")
    public Map<String, Object> executeWorkflow(
            @PathVariable String workflowName,
            @RequestBody Map<String, String> request) {
        
        String input = request.getOrDefault("input", "");
        var result = engine.executeWorkflow(workflowName, input);
        
        return Map.of(
            "workflow", workflowName,
            "input", input,
            "output", result.getFinalOutputAsString() != null ? result.getFinalOutputAsString() : "",
            "success", result.getFinalOutput() != null
        );
    }

    /**
     * 列出所有已注册的 Agent。
     */
    @GetMapping("/agents")
    public Map<String, Object> listAgents() {
        return Map.of("agents", engine.getRegistry().getAgentNames());
    }

    /**
     * 列出所有已注册的 Workflow。
     */
    @GetMapping("/workflows")
    public Map<String, Object> listWorkflows() {
        return Map.of("workflows", engine.getRegistry().getWorkflowNames());
    }
}
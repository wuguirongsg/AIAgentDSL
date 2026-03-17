package com.agentdsl.springboot.web;

import com.agentdsl.runtime.AgentDslEngine;
import com.agentdsl.runtime.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AgentDSL REST API Controller。
 * 
 * <p>提供以下端点：
 * <ul>
 *   <li>GET /api/agents - 列出所有 Agent</li>
 *   <li>POST /api/agents/{name}/chat - 与 Agent 对话</li>
 *   <li>GET /api/workflows - 列出所有 Workflow</li>
 *   <li>POST /api/workflows/{name}/execute - 执行 Workflow</li>
 * </ul>
 */
@RestController
public class AgentDslRestController {

    private static final Logger log = LoggerFactory.getLogger(AgentDslRestController.class);

    private final AgentDslEngine engine;

    public AgentDslRestController(AgentDslEngine engine, String basePath) {
        this.engine = engine;
    }

    @GetMapping("${agentdsl.api.base-path:/api}/agents")
    public ResponseEntity<List<AgentInfo>> listAgents() {
        List<AgentInfo> agents = engine.getRegistry().getAgentNames().stream()
                .map(name -> new AgentInfo(name, getAgentDescription(name)))
                .toList();
        return ResponseEntity.ok(agents);
    }

    @PostMapping("${agentdsl.api.base-path:/api}/agents/{name}/chat")
    public ResponseEntity<AgentChatResponse> chat(
            @PathVariable String name,
            @RequestBody AgentChatRequest request) {
        
        log.debug("Chat request: agent={}, message={}", name, request.message());
        
        try {
            String response = engine.chat(name, request.message());
            return ResponseEntity.ok(new AgentChatResponse(response, true, null));
        } catch (Exception e) {
            log.error("Chat failed: agent={}, error={}", name, e.getMessage(), e);
            return ResponseEntity.ok(new AgentChatResponse(null, false, e.getMessage()));
        }
    }

    @GetMapping("${agentdsl.api.base-path:/api}/workflows")
    public ResponseEntity<List<WorkflowInfo>> listWorkflows() {
        List<WorkflowInfo> workflows = engine.getRegistry().getWorkflowNames().stream()
                .map(name -> new WorkflowInfo(name, getWorkflowDescription(name)))
                .toList();
        return ResponseEntity.ok(workflows);
    }

    @PostMapping("${agentdsl.api.base-path:/api}/workflows/{name}/execute")
    public ResponseEntity<WorkflowExecuteResponse> executeWorkflow(
            @PathVariable String name,
            @RequestBody WorkflowExecuteRequest request) {
        
        log.debug("Workflow execute request: workflow={}, input={}", name, request.input());
        
        try {
            WorkflowResult result = engine.executeWorkflow(name, request.input());
            return ResponseEntity.ok(new WorkflowExecuteResponse(
                    result.getFinalOutputAsString(),
                    result.getStepResults(),
                    true,
                    null
            ));
        } catch (Exception e) {
            log.error("Workflow execution failed: workflow={}, error={}", name, e.getMessage(), e);
            return ResponseEntity.ok(new WorkflowExecuteResponse(
                    null,
                    null,
                    false,
                    e.getMessage()
            ));
        }
    }

    private String getAgentDescription(String name) {
        try {
            var spec = engine.getRegistry().get(name).getSpec();
            return spec.getDescription() != null ? spec.getDescription() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String getWorkflowDescription(String name) {
        try {
            var workflow = engine.getRegistry().getWorkflow(name);
            return workflow.getDescription() != null ? workflow.getDescription() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // --- DTO Records ---

    public record AgentInfo(String name, String description) {}

    public record WorkflowInfo(String name, String description) {}

    public record AgentChatRequest(String message) {}

    public record AgentChatResponse(String content, boolean success, String error) {}

    public record WorkflowExecuteRequest(String input) {}

    public record WorkflowExecuteResponse(
            String finalOutput,
            Map<String, Object> stepResults,
            boolean success,
            String error
    ) {}
}
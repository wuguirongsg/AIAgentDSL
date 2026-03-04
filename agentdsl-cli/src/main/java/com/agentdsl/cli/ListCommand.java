package com.agentdsl.cli;

import com.agentdsl.compiler.DslCompileResult;
import com.agentdsl.compiler.DslCompiler;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import com.agentdsl.core.spec.ToolSpec;
import com.agentdsl.core.spec.WorkflowSpec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code agentdsl list} — 列出脚本中定义的所有 Agent、独立 Tool、Workflow、Skill。
 *
 * <h3>示例</h3>
 * 
 * <pre>
 *   agentdsl list examples/workflow-pipeline.agent.groovy
 *   agentdsl list examples/skill-demo.agent.groovy --format json
 * </pre>
 */
@Command(name = "list", description = "列出 DSL 脚本中定义的所有 Agent、工具、工作流和技能", mixinStandardHelpOptions = true)
public class ListCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "DSL 脚本文件路径 (.agent.groovy)")
    private Path scriptPath;

    @Option(names = { "--format", "-f" }, description = "输出格式: text（默认）或 json", defaultValue = "text")
    private String format;

    @Override
    public Integer call() {
        try {
            DslCompiler compiler = new DslCompiler();
            DslCompileResult result = compiler.compileFile(scriptPath);

            if ("json".equalsIgnoreCase(format)) {
                printJson(result);
            } else {
                printText(result, scriptPath);
            }

            return 0;
        } catch (Exception e) {
            System.err.println("❌ 解析失败: " + e.getMessage());
            return 1;
        }
    }

    private void printText(DslCompileResult result, Path scriptPath) {
        System.out.println("📄 脚本: " + scriptPath);
        System.out.println();

        // Agents
        System.out.printf("🤖 Agents (%d):%n", result.getAgents().size());
        if (result.getAgents().isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (AgentSpec agent : result.getAgents()) {
                String model = agent.getModel() != null
                        ? agent.getModel().getProvider() + "/" + agent.getModel().getModelName()
                        : "未配置";
                int toolCount = agent.getTools() != null ? agent.getTools().size() : 0;
                int toolRefCount = agent.getToolRefs() != null ? agent.getToolRefs().size() : 0;
                System.out.printf("  %-25s [model: %s, tools: %d, includes: %d]%n",
                        agent.getName(), model, toolCount, toolRefCount);
            }
        }

        System.out.println();

        // Standalone Tools
        System.out.printf("🔧 独立工具 (%d):%n", result.getTools().size());
        if (result.getTools().isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (ToolSpec tool : result.getTools()) {
                System.out.printf("  %-25s — %s%n", tool.getName(),
                        tool.getDescription() != null ? tool.getDescription() : "无描述");
            }
        }

        System.out.println();

        // Workflows
        System.out.printf("⚙️  工作流 (%d):%n", result.getWorkflows().size());
        if (result.getWorkflows().isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (WorkflowSpec workflow : result.getWorkflows()) {
                int stepCount = workflow.getSteps() != null ? workflow.getSteps().size() : 0;
                System.out.printf("  %-25s [steps: %d]%n", workflow.getName(), stepCount);
            }
        }

        System.out.println();

        // Skills
        System.out.printf("💡 技能 (Skills) (%d):%n", result.getSkills().size());
        if (result.getSkills().isEmpty()) {
            System.out.println("  (无)");
        } else {
            for (SkillSpec skill : result.getSkills()) {
                System.out.printf("  %-25s [type: %-6s] — %s%n",
                        skill.getName(),
                        skill.getType() != null ? skill.getType().name().toLowerCase() : "?",
                        skill.getDescription() != null ? skill.getDescription() : "无描述");
            }
        }
    }

    private void printJson(DslCompileResult result) {
        // 简单 JSON 输出（不引入额外序列化依赖）
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"agents\": [");
        for (int i = 0; i < result.getAgents().size(); i++) {
            AgentSpec a = result.getAgents().get(i);
            sb.append("\n    {\"name\": \"").append(a.getName()).append("\"");
            if (a.getModel() != null) {
                sb.append(", \"model\": \"")
                        .append(a.getModel().getProvider()).append("/")
                        .append(a.getModel().getModelName()).append("\"");
            }
            sb.append("}");
            if (i < result.getAgents().size() - 1)
                sb.append(",");
        }
        sb.append("\n  ],\n");
        sb.append("  \"tools\": [");
        for (int i = 0; i < result.getTools().size(); i++) {
            ToolSpec t = result.getTools().get(i);
            sb.append("\n    {\"name\": \"").append(t.getName()).append("\"}");
            if (i < result.getTools().size() - 1)
                sb.append(",");
        }
        sb.append("\n  ],\n");
        sb.append("  \"workflows\": [");
        for (int i = 0; i < result.getWorkflows().size(); i++) {
            WorkflowSpec w = result.getWorkflows().get(i);
            int steps = w.getSteps() != null ? w.getSteps().size() : 0;
            sb.append("\n    {\"name\": \"").append(w.getName())
                    .append("\", \"steps\": ").append(steps).append("}");
            if (i < result.getWorkflows().size() - 1)
                sb.append(",");
        }
        sb.append("\n  ],\n");
        sb.append("  \"skills\": [");
        for (int i = 0; i < result.getSkills().size(); i++) {
            SkillSpec s = result.getSkills().get(i);
            sb.append("\n    {\"name\": \"").append(s.getName())
                    .append("\", \"type\": \"").append(s.getType() != null ? s.getType().name().toLowerCase() : "")
                    .append("\"}");
            if (i < result.getSkills().size() - 1)
                sb.append(",");
        }
        sb.append("\n  ]\n}");
        System.out.println(sb);
    }
}

package com.agentdsl.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * AgentDSL 命令行工具主入口。
 *
 * <h3>使用方式</h3>
 * 
 * <pre>
 *   # 校验脚本语法
 *   agentdsl validate examples/simple-chat.agent.groovy
 *
 *   # 运行脚本并向 Agent 发送消息
 *   agentdsl run examples/simple-chat.agent.groovy --chat "你好"
 *
 *   # 列出脚本中定义的所有 Agent、工具、工作流
 *   agentdsl list examples/workflow-pipeline.agent.groovy
 *
 *   # 执行工作流
 *   agentdsl run examples/workflow.agent.groovy --workflow translate-pipeline --input "Hello"
 * </pre>
 */
@Command(name = "agentdsl", mixinStandardHelpOptions = true, version = "AgentDSL CLI 0.1.0", description = "AgentDSL 命令行工具 — 运行、校验、检查 .agent.groovy 脚本", subcommands = {
        RunCommand.class,
        ValidateCommand.class,
        ListCommand.class,
        CommandLine.HelpCommand.class
})
public class AgentDslCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AgentDslCli())
                .setUsageHelpWidth(120)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // 无子命令时显示帮助
        CommandLine.usage(this, System.out);
    }
}

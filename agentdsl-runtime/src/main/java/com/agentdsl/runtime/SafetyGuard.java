package com.agentdsl.runtime;

import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全拦截系统.
 * 主要用于拦截 MCP Server (如 Browser Use) 的敏感操作，实现 Human In The Loop (HITL).
 */
public class SafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuard.class);

    /**
     * 在控制台向用户发起是否继续操作的确认.
     * 
     * @param toolName  被拦截的工具名
     * @param arguments 传递给工具的参数
     * @return 如果用户允许执行，返回 true；否则返回 false
     */
    public boolean confirmAction(String toolName, String arguments) {
        System.out.println("=================================================");
        System.out.println("⚠️ 安全拦截 (HITL)：检测到敏感操作即将执行");
        System.out.println("    工具名称: " + toolName);
        System.out.println("    操作参数: " + arguments);
        System.out.print("\n是否允许本次操作执行？(y/n) [默认 n]: ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();

        if ("y".equalsIgnoreCase(input) || "yes".equalsIgnoreCase(input)) {
            log.info("用户已授权执行危险工具: {}", toolName);
            return true;
        } else {
            log.warn("用户拒绝执行危险工具: {}", toolName);
            return false;
        }
    }
}

package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentDSL Spring Boot Demo 应用。
 * 
 * <p>启动后可访问：
 * <ul>
 *   <li>GET /api/agents - 列出所有 Agent</li>
 *   <li>POST /api/agents/{name}/chat - 与 Agent 对话</li>
 *   <li>GET /api/workflows - 列出所有 Workflow</li>
 *   <li>POST /api/workflows/{name}/execute - 执行 Workflow</li>
 * </ul>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
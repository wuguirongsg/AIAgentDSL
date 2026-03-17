package com.agentdsl.springboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentScanner 脚本扫描器测试。
 * 
 * 注意：部分测试需要 agentdsl-core 模块完全加载才能运行。
 * 这里只测试路径解析功能，不需要完整的 DSL 编译器依赖。
 */
class AgentScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCountScanResultsCorrectly() {
        // 这是一个纯数据类测试，不依赖 DSL 编译器
        AgentScanner.ScanResult result = new AgentScanner.ScanResult();
        
        assertThat(result.filesLoaded).isEqualTo(0);
        assertThat(result.agentsAdded).isEqualTo(0);
        assertThat(result.toolsAdded).isEqualTo(0);
        assertThat(result.workflowsAdded).isEqualTo(0);
        assertThat(result.errors).isEqualTo(0);
        
        result.filesLoaded = 5;
        result.agentsAdded = 10;
        result.toolsAdded = 20;
        result.workflowsAdded = 3;
        result.errors = 1;
        
        assertThat(result.toString()).contains("files=5", "agents=10", "tools=20", "workflows=3", "errors=1");
    }

    // 以下测试依赖 agentdsl-core 模块完整加载，
    // 在解决 logback 版本冲突后启用
    
    // @Test
    // void shouldScanDslFilesFromFilesystem() throws IOException, URISyntaxException { ... }
    
    // @Test
    // void shouldReturnEmptyListForNonexistentDirectory() throws Exception { ... }
    
    // @Test
    // void shouldSupportFilePrefix() throws IOException, URISyntaxException { ... }
}
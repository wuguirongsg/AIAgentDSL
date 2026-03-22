package com.agentdsl.compiler;

import com.agentdsl.core.spec.AgentSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryDslExtensionTest {

    @Test
    @DisplayName("memory 块应兼容过渡期的扁平插件扩展参数")
    void shouldParsePluginSpecificMemoryOptions() {
        String dsl = """
                agent("hyper-memory-agent") {
                    model {
                        provider "openai"
                        modelName "gpt-4"
                    }
                    memory {
                        type "hypergraph"
                        stmMaxMessages 12
                        sqliteJdbcUrl "jdbc:sqlite:./build/custom-memory.db"
                        summaryMaxLength 128
                    }
                }
                """;

        DslCompiler compiler = new DslCompiler();
        DslCompileResult result = compiler.compile(dsl);
        AgentSpec agent = result.getFirstAgent();

        assertEquals("hypergraph", agent.getMemory().getType());
        assertEquals(12, agent.getMemory().getOption("stmMaxMessages"));
        assertEquals("jdbc:sqlite:./build/custom-memory.db", agent.getMemory().getOption("sqliteJdbcUrl"));
        assertEquals(128, agent.getMemory().getOption("summaryMaxLength"));
    }

    @Test
    @DisplayName("memory 块应支持设计稿中的结构化 hypergraph 配置")
    void shouldParseStructuredHypergraphMemoryOptions() {
        String dsl = """
                agent("hyper-memory-agent") {
                    model {
                        provider "openai"
                        modelName "gpt-4"
                    }
                    memory {
                        type "hypergraph"
                        stm {
                            maxEdges 12
                            ttlHours 24
                        }
                        ltm {
                            backend "sqlite"
                            path "./build/custom-memory.db"
                            compressionModel "qwen3:4b"
                        }
                        vector {
                            store "file-local"
                            embeddingModel "bge-m3"
                            path "./build/custom-memory.archive.json"
                        }
                        decay {
                            baseRate 0.1
                            importanceBoost 5.0
                            compressionThreshold 0.35
                        }
                        consolidation {
                            intervalHours 6
                        }
                        deepRecallThreshold 0.85
                    }
                }
                """;

        DslCompiler compiler = new DslCompiler();
        DslCompileResult result = compiler.compile(dsl);
        AgentSpec agent = result.getFirstAgent();

        assertEquals("hypergraph", agent.getMemory().getType());
        assertEquals(12, agent.getMemory().getStm().getMaxEdges());
        assertEquals(24, agent.getMemory().getStm().getTtlHours());
        assertEquals("sqlite", agent.getMemory().getLtm().getBackend());
        assertEquals("./build/custom-memory.db", agent.getMemory().getLtm().getPath());
        assertEquals("qwen3:4b", agent.getMemory().getLtm().getCompressionModel());
        assertEquals("file-local", agent.getMemory().getVector().getStore());
        assertEquals("bge-m3", agent.getMemory().getVector().getEmbeddingModel());
        assertEquals("./build/custom-memory.archive.json", agent.getMemory().getVector().getPath());
        assertEquals(0.1, agent.getMemory().getDecay().getBaseRate());
        assertEquals(5.0, agent.getMemory().getDecay().getImportanceBoost());
        assertEquals(0.35, agent.getMemory().getDecay().getCompressionThreshold());
        assertEquals(6, agent.getMemory().getConsolidation().getIntervalHours());
        assertEquals(0.85, agent.getMemory().getDeepRecallThreshold());
    }
}

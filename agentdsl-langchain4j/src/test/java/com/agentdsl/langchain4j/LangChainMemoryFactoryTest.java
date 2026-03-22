package com.agentdsl.langchain4j;

import com.agentdsl.core.spec.MemorySpec;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LangChainMemoryFactoryTest {

    @Test
    @DisplayName("应通过 SPI 发现 hypergraph memory 插件")
    void shouldDiscoverHypergraphMemoryPlugin() throws Exception {
        Path tempDir = Files.createTempDirectory("langchain-memory-factory-test");
        MemorySpec spec = new MemorySpec();
        spec.setType("hypergraph");
        spec.putOption("memoryId", "test-hypergraph");
        spec.putOption("sqliteJdbcUrl", "jdbc:sqlite:" + tempDir.resolve("test.db"));
        spec.putOption("stmMaxMessages", 2);

        LangChainMemoryFactory factory = new LangChainMemoryFactory();
        ChatMemory memory = factory.create(spec);

        assertNotNull(memory);
        assertEquals("com.agentdsl.memory.hypergraph.HypergraphChatMemory", memory.getClass().getName());

        memory.add(UserMessage.from("第一条"));
        memory.add(AiMessage.from("第二条"));
        memory.add(UserMessage.from("第三条"));

        assertEquals(2, memory.messages().size(), "STM 溢出后只保留最近 2 条消息");
    }
}

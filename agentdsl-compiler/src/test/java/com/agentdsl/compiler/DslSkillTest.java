package com.agentdsl.compiler;

import com.agentdsl.core.exception.DslCompilationException;
import com.agentdsl.core.spec.AgentSpec;
import com.agentdsl.core.spec.SkillSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DslSkillTest — Skills 特性单元测试。
 * 验证 skill() 顶层关键字和 skills { include } 块的解析与校验。
 */
class DslSkillTest {

    private DslCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DslCompiler();
    }

    @Nested
    @DisplayName("Prompt Skill 解析")
    class PromptSkillParsing {

        @Test
        @DisplayName("基础 Prompt Skill 解析")
        void shouldParsePromptSkill() {
            String dsl = """
                        skill("summarize") {
                            type        "prompt"
                            description "对文本生成摘要"
                            instruction "请将输入文本用 3 句以内的中文总结。"

                            parameter {
                                name        "text"
                                type        "string"
                                description "待摘要的文本"
                                required    true
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(0, result.getAgents().size());
            assertEquals(0, result.getTools().size());
            assertEquals(1, result.getSkills().size());

            SkillSpec skill = result.getSkills().get(0);
            assertEquals("summarize", skill.getName());
            assertEquals(SkillSpec.SkillType.PROMPT, skill.getType());
            assertEquals("对文本生成摘要", skill.getDescription());
            assertEquals("请将输入文本用 3 句以内的中文总结。", skill.getInstruction());
            assertNull(skill.getExecuteBody());
            assertEquals(1, skill.getParameters().size());
            assertEquals("text", skill.getParameters().get(0).getName());
            assertTrue(skill.getParameters().get(0).isRequired());
        }

        @Test
        @DisplayName("Prompt Skill 缺少 instruction 应抛出异常")
        void shouldThrowWhenInstructionMissing() {
            String dsl = """
                        skill("badPrompt") {
                            type        "prompt"
                            description "没有 instruction 的 prompt skill"
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("instruction"));
        }
    }

    @Nested
    @DisplayName("Logic Skill 解析")
    class LogicSkillParsing {

        @Test
        @DisplayName("基础 Logic Skill 解析")
        void shouldParseLogicSkill() {
            String dsl = """
                        skill("formatList") {
                            type        "logic"
                            description "将逗号分隔的字符串格式化为有序列表"

                            parameter {
                                name        "items"
                                type        "string"
                                description "逗号分隔的列表项"
                                required    true
                            }

                            execute { params ->
                                def itemList = params.items?.split(",") ?: []
                                itemList.eachWithIndex { item, idx ->
                                    println("${idx + 1}. ${item.trim()}")
                                }
                                return "done"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(1, result.getSkills().size());

            SkillSpec skill = result.getSkills().get(0);
            assertEquals("formatList", skill.getName());
            assertEquals(SkillSpec.SkillType.LOGIC, skill.getType());
            assertEquals("将逗号分隔的字符串格式化为有序列表", skill.getDescription());
            assertNotNull(skill.getExecuteBody());
            assertNull(skill.getInstruction());
            assertEquals(1, skill.getParameters().size());
        }

        @Test
        @DisplayName("Logic Skill 缺少 execute 应抛出异常")
        void shouldThrowWhenExecuteMissing() {
            String dsl = """
                        skill("noExec") {
                            type        "logic"
                            description "缺少 execute 的 logic skill"
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("execute"));
        }
    }

    @Nested
    @DisplayName("Skill 基础校验")
    class SkillValidation {

        @Test
        @DisplayName("Skill 缺少 type 应抛出异常")
        void shouldThrowWhenTypeMissing() {
            String dsl = """
                        skill("noType") {
                            description "缺少 type 字段"
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("type"));
        }

        @Test
        @DisplayName("Skill 缺少 description 应抛出异常")
        void shouldThrowWhenDescriptionMissing() {
            String dsl = """
                        skill("noDesc") {
                            type        "logic"
                            execute { params -> "ok" }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-001"));
            assertTrue(ex.getMessage().contains("description"));
        }

        @Test
        @DisplayName("Skill type 无效值应抛出异常")
        void shouldThrowWhenTypeInvalid() {
            String dsl = """
                        skill("badType") {
                            type        "unknown_type"
                            description "无效的 type 值"
                        }
                    """;

            assertThrows(Exception.class, () -> compiler.compile(dsl));
        }

        @Test
        @DisplayName("多个 Skill 定义在同一脚本")
        void shouldParseMultipleSkills() {
            String dsl = """
                        skill("skillA") {
                            type        "prompt"
                            description "技能 A"
                            instruction "执行 A"
                        }

                        skill("skillB") {
                            type        "logic"
                            description "技能 B"
                            execute { params -> "B result" }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(2, result.getSkills().size());
            assertEquals("skillA", result.getSkills().get(0).getName());
            assertEquals("skillB", result.getSkills().get(1).getName());
        }
    }

    @Nested
    @DisplayName("Agent 挂载 Skill")
    class AgentSkillMounting {

        @Test
        @DisplayName("Agent 挂载 Skill，skillRefs 正确解析")
        void shouldParseAgentWithSkillRefs() {
            String dsl = """
                        skill("mySkill") {
                            type        "prompt"
                            description "我的技能"
                            instruction "执行我的技能"
                        }

                        agent("myAgent") {
                            model {
                                provider  "openai"
                                modelName "gpt-4"
                            }
                            skills {
                                include "mySkill"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(1, result.getSkills().size());
            assertEquals(1, result.getAgents().size());

            AgentSpec agent = result.getFirstAgent();
            assertEquals(1, agent.getSkillRefs().size());
            assertTrue(agent.getSkillRefs().contains("mySkill"));
        }

        @Test
        @DisplayName("Agent 包含多个 Skill 引用")
        void shouldParseMultipleSkillRefs() {
            String dsl = """
                        skill("skillOne") {
                            type        "prompt"
                            description "技能一"
                            instruction "执行技能一"
                        }

                        skill("skillTwo") {
                            type        "logic"
                            description "技能二"
                            execute { params -> "ok" }
                        }

                        agent("superAgent") {
                            model {
                                provider  "ollama"
                                modelName "qwen2.5:3b"
                            }
                            skills {
                                include "skillOne"
                                include "skillTwo"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            AgentSpec agent = result.getFirstAgent();
            assertEquals(2, agent.getSkillRefs().size());
            assertTrue(agent.getSkillRefs().contains("skillOne"));
            assertTrue(agent.getSkillRefs().contains("skillTwo"));
        }

        @Test
        @DisplayName("Agent 引用未定义的 Skill 应抛出异常")
        void shouldThrowWhenSkillRefUndefined() {
            String dsl = """
                        skill("existingSkill") {
                            type        "prompt"
                            description "已定义技能"
                            instruction "执行"
                        }

                        agent("badAgent") {
                            model {
                                provider  "openai"
                                modelName "gpt-4"
                            }
                            skills {
                                include "nonExistentSkill"
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-003"));
            assertTrue(ex.getMessage().contains("nonExistentSkill"));
        }

        @Test
        @DisplayName("Agent 同时使用工具和技能")
        void shouldParseAgentWithBothToolsAndSkills() {
            String dsl = """
                        tool("myTool") {
                            description "我的工具"
                            execute { params -> "tool result" }
                        }

                        skill("mySkill") {
                            type        "logic"
                            description "我的技能"
                            execute { params -> "skill result" }
                        }

                        agent("mixedAgent") {
                            model {
                                provider  "openai"
                                modelName "gpt-4"
                            }
                            tools {
                                include "myTool"
                            }
                            skills {
                                include "mySkill"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(1, result.getTools().size());
            assertEquals(1, result.getSkills().size());
            assertEquals(1, result.getAgents().size());

            AgentSpec agent = result.getFirstAgent();
            assertTrue(agent.getToolRefs().contains("myTool"));
            assertTrue(agent.getSkillRefs().contains("mySkill"));
        }

        @Test
        @DisplayName("hypergraph memory 可直接引用内置 deep_recall skill")
        void shouldAllowBuiltinDeepRecallSkillForHypergraphMemory() {
            String dsl = """
                        agent("memoryAgent") {
                            model {
                                provider  "ollama"
                                modelName "qwen2.5:3b"
                            }
                            memory {
                                type "hypergraph"
                                stm {
                                    maxEdges 2
                                }
                                ltm {
                                    backend "sqlite"
                                    path "./build/test-memory.db"
                                }
                            }
                            skills {
                                include "deep_recall"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);

            AgentSpec agent = result.getFirstAgent();
            assertTrue(agent.getSkillRefs().contains("deep_recall"));
        }

        @Test
        @DisplayName("非 hypergraph memory 不应引用内置 deep_recall skill")
        void shouldRejectBuiltinDeepRecallSkillWithoutHypergraphMemory() {
            String dsl = """
                        agent("badAgent") {
                            model {
                                provider  "ollama"
                                modelName "qwen2.5:3b"
                            }
                            memory {
                                type "message_window"
                                maxMessages 10
                            }
                            skills {
                                include "deep_recall"
                            }
                        }
                    """;

            DslCompilationException ex = assertThrows(
                    DslCompilationException.class,
                    () -> compiler.compile(dsl));
            assertTrue(ex.getMessage().contains("ADSL-003"));
            assertTrue(ex.getMessage().contains("deep_recall"));
        }
    }

    @Nested
    @DisplayName("DslCompileResult Skills 字段")
    class CompileResultSkills {

        @Test
        @DisplayName("无 Skill 的脚本，getSkills() 返回空列表")
        void shouldReturnEmptySkillsWhenNone() {
            String dsl = """
                        agent("simple") {
                            model {
                                provider  "openai"
                                modelName "gpt-4"
                            }
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            assertNotNull(result.getSkills());
            assertEquals(0, result.getSkills().size());
        }

        @Test
        @DisplayName("getSkills() 返回只读列表")
        void shouldReturnUnmodifiableSkillsList() {
            String dsl = """
                        skill("readonlyTest") {
                            type        "prompt"
                            description "只读列表测试"
                            instruction "测试指令"
                        }
                    """;

            DslCompileResult result = compiler.compile(dsl);
            List<SkillSpec> skills = result.getSkills();

            assertThrows(UnsupportedOperationException.class,
                    () -> skills.add(new SkillSpec("hack")));
        }
    }

    @Nested
    @DisplayName("includeFile — 外部 .skill.md 文件")
    class IncludeFileSkill {

        /**
         * 解析项目根目录（agentdsl-compiler 的父目录）。
         * Gradle 测试的 CWD 是子模块目录，需要向上查找项目根。
         */
        private static String resolveProjectRoot() {
            // CWD 是 agentdsl-compiler/，父目录是项目根
            java.io.File cwd = new java.io.File(System.getProperty("user.dir"));
            // 如果当前目录有 settings.gradle.kts 就是根目录，否则上移一级
            if (new java.io.File(cwd, "settings.gradle.kts").exists()) {
                return cwd.getAbsolutePath();
            }
            return cwd.getParentFile().getAbsolutePath();
        }

        @Test
        @DisplayName("includeFile 正确解析品牌规范 Skill 文件")
        void shouldParseSkillFromMdFile() {
            String root = resolveProjectRoot();
            String skillPath = root + "/skills/brand-guidelines.skill.md";

            String dsl = """
                        agent("brandAgent") {
                            model {
                                provider  "openai"
                                modelName "gpt-4"
                            }
                            skills {
                                includeFile "%s"
                            }
                        }
                    """.formatted(skillPath);

            DslCompileResult result = compiler.compile(dsl);

            assertEquals(1, result.getAgents().size());
            AgentSpec agent = result.getFirstAgent();

            // includeFile 不产生全局 Skill，而是直接写入 agent.inlineSkills
            assertEquals(0, agent.getSkillRefs().size(),
                    "includeFile 不应产生 skillRefs");
            assertEquals(1, agent.getInlineSkills().size(),
                    "includeFile 应产生 1 个 inlineSkill");

            SkillSpec skill = agent.getInlineSkills().get(0);
            assertEquals("brand-guidelines", skill.getName());
            assertEquals(SkillSpec.SkillType.PROMPT, skill.getType());
            // description 从 YAML frontmatter 提取，应包含 brand 或 Anthropic
            assertTrue(skill.getDescription().toLowerCase().contains("brand")
                    || skill.getDescription().contains("Anthropic"),
                    "description 应来自 YAML frontmatter，实际: " + skill.getDescription());
            // instruction 是 frontmatter 之后的正文
            assertNotNull(skill.getInstruction());
            assertTrue(skill.getInstruction().length() > 50,
                    "instruction 应包含完整的品牌规范正文，长度: " + skill.getInstruction().length());
        }

        @Test
        @DisplayName("includeFile 文件不存在应抛出异常")
        void shouldThrowWhenFileNotFound() {
            String dsl = """
                        agent("badAgent") {
                            model {
                                provider  "openai"
                                modelName "gpt-4"
                            }
                            skills {
                                includeFile "skills/does-not-exist.skill.md"
                            }
                        }
                    """;

            Exception ex = assertThrows(Exception.class, () -> compiler.compile(dsl));
            // 异常或 cause 中包含找不到文件的信息
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            String causeMsg = ex.getCause() != null ? ex.getCause().getMessage() : "";
            assertTrue(msg.contains("does-not-exist") || causeMsg.contains("does-not-exist")
                    || msg.contains("找不到") || causeMsg.contains("找不到"),
                    "异常信息应包含文件路径，message=" + msg + ", cause=" + causeMsg);
        }
    }
}

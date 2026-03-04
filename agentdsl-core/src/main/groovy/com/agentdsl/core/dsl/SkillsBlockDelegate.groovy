package com.agentdsl.core.dsl

import com.agentdsl.core.spec.*

/**
 * Agent 内 skills { } 块的委托类。
 * 处理 skills { include "..." } 和 skills { includeFile "path" } 关键字。
 *
 * 支持两种引用方式：
 * - include "skillName"：引用已在脚本顶层用 skill() {...} 定义的全局技能
 * - includeFile "path/to/xxx.skill.md"：直接读取外部 skill 文件（如 Anthropic Skills 格式），
 *   自动解析 YAML frontmatter，将正文作为 instruction 生成 Prompt Skill 内联到 AgentSpec
 */
class SkillsBlockDelegate {

    private final AgentSpec spec

    SkillsBlockDelegate(AgentSpec spec) {
        this.spec = spec
    }

    /**
     * include "skill-name" — 引用已在脚本顶层定义的全局技能。
     * 可多次调用引用多个技能。
     */
    void include(String skillName) {
        spec.skillRefs << skillName
    }

    /**
     * includeFile "path/to/xxx.skill.md" — 读取外部 Skill 文件并内联到 Agent。
     *
     * <p>支持标准的 YAML frontmatter 格式：
     * <pre>
     * ---
     * name: my-skill
     * description: 技能的语义描述
     * ---
     * # Skill 正文内容（作为 instruction 注入 LLM system prompt）
     * </pre>
     *
     * <p>frontmatter 中的 name 和 description 会自动提取；正文内容整体作为
     * 该 Prompt Skill 的 instruction 使用。
     *
     * <p>路径解析顺序：
     * <ol>
     *   <li>以传入路径直接读取（绝对路径或相对于 JVM 工作目录）</li>
     *   <li>如果找不到，抛出 DslCompilationException</li>
     * </ol>
     */
    void includeFile(String path) {
        def file = new File(path)
        if (!file.exists()) {
            throw new com.agentdsl.core.exception.DslCompilationException(
                'ADSL-001',
                "includeFile 找不到 Skill 文件: ${path}，请检查路径是否正确（相对于工作目录）")
        }

        def content = file.text
        def parsed  = parseSkillMd(content, file)

        def skillSpec = new SkillSpec(parsed.name)
        skillSpec.description = parsed.description
        skillSpec.setTypeFromString('prompt')
        skillSpec.instruction = parsed.body

        spec.inlineSkills << skillSpec
    }

    // ─── 私有工具方法 ───────────────────────────────────────────────────────

    /**
     * 解析 .skill.md 文件内容。
     * 返回 Map：name, description, body
     */
    private static Map<String, String> parseSkillMd(String content, File file) {
        def result = [
            name       : deriveNameFromFile(file),
            description: '',
            body       : content
        ]

        // 解析 YAML frontmatter（--- ... ---）
        if (!content.startsWith('---')) {
            // 没有 frontmatter，整个内容作为 instruction
            return result
        }

        def lines  = content.split('\n') as List
        // 找第二个 --- 的行号
        int endIdx = -1
        for (int i = 1; i < lines.size(); i++) {
            if (lines[i].trim() == '---') {
                endIdx = i
                break
            }
        }

        if (endIdx < 0) {
            // 没有找到结束 ---，整个内容作为 instruction
            return result
        }

        // 解析 frontmatter 键值对
        lines[1..<endIdx].each { String line ->
            def colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                def key   = line[0..<colonIdx].trim().toLowerCase()
                def value = line[(colonIdx + 1)..-1].trim()
                // 去掉引号
                value = value.replaceAll(/^['"]|['"]$/, '')
                if (key == 'name')        result.name        = value
                if (key == 'description') result.description = value
            }
        }

        // 正文 = frontmatter 结束行之后的所有内容
        result.body = lines[(endIdx + 1)..-1].join('\n').trim()

        return result
    }

    /** 从文件名推导技能名（去掉 .skill.md 或 .md 后缀） */
    private static String deriveNameFromFile(File file) {
        def name = file.name
        name = name.replaceAll(/\.skill\.md$/, '')
        name = name.replaceAll(/\.md$/, '')
        return name
    }

}

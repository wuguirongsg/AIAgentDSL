/**
 * brand-homepage.agent.groovy
 *
 * 演示：使用 includeFile 直接加载 Anthropic Skills 格式的 .skill.md 文件。
 * 该 Agent 使用 brand-guidelines.skill.md 中的品牌规范，生成品牌官网首页 HTML。
 *
 * 运行方式（在项目根目录）：
 *   export GEMINI_API_KEY=xxx
 *   java -jar agentdsl-cli/build/libs/agentdsl.jar run \
 *     examples/brand-homepage.agent.groovy \
 *     --agent brandWebAgent \
 *     --chat "生成 AgentDSL 品牌官网首页 HTML 并保存文件"
 */

// ─── Agent：品牌官网生成器 ──────────────────────────────────────────────────

agent('brandWebAgent') {
    description '品牌官网首页生成器 — 遵循 Anthropic 品牌规范生成高质量 HTML，并使用内置 file_write 工具保存'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        // provider  'openai'
        // modelName 'gpt-4o'
        temperature   0.4
        maxTokens  8192
    }

    systemPrompt '''
        你是一位专业的前端开发工程师和品牌设计师，擅长根据品牌规范生成精美的响应式 HTML 页面。

        ## 任务流程（严格按此顺序执行）

        当用户要求生成品牌官网时，你必须：

        1. 生成完整的 HTML 内容（单文件，内联 CSS 和少量内联 JS）
        2. 立即调用 file_write 工具，将 HTML 写入文件：
           - path: "output/brand-homepage.html"
           - content: <完整HTML内容>
        3. 返回"✅ 已生成并保存 output/brand-homepage.html"

        ## 代码要求
        - 单文件 HTML，所有 CSS 写在 <style> 标签内
        - 使用 Google Fonts 引入 Poppins 和 Lora 字体
        - 完全响应式（手机/平板/桌面）
        - 包含：Hero 区、特性展示区、代码示例区、CTA 区、Footer
        - 科技感强：渐变背景、微动画、代码高亮风格元素

        ## 重要：你必须调用 file_write 工具来保存文件，不要只输出 HTML 文本。
    '''

    // ── 挂载品牌规范 Skill（直接读取 .skill.md 文件）
    skills {
        includeFile 'skills/brand-guidelines.skill.md'
    }

    // ── 挂载内置 file_write 工具，用于将生成的 HTML 写入文件
    tools {
        include 'file_write'
    }

    memory {
        type        'message_window'
        maxMessages 6
    }
}

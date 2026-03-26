/**
 * 综合应用 (Application) 示例合集
 * 演示通过 AgentDSL 构建具有实际业务价值的智能体应用，包括：
 * 
 * 1. 品牌官网生成器：读取外部品牌规范文件 (.skill.md)，并自动生成 HTML 文件
 * 2. 数据库与表格办公助理：连接本地数据库、读写 Excel / PDF、执行系统命令等
 */

// ==========================================
// 1. 品牌官网生成器
// ==========================================
// 运行示例: bin/agentdsl.sh run examples/app-demo.agent.groovy --agent brandWebAgent --chat "生成 AgentDSL 品牌官网首页 HTML 并保存文件"
agent('brandWebAgent') {
    description '品牌官网首页生成器 — 遵循规范生成高质量 HTML 并保存'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
        temperature 0.4
        maxTokens 8192
    }

    systemPrompt '''你是一位专业的前端开发工程师和品牌设计师，擅长根据品牌规范生成精美的响应式 HTML 页面。

## 任务流程（严格按此顺序执行）
当用户要求生成品牌官网时，你必须：
1. 生成完整的 HTML 内容（单文件，内联 CSS 和少量内联 JS）
2. 立即调用 file_write 工具，将 HTML 写入文件：
   - path: "output/brand-homepage.html"
   - content: <完整HTML内容>
3. 返回"✅ 已生成并保存 output/brand-homepage.html"

## 代码要求
- 单文件 HTML，所有 CSS 写在 <style> 标签内
- 完全响应式（手机/平板/桌面）
- 包含：Hero 区、特性展示区、代码示例区、CTA 区、Footer
- 科技感强：渐变背景、微动画、代码高亮风格元素

## 重要：你必须调用 file_write 工具来保存文件，不要只输出 HTML 文本。'''

    // 挂载品牌规范 Skill（直接读取外部 Markdown 格式的技能文件）
    skills {
        includeFile 'skills/brand-guidelines.skill.md'
    }

    tools {
        include 'file_write'
    }

    memory {
        type 'message_window'
        maxMessages 6
    }
}


// ==========================================
// 2. 数据库与办公助理
// ==========================================
// 定义本地 H2 数据库数据源
datasource('my_h2_db') {
    type 'h2'
    url 'jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1'
    username 'sa'
    password ''
}

// 运行示例：
// bin/agentdsl.sh run examples/app-demo.agent.groovy --agent data_assistant --chat "用excel生成一个小学课程表的的示例"
// bin/agentdsl.sh run examples/app-demo.agent.groovy --agent data_assistant --chat "查一下数据库里面有没有一张test表"
// bin/agentdsl.sh run examples/app-demo.agent.groovy --agent data_assistant --chat "写入100条随机数据到test表中，如果没有这个表就建一个"
agent('data_assistant') {
    description '综合办公与数据可视化助理'

    model {
        provider 'gemini'
        modelName 'gemini-2.5-flash'
    }

    systemPrompt '''你是一个数据助理。
使用提供的工具为用户服务，你可以使用内置数据源 'my_h2_db' 进行数据库交互。'''

    tools {
        include 'excel_read'
        include 'excel_write'
        include 'pdf_read'
        include 'cmd_execute'
        include 'db_query'
        include 'db_execute'
    }

    datasources {
        attach 'my_h2_db'
    }
}

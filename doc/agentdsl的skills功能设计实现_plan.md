# Skill 模块集成 — 可行性评审与实现方案

## 原始设计评审

对照 [doc/agentSkills设计说明.md](file:///Users/wuguirong/sourceCode/AgentDSL/doc/agentSkills%E8%AE%BE%E8%AE%A1%E8%AF%B4%E6%98%8E.md)，逐项评估：

### ✅ 合理且可直接复用的部分

| 设计点 | 评价 |
|--------|------|
| 两类技能划分（Prompt Skill / Logic Skill） | **合理**。恰好对应系统已有的两种执行路径：LLM 对话 vs Groovy 闭包/Workflow |
| Skill 通过 `description` 做语义发现 | **合理**。LangChain4j 的 `ToolSpecification` 就是靠 description 让 LLM 选择工具，Skill 可复用同一机制 |
| Agent 通过 `use_skill` 挂载 | **合理**。语法上等同于现有的 `include "tool-name"`，可统一为引用机制 |

### ⚠️ 需要调整的部分

| 原始设计 | 问题 | 调整方案 |
|---------|------|---------|
| `SkillSpec` 用抽象类 + 继承（`PromptSkillSpec` / `LogicSkillSpec`） | 引入继承层级会让 DSL 解析、序列化、校验都变复杂。系统中 [AgentSpec](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-core/src/main/java/com/agentdsl/core/spec/AgentSpec.java#10-137)、`ToolSpec`、`WorkflowSpec` 全部是**扁平 POJO** | **改为单一 `SkillSpec` 类 + `type` 枚举字段**，参照 [StepSpec](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-core/src/main/java/com/agentdsl/core/spec/StepSpec.java#20-178) 模式 |
| Prompt Skill 启动"隐形 Sub-Agent" | 引入临时 Agent 实例管理过于复杂，还需要额外的模型连接 | **改为将 instruction 注入当前 Agent 的 system prompt 中作为追加上下文**，或直接包装为一个闭包交给 LLM 完成，无需创建新 Agent |
| 新增 `agentdsl-storage` 模块做 SaaS 持久化 | 引入数据库层级的模块超出了当前 DSL 框架的范围，且与"易于使用"目标冲突 | **推迟到第三阶段**。当前只做纯 DSL 文件级的 Skill 定义，不引入持久化。SaaS 场景后续通过独立服务层解决 |
| `SkillParser` 支持 `extends` 继承语法 | Groovy DSL 中实现 `extends` 需要修改 AST 编译器，复杂度极高 | **改为 `from` 组合语法**（基于现有技能扩展，类似 mixin），编译期展开即可 |

### ❌ 不建议在本阶段实现的部分

| 设计点 | 原因 |
|--------|------|
| 技能市场 (Skill Marketplace) | 需要用户系统、权限、网络 → 不属于 DSL 框架范畴 |
| 自动发现 (Auto-Discovery) | 需要向量检索引擎 + 运行时反射 → 依赖过重 |
| Web 零代码界面 | 前端工程 → 独立项目 |

---

## 调整后的实现方案

### Skill 在 DSL 中的语法

```groovy
// === 描述型技能（Prompt Skill）===
skill("competitive-analyst") {
    type "prompt"
    description "当用户需要分析某公司的竞争对手和市场趋势时，调用此技能"

    // 给 LLM 的指令模板
    instruction '''
        你是一位资深市场分析师。请根据以下信息进行竞品分析：
        1. 识别主要竞争对手
        2. 分析各自优劣势
        3. 给出市场趋势判断
    '''

    // 输入参数
    parameter {
        name "company"
        type "string"
        description "目标公司名称"
        required true
    }
}

// === 逻辑型技能（Logic Skill）===
skill("data-pipeline") {
    type "logic"
    description "当用户需要从多个数据源汇总并清洗数据时，调用此技能"

    parameter {
        name "sources"
        type "string"
        description "数据源列表，逗号分隔"
        required true
    }

    // 逻辑执行体（与 tool 的 execute 语法一致）
    execute { params ->
        def sources = params.sources.split(",")
        def results = sources.collect { src ->
            // 调用已注册的工具
            toolCall("http_get", [url: src.trim()])
        }
        return results.join("\n---\n")
    }
}

// === Agent 挂载技能 ===
agent("market-bot") {
    model {
        provider "ollama"
        modelName "qwen:0.5b-chat"
    }
    systemPrompt "你是市场分析助手"

    skills {
        include "competitive-analyst"
        include "data-pipeline"
    }
}
```

### 核心设计：Skill = 增强型 Tool

> [!IMPORTANT]
> **关键洞察**：在 LangChain4j 的运行时视角里，Skill 本质就是一个 `ToolSpecification` + `ToolExecutor`，
> 与现有的 `ToolSpec` 共享同一套注册、发现、执行通道。
> 差异仅在于 **Prompt Skill 的执行器内部会发起一次 LLM 子调用**（带 instruction），而非直接运行闭包。

这意味着我们不需要另建一套并行的发现/执行框架，只需：
1. 新增 `SkillSpec` 模型 + `SkillDelegate` 解析
2. 在运行时，**将 Skill 展平为 ToolSpec** 注册到 [AgentRegistry](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-runtime/src/main/java/com/agentdsl/runtime/AgentRegistry.java#29-255)

---

### 涉及修改的文件

#### `agentdsl-core`（模型 + DSL 解析）

##### [NEW] `SkillSpec.java`

```
agentdsl-core/src/main/java/com/agentdsl/core/spec/SkillSpec.java
```

单一扁平类，`type` 枚举区分 `PROMPT` / `LOGIC`：
- `name`, `description`, `type`
- `instruction`（Prompt 型专有）
- `executeBody`（Logic 型专有，Groovy Closure）
- `parameters`（复用现有 `ParameterSpec`）
- `requiredToolRefs`（Logic 型可引用的工具）

##### [NEW] `SkillDelegate.groovy`

```
agentdsl-core/src/main/groovy/com/agentdsl/core/dsl/SkillDelegate.groovy
```

处理 `skill("name") { ... }` 块内的关键字。

##### [NEW] `SkillsBlockDelegate.groovy`

```
agentdsl-core/src/main/groovy/com/agentdsl/core/dsl/SkillsBlockDelegate.groovy
```

处理 Agent 内的 `skills { include "xxx" }` 块。

##### [MODIFY] [AgentSpec.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-core/src/main/java/com/agentdsl/core/spec/AgentSpec.java) — 增加 `skillRefs` 字段

##### [MODIFY] [AgentDelegate.groovy](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-core/src/main/groovy/com/agentdsl/core/dsl/AgentDelegate.groovy) — 增加 `skills { ... }` 关键字

##### [MODIFY] [DslBaseScript.groovy](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-core/src/main/groovy/com/agentdsl/core/dsl/DslBaseScript.groovy) — 增加顶层 `skill()` 关键字 + `skills` 收集列表

---

#### `agentdsl-compiler`（编译 + 校验）

##### [MODIFY] [DslCompileResult.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-compiler/src/main/java/com/agentdsl/compiler/DslCompileResult.java) — 增加 `skills` 字段

##### [MODIFY] [DslValidator.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-compiler/src/main/java/com/agentdsl/compiler/DslValidator.java) — 增加 `validateSkill()` + `validateSkillReferences()`

##### [MODIFY] [DslCompiler.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-compiler/src/main/java/com/agentdsl/compiler/DslCompiler.java) — 将 `skills` 传入 [DslCompileResult](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-compiler/src/main/java/com/agentdsl/compiler/DslCompileResult.java#13-68)

---

#### `agentdsl-runtime`（运行时注册 + 执行）

##### [MODIFY] [AgentRegistry.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-runtime/src/main/java/com/agentdsl/runtime/AgentRegistry.java) — 增加 `registerSkill()` / `registerSkills()`

将 SkillSpec 展平为 ToolSpec 并注册：
- **Prompt 型**：生成一个 `ToolSpec`，其 `executeBody` 内部发起一次带 instruction 的 LLM 子调用
- **Logic 型**：直接复用其 `executeBody` 作为 ToolSpec 的执行闭包

##### [MODIFY] [AgentDslEngine.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-runtime/src/main/java/com/agentdsl/runtime/AgentDslEngine.java) — 在 [loadFile()](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-runtime/src/main/java/com/agentdsl/runtime/AgentDslEngine.java#69-76) / `loadAll()` 中增加 Skill 注册步骤

---

#### CLI + 测试

##### [MODIFY] [ListCommand.java](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-cli/src/main/java/com/agentdsl/cli/ListCommand.java) — 增加 Skill 列表展示

##### [NEW] `DslSkillTest.java` — Skill 解析和校验的单元测试

---

## 验证计划

### 自动化测试
- [DslCompiler](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-compiler/src/main/java/com/agentdsl/compiler/DslCompiler.java#26-182) 编译包含 `skill()` 定义的脚本，验证 `SkillSpec` 正确解析
- [DslValidator](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-compiler/src/main/java/com/agentdsl/compiler/DslValidator.java#18-167) 验证 Skill 引用校验（Agent `include` 不存在的 Skill 时报错）
- [AgentRegistry](file:///Users/wuguirong/sourceCode/AgentDSL/agentdsl-runtime/src/main/java/com/agentdsl/runtime/AgentRegistry.java#29-255) 验证 Skill → ToolSpec 展平注册后，Agent 能正确发现 Skill

### 手动验证
- 编写 `examples/skill-demo.agent.groovy` 示例脚本
- 通过 `agentdsl list` 命令确认 Skill 被正确解析
- 通过 `agentdsl validate` 命令确认校验通过

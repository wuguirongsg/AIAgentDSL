# AgentDSL 技能模块 (Skills Module) 设计说明书

## 1. 需求背景与初衷

在当前的 Agent 实践中，开发者面临两难境地：

- **工具太原子化**：单纯的 API 调用（Tools）无法处理复杂的业务“套路”（如先搜索、再过滤、最后总结）。
    
- **Agent 太重**：为每一个小任务定义一个完整的 Agent 成本太高，且难以复用。
    
- **用户门槛矛盾**：SaaS 用户希望像 Claude 一样通过写文档（Markdown）定义技能，而企业级场景要求逻辑必须 100% 准确（逻辑代码）。
    

**目的**：引入 **Skill** 抽象层，向上屏蔽复杂性，向下整合原子能力，实现“语义发现、逻辑执行”的统一。

---

## 2. 产品模块设计

我们将技能分为两类，但在用户视角下，它们拥有统一的入口和调用方式。

### 2.1 技能分类定义

|**技能类型**|**核心构成 (Constituents)**|**适用场景**|**用户群体**|
|---|---|---|---|
|**描述型技能 (Prompt Skill)**|`Description` + `System Prompt` + `Context`|角色扮演、文本风格转换、简单决策引导。|运营、非技术人员。|
|**逻辑型技能 (Logic Skill)**|`Description` + `Groovy Closure/Workflow` + `Tools`|数据清洗、多步 API 编排、带条件循环的自动化任务。|开发者、高级分析师。|

### 2.2 用户操作链路 (SaaS 场景)

1. **技能创建**：用户选择“写描述”或“写逻辑（或拖拽工作流）”。
    
2. **语义标注**：用户必须提供一段清晰的 Markdown 描述，这部分将作为 LLM 识别该技能的“唯一索引”。
    
3. **技能发布**：技能进入 `Skill Registry`（技能仓库），可被不同的 Agent 挂载。
    

---

## 3. 架构设计方案

### 3.1 核心模型 (Core Specs)

在 `agentdsl-core` 中，我们将 `SkillSpec` 设计为一个多态模型：

Java

```
// 伪代码：Skill 核心抽象
public abstract class SkillSpec {
    String id;
    String name;
    String description; // 给 LLM 看的语义描述 (Markdown)
    Map<String, Parameter> inputs; // 输入参数定义
}

// 描述型实现
public class PromptSkillSpec extends SkillSpec {
    String instruction; // 核心指令集
}

// 逻辑型实现
public class LogicSkillSpec extends SkillSpec {
    Closure logic; // Groovy 逻辑块
    List<String> requiredToolRefs; // 依赖的工具
    String workflowRef; // 或者引用一个现有的 Workflow
}
```

### 3.2 运行机制 (Execution Flow)

当 Agent 调用一个技能时，`agentdsl-runtime` 遵循以下链路：

1. **语义匹配**：Agent 的 LLM 根据用户输入，从挂载的 Skill 列表中发现匹配的 `Skill.description`。
    
2. **参数提取**：LLM 按照 `Skill.inputs` 定义的 Schema 提取参数。
    
3. **路由分发**：
    
    - 如果是 **Prompt Skill**：引擎启动一个“隐形 Sub-Agent”，将指令注入，获取结果。
        
    - 如果是 **Logic Skill**：引擎调用 `GroovySandbox` 执行逻辑闭包，或者跳转至 `WorkflowExecutor` 执行任务流。
        
4. **结果反馈**：将输出返回给主 Agent，决定下一步行动。
    

### 3.3 架构模块调整

- **`agentdsl-compiler`**：新增 `SkillParser`。支持在 DSL 中声明 `skill { ... }` 块，并支持 `extends` 语法实现技能复用。
    
- **`agentdsl-langchain4j`**：实现 `SkillAsTool` 适配器。每一个 Skill 在 LangChain4j 看来都是一个特殊的 `ToolSpecification`。
    
- **`agentdsl-storage` (新增)**：为了支持 SaaS，需要一个数据库存储层，将 `SkillSpec` 序列化为 JSON 持久化，支持版本管理和权限控制。
    

---

## 4. 实现方法 (Implementation Roadmap)

### 第一阶段：DSL 语法支持 (已具备)

在现有的 `AgentSpec` 中增加 `skills` 属性，并实现基本的 `SkillDelegate`。

> **目标**：能用 Groovy 定义出这两类技能。

### 第二阶段：混合执行引擎

- 开发 `SkillInvocationHandler`。
    
- 针对 **Prompt Skill**：实现指令的动态注入逻辑（System Message Overriding）。
    
- 针对 **Logic Skill**：复用现有的 `ToolMetrics` 和 `ExecutionTrace`，确保技能执行过程可被追踪。
    

### 第三阶段：SaaS 可视化与安全

- **安全沙箱增强**：限制逻辑型技能的资源占用（内存/CPU/执行时长）。
    
- **零代码定义**：提供一个简单的 Web 界面，左边写 Markdown，右边勾选依赖的 Tools，点击“生成”即可产出 `SkillSpec`。
    

---

## 5. 预期效果示例

**用户在 SaaS 平台上定义一个“竞品情报官”技能：**

- **描述 (Markdown)**：`如果你需要获取特定公司的竞争对手信息及最新的市场新闻，请调用此技能。`
    
- **逻辑 (Logic)**：
    
    1. 调用 `Google Search` 获取初级名单。
        
    2. 对每个名单调用 `mcp_github_search` 看是否有开源项目。
        
    3. 调用 `llm_summarize` 汇总。
        

**Agent 的 DSL 引用：**

Groovy

```
agent "MarketBot" {
    model "gpt-4o"
    // 像挂载工具一样挂载技能
    use_skill "CompetitorIntelligence" 
    on_message { "正在为您搜集市场情报..." }
}
```

---

## 6. 后续演进方向

- **技能市场 (Skill Marketplace)**：允许用户分享自己定义的“描述型技能”（类似 Prompt 市场）。
    
- **自动发现 (Auto-Discovery)**：当 Agent 发现自己缺乏某种能力时，自动去仓库搜索并提示用户：“我发现仓库里有一个‘法律咨询’技能，是否需要挂载？”
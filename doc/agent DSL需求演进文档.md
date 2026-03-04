# AgentDSL 演进需求文档：从“工具编排”到“数字员工”

**文档版本**：v2.0 (Draft)

**目标**：在现有 DSL 基础上，引入计算机使用（Computer Use）、自主任务规划（Autonomous）、视觉感知（Fara）以及高性能执行与优化（Agent-Lightning）。

---

## 1. 核心需求概览

| **需求项**                    | **核心目标**                                                 | **关键技术点**                              |
| ----------------------------- | ------------------------------------------------------------ | ------------------------------------------- |
| **计算机使用 (Computer Use)** | 使 Agent 能像人一样操作网页、OA、ERP 及桌面客户端。          | Playwright, Java Robot, 屏幕映射。          |
| **自主模式 (Autonomous)**     | 实现“意图 -> 规划 -> 执行 -> 观察 -> 修正”的自主循环。       | ReAct 框架, 目标导向规划（Goal-oriented）。 |
| **视觉驱动 (FARA)**           | 摆脱对 HTML 源码的依赖，通过屏幕截图直接识别坐标并执行动作。 | Microsoft Fara-7B 模型, 像素坐标转换。      |
| **性能与进化 (Lightning)**    | 提升高并发执行能力，并通过运行轨迹（Trace）自动优化 Prompt。 | 异步事件驱动, APO (自动提示词优化)。        |

---

## 2. 详细功能定义

### 2.1 计算机使用与 FARA 集成 (视觉感知与执行)

**前提背景**：传统的 RPA 依赖 Selector 容易失效，SaaS 场景下用户无法提供复杂的 DOM 定位。

**实现方法**：

- **Fara 桥接层**：在 `agentdsl-tools` 中新增 `VisionActionProvider`。
    
- **动作闭环**：
    
    1. **感知 (Perception)**：通过截图 API 获取当前屏幕 Base64。
        
    2. **推理 (Reasoning)**：调用 Fara-7B 解析图像，返回 UI 元素坐标 `(x, y)`。
        
    3. **执行 (Action)**：使用 Java 原生或远程 Client 模拟鼠标点击、滚动、输入。
        
- **DSL 语法扩展**：
    
    Groovy
    
    ```
    skill "HandleOA" {
        // 使用视觉定位而非 DOM
        execute {
            computer.v_click("待办流程") // 自动调用 Fara 识别坐标
            computer.v_type("审批单号", "REQ-2026")
        }
    }
    ```
    

### 2.2 自主型智能体 (Autonomous Agent)

**前提背景**：用户不想写固定的 Step，只想描述模糊的需求（如“去帮我订张票”）。

**实现方法**：

- **引入 `AUTONOMOUS` 运行时**：在 `agentdsl-runtime` 中新增 `AutonomousExecutor`。
    
- **规划器 (Planner)**：LLM 根据用户目标（Goal）动态从 `Skill Registry` 中提取可选技能。
    
- **循环机制**：
    
    - **Step 1**: 生成当前最优步骤。
        
    - **Step 2**: 执行 Tool/Skill。
        
    - **Step 3**: 获取结果作为 `Observation`。
        
    - **Step 4**: 修正下一步计划，直到 `Goal` 完成。
        
- **DSL 语法扩展**：
    
    Groovy
    
    ```
    agent "AutoAssistant" {
        mode AUTONOMOUS  // 开启自主规划模式
        capabilities(["WebSearch", "OASkill", "FileSystem"]) // 赋予权限
        max_steps 10      // 防止陷入死循环的安全阈值
    }
    ```
    

### 2.3 Agent-Lightning 集成 (高性能执行与自进化)

**前提背景**：大规模运行 Agent 时响应慢、成本高，且 Prompt 难以维护。

**实现方法**：

- **异步事件引擎**：借鉴 Lightning 架构，将 `WorkflowExecutor` 升级为基于虚拟线程（Project Loom）的非阻塞事件流。
    
- **运行轨迹脱敏 (Trace Disaggregation)**：将执行逻辑与学习逻辑分离。
    
- **反馈闭环**：
    
    - 记录 Agent 的失败轨迹（例如：多次寻找按钮失败）。
        
    - Lightning 模块后台分析 Trace，自动调整 `SystemPrompt`。
        
    - **下一次执行时，Agent 变得更聪明，不再犯错。**
        

---

## 3. 架构设计调整

### 3.1 模块化更新方案

1. **`agentdsl-vision` (新增)**：封装 Fara-7B 的 API 调用及图像预处理逻辑。
    
2. **`agentdsl-runtime` (增强)**：
    
    - 新增 `EventLoop` 支持异步执行。
        
    - 新增 `PlannerEngine` 支持 ReAct 自主规划。
        
3. **`agentdsl-lightning-bridge` (新增)**：负责将 `ExecutionTrace` 导出为符合 Lightning 规范的优化数据。
    

### 3.2 流程图：自主任务执行链路

> **用户意图** -> **Autonomous Agent** -> **Planner (生成计划)** -> **Skill (视觉/逻辑)** -> **Fara (识别坐标)** -> **OS (执行动作)** -> **Observer (反馈结果)** -> **Lightning (后台优化)**

---

## 4. 安全与约束 (SaaS 场景必备)

1. **人机协同 (HITL)**：对于涉及“资金”、“删除”、“发送邮件”等关键 Fara 动作，DSL 必须强制触发 `approval_node`，用户在 UI 点击确认后方可继续。
    
2. **视觉沙箱**：限制 Agent 只能识别特定浏览器的窗口，禁止访问系统级敏感区域。
    
3. **Token 熔断**：自主模式下，若单次任务消耗超过预设 Token 或步数，强制挂起并报警。
    

---

## 5. 项目成功的核心意义

通过以上集成，**AgentDSL** 将具备以下独特竞争力：

1. **像人一样看和做**：集成 Fara 后，它不再只是调 API 的代码块，而是能操作任何软件的数字员工。
    
2. **不需要教就会做**：自主模式让非技术用户也能通过一句话驱动复杂流程。
    
3. **越用越快，越用越准**：集成 Lightning 后，系统具备了自我进化的能力，解决了 AI 幻觉和 Prompt 维护难的问题。
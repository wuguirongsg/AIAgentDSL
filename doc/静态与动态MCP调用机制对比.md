# 静态 MCP 与动态 MCP 发现：实现机制对比

## 一、调用链对比

| 环节 | 静态 MCP（DSL 配置） | 动态 MCP（auto_discover_mcp） |
|------|----------------------|-------------------------------|
| **配置来源** | DSL 里写死 `mcp { server('x') { command 'npx', '-y', '@pkg', ... } }` | 运行时查 Registry API，按关键词拿到候选列表 |
| **连接时机** | Agent 注册时一次性连接（`AgentRegistry.register()` → `mcpBridge.connect(agentSpec.getMcp())`） | 缺工具时再连接：主动发现（无工具时）或被动发现（调用缺失工具时） |
| **command 处理** | **原样**传入 Bridge，不做任何包装 | 在 `tryAutoDiscoverAndAttachTool()` 里先做 **白名单校验**，再对 `npx -y @pkg` 做 **wrapNpxCommandWithNode**，再传给 Bridge |
| **最终执行** | 同一处：`McpToolProviderBridge.createTransport(spec)` → `StdioMcpTransport.Builder().command(spec.getCommand())` | 同上，只是 `spec.getCommand()` 已是包装后的 `node mcp-npx-run.js @pkg` |

结论：**底层都是同一套 StdioMcpTransport + 同一套 Bridge**，差异只在于 **command 从哪来、有没有被包装**。

---

## 二、为何静态“没问题”、动态“各种问题”

### 1. command 是否经过 npx 包装（shebang 问题）

- **静态**：command 直接来自 DSL，**没有**走 `wrapNpxCommandWithNode()`，所以实际执行的是 `npx -y @modelcontextprotocol/server-filesystem ./skills`。  
  官方包（如 `@modelcontextprotocol/server-filesystem`）的 bin 一般带 `#!/usr/bin/env node`，用 npx 直接跑没问题。
- **动态**：command 在 `tryAutoDiscoverAndAttachTool()` 里会先被包装成 `node mcp-npx-run.js @pkg`，**强制用 Node 跑 bin**，所以即使用户选到无 shebang 的社区包（如某 weather 包），也不会出现 “import: command not found”。

若你在静态里写一个 **bin 无 shebang** 的 npx 包，同样会报错；只是当前静态示例用的是官方包，所以看起来“没问题”。

### 2. 包来源与质量

- **静态**：包名和参数都是你**手写**的，通常选的是已知可用的官方/自建包，环境、参数都可控。
- **动态**：包来自 Registry 搜索，是**任意社区包**，可能：
  - bin 无 shebang（已通过包装缓解）、
  - 需要 API Key / 环境变量（未配置就超时或报错）、
  - 实现有 bug 或启动慢，容易超时。

### 3. 连接时机与重试

- **静态**：注册时连一次，失败会直接抛错（如 “MCP 连接失败”），没有“换一个包再试”。
- **动态**：在“缺工具”时再连，且会**按候选列表依次重试**，某个包超时/失败就试下一个，所以你会看到多次尝试、超时、换包等“各种情况”。

### 4. 参数与环境

- **静态**：可以写任意参数，例如 `'./skills'`、env 等，DSL 里完全可控。
- **动态**：command 来自 Registry 的 `packages[].identifier`，一般是 **`npx -y @pkg`**，没有你自定义的工作目录或额外参数，也不能在发现阶段注入 env（除非后续扩展）。

---

## 三、总结表

| 维度 | 静态 MCP | 动态 MCP |
|------|----------|----------|
| command 来源 | DSL 写死 | Registry API 搜索得到 |
| 是否包装 npx（防 shebang） | **否**（当前未统一） | **是** |
| 连接时机 | 注册时一次 | 缺工具时按需连接 |
| 失败策略 | 一次失败即报错 | 多候选依次重试 |
| 包/参数可控性 | 高（你指定包名和参数） | 低（仅能选“第一个能连上的”候选） |

因此：**实现机制上，两者最后都走同一套 Bridge + StdioMcpTransport**；你感觉“静态没问题、动态各种问题”，主要是因为 **动态多了“自动选包 + 多候选重试”**，且 **只有动态路径对 npx 做了包装**。若希望静态配置的 npx 包也避免 shebang 问题，可以对静态 MCP 的 command 做同样的 npx 包装（见下文）。

---

## 四、建议：静态 MCP 也统一做 npx 包装

为了一致性和少踩坑，建议在 **McpToolProviderBridge.createTransport()** 里，对 `spec.getCommand()` 做一次判断：若形如 `npx -y @xxx`，则先替换为 `node mcp-npx-run.js @xxx`（或调用与 `wrapNpxCommandWithNode` 等价的逻辑），再交给 `StdioMcpTransport`。这样：

- 静态配置的 `command 'npx', '-y', '@modelcontextprotocol/server-filesystem', './skills'` 也会自动变成用 Node 跑 bin，和动态路径一致；
- 以后在静态里使用无 shebang 的社区 npx 包也不会再出现 “import: command not found”。

（实现上可以把 `wrapNpxCommandWithNode` 抽到公共位置，静态/动态两处都调用。）

# AgentDSL 插件开发指南

> **文档版本**: v1.0 &nbsp;|&nbsp; **最后更新**: 2026-03-26
>
> **目标读者**: 插件开发者、项目贡献者

---

## 1. 架构概述

AgentDSL 采用 **Java SPI (Service Provider Interface)** 实现插件架构，插件无需修改 core 模块即可扩展 DSL 的能力。

```
                    ┌──────────────────────┐
                    │   agentdsl-core      │
                    │  ┌────────────────┐  │
                    │  │ AgentDslPlugin │◄─┼──── Java ServiceLoader 自动发现
                    │  │ PluginRegistry │  │
                    │  │ MemoryFactory  │  │
                    │  │ PluginLoader   │  │
                    │  └────────────────┘  │
                    └──────────┬───────────┘
                               │ compileOnly
                    ┌──────────┴───────────┐
                    │ 你的插件模块            │
                    │                      │
                    │ MyPlugin             │
                    │   implements         │
                    │   AgentDslPlugin     │
                    │                      │
                    │ META-INF/services/   │
                    │   com.agentdsl.core  │
                    │   .plugin            │
                    │   .AgentDslPlugin    │
                    └──────────────────────┘
```

**核心原则**：
- 插件通过 `compileOnly` 依赖 core（不传递），保证插件可独立发布
- core 通过 `ServiceLoader` 自动发现，无需手动注册
- 运行时由最终应用（CLI / Spring Boot Starter）通过 `runtimeOnly` 决定包含哪些插件

---

## 2. 快速开始：5 步创建插件

### 步骤 1: 新建 Gradle 模块

在 `settings.gradle.kts` 中注册：

```kotlin
include("agentdsl-my-plugin")
```

创建 `agentdsl-my-plugin/build.gradle.kts`：

```kotlin
dependencies {
    // 仅编译期依赖 core（不传递给消费者）
    compileOnly(project(":agentdsl-core"))

    // 你的插件自身依赖
    implementation("com.example:my-library:1.0.0")
}
```

### 步骤 2: 实现 AgentDslPlugin 接口

```java
package com.example.myplugin;

import com.agentdsl.core.plugin.AgentDslPlugin;
import com.agentdsl.core.plugin.PluginRegistry;
import java.util.List;

public class MyPlugin implements AgentDslPlugin {

    @Override
    public String pluginId() {
        return "my-plugin";  // 全局唯一标识
    }

    @Override
    public List<String> contributedKeywords() {
        return List.of("my_keyword");  // DSL 中引入的关键字（用于文档和冲突检测）
    }

    @Override
    public int priority() {
        return 50;  // 优先级，数字越大越优先（默认 0）
    }

    @Override
    public void register(PluginRegistry registry) {
        // 注册你的扩展能力
        registry.registerMemoryFactory("my-memory", config -> {
            // 创建并返回 ChatMemory 实例
            return new MyCustomChatMemory(config);
        });

        registry.registerBuiltinSkill("my_skill");
        registry.registerBuiltinTool("my_tool");
    }
}
```

### 步骤 3: 声明 SPI

创建文件 `src/main/resources/META-INF/services/com.agentdsl.core.plugin.AgentDslPlugin`：

```
com.example.myplugin.MyPlugin
```

### 步骤 4: 在最终应用中引入

在 `agentdsl-cli/build.gradle.kts` 或你的应用中：

```kotlin
dependencies {
    runtimeOnly(project(":agentdsl-my-plugin"))
}
```

### 步骤 5: 验证

运行构建，插件会在启动时被自动发现：

```bash
./gradlew build
```

日志中会看到：

```
INFO  发现 AgentDSL 插件: id=my-plugin, priority=50, keywords=[my_keyword]
INFO  插件注册成功: my-plugin
```

---

## 3. PluginRegistry 可注册的扩展点

| 方法 | 用途 | 示例 |
|------|------|------|
| `registerMemoryFactory(type, factory)` | 注册自定义 ChatMemory 类型 | `"hypergraph"` → HypergraphMemoryProvider |
| `registerBuiltinSkill(name)` | 注册内置 Skill 名称 | `"deep_recall"` |
| `registerBuiltinTool(name)` | 注册内置 Tool 名称 | `"my_search"` |

**MemoryFactory** 接口：

```java
@FunctionalInterface
public interface MemoryFactory {
    // config 来自 DSL MemorySpec 的配置映射
    // 返回值必须是 dev.langchain4j.memory.ChatMemory
    Object create(Map<String, Object> config);
}
```

---

## 4. 现有插件参考：agentdsl-memory-hypergraph

超图记忆插件是第一个按此架构实现的官方插件，可作为完整参考。

| 文件 | 作用 |
|------|------|
| `HypergraphMemoryPlugin.java` | 实现 `AgentDslPlugin`，注册 `hypergraph` MemoryFactory + `deep_recall` Skill |
| `HypergraphMemoryProvider.java` | 实际的 ChatMemory 创建逻辑 |
| `META-INF/services/com.agentdsl.core.plugin.AgentDslPlugin` | SPI 声明 |
| `build.gradle.kts` | `compileOnly(project(":agentdsl-core"))` |

**注册逻辑**：

```java
@Override
public void register(PluginRegistry registry) {
    HypergraphMemoryProvider provider = new HypergraphMemoryProvider();
    registry.registerMemoryFactory("hypergraph", provider::create);
    registry.registerBuiltinSkill("deep_recall");
}
```

---

## 5. 注意事项

### 5.1 依赖管理

| 依赖类型 | 用途 |
|---------|------|
| `compileOnly(project(":agentdsl-core"))` | 引用 `AgentDslPlugin` 等接口，**必须** |
| `implementation(...)` | 插件自身的运行时依赖 |
| `runtimeOnly(...)` | 在最终应用（CLI/Starter）中引入插件 |

> **不要** 使用 `implementation(project(":agentdsl-core"))`，否则会导致依赖传递。

### 5.2 优先级

当多个插件注册同一个 memory type 时，`priority()` 值大的生效。建议：

| 优先级范围 | 用途 |
|-----------|------|
| 0 ~ 49 | 社区/第三方插件 |
| 50 ~ 99 | 项目内定制插件 |
| 100+ | 官方插件 |

### 5.3 线程安全

- `PluginLoader.loadAll()` 在引擎启动时调用一次，之后注册表只读
- `DefaultPluginRegistry` 内部使用 `LinkedHashMap`，插件注册阶段为单线程
- `BuiltinSkillRegistry` 使用 `ConcurrentHashMap.newKeySet()`，线程安全

### 5.4 向后兼容

旧的 `META-INF/agentdsl/memory.providers` 反射机制仍然可用作为回退。`LangChainMemoryFactory` 的查找顺序：

1. **PluginRegistry**（SPI 新机制） — 优先
2. **memory.providers**（旧反射机制） — 兼容回退
3. **内置 message_window / token_window** — 默认

---

## 6. 测试插件

```java
@Test
void shouldRegisterViaPlugin() {
    DefaultPluginRegistry registry = new DefaultPluginRegistry();
    MyPlugin plugin = new MyPlugin();
    plugin.register(registry);

    assertNotNull(registry.getMemoryFactory("my-memory"));
    assertTrue(registry.isBuiltinSkill("my_skill"));
}
```

完整的 SPI 集成测试可参考 `agentdsl-core` 中的 `PluginRegistryTest`。

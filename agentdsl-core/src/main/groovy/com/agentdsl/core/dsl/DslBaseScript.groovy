package com.agentdsl.core.dsl

import com.agentdsl.core.spec.*
import groovy.transform.CompileStatic

/**
 * DSL 脚本基类。
 * 所有 .agent.groovy 脚本在编译时会自动继承此类。
 * 提供顶层关键字：agent(), tool(), workflow(), skill()
 */
abstract class DslBaseScript extends Script {

    /** 编译后收集到的所有 AgentSpec */
    List<AgentSpec> agents = []

    /** 编译后收集到的所有独立 ToolSpec */
    List<ToolSpec> standaloneTools = []

    /** 编译后收集到的所有 WorkflowSpec */
    List<WorkflowSpec> workflows = []

    /** 编译后收集到的所有独立 SkillSpec */
    List<SkillSpec> standaloneSkills = []

    /** 编译后收集到的所有 DataSourceSpec */
    List<DataSourceSpec> datasources = []

    /**
     * 顶层关键字：agent("name") { ... }
     */
    void agent(String name, @DelegatesTo(AgentDelegate) Closure config) {
        def spec = new AgentSpec(name)
        def delegate = new AgentDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        agents << spec
    }

    /**
     * 顶层关键字：tool("name") { ... }
     */
    void tool(String name, @DelegatesTo(ToolDelegate) Closure config) {
        def spec = new ToolSpec(name)
        def delegate = new ToolDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        standaloneTools << spec
    }

    /**
     * 顶层关键字：workflow("name") { ... }
     */
    void workflow(String name, @DelegatesTo(WorkflowDelegate) Closure config) {
        def spec = new WorkflowSpec(name)
        def delegate = new WorkflowDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        workflows << spec
    }

    /**
     * 顶层关键字：skill("name") { ... }
     * 定义一个全局技能，可被多个 Agent 通过 skills { include "name" } 引用。
     */
    void skill(String name, @DelegatesTo(SkillDelegate) Closure config) {
        def spec = new SkillSpec(name)
        def delegate = new SkillDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        standaloneSkills << spec
    }

    /**
     * 顶层关键字：datasource("name") { ... }
     * 定义一个全局数据源，可被 Agent 通过 datasources { use "name" } 引用。
     */
    void datasource(String name, @DelegatesTo(DataSourceDelegate) Closure config) {
        def spec = new DataSourceSpec(name)
        def delegate = new DataSourceDelegate(spec)
        config.delegate = delegate
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        datasources << spec
    }

    /**
     * 内置函数：env("VAR_NAME") — 读取环境变量
     */
    static String env(String key) {
        String value = System.getProperty(key)
        if (value == null) {
            value = System.getenv(key)
        }
        if (value == null) {
            throw new com.agentdsl.core.exception.DslRuntimeException(
                'ADSL-012', "环境变量未找到: ${key}")
        }
        return value
    }

    /**
     * 内置函数：file("path") — 读取文件内容
     */
    static String file(String path) {
        def f = new File(path)
        if (!f.exists()) {
            throw new com.agentdsl.core.exception.DslRuntimeException(
                'ADSL-012', "文件未找到: ${path}")
        }
        return f.text
    }

    /**
     * 内置函数：resource("classpath") — 读取 classpath 资源
     */
    static String resource(String classpath) {
        def stream = Thread.currentThread().contextClassLoader.getResourceAsStream(classpath)
        if (stream == null) {
            throw new com.agentdsl.core.exception.DslRuntimeException(
                'ADSL-012', "Classpath 资源未找到: ${classpath}")
        }
        return stream.text
    }

}

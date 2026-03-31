package com.agentdsl.core.builtin;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DSL 内置技能注册表。
 * <p>
 * 维护 builtin skill 名称白名单，供编译期和运行期做名称合法性校验。
 * 各 skill 的生效条件（如需要特定 memory type）由对应的 resolver 自行判断。
 *
 * <h3>v1.5 改动</h3>
 * 从硬编码 {@code Set.of()} 改为动态注册。插件通过
 * {@link com.agentdsl.core.plugin.PluginRegistry#registerBuiltinSkill(String)}
 * 间接调用 {@link #register(String)} 完成注册。
 */
public final class BuiltinSkillRegistry {

    public static final String DEEP_RECALL = "deep_recall";

    /** 使用 ConcurrentHashMap.newKeySet() 保证线程安全 */
    private static final Set<String> BUILTIN_SKILLS = ConcurrentHashMap.newKeySet();

    static {
        // 保留默认内置 skill，向后兼容
        BUILTIN_SKILLS.add(DEEP_RECALL);
    }

    private BuiltinSkillRegistry() {
    }

    /**
     * 动态注册一个内置 Skill 名称。
     *
     * @param name skill 名称
     */
    public static void register(String name) {
        if (name != null && !name.isBlank()) {
            BUILTIN_SKILLS.add(name);
        }
    }

    /**
     * 取消注册一个内置 Skill 名称。
     *
     * @param name skill 名称
     */
    public static void unregister(String name) {
        if (name != null) {
            BUILTIN_SKILLS.remove(name);
        }
    }

    public static boolean isBuiltinSkill(String name) {
        return name != null && BUILTIN_SKILLS.contains(name);
    }

    /**
     * 获取所有已注册的内置 Skill 名称（不可变视图）。
     */
    public static Set<String> all() {
        return Collections.unmodifiableSet(BUILTIN_SKILLS);
    }
}

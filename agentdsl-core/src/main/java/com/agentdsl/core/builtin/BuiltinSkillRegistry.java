package com.agentdsl.core.builtin;

import java.util.Set;

/**
 * DSL 内置技能注册表。
 * 维护 builtin skill 名称白名单，供编译期和运行期做名称合法性校验。
 * 各 skill 的生效条件（如需要特定 memory type）由对应的 resolver 自行判断。
 */
public final class BuiltinSkillRegistry {

    public static final String DEEP_RECALL = "deep_recall";

    private static final Set<String> BUILTIN_SKILLS = Set.of(DEEP_RECALL);

    private BuiltinSkillRegistry() {
    }

    public static boolean isBuiltinSkill(String name) {
        return name != null && BUILTIN_SKILLS.contains(name);
    }
}

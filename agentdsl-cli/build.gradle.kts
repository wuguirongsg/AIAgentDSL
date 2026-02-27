plugins {
    application
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

dependencies {
    // CLI 依赖完整运行时（传递依赖 agentdsl-langchain4j/tools/mcp 等）
    implementation(project(":agentdsl-runtime"))
    // 显式依赖编译器和核心（避免编译期找不到类）
    implementation(project(":agentdsl-compiler"))
    implementation(project(":agentdsl-core"))

    // picocli — 命令行框架
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // Test
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
}

application {
    mainClass.set("com.agentdsl.cli.AgentDslCli")
}

tasks.shadowJar {
    archiveBaseName.set("agentdsl")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.agentdsl.cli.AgentDslCli"
    }
}

// 跳过根项目的 shadowJar 任务（仅 CLI 模块需要）
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

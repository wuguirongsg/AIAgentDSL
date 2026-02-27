val langchain4jVersion = "1.11.0"

dependencies {
    implementation(project(":agentdsl-core"))

    // LangChain4j MCP 核心
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-mcp:1.0.0-beta5")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

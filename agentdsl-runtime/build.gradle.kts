val langchain4jVersion = "1.11.0"

dependencies {
    implementation(project(":agentdsl-core"))
    implementation(project(":agentdsl-compiler"))
    implementation(project(":agentdsl-langchain4j"))
    implementation(project(":agentdsl-tools"))
    implementation(project(":agentdsl-mcp"))

    // Runtime module directly uses LangChain4j types
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-mcp:1.0.0-beta5")

    // Test
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
}

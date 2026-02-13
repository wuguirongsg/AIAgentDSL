val langchain4jVersion = "1.11.0"

dependencies {
    implementation(project(":agentdsl-core"))
    implementation(project(":agentdsl-compiler"))
    implementation(project(":agentdsl-langchain4j"))

    // Runtime module directly uses LangChain4j types
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
}

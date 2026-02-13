val langchain4jVersion = "1.11.0"

dependencies {
    implementation(project(":agentdsl-core"))
    implementation("org.apache.groovy:groovy-json:4.0.27")

    // LangChain4j core
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")

    // LangChain4j model providers
    implementation("dev.langchain4j:langchain4j-open-ai:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")
}

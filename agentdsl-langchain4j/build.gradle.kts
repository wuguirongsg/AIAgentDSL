val langchain4jVersion = "1.11.0"

dependencies {
    implementation(project(":agentdsl-core"))
    implementation("org.apache.groovy:groovy-json:4.0.27")
    runtimeOnly("com.agentdsl:agentdsl-memory-hypergraph:0.1.0-SNAPSHOT")
    // 为测试添加编译期依赖，使 SPI 能够在测试中发现插件
    testImplementation("com.agentdsl:agentdsl-memory-hypergraph:0.1.0-SNAPSHOT")

    // LangChain4j core
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")

    // LangChain4j model providers
    implementation("dev.langchain4j:langchain4j-open-ai:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-anthropic:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:$langchain4jVersion")

    // LangChain4j RAG — embedding model + in-memory store (beta versioning scheme)
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.11.0-beta19")
    implementation("dev.langchain4j:langchain4j-embeddings:1.11.0-beta19")
}

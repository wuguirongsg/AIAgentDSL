val langchain4jVersion = "1.11.0"

dependencies {
    // SPI 插件接口（编译期依赖，不传递）
    compileOnly(project(":agentdsl-core"))

    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-embeddings:1.11.0-beta19")
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:1.11.0-beta19")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("com.huaban:jieba-analysis:1.0.2")
}

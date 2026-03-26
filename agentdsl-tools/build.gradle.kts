dependencies {
    implementation(project(":agentdsl-core"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.microsoft.playwright:playwright:1.48.0")
    
    // Data expansion dependencies
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.eclipse.angus:jakarta.mail:2.0.3")
    implementation("org.commonmark:commonmark:0.21.0")

    // Web Search dependencies
    implementation("dev.langchain4j:langchain4j-web-search-engine-tavily:0.36.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}


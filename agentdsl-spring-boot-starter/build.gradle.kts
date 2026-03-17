plugins {
    java
    id("io.spring.dependency-management") version "1.1.4"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.0")
    }
}

dependencies {
    // === 核心依赖 ===
    implementation(project(":agentdsl-runtime")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    implementation(project(":agentdsl-compiler")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    implementation(project(":agentdsl-core")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }

    // === Spring Boot 自动配置 ===
    implementation("org.springframework.boot:spring-boot-starter")
    
    // === 可选依赖（Web 应用需要显式引入） ===
    // REST Controller 仅在 Web 应用中启用（通过 @ConditionalOnWebApplication）
    // 非 Web 项目不会引入 Tomcat
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // === 开发工具（可选） ===
    compileOnly("org.springframework.boot:spring-boot-devtools")

    // === 测试依赖 ===
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.14.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("agentdsl-spring-boot-starter")
}
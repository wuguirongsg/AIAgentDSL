plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.0")
    }
    dependencies {
        dependency("ch.qos.logback:logback-classic:1.4.11")
        dependency("ch.qos.logback:logback-core:1.4.11")
    }
}

dependencies {
    // AgentDSL - 直接依赖 runtime 模块（避免 starter 的 logback 冲突）
    implementation(project(":agentdsl-runtime")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
    implementation(project(":agentdsl-compiler")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
    implementation(project(":agentdsl-core")) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
    
    // AgentDSL Spring Boot Starter（自动配置）
    implementation(project(":agentdsl-spring-boot-starter"))
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
    }
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("ch.qos.logback:logback-core:1.4.11")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
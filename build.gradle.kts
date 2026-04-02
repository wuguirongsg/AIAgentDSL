plugins {
    java
    groovy
}

allprojects {
    group = "com.agentdsl"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "groovy")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 确保 Groovy 源文件目录被 IDE 正确识别
    sourceSets {
        main {
            groovy {
                srcDirs("src/main/groovy")
            }
            java {
                srcDirs("src/main/java")
            }
        }
    }

    dependencies {
        // Groovy
        implementation("org.apache.groovy:groovy:4.0.27")

        // Logging
        implementation("org.slf4j:slf4j-api:2.0.16")
        runtimeOnly("ch.qos.logback:logback-classic:1.5.16")

        // Test
        testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation("org.wiremock:wiremock:3.9.1")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

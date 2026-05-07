plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("maven-publish")
}

group = "com.tomdh.schoolconnector"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Session-aware web client (our own library)
    implementation("com.github.tomdh-git:session-aware-web-client:8e4a5b71fb")

    // Spring Boot (non-starter, so we don't pull in auto-config)
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.3.4")
    implementation("org.springframework.boot:spring-boot-starter-actuator:3.3.4")
    implementation("org.springframework.boot:spring-boot-starter-cache:3.3.4")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // HTML Parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
